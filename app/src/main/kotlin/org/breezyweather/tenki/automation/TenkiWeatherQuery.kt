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
import breezyweather.domain.weather.model.Weather
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
     * A field the chosen source does not carry comes back as the **empty string**, never as a zero:
     * the band draws whatever it is handed, and a fabricated 0 °C on the wrist is worse than a blank.
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
                kind = "observed",
                // When we fetched it. An observation has a real measurement behind it.
                observedAt = weather.base.currentUpdateTime ?: weather.base.refreshTime,
                fetchedAt = weather.base.currentUpdateTime ?: weather.base.refreshTime
            )
        } ?: hour?.temperature?.temperature?.let { temperature ->
            Reading(
                temperature = temperature.inCelsius,
                humidity = hour.relativeHumidity?.inPercent,
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
                "unit" to UNIT
            )
        )
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
    private fun List<breezyweather.domain.weather.model.Hourly>.nearestTo(now: Date) =
        minByOrNull { abs(it.date.time - now.time) }
            ?.takeIf { abs(it.date.time - now.time) <= HOUR_TOLERANCE_MS }

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
