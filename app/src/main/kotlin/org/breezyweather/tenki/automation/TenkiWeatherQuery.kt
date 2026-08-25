/*
 * 白い熊 天気 (shiroikuma-tenki) fork: the weather half of the sister-app automation contract —
 * 白い熊 自由作業盤 reads figures from our cache and pushes them to the HUAWEI band's watch face.
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package org.breezyweather.tenki.automation

import android.content.Context
import breezyweather.data.location.LocationRepository
import breezyweather.data.weather.WeatherRepository
import breezyweather.domain.location.model.Location
import breezyweather.domain.source.SourceFeature
import breezyweather.domain.weather.model.Current
import breezyweather.domain.weather.model.Daily
import breezyweather.domain.weather.model.Hourly
import breezyweather.domain.weather.model.Precipitation
import breezyweather.domain.weather.model.Weather
import breezyweather.domain.weather.reference.WeatherCode
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.breezyweather.common.source.ConfigurableSource
import org.breezyweather.common.source.getName
import org.breezyweather.domain.location.model.getPlace
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.sources.SourceManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The read-only half of the automation surface: which locations we hold, which forecast sources
 * each one carries, and one location-and-source's figures for a watch face.
 *
 * **Cache only.** Every answer comes out of the database exactly as the app last stored it — no
 * network, no geocoding, no API quota. A watch face updates constantly and must work with the
 * phone offline, so going out to a source on each request would be both slow and expensive.
 * Staleness is reported rather than repaired: see [Answer] and the `stale` / `age_minutes` extras.
 *
 * The honesty rule this whole file exists to keep: **an alternate forecast source has no current
 * conditions.** Only the location's own [Location.forecastSource] produces an observed reading;
 * every other source cached against that location carries forecast arrays alone. So a request for
 * "the temperature right now, from source X" can be answered truthfully only when X is the primary,
 * and otherwise the best available figure is X's own forecast for the hour containing now. The two
 * are never blurred — `temperature_kind` says which one is on the wrist.
 *
 * The same honesty governs the hourly and daily **series** the band's forecast screen needs: every
 * element is a figure the chosen source really holds for that hour or that day, and a slot we do not
 * hold is an empty element. Nothing is ever interpolated and nothing is ever repeated to fill a
 * length — a temperature copied across 24 hours draws as a flat line, which is a lie with a chart
 * around it. Padding, if the band wants any, is the band's decision to make with its eyes open.
 */
object TenkiWeatherQuery {

    /** °C, one decimal, whatever unit the app itself is set to display. */
    private const val UNIT = "C"

    /**
     * How far the hour we report may sit from now. A source that goes six-hourly out in the week
     * (MET Norway does exactly that) must still answer, but a forecast whose nearest hour is half a
     * day away is not a reading of the present and is refused instead.
     */
    private const val HOUR_TOLERANCE_MS = 6 * 60 * 60 * 1000L

    /** How near a saved location a bare coordinate pair has to fall to mean that location. */
    private const val SNAP_TOLERANCE_KM = 25.0

    private const val EARTH_RADIUS_KM = 6371.0

    private const val HOUR_MS = 60 * 60 * 1000L

    private const val DAY_MS = 24 * HOUR_MS

    /**
     * How many hours the band's forecast wants. Its push is refused outright below this — and the
     * refusal takes the current-conditions half down with it, since the band treats the two as one
     * record — so the count is a hard part of the contract, not a preference.
     */
    private const val HOURLY_SLOTS = 24

    /** The most days the band accepts. It refuses below eight; we send what we hold up to this. */
    private const val DAILY_SLOTS_MAX = 15

    /** How far a cached hour may sit from the slot it fills before it is simply not that hour. */
    private const val SLOT_TOLERANCE_MS = 15 * 60 * 1000L

    /**
     * The same for a day. Two sources date the same day at the same local midnight, so this only has
     * to survive one that dates its days at noon — never enough to reach a neighbouring day.
     */
    private const val DAY_TOLERANCE_MS = 12 * HOUR_MS

    /** Beyond this a name is not a short label any more, and the bare city is used instead. */
    private const val SHORT_PLACE_MAX = 12

    /**
     * The status line, plus the named extras that ride beside it.
     *
     * [result] is the single `OK:` / `ERROR:` string the sister-app contract already gates on, so a
     * caller tests one variable here exactly as it does for the export. The figures are additionally
     * broken out into [extras] so nothing has to be parsed out of the status line.
     */
    data class Answer(
        val result: String,
        val extras: Map<String, String> = emptyMap(),
    )

    /**
     * One coherent reading: the figure, what KIND of thing it is, and the two timestamps that mean
     * different things for each kind. Kept together deliberately — the whole point of the contract is
     * that a caller is never handed an observed temperature next to a forecast humidity.
     */
    private data class Reading(
        val temperature: Double,
        val humidity: Double?,
        val uv: Double?,
        /** km/h, which is what the band's push carries — our own store keeps m/s. */
        val windSpeedKph: Double?,
        val kind: String,
        /** For `observed`, when we fetched it; for `hourly`, the forecast hour, possibly ahead of now. */
        val observedAt: Date?,
        /** Always when we fetched the data, for both kinds — this is what `age_minutes` measures. */
        val fetchedAt: Date?,
    )

    @InstallIn(SingletonComponent::class)
    @EntryPoint
    interface TenkiAutomationEntryPoint {
        fun locationRepository(): LocationRepository
        fun weatherRepository(): WeatherRepository
        fun sourceManager(): SourceManager
    }

    private fun entryPoint(context: Context): TenkiAutomationEntryPoint =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            TenkiAutomationEntryPoint::class.java
        )

    // ------------------------------------------------------------------ LIST_LOCATIONS

    /** `formattedId<TAB>name<TAB>primary_source`, one saved location per line. */
    suspend fun listLocations(context: Context): Answer {
        val entry = entryPoint(context)
        val locations = entry.locationRepository().getAllLocations(withParameters = false)
        if (locations.isEmpty()) return Answer("ERROR:no locations")

        return Answer(
            "OK:" + locations.joinToString("\n") { location ->
                listOf(
                    location.formattedId,
                    location.getPlace(context),
                    location.forecastSource
                ).joinToString("\t") { it.oneLine() }
            }
        )
    }

    // ------------------------------------------------------------------ LIST_PROVIDERS

    /**
     * `id<TAB>name<TAB>configured<TAB>cached`, one forecast source per line.
     *
     * Never empty for a location we know: [Location.orderedHourlyForecastSources] is self-healing
     * and always contains the primary, which is the source baked into the location's identity. A
     * blank list coming out of here would be a bug, not a state worth rendering.
     *
     * `cached` is the honest half — it says whether we are actually holding that source's arrays
     * right now, which a location that has never been refreshed is not.
     */
    suspend fun listProviders(
        context: Context,
        locationExtra: String?,
        latitude: String?,
        longitude: String?,
        all: Boolean,
    ): Answer {
        val entry = entryPoint(context)
        val location = resolveLocation(entry, locationExtra, latitude, longitude)
            ?: return Answer("ERROR:unknown location")
        val weather = entry.weatherRepository().getWeatherByLocationId(
            location.formattedId,
            withMinutely = false,
            withAlerts = false,
            withNormals = false
        )
        val sourceManager = entry.sourceManager()

        val own = location.orderedForecastSources
        val lines = own.map { id ->
            providerLine(context, sourceManager, location, id, cached = weather.holds(location, id))
        }.toMutableList()

        if (all) {
            // The whole catalogue, minus what we already listed: a source this location does not
            // fetch is still a legitimate thing for 白い熊 to point the band at, once they add it.
            sourceManager.getSupportedFeatureSources(SourceFeature.FORECAST, location)
                .map { it.id }
                .filter { it !in own }
                .forEach { id ->
                    lines.add(providerLine(context, sourceManager, location, id, cached = false))
                }
        }

        return Answer("OK:" + lines.joinToString("\n"))
    }

    private fun providerLine(
        context: Context,
        sourceManager: SourceManager,
        location: Location,
        id: String,
        cached: Boolean,
    ): String {
        val source = sourceManager.getFeatureSource(id)
        // Falls back to the bare id, which is all we can say about a source no longer built in.
        val name = source?.getName(context, SourceFeature.FORECAST, location) ?: id
        val configured = source !is ConfigurableSource || (source.isConfigured && !source.isRestricted)
        return listOf(id, name, configured.flag(), cached.flag()).joinToString("\t") { it.oneLine() }
    }

    // ------------------------------------------------------------------ QUERY_WEATHER

    /**
     * One location-and-source's figures, as strings.
     *
     * A field nobody has comes back as the **empty string**, never as a zero: the band draws whatever
     * it is handed, and a fabricated 0 °C on the wrist is worse than a blank.
     *
     * Beside the single figures ride the two series the band's forecast screen needs: [HOURLY_SLOTS]
     * hours from the one now in progress, and up to [DAILY_SLOTS_MAX] days from today. They are
     * comma-separated with no spaces, every array of a group is the same length, and position `i`
     * therefore means the same hour or the same day in each of them.
     *
     * **This app holds several sources per location, so an empty answer must mean nobody has it.** A
     * field the chosen source does not carry is borrowed from the next source cached against this
     * location that does, walking [Location.orderedForecastSources] in the order 白い熊 arranged them.
     * The borrow is per FIELD, not per query: a source that knows the temperature but not the UV index
     * no longer costs us the UV index. `<key>_source` then names where the field came from, and is
     * blank whenever the chosen source supplied it itself — a borrowed figure is never passed off as
     * the chosen source's own.
     *
     * Borrowing never blurs a series: a whole array comes from one source or not at all, and it is
     * re-indexed onto the hours and days the grid already defines, so position `i` still means the
     * same hour whichever source filled it. Pasting a second source's array in at position 0 would
     * shift every hour, and mixing sources element by element would put a different forecast at each
     * position — the flat line's lie in another costume.
     */
    suspend fun queryWeather(
        context: Context,
        locationExtra: String?,
        latitude: String?,
        longitude: String?,
        providerExtra: String?,
    ): Answer {
        val entry = entryPoint(context)
        val location = resolveLocation(entry, locationExtra, latitude, longitude)
            ?: return Answer("ERROR:unknown location")
        val sourceManager = entry.sourceManager()

        val providerId = providerExtra?.trim()?.takeIf { it.isNotEmpty() } ?: location.forecastSource
        if (sourceManager.getFeatureSource(providerId) == null) {
            return Answer("ERROR:unknown provider")
        }
        if (providerId !in location.orderedForecastSources) {
            return Answer("ERROR:no data for provider")
        }

        val weather = entry.weatherRepository().getWeatherByLocationId(
            location.formattedId,
            withMinutely = false,
            withAlerts = false,
            withNormals = false
        ) ?: return Answer("ERROR:no data for provider")

        // The alternates carry only arrays, so an alternate is read through a Weather wearing them —
        // which is exactly how the stacked charts read one, and keeps `today` upstream's own rule.
        val isPrimary = providerId == location.forecastSource
        val view = if (isPrimary) {
            weather
        } else {
            val alternate = weather.alternateForecasts[providerId]?.takeIf { !it.isEmpty }
                ?: return Answer("ERROR:no data for provider")
            weather.copy(
                dailyForecast = alternate.dailyForecast,
                hourlyForecast = alternate.hourlyForecast
            )
        }

        val now = Date()
        // Only the primary can hold an observation, and even it may not: plenty of sources report no
        // current conditions at all. Falling back to that source's own forecast hour is still the
        // truth, as long as we say which one this is.
        val observedCurrent = if (isPrimary) weather.current else null
        val hour = view.hourlyForecast.nearestTo(now)

        // Taken as a whole rather than field by field, so an observed temperature can never end up
        // reported beside a humidity that came out of a forecast.
        val reading = observedCurrent?.temperature?.temperature?.let { temperature ->
            Reading(
                temperature = temperature.inCelsius,
                humidity = observedCurrent.relativeHumidity?.inPercent,
                uv = observedCurrent.uV?.index,
                windSpeedKph = observedCurrent.wind?.speed?.inKilometersPerHour,
                kind = "observed",
                // When we fetched it. An observation has a real measurement behind it.
                observedAt = weather.base.currentUpdateTime ?: weather.base.refreshTime,
                fetchedAt = weather.base.currentUpdateTime ?: weather.base.refreshTime
            )
        } ?: hour?.temperature?.temperature?.let { temperature ->
            Reading(
                temperature = temperature.inCelsius,
                humidity = hour.relativeHumidity?.inPercent,
                uv = hour.uV?.index,
                windSpeedKph = hour.wind?.speed?.inKilometersPerHour,
                kind = "hourly",
                // The forecast hour itself, which sits up to an hour in the FUTURE and is NOT a
                // measurement time — nothing was measured. Freshness belongs to age_minutes and
                // stale, never to arithmetic on this field.
                observedAt = hour.date,
                fetchedAt = weather.base.forecastUpdateTime ?: weather.base.refreshTime
            )
        } ?: return Answer("ERROR:no data for provider")

        val today = view.today
        val stale = !weather.isValid(SettingsManager.getInstance(context).updateInterval.interval)

        // The chosen source first, then every other source this location caches, in 白い熊's own
        // order. This is the order a missing field is borrowed along, so the answer prefers what the
        // charts already put nearest the top.
        val candidates = (listOf(providerId) + location.orderedForecastSources.filter { it != providerId })
            .mapNotNull { candidate(context, sourceManager, weather, location, it) }

        // The grid both series are laid on. Taken from the chosen source where it has one, and
        // otherwise from the first source that does — so a source with no hours of its own can still
        // answer with days, and a borrowed array has somewhere fixed to be re-indexed onto.
        val hourStart = candidates.firstNotNullOfOrNull { hourStartOf(it.hourly, now) }
        val dayAnchors = candidates.firstNotNullOfOrNull { daysFromToday(it.daily, now) }
            ?.take(DAILY_SLOTS_MAX)
            ?.map { it.date }
            .orEmpty()
        val series = candidates.map {
            Series(it, hourSlots(it.hourly, hourStart), daySlots(it.daily, dayAnchors))
        }

        val uv = borrow(series) { (if (it.of.id == providerId) reading.uv else it.of.uvNow(now)).whole() }
        val uvMax = borrow(series) { it.of.uvMaxToday(now).whole() }
        val windSpeed = borrow(series) {
            (if (it.of.id == providerId) reading.windSpeedKph else it.of.windNow(now)).whole()
        }

        val hourlyTemperature = borrow(series) { s ->
            cells(s.hours) { it?.temperature?.temperature?.inCelsius.whole() }
        }
        val hourlyCondition = borrow(series) { s ->
            cells(s.hours) {
                conditionWord(it?.weatherCode, it?.precipitation?.total?.inMillimeters, HEAVY_HOURLY_MM)
            }
        }
        val hourlyUv = borrow(series) { uvCells(it.hours) }
        val hourlyFeelsLike = borrow(series) { s ->
            cells(s.hours) { it?.temperature?.feelsLikeTemperature?.inCelsius.whole() }
        }

        val dailyHigh = borrow(series) { s ->
            cells(s.days) { it?.day?.temperature?.temperature?.inCelsius.whole() }
        }
        val dailyLow = borrow(series) { s ->
            cells(s.days) { it?.night?.temperature?.temperature?.inCelsius.whole() }
        }
        val dailyCondition = borrow(series) { s ->
            cells(s.days) {
                conditionWord(
                    it?.day?.weatherCode,
                    it?.day?.precipitation?.total?.inMillimeters,
                    HEAVY_HALF_DAY_MM
                )
            }
        }

        // A group that carries anything keeps every one of its keys at the group's length, so
        // position `i` still lines up across the arrays even where a whole field is missing.
        val hourlyGroup = listOf(hourlyTemperature, hourlyCondition, hourlyUv, hourlyFeelsLike)
        val dailyGroup = listOf(dailyHigh, dailyLow, dailyCondition)
        val blankHours = if (hourlyGroup.any { it != null }) blank(HOURLY_SLOTS) else ""
        val blankDays = if (dailyGroup.any { it != null }) blank(dayAnchors.size) else ""

        return Answer(
            "OK:$providerId",
            mapOf(
                "temperature" to reading.temperature.oneDecimal(),
                "high" to today?.day?.temperature?.temperature?.inCelsius.oneDecimal(),
                "low" to today?.night?.temperature?.temperature?.inCelsius.oneDecimal(),
                "humidity" to reading.humidity.whole(),
                "place" to location.getPlace(context).oneLine(),
                "provider" to providerId,
                "provider_name" to (
                    sourceManager.getFeatureSource(providerId)
                        ?.getName(context, SourceFeature.FORECAST, location)
                        ?: providerId
                    ).oneLine(),
                "temperature_kind" to reading.kind,
                "observed_at" to reading.observedAt.iso(location),
                "observed_at_epoch" to (reading.observedAt?.let { it.time / 1000 }?.toString() ?: ""),
                "age_minutes" to reading.fetchedAt.ageMinutes(now),
                "stale" to stale.flag(),
                "unit" to UNIT,
                "place_short" to shortPlace(context, location),

                // The band lays our 24 hours across two calendar days, so it needs to know where the
                // day boundary falls before it can take a maximum over "today" rather than over both.
                "hourly_start_epoch" to (hourStart?.let { it / 1000 }?.toString() ?: ""),
                "daily_start_epoch" to (dayAnchors.firstOrNull()?.let { it.time / 1000 }?.toString() ?: ""),
                "utc_offset_seconds" to (location.timeZone.getOffset(now.time) / 1000).toString(),

                "uv" to uv.value(),
                "uv_source" to uv.origin(providerId),
                "uv_max" to uvMax.value(),
                "uv_max_source" to uvMax.origin(providerId),
                "wind_speed" to windSpeed.value(),
                "wind_speed_source" to windSpeed.origin(providerId),

                "hourly_temperature" to hourlyTemperature.value(blankHours),
                "hourly_temperature_source" to hourlyTemperature.origin(providerId),
                "hourly_condition" to hourlyCondition.value(blankHours),
                "hourly_condition_source" to hourlyCondition.origin(providerId),
                "hourly_uv" to hourlyUv.value(blankHours),
                "hourly_uv_source" to hourlyUv.origin(providerId),
                "hourly_feels_like" to hourlyFeelsLike.value(blankHours),
                "hourly_feels_like_source" to hourlyFeelsLike.origin(providerId),

                "daily_high" to dailyHigh.value(blankDays),
                "daily_high_source" to dailyHigh.origin(providerId),
                "daily_low" to dailyLow.value(blankDays),
                "daily_low_source" to dailyLow.origin(providerId),
                "daily_condition" to dailyCondition.value(blankDays),
                "daily_condition_source" to dailyCondition.origin(providerId)
            )
        )
    }

    // ------------------------------------------------------------------ borrowing across sources

    /** One cached source's arrays, plus the name a borrowed field is credited to. */
    private data class Candidate(
        val id: String,
        val name: String,
        val hourly: List<Hourly>,
        val daily: List<Daily>,
        /** Only the location's own forecast source can hold an observation; every other one is null. */
        val current: Current?,
    )

    /** One source's arrays already laid on the shared grid, so every candidate indexes alike. */
    private data class Series(val of: Candidate, val hours: List<Hourly?>, val days: List<Daily?>)

    /** A rendered field and the source it actually came from. */
    private data class Borrowed(val text: String, val from: Candidate)

    private fun candidate(
        context: Context,
        sourceManager: SourceManager,
        weather: Weather,
        location: Location,
        id: String,
    ): Candidate? {
        val name = sourceManager.getFeatureSource(id)
            ?.getName(context, SourceFeature.FORECAST, location)
            ?: id
        return if (id == location.forecastSource) {
            if (weather.hourlyForecast.isEmpty() && weather.dailyForecast.isEmpty()) {
                null
            } else {
                Candidate(id, name.oneLine(), weather.hourlyForecast, weather.dailyForecast, weather.current)
            }
        } else {
            weather.alternateForecasts[id]?.takeIf { !it.isEmpty }?.let {
                Candidate(id, name.oneLine(), it.hourlyForecast, it.dailyForecast, null)
            }
        }
    }

    /** The first source that renders this field to anything at all, walking them in order. */
    private fun borrow(series: List<Series>, render: (Series) -> String): Borrowed? =
        series.firstNotNullOfOrNull { one ->
            render(one).takeIf { it.isNotEmpty() }?.let { Borrowed(it, one.of) }
        }

    private fun Borrowed?.value(blank: String = ""): String = this?.text ?: blank

    /** Blank when the chosen source supplied it: only a BORROWED field names a source. */
    private fun Borrowed?.origin(chosen: String): String =
        this?.takeIf { it.from.id != chosen }?.from?.name.orEmpty()

    private fun Candidate.uvNow(now: Date): Double? =
        current?.uV?.index ?: hourly.nearestTo(now)?.uV?.index

    private fun Candidate.windNow(now: Date): Double? =
        current?.wind?.speed?.inKilometersPerHour ?: hourly.nearestTo(now)?.wind?.speed?.inKilometersPerHour

    /**
     * Today's peak, stated by the daily entry where the source states one and otherwise the highest
     * of today's hours — a maximum over figures we hold is a reading, not a guess.
     */
    private fun Candidate.uvMaxToday(now: Date): Double? {
        val today = daysFromToday(daily, now)?.firstOrNull() ?: return null
        return today.uV?.index ?: hourly
            .filter { it.date.time >= today.date.time && it.date.time < today.date.time + DAY_MS }
            .mapNotNull { it.uV?.index }
            .maxOrNull()
    }

    // ------------------------------------------------------------------ the band's two series

    /**
     * Where slot 0 sits: the hour now in progress, in the DATA's own alignment rather than our
     * clock's. A half-hour zone (India, Nepal) puts its hours on the half hour, and snapping those to
     * a whole UTC hour would miss every slot by thirty minutes.
     */
    private fun hourStartOf(hours: List<Hourly>, now: Date): Long? {
        if (hours.isEmpty()) return null
        // Both operands are milliseconds since 1970 and so never negative; plain division floors.
        val offset = hours.minOf { it.date.time } % HOUR_MS
        return (now.time - offset) / HOUR_MS * HOUR_MS + offset
    }

    /**
     * [HOURLY_SLOTS] consecutive hours from [start], each filled by the cached hour nearest it.
     *
     * A slot with nothing near it stays null, which is how a source that reports three-hourly comes
     * out honestly sparse instead of quietly stretched — and how a BORROWED source lands on the right
     * hours instead of being pasted in at position 0.
     */
    private fun hourSlots(hours: List<Hourly>, start: Long?): List<Hourly?> {
        if (start == null) return emptyList()
        return (0 until HOURLY_SLOTS).map { slot ->
            val at = start + slot * HOUR_MS
            hours.minByOrNull { abs(it.date.time - at) }
                ?.takeIf { abs(it.date.time - at) <= SLOT_TOLERANCE_MS }
        }
    }

    /** Upstream's own rule for where today starts, applied to whichever source's list this is. */
    private fun daysFromToday(days: List<Daily>, now: Date): List<Daily>? {
        val index = days.indexOfFirst { it.date.time > now.time - DAY_MS }
        return if (index < 0) null else days.subList(index, days.size).takeIf { it.isNotEmpty() }
    }

    /** The same re-indexing for days: matched by date, so position `i` is one day for every source. */
    private fun daySlots(days: List<Daily>, anchors: List<Date>): List<Daily?> = anchors.map { anchor ->
        days.minByOrNull { abs(it.date.time - anchor.time) }
            ?.takeIf { abs(it.date.time - anchor.time) <= DAY_TOLERANCE_MS }
    }

    /** Joined — or empty when not one slot carries a figure, which is what makes the field borrowable. */
    private fun <T> cells(slots: List<T?>, cell: (T?) -> String): String {
        val rendered = slots.map(cell)
        return if (rendered.any { it.isNotEmpty() }) rendered.joinToString(",") else ""
    }

    /**
     * The UV hours, empty for a source that reports no UV at all — which is what sends the field on
     * to the next source rather than answering with a night's worth of zeroes.
     */
    private fun uvCells(slots: List<Hourly?>): String {
        if (slots.none { it?.uV?.index != null }) return ""
        return slots.joinToString(",") { hour ->
            val index = hour?.uV?.index
            when {
                index != null -> index.whole()
                // A source that publishes UV by day only is not missing the night figure: after dark
                // the index IS zero. Reached only for a source that reports UV, per the guard above.
                hour != null && !hour.isDaylight -> "0"
                else -> ""
            }
        }
    }

    private fun blank(length: Int): String = List(length) { "" }.joinToString(",")

    /**
     * Our weather code as one of the sixteen words the band's push understands.
     *
     * Upstream's [WeatherCode] set is coarser than that vocabulary, and the gap is closed only where
     * we hold a figure that closes it: the amount of precipitation, against the thresholds the app
     * itself classifies by, tells heavy rain and heavy snow from ordinary ones. The words that would
     * need a distinction we do not measure — `mostly_clear`, `overcast`, `drizzle` — are deliberately
     * never sent, and an unknown code sends an empty element so the band falls back rather than being
     * handed a guess.
     */
    private fun conditionWord(code: WeatherCode?, precipitationMm: Double?, heavyMm: Double): String {
        val heavy = precipitationMm != null && precipitationMm >= heavyMm
        return when (code) {
            WeatherCode.CLEAR -> "clear"
            WeatherCode.PARTLY_CLOUDY -> "partly_cloudy"
            WeatherCode.CLOUDY -> "cloudy"
            WeatherCode.FOG -> "fog"
            WeatherCode.HAZE -> "haze"
            WeatherCode.SLEET -> "sleet"
            WeatherCode.HAIL -> "hail"
            WeatherCode.WIND -> "wind"
            // The band has one word for both, and thunder without rain is still a thunderstorm to it.
            WeatherCode.THUNDER, WeatherCode.THUNDERSTORM -> "thunderstorm"
            WeatherCode.RAIN -> if (heavy) "heavy_rain" else "rain"
            WeatherCode.SNOW -> if (heavy) "heavy_snow" else "snow"
            null -> ""
        }
    }

    /**
     * A label that fits the band's one line, which cuts anything longer — `プラハ, Jiráskova čtvrť`
     * arrives there with its head bitten off.
     *
     * `place` is untouched and this rides beside it. A custom name 白い熊 kept short IS the short
     * label; otherwise the city alone is the shortest true name of the place.
     */
    private fun shortPlace(context: Context, location: Location): String {
        val custom = location.customName?.trim().orEmpty()
        if (custom.isNotEmpty() && custom.codePointCount(0, custom.length) <= SHORT_PLACE_MAX) {
            return custom.oneLine()
        }
        return listOf(location.city, location.district.orEmpty(), custom)
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.oneLine()
            ?: location.getPlace(context, showCurrentPositionInPriority = true).oneLine()
    }

    // ------------------------------------------------------------------ resolving

    /**
     * Blank means the first saved location, `current` the current-position one, anything else a
     * `formattedId` from LIST_LOCATIONS.
     *
     * Coordinates are a **fallback, not an address**: they snap to a saved location within
     * [SNAP_TOLERANCE_KM] and otherwise fail, because we hold weather per saved location and a point
     * we have never been asked to track has nothing cached behind it.
     */
    private suspend fun resolveLocation(
        entry: TenkiAutomationEntryPoint,
        locationExtra: String?,
        latitude: String?,
        longitude: String?,
    ): Location? {
        val repository = entry.locationRepository()
        val requested = locationExtra?.trim().orEmpty()

        if (requested.isEmpty() && latitude.isNullOrBlank() && longitude.isNullOrBlank()) {
            return repository.getFirstLocation(withParameters = false)
        }
        if (requested.equals("current", ignoreCase = true)) {
            return repository.getLocation(Location.CURRENT_POSITION_ID, withParameters = false)
        }
        if (requested.isNotEmpty()) {
            repository.getLocation(requested, withParameters = false)?.let { return it }
        }

        val lat = latitude?.trim()?.toDoubleOrNull()
        val lon = longitude?.trim()?.toDoubleOrNull()
        if (lat == null || lon == null) return null

        return repository.getAllLocations(withParameters = false)
            .map { it to distanceKm(lat, lon, it.latitude, it.longitude) }
            .filter { it.second <= SNAP_TOLERANCE_KM }
            .minByOrNull { it.second }
            ?.first
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    // ------------------------------------------------------------------ small helpers

    /** Whether we are actually holding this source's arrays for this location right now. */
    private fun Weather?.holds(location: Location, sourceId: String): Boolean = when {
        this == null -> false
        sourceId == location.forecastSource -> dailyForecast.isNotEmpty() || hourlyForecast.isNotEmpty()
        else -> alternateForecasts[sourceId]?.isEmpty == false
    }

    /**
     * The hour containing now — or the nearest one either side, since a source that reports
     * six-hourly has no hour containing anything. Beyond [HOUR_TOLERANCE_MS] there is no reading of
     * the present to give, and null is the honest answer.
     */
    private fun List<Hourly>.nearestTo(now: Date) =
        minByOrNull { abs(it.date.time - now.time) }
            ?.takeIf { abs(it.date.time - now.time) <= HOUR_TOLERANCE_MS }

    /** Upstream's own "heavy" marks, so the band is told what the app itself would call heavy. */
    private const val HEAVY_HOURLY_MM = Precipitation.PRECIPITATION_HOURLY_HEAVY
    private const val HEAVY_HALF_DAY_MM = Precipitation.PRECIPITATION_HALF_DAY_HEAVY

    private fun Boolean.flag(): String = if (this) "1" else "0"

    private fun Double?.oneDecimal(): String =
        this?.let { String.format(Locale.US, "%.1f", it) } ?: ""

    private fun Double?.whole(): String =
        this?.let { String.format(Locale.US, "%d", it.roundToLong()) } ?: ""

    private fun Date?.iso(location: Location): String = this?.let {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            .apply { timeZone = location.timeZone }
            .format(it)
    } ?: ""

    private fun Date?.ageMinutes(now: Date): String =
        this?.let { ((now.time - it.time) / 60000L).toString() } ?: ""

    /** A tab or a newline in a name would split the very line it sits on. */
    private fun String.oneLine(): String = replace('\t', ' ').replace('\n', ' ').trim()
}
