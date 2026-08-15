/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package org.breezyweather.sources.chmi

import android.content.Context
import breezyweather.domain.location.model.Location
import breezyweather.domain.source.SourceContinent
import breezyweather.domain.source.SourceFeature
import breezyweather.domain.weather.model.AirQuality
import breezyweather.domain.weather.model.Alert
import breezyweather.domain.weather.model.DailyCloudCover
import breezyweather.domain.weather.model.Normals
import breezyweather.domain.weather.model.Precipitation
import breezyweather.domain.weather.model.Wind
import breezyweather.domain.weather.reference.AlertSeverity
import breezyweather.domain.weather.reference.Month
import breezyweather.domain.weather.reference.WeatherCode
import breezyweather.domain.weather.wrappers.AirQualityWrapper
import breezyweather.domain.weather.wrappers.CurrentWrapper
import breezyweather.domain.weather.wrappers.DailyWrapper
import breezyweather.domain.weather.wrappers.HalfDayWrapper
import breezyweather.domain.weather.wrappers.HourlyWrapper
import breezyweather.domain.weather.wrappers.TemperatureWrapper
import breezyweather.domain.weather.wrappers.WeatherWrapper
import com.google.maps.android.PolyUtil
import com.google.maps.android.SphericalUtil
import com.google.maps.android.data.geojson.GeoJsonFeature
import com.google.maps.android.data.geojson.GeoJsonMultiPolygon
import com.google.maps.android.data.geojson.GeoJsonParser
import com.google.maps.android.data.geojson.GeoJsonPolygon
import com.google.maps.android.model.LatLng
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Observable
import org.breezyweather.R
import org.breezyweather.common.exceptions.InvalidLocationException
import org.breezyweather.common.extensions.currentLocale
import org.breezyweather.common.extensions.getCountryName
import org.breezyweather.common.extensions.getIsoFormattedDate
import org.breezyweather.common.extensions.parseRawGeoJson
import org.breezyweather.common.extensions.toCalendar
import org.breezyweather.common.extensions.toDateNoHour
import org.breezyweather.common.source.HttpSource
import org.breezyweather.common.source.LocationParametersSource
import org.breezyweather.common.source.WeatherSource
import org.breezyweather.common.source.WeatherSource.Companion.PRIORITY_HIGHEST
import org.breezyweather.common.source.WeatherSource.Companion.PRIORITY_NONE
import org.breezyweather.common.utils.ISO8601Utils
import org.breezyweather.sources.chmi.json.ChmiAirQualityMetadata
import org.breezyweather.sources.chmi.json.ChmiDataResult
import org.breezyweather.sources.chmi.json.ChmiMeteogramHour
import org.breezyweather.sources.chmi.json.ChmiMeteogramResult
import org.breezyweather.sources.chmi.json.ChmiOutlookResult
import org.breezyweather.sources.chmi.json.ChmiTextForecastResult
import org.breezyweather.sources.common.xml.CapAlert
import org.breezyweather.unit.pollutant.PollutantConcentration.Companion.microgramsPerCubicMeter
import org.breezyweather.unit.precipitation.Precipitation.Companion.millimeters
import org.breezyweather.unit.pressure.Pressure.Companion.hectopascals
import org.breezyweather.unit.ratio.Ratio.Companion.fraction
import org.breezyweather.unit.ratio.Ratio.Companion.percent
import org.breezyweather.unit.speed.Speed.Companion.metersPerSecond
import org.breezyweather.unit.temperature.Temperature.Companion.celsius
import retrofit2.Retrofit
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Objects
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Named
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Český hydrometeorologický ústav — the Czech national weather service. Everything it
 * publishes is free of charge under CC BY 4.0.
 *
 *  - **Alerts.** Two CAP 1.2 bulletins: the SIVS warnings and, separately, drought. Areas are
 *    geocoded by `CISORP` — the ČSÚ code of a *správní obvod obce s rozšířenou působností* —
 *    with no polygon, so which ORP a location sits in is answered offline from the boundaries
 *    bundled in `R.raw.chmi_orp` (cut by `tools/chmi/build_orp_geojson.py`).
 *  - **Current.** Ten-minute readings from the nearest station, deepened with sea-level
 *    pressure, dew point and cloud cover where a professional station is close enough, and
 *    captioned with the duty forecaster's own regional text forecast.
 *  - **Air quality.** The national monitoring network, all six pollutants in µg/m³.
 *  - **Normals.** The 1991–2020 monthly means of the daily maximum and minimum.
 *  - **Forecast.** ALADIN hour by hour, from the API behind ČHMÚ's own web pages
 *    ([ChmiDataProviderApi]). Three days for the exact coordinates, and past the end of that
 *    horizon the national nine-day outlook, which is the same figure everywhere in the
 *    country. The hours already gone are replaced with what the nearest station actually
 *    measured, out of the very stream the current reading is built from.
 *
 * ALADIN reaches the app through the Open-Meteo source as well, as the `chmi_aladin_*`
 * models. The two are meant to be stacked: same model, different pipe, and where they
 * disagree it is the post-processing that differs.
 */
class ChmiService @Inject constructor(
    @ApplicationContext context: Context,
    @Named("JsonClient") jsonClient: Retrofit.Builder,
    @Named("XmlClient") xmlClient: Retrofit.Builder,
) : HttpSource(),
    WeatherSource,
    LocationParametersSource {

    override val id = "chmi"
    override val name = "ČHMÚ (${context.currentLocale.getCountryName("CZ")})"
    override val continent = SourceContinent.EUROPE
    override val privacyPolicyUrl =
        "https://www.chmi.cz/o-chmu/povinne-zverejnovane-informace/zasady-prace-s-cookies"

    private val weatherAttribution = "Český hydrometeorologický ústav"

    private val mApi by lazy {
        jsonClient.baseUrl(CHMI_BASE_URL).build().create(ChmiApi::class.java)
    }
    private val mCapApi by lazy {
        xmlClient.baseUrl(CHMI_CAP_BASE_URL).build().create(ChmiCapApi::class.java)
    }
    private val mDataProviderApi by lazy {
        jsonClient.baseUrl(CHMI_DATA_PROVIDER_BASE_URL).build().create(ChmiDataProviderApi::class.java)
    }

    /**
     * Parsed on first use only — [requestLocationParameters] resolves the ORP once per
     * location, so a refresh never touches this.
     */
    private val orpBoundaries: GeoJsonParser by lazy {
        context.parseRawGeoJson(R.raw.chmi_orp)
    }

    override val supportedFeatures = mapOf(
        SourceFeature.FORECAST to weatherAttribution,
        SourceFeature.CURRENT to weatherAttribution,
        SourceFeature.AIR_QUALITY to weatherAttribution,
        SourceFeature.ALERT to weatherAttribution,
        SourceFeature.NORMALS to weatherAttribution
    )
    override val attributionLinks = mapOf(
        weatherAttribution to "https://www.chmi.cz/"
    )

    override fun isFeatureSupportedForLocation(
        location: Location,
        feature: SourceFeature,
    ): Boolean {
        return location.countryCode.equals("CZ", ignoreCase = true)
    }

    override fun getFeaturePriorityForLocation(
        location: Location,
        feature: SourceFeature,
    ): Int {
        return when {
            isFeatureSupportedForLocation(location, feature) -> PRIORITY_HIGHEST
            else -> PRIORITY_NONE
        }
    }

    override fun requestWeather(
        context: Context,
        location: Location,
        requestedFeatures: List<SourceFeature>,
    ): Observable<WeatherWrapper> {
        val parameters = location.parameters.getOrElse(id) { null }.orEmpty()
        val failedFeatures = mutableMapOf<SourceFeature, Throwable>()

        // The forecast rides on two calls that fail independently, and either one alone still
        // makes a usable tab, so their errors are held here and only reported if both come up
        // empty.
        val forecastFailures = mutableListOf<Throwable>()

        // Carries the ten-minute observations, which serve the current reading and the hours
        // already gone on the forecast alike — hence fetched here rather than inside either.
        val current = requestCurrent(parameters, requestedFeatures, failedFeatures)

        val meteogram = if (SourceFeature.FORECAST in requestedFeatures) {
            mDataProviderApi.getMeteogram(location.longitude, location.latitude).onErrorResumeNext {
                forecastFailures.add(it)
                Observable.just(ChmiMeteogramResult())
            }
        } else {
            Observable.just(ChmiMeteogramResult())
        }

        val outlook = if (SourceFeature.FORECAST in requestedFeatures) {
            mDataProviderApi.getOutlook().onErrorResumeNext {
                forecastFailures.add(it)
                Observable.just(ChmiOutlookResult())
            }
        } else {
            Observable.just(ChmiOutlookResult())
        }

        val alerts = if (SourceFeature.ALERT in requestedFeatures) {
            requestAlerts(parameters, failedFeatures)
        } else {
            Observable.just(emptyList<CapAlert>())
        }

        val airQuality = if (SourceFeature.AIR_QUALITY in requestedFeatures) {
            if (parameters.keys.none { it.startsWith(PARAMETER_AIR_QUALITY_PREFIX) }) {
                failedFeatures[SourceFeature.AIR_QUALITY] = InvalidLocationException()
                Observable.just("")
            } else {
                mApi.getAirQuality().map { it.string() }.onErrorResumeNext {
                    failedFeatures[SourceFeature.AIR_QUALITY] = it
                    Observable.just("")
                }
            }
        } else {
            Observable.just("")
        }

        val normals = if (SourceFeature.NORMALS in requestedFeatures) {
            requestNormals(parameters, failedFeatures)
        } else {
            Observable.just(emptyMap<Month, Normals>())
        }

        return Observable.zip(current, alerts, airQuality, normals, meteogram, outlook) {
                currentResult: CurrentPieces,
                alertsResult: List<CapAlert>,
                airQualityResult: String,
                normalsResult: Map<Month, Normals>,
                meteogramResult: ChmiMeteogramResult,
                outlookResult: ChmiOutlookResult,
            ->
            val hourly = if (SourceFeature.FORECAST in requestedFeatures) {
                getHourlyList(context, meteogramResult, currentResult.observations)
            } else {
                null
            }
            val daily = if (SourceFeature.FORECAST in requestedFeatures) {
                getDailyList(context, location, hourly.orEmpty(), outlookResult)
            } else {
                null
            }
            if (SourceFeature.FORECAST in requestedFeatures && hourly.isNullOrEmpty() && daily.isNullOrEmpty()) {
                failedFeatures[SourceFeature.FORECAST] =
                    forecastFailures.firstOrNull() ?: InvalidLocationException()
            }

            WeatherWrapper(
                dailyForecast = daily,
                hourlyForecast = hourly,
                current = if (SourceFeature.CURRENT in requestedFeatures) {
                    getCurrent(currentResult)
                } else {
                    null
                },
                airQuality = if (SourceFeature.AIR_QUALITY in requestedFeatures) {
                    getAirQuality(airQualityResult, parameters)
                } else {
                    null
                },
                alertList = if (SourceFeature.ALERT in requestedFeatures) {
                    getAlertList(context, parameters[PARAMETER_ORP], alertsResult)
                } else {
                    null
                },
                normals = if (SourceFeature.NORMALS in requestedFeatures) {
                    normalsResult.ifEmpty { null }
                } else {
                    null
                },
                failedFeatures = failedFeatures
            )
        }
    }

    // Forecast

    /**
     * The meteogram, hour by hour, with the hours already behind us replaced by what the
     * nearest station actually measured.
     */
    private fun getHourlyList(
        context: Context,
        meteogram: ChmiMeteogramResult,
        observations: ChmiDataResult,
    ): List<HourlyWrapper> {
        val hours = meteogram.data.orEmpty().mapNotNull { hour ->
            hour.validityTime?.let { parseDate(it) }?.let { it to hour }
        }
        if (hours.isEmpty()) return emptyList()

        val measured = measuredHours(observations)
        // The hour we are in is left to the model: it still has readings to come, and an
        // average of the few that have arrived would read as a dip in the curve.
        val currentHour = System.currentTimeMillis() / HOUR_IN_MILLIS * HOUR_IN_MILLIS

        return hours.map { (date, hour) ->
            val past = if (date.time < currentHour) measured[date.time] else null
            HourlyWrapper(
                date = date,
                weatherText = getWeatherText(context, hour.icon),
                weatherCode = getWeatherCode(hour.icon),
                temperature = (past?.temperature ?: hour.t2m)?.let {
                    TemperatureWrapper(temperature = it.celsius)
                },
                precipitation = getPrecipitation(hour, past),
                wind = getWind(hour, past),
                relativeHumidity = (past?.humidity ?: hour.rh2m)?.percent,
                // The ten-minute stream reports the pressure at the station, not one reduced
                // to sea level, so the model's stands even for an hour that is past.
                pressure = hour.mslp?.hectopascals,
                // Neither an icon nor a cloud amount is measured by these stations.
                cloudCover = hour.cloudsTot?.percent,
                sunshineDuration = past?.sunshine
            )
        }
    }

    /**
     * The ten-minute stream folded into whole hours, keyed by the hour it opens.
     *
     * Rain and sunshine accumulate, so they are summed; everything else is a state, and is
     * averaged. A gust is the strongest of the hour rather than the mean of them, since that
     * is what a gust means.
     */
    private fun measuredHours(
        observations: ChmiDataResult,
    ): Map<Long, MeasuredHour> {
        val table = observations.data?.data ?: return emptyMap()
        val readings = mutableMapOf<Long, MutableMap<String, MutableList<Double>>>()
        table.rows().forEach { row ->
            val element = table.string(row, COLUMN_ELEMENT) ?: return@forEach
            if (element !in MEASURED_ELEMENTS) return@forEach
            val quality = table.double(row, COLUMN_QUALITY)
            if (quality == QUALITY_POOR || quality == QUALITY_MISSING) return@forEach
            val value = table.double(row, COLUMN_VALUE) ?: return@forEach
            val date = table.string(row, COLUMN_DATE)?.let { parseDate(it) } ?: return@forEach
            readings.getOrPut(date.time / HOUR_IN_MILLIS * HOUR_IN_MILLIS) { mutableMapOf() }
                .getOrPut(element) { mutableListOf() }
                .add(value)
        }

        return readings.mapValues { (_, elements) ->
            MeasuredHour(
                temperature = elements[ELEMENT_TEMPERATURE]?.average(),
                humidity = elements[ELEMENT_HUMIDITY]?.average(),
                precipitation = elements[ELEMENT_PRECIPITATION]?.sum(),
                windSpeed = (elements[ELEMENT_WIND_SPEED_MEAN] ?: elements[ELEMENT_WIND_SPEED])
                    ?.average(),
                windGusts = elements[ELEMENT_WIND_GUSTS]?.max(),
                windDirection = (
                    elements[ELEMENT_WIND_DIRECTION_MEAN] ?: elements[ELEMENT_WIND_DIRECTION]
                    )?.let { meanDegree(it) },
                sunshine = elements[ELEMENT_SUNSHINE]?.sum()?.seconds
            )
        }
    }

    /**
     * The mean of a set of bearings, taken as vectors — averaged as plain numbers, 350° and
     * 10° would come out due south instead of due north.
     */
    private fun meanDegree(
        degrees: List<Double>,
    ): Double? {
        var x = 0.0
        var y = 0.0
        degrees.forEach {
            val radians = Math.toRadians(it)
            x += cos(radians)
            y += sin(radians)
        }
        // Bearings that cancel out exactly leave no direction to report.
        if (x == 0.0 && y == 0.0) return null
        return (Math.toDegrees(atan2(y, x)) + FULL_CIRCLE) % FULL_CIRCLE
    }

    private fun getPrecipitation(
        hour: ChmiMeteogramHour,
        measured: MeasuredHour?,
    ): Precipitation? {
        // A rain gauge reports what fell, never what it was made of, so a measured hour
        // carries a total and nothing more.
        measured?.precipitation?.let { return Precipitation(total = it.millimeters) }

        // Millimetres an hour, on an hourly step, so the rate is also the accumulation.
        val total = hour.prec ?: return null
        val snow = hour.snow
        return Precipitation(
            total = total.millimeters,
            rain = snow?.let { (total - it).coerceAtLeast(0.0).millimeters },
            snow = snow?.millimeters
        )
    }

    private fun getWind(
        hour: ChmiMeteogramHour,
        measured: MeasuredHour?,
    ): Wind? {
        val degree = measured?.windDirection ?: hour.windDirection
        val speed = measured?.windSpeed ?: hour.windSpeed
        // ČHMÚ writes a zero where no gust was reported, and a gust weaker than the wind
        // carrying it would be a contradiction rather than a calm hour.
        val gusts = (measured?.windGusts ?: hour.windGustSpeed)
            ?.takeIf { speed == null || it > speed }
        if (degree == null && speed == null && gusts == null) return null
        return Wind(
            degree = degree?.let { Wind.validateDegree(it) },
            speed = speed?.metersPerSecond,
            gusts = gusts?.metersPerSecond
        )
    }

    /**
     * The days the meteogram can speak for itself about, then the national outlook for as far
     * as it reaches beyond them.
     *
     * The near days are deliberately left blank: `completeDailyListFromHourlyList` fills them
     * from the hourly list built above, which is how this location's own rain — the one thing
     * the outlook never publishes — reaches the daily tab.
     */
    private fun getDailyList(
        context: Context,
        location: Location,
        hourlyForecast: List<HourlyWrapper>,
        outlook: ChmiOutlookResult,
    ): List<DailyWrapper> {
        // The first day is clipped by the 00:00 UTC start and the last is a stub of a few
        // hours, so this is counted rather than assumed to be three.
        val covered = hourlyForecast
            .groupBy { it.date.getIsoFormattedDate(location) }
            .filterValues { it.size >= HOURS_FOR_A_LOCAL_DAY }
            .keys

        val localDays = covered.mapNotNull { day ->
            day.toDateNoHour(location.timeZone)?.let { DailyWrapper(date = it) }
        }
        val outlookDays = getOutlookDays(context, location, outlook).filterNot {
            it.date.getIsoFormattedDate(location) in covered
        }

        return (localDays + outlookDays).sortedBy { it.date }
    }

    /**
     * The national outlook as whole days.
     *
     * ČHMÚ files a night's minimum under the day it *precedes*; Breezy Weather's night is the
     * one that *follows* its day. So a day takes the **next** day's minimum for its night, and
     * the last day of the outlook is left without one rather than borrowing the wrong night.
     *
     * That next day is found by date and not by the number after it: the numbering has been
     * seen to skip a value while the dates run on unbroken, and following it would have lost a
     * night silently.
     */
    private fun getOutlookDays(
        context: Context,
        location: Location,
        outlook: ChmiOutlookResult,
    ): List<DailyWrapper> {
        val days = outlook.data.orEmpty()
            .mapNotNull { row -> row.number?.let { it to row } }
            .groupBy({ it.first }, { it.second })
            .values
            .mapNotNull { rows ->
                // The maximum sits on the midday row — the only one of the pair whose own date
                // is the day it belongs to, whatever the offset from UTC happens to be.
                val midday = rows.firstOrNull { it.maximumTemperature != null }
                    ?: return@mapNotNull null
                val date = midday.time?.let { parseDate(it) }?.getIsoFormattedDate(location)
                    ?: return@mapNotNull null
                OutlookDay(
                    date = date,
                    maximum = midday.maximumTemperature,
                    // The night this day opens with, which belongs to the day before it.
                    minimum = rows.firstNotNullOfOrNull { it.minimumTemperature },
                    icon = rows.firstNotNullOfOrNull { it.weatherIcon },
                    cloudCover = rows.firstNotNullOfOrNull { it.totalCloudCover }
                )
            }
        val minimums = days.mapNotNull { day -> day.minimum?.let { day.date to it } }.toMap()

        return days.mapNotNull { day ->
            val date = day.date.toDateNoHour(location.timeZone) ?: return@mapNotNull null
            DailyWrapper(
                date = date,
                day = HalfDayWrapper(
                    weatherText = getWeatherText(context, day.icon),
                    weatherCode = getWeatherCode(day.icon),
                    temperature = day.maximum?.let { TemperatureWrapper(temperature = it.celsius) }
                ),
                night = minimums[nextDay(date, location)]?.let {
                    HalfDayWrapper(
                        // One icon a day is all the outlook publishes, and a weather code is
                        // drawn as a moon at night of its own accord.
                        weatherText = getWeatherText(context, day.icon),
                        weatherCode = getWeatherCode(day.icon),
                        temperature = TemperatureWrapper(temperature = it.celsius)
                    )
                },
                cloudCover = day.cloudCover
                    ?.takeIf { it <= OKTAS }
                    ?.let { DailyCloudCover(average = (it / OKTAS).fraction) }
            )
        }.sortedBy { it.date }
    }

    /**
     * The day after this one where the location stands — by the calendar rather than by adding
     * a day's worth of milliseconds, which lands an hour out either side of a clock change.
     */
    private fun nextDay(
        date: Date,
        location: Location,
    ): String {
        return date.toCalendar(location).apply { add(Calendar.DAY_OF_YEAR, 1) }.getIsoFormattedDate()
    }

    /**
     * ČHMÚ's icon vocabulary, published at https://www.chmi.cz/predpoved-pocasi/ikony-pocasi
     *
     * It is a two-digit grammar: the tens say how much cloud there is, the ones what is
     * falling out of it, and the night of any icon is the same code plus a hundred. Reading
     * the digits rather than tabulating some sixty codes also means one ČHMÚ adds later still
     * lands somewhere sensible instead of being dropped.
     */
    private fun getWeatherCode(
        icon: Int?,
    ): WeatherCode? {
        val base = (icon ?: return null) % NIGHT_ICON_OFFSET
        return when (base % 10) {
            // Breezy Weather has no freezing rain of its own, and calls sleet the mixture
            // rather than the ice, so freezing rain is filed as rain the way it is elsewhere.
            PRECIPITATION_RAIN, PRECIPITATION_FREEZING_RAIN -> WeatherCode.RAIN
            PRECIPITATION_SLEET -> WeatherCode.SLEET
            PRECIPITATION_SNOW, PRECIPITATION_SNOW_SHOWER -> WeatherCode.SNOW
            PRECIPITATION_THUNDERSTORM -> WeatherCode.THUNDERSTORM
            PRECIPITATION_HAIL -> WeatherCode.HAIL
            else -> when (base / 10) {
                CLOUD_CLEAR, CLOUD_MOSTLY_CLEAR -> WeatherCode.CLEAR
                CLOUD_PARTLY_CLOUDY, CLOUD_CLOUDY -> WeatherCode.PARTLY_CLOUDY
                CLOUD_MOSTLY_OVERCAST, CLOUD_OVERCAST -> WeatherCode.CLOUDY
                CLOUD_FOG -> WeatherCode.FOG
                else -> null
            }
        }
    }

    /**
     * The same vocabulary in words, out of Breezy Weather's own shared set rather than ČHMÚ's
     * Czech — so it reads in whatever language the app is in.
     */
    private fun getWeatherText(
        context: Context,
        icon: Int?,
    ): String? {
        val base = (icon ?: return null) % NIGHT_ICON_OFFSET
        val cloud = base / 10
        // ČHMÚ words the same precipitation by how much cloud is above it — a shower under a
        // broken sky, plain rain under a closed one — and so does Breezy Weather.
        val showery = cloud <= CLOUD_CLOUDY
        return when (base % 10) {
            PRECIPITATION_RAIN -> if (showery) {
                R.string.common_weather_text_rain_showers
            } else {
                R.string.common_weather_text_rain
            }
            PRECIPITATION_FREEZING_RAIN -> R.string.common_weather_text_rain_freezing
            PRECIPITATION_SLEET -> if (showery) {
                R.string.common_weather_text_rain_snow_mixed_showers
            } else {
                R.string.common_weather_text_rain_snow_mixed
            }
            PRECIPITATION_SNOW -> R.string.common_weather_text_snow
            PRECIPITATION_SNOW_SHOWER -> R.string.common_weather_text_snow_showers
            PRECIPITATION_THUNDERSTORM -> R.string.weather_kind_thunderstorm
            PRECIPITATION_HAIL -> R.string.weather_kind_hail
            else -> when (cloud) {
                CLOUD_CLEAR -> R.string.common_weather_text_clear_sky
                CLOUD_MOSTLY_CLEAR -> R.string.common_weather_text_mostly_clear
                CLOUD_PARTLY_CLOUDY -> R.string.common_weather_text_partly_cloudy
                CLOUD_CLOUDY -> R.string.common_weather_text_cloudy
                CLOUD_MOSTLY_OVERCAST -> R.string.common_weather_text_mostly_cloudy
                CLOUD_OVERCAST -> R.string.common_weather_text_overcast
                CLOUD_FOG -> R.string.common_weather_text_fog
                else -> null
            }
        }?.let { context.getString(it) }
    }

    // Current

    /**
     * The three ingredients of a current observation: the ten-minute stream from the nearest
     * station, the hourly synoptic stream if a professional station is close enough, and the
     * regional text forecast.
     *
     * The ten-minute stream is fetched for the **forecast** as well, which replaces its own
     * past hours with what was measured, so this runs whenever either feature is asked for and
     * only the two current-only ingredients are skipped. A missing station is fatal to the
     * current reading but not to the forecast, which simply keeps the model's past hours.
     */
    private fun requestCurrent(
        parameters: Map<String, String>,
        requestedFeatures: List<SourceFeature>,
        failedFeatures: MutableMap<SourceFeature, Throwable>,
    ): Observable<CurrentPieces> {
        val wantsCurrent = SourceFeature.CURRENT in requestedFeatures
        val wantsObservations = wantsCurrent || SourceFeature.FORECAST in requestedFeatures

        val station = parameters[PARAMETER_STATION]
        val observations = if (!wantsObservations) {
            Observable.just(ChmiDataResult())
        } else if (station.isNullOrEmpty()) {
            if (wantsCurrent) failedFeatures[SourceFeature.CURRENT] = InvalidLocationException()
            Observable.just(ChmiDataResult())
        } else {
            withDayFallback { mApi.getObservations(station, it) }.onErrorResumeNext {
                if (wantsCurrent) failedFeatures[SourceFeature.CURRENT] = it
                Observable.just(ChmiDataResult())
            }
        }

        // Absent whenever the nearest professional station is out of range, which is most of
        // the country — the ten-minute stream still carries temperature, humidity and wind.
        val hourlyStation = parameters[PARAMETER_STATION_HOURLY]
        val hourly = if (!wantsCurrent || hourlyStation.isNullOrEmpty()) {
            Observable.just(ChmiDataResult())
        } else {
            withDayFallback { mApi.getHourlyObservations(hourlyStation, it) }
                .onErrorResumeNext { Observable.just(ChmiDataResult()) }
        }

        val cisorp = parameters[PARAMETER_ORP]
        val text = if (!wantsCurrent || cisorp.isNullOrEmpty()) {
            Observable.just(ChmiTextForecastResult())
        } else {
            requestTextForecast(textForecastCandidates(cisorp))
        }

        return Observable.zip(observations, hourly, text) {
                observationsResult: ChmiDataResult,
                hourlyResult: ChmiDataResult,
                textResult: ChmiTextForecastResult,
            ->
            CurrentPieces(observationsResult, hourlyResult, textResult)
        }
    }

    private fun getCurrent(
        pieces: CurrentPieces,
    ): CurrentWrapper? {
        val latest = latestByElement(pieces.observations) + latestByElement(pieces.hourly)
        val summary = getTextForecast(pieces.text)
        if (ELEMENTS_WORTH_SHOWING.none { it in latest } && summary == null) return null

        val direction = latest[ELEMENT_WIND_DIRECTION_MEAN] ?: latest[ELEMENT_WIND_DIRECTION]
        val speed = latest[ELEMENT_WIND_SPEED_MEAN] ?: latest[ELEMENT_WIND_SPEED]
        return CurrentWrapper(
            temperature = latest[ELEMENT_TEMPERATURE]?.let {
                TemperatureWrapper(temperature = it.value.celsius)
            },
            wind = Wind(
                degree = direction?.let {
                    if (it.flag == FLAG_VARIABLE) VARIABLE_WIND_DEGREE else Wind.validateDegree(it.value)
                },
                speed = speed?.value?.metersPerSecond,
                gusts = latest[ELEMENT_WIND_GUSTS]?.value?.metersPerSecond
            ),
            relativeHumidity = latest[ELEMENT_HUMIDITY]?.value?.percent,
            dewPoint = latest[ELEMENT_DEW_POINT]?.value?.celsius,
            // Only the sea-level reading, never the station pressure the ten-minute stream
            // reports as "P" — CurrentWrapper documents this field as reduced to sea level.
            pressure = latest[ELEMENT_PRESSURE_SEA_LEVEL]?.value?.hectopascals,
            // Oktas. 9 means the sky could not be seen, which is not a cloud amount.
            cloudCover = latest[ELEMENT_CLOUD_COVER]?.value?.takeIf { it <= OKTAS }?.let {
                (it / OKTAS).fraction
            },
            dailyForecast = summary
        )
        // Visibility and present weather are in the hourly stream too, but as ČHMÚ code
        // numbers with no published code list, so they are left out rather than guessed at.
    }

    /**
     * The newest reading of each element in an observation file.
     */
    private fun latestByElement(
        result: ChmiDataResult,
    ): Map<String, Observation> {
        val table = result.data?.data ?: return emptyMap()
        val latest = mutableMapOf<String, Observation>()
        table.rows().forEach { row ->
            val element = table.string(row, COLUMN_ELEMENT) ?: return@forEach
            val value = table.double(row, COLUMN_VALUE) ?: return@forEach
            val quality = table.double(row, COLUMN_QUALITY)
            if (quality == QUALITY_POOR || quality == QUALITY_MISSING) return@forEach
            val date = table.string(row, COLUMN_DATE)?.let { parseDate(it) } ?: return@forEach
            val known = latest[element]
            if (known == null || date.after(known.date)) {
                latest[element] = Observation(date, value, table.string(row, COLUMN_FLAG))
            }
        }
        return latest
    }

    /**
     * Filenames carry the issue time and the directory has no index, so the candidates are
     * built from ČHMÚ's fixed publishing schedule and tried newest first. Normally the first
     * one answers.
     */
    private fun requestTextForecast(
        candidates: List<String>,
    ): Observable<ChmiTextForecastResult> {
        if (candidates.isEmpty()) return Observable.just(ChmiTextForecastResult())
        return mApi.getTextForecast(candidates.first()).onErrorResumeNext {
            requestTextForecast(candidates.drop(1))
        }
    }

    private fun textForecastCandidates(
        cisorp: String,
    ): List<String> {
        val region = textForecastRegion(cisorp) ?: return emptyList()
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val nowAsTime = now.get(Calendar.HOUR_OF_DAY) * 100 + now.get(Calendar.MINUTE)
        val candidates = mutableListOf<String>()
        for (dayOffset in 0 downTo -1) {
            val day = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
            val dayOfMonth = String.format(Locale.ENGLISH, "%02d", day.get(Calendar.DAY_OF_MONTH))
            TEXT_FORECAST_ISSUE_TIMES.forEach { time ->
                if (dayOffset < 0 || time <= nowAsTime) {
                    candidates.add(
                        "web_pCK0tx_${region}_$dayOfMonth${String.format(Locale.ENGLISH, "%04d", time)}.json"
                    )
                }
            }
        }
        return candidates
    }

    private fun getTextForecast(
        result: ChmiTextForecastResult,
    ): String? {
        val properties = result.data?.features?.firstOrNull()?.properties ?: return null
        val blocks = properties.data.orEmpty()
            .sortedBy { it.displayOrder ?: Int.MAX_VALUE }
            .mapNotNull { block ->
                block.displayText?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
                    block.headline?.trim()?.takeIf { it.isNotEmpty() }?.let { "$it $text" } ?: text
                }
            }
        return (listOfNotNull(properties.headline?.headline?.trim()?.takeIf { it.isNotEmpty() }) + blocks)
            .joinToString("\n")
            .takeIf { it.isNotEmpty() }
    }

    // Air quality

    private fun getAirQuality(
        csv: String,
        parameters: Map<String, String>,
    ): AirQualityWrapper? {
        if (csv.isEmpty()) return null
        val registrations = parameters.entries
            .filter { it.key.startsWith(PARAMETER_AIR_QUALITY_PREFIX) && it.value.isNotEmpty() }
            .associate { it.value to it.key.removePrefix(PARAMETER_AIR_QUALITY_PREFIX) }
        if (registrations.isEmpty()) return null

        val concentrations = mutableMapOf<String, Pair<String, Double>>()
        csv.lineSequence().drop(1).forEach { line ->
            val cells = line.split(",").map { it.trim() }
            if (cells.size < 4) return@forEach
            val pollutant = registrations[cells[0]] ?: return@forEach
            val valueType = cells[2].toIntOrNull() ?: return@forEach
            // Anything else is an error code, or the index rather than a concentration
            if (valueType !in AIR_QUALITY_VALUE_TYPES) return@forEach
            val value = cells[3].toDoubleOrNull() ?: return@forEach
            val known = concentrations[pollutant]
            if (known == null || cells[1] > known.first) concentrations[pollutant] = cells[1] to value
        }
        if (concentrations.isEmpty()) return null

        // ČHMÚ reports every pollutant, carbon monoxide included, in µg/m³
        val airQuality = AirQuality(
            pM25 = concentrations[POLLUTANT_PM25]?.second?.microgramsPerCubicMeter,
            pM10 = concentrations[POLLUTANT_PM10]?.second?.microgramsPerCubicMeter,
            sO2 = concentrations[POLLUTANT_SO2]?.second?.microgramsPerCubicMeter,
            nO2 = concentrations[POLLUTANT_NO2]?.second?.microgramsPerCubicMeter,
            o3 = concentrations[POLLUTANT_O3]?.second?.microgramsPerCubicMeter,
            cO = concentrations[POLLUTANT_CO]?.second?.microgramsPerCubicMeter
        )
        return if (airQuality.isValid) AirQualityWrapper(current = airQuality) else null
    }

    // Normals

    private fun requestNormals(
        parameters: Map<String, String>,
        failedFeatures: MutableMap<SourceFeature, Throwable>,
    ): Observable<Map<Month, Normals>> {
        val station = parameters[PARAMETER_STATION_NORMALS]
        if (station.isNullOrEmpty()) {
            failedFeatures[SourceFeature.NORMALS] = InvalidLocationException()
            return Observable.just(emptyMap())
        }
        val maxima = mApi.getNormals(station, ELEMENT_TEMPERATURE_MAXIMUM).map { it.string() }
        val minima = mApi.getNormals(station, ELEMENT_TEMPERATURE_MINIMUM).map { it.string() }
        return Observable.zip(maxima, minima) { maximaCsv: String, minimaCsv: String ->
            getNormals(maximaCsv, minimaCsv)
        }.onErrorResumeNext {
            failedFeatures[SourceFeature.NORMALS] = it
            Observable.just(emptyMap())
        }
    }

    private fun getNormals(
        maximaCsv: String,
        minimaCsv: String,
    ): Map<Month, Normals> {
        val maxima = parseNormals(maximaCsv)
        val minima = parseNormals(minimaCsv)
        return Month.entries.mapNotNull { month ->
            val daytime = maxima[month.value]
            val nighttime = minima[month.value]
            if (daytime == null && nighttime == null) {
                null
            } else {
                month to Normals(
                    daytimeTemperature = daytime?.celsius,
                    nighttimeTemperature = nighttime?.celsius
                )
            }
        }.toMap()
    }

    /**
     * `Eg.Gh.Id,Eg.El.Abbreviation,Month,Normal.AVG`, one row per month. Month 13 is the
     * annual figure and is skipped.
     */
    private fun parseNormals(
        csv: String,
    ): Map<Int, Double> {
        return buildMap {
            csv.lineSequence().drop(1).forEach { line ->
                val cells = line.split(",").map { it.trim() }
                if (cells.size < 4) return@forEach
                val month = cells[2].toIntOrNull() ?: return@forEach
                if (month !in 1..MONTHS_IN_YEAR) return@forEach
                cells[3].toDoubleOrNull()?.let { put(month, it) }
            }
        }
    }

    // Alerts

    private fun requestAlerts(
        parameters: Map<String, String>,
        failedFeatures: MutableMap<SourceFeature, Throwable>,
    ): Observable<List<CapAlert>> {
        if (parameters[PARAMETER_ORP].isNullOrEmpty()) {
            failedFeatures[SourceFeature.ALERT] = InvalidLocationException()
            return Observable.just(emptyList())
        }
        val warnings = mCapApi.getAlerts().onErrorResumeNext {
            failedFeatures[SourceFeature.ALERT] = it
            Observable.just(CapAlert())
        }
        // A missing drought bulletin is not worth failing the whole feature over
        val drought = mCapApi.getDroughtAlerts().onErrorResumeNext { Observable.just(CapAlert()) }
        return Observable.zip(warnings, drought) { warningsResult: CapAlert, droughtResult: CapAlert ->
            listOf(warningsResult, droughtResult)
        }
    }

    /**
     * ČHMÚ writes each warning twice into the one bulletin, once in Czech and once in
     * English, and — for every hazard it is *not* warning about — an explicit "no warning in
     * force" block. Those negatives are always `Minor`; a real warning is `Moderate` upwards,
     * which is what SIVS levels yellow, orange and red map to.
     */
    private fun getAlertList(
        context: Context,
        cisorp: String?,
        capAlerts: List<CapAlert>,
    ): List<Alert>? {
        if (cisorp.isNullOrEmpty()) return null
        return capAlerts.flatMap { capAlert ->
            if (capAlert.msgType?.value.equals("Cancel", ignoreCase = true)) return@flatMap emptyList()
            val infos = capAlert.info ?: return@flatMap emptyList()

            val languages = infos.mapNotNull { it.language?.value }.distinct()
            val language = languages.firstOrNull {
                it.startsWith(context.currentLocale.language, ignoreCase = true)
            } ?: languages.firstOrNull { it.startsWith("en", ignoreCase = true) }

            infos.mapNotNull { info ->
                val severity = when (info.severity?.value) {
                    "Extreme" -> AlertSeverity.EXTREME
                    "Severe" -> AlertSeverity.SEVERE
                    "Moderate" -> AlertSeverity.MODERATE
                    else -> return@mapNotNull null
                }
                if (language != null && !info.language?.value.equals(language, ignoreCase = true)) {
                    return@mapNotNull null
                }
                // Met covers the weather warnings, Env the smog and ground-level ozone ones
                if (!ALERT_CATEGORIES.any { info.category?.value.equals(it, ignoreCase = true) }) {
                    return@mapNotNull null
                }
                if (info.urgency?.value.equals("Past", ignoreCase = true)) return@mapNotNull null
                if (!info.containsGeocode(GEOCODE_ORP, cisorp)) return@mapNotNull null

                val start = info.onset?.value ?: info.effective?.value ?: capAlert.sent?.value
                val description = listOfNotNull(
                    info.formatAlertText(source = weatherAttribution, text = info.description?.value),
                    info.parameters?.firstOrNull {
                        it.valueName?.value.equals(PARAMETER_SITUATION, ignoreCase = true)
                    }?.value?.value
                ).joinToString("\n\n").takeIf { it.isNotEmpty() }

                Alert(
                    // Not the CAP identifier: ČHMÚ mints a new one every time it re-issues a
                    // bulletin, which would make every warning look new on every refresh.
                    alertId = Objects.hash(info.event?.value, severity, start).toString(),
                    startDate = start,
                    endDate = info.expires?.value,
                    headline = info.event?.value,
                    description = description,
                    instruction = info.formatAlertText(source = weatherAttribution, text = info.instruction?.value),
                    source = weatherAttribution,
                    severity = severity,
                    color = Alert.colorFromSeverity(severity)
                )
            }
        }
    }

    // Location parameters

    override fun needsLocationParametersRefresh(
        location: Location,
        coordinatesChanged: Boolean,
        features: List<SourceFeature>,
    ): Boolean {
        if (coordinatesChanged) return true
        val parameters = location.parameters.getOrElse(id) { null }.orEmpty()
        // An empty value means "resolved, and there is nothing near enough"; only a key that
        // was never written at all is worth another round trip.
        return (SourceFeature.ALERT in features && PARAMETER_ORP !in parameters) ||
            (
                SourceFeature.CURRENT in features &&
                    (PARAMETER_STATION !in parameters || PARAMETER_STATION_HOURLY !in parameters)
                ) ||
            (SourceFeature.NORMALS in features && PARAMETER_STATION_NORMALS !in parameters) ||
            (SourceFeature.AIR_QUALITY in features && PARAMETER_AIR_QUALITY !in parameters)
    }

    override fun requestLocationParameters(
        context: Context,
        location: Location,
    ): Observable<Map<String, String>> {
        val stations = withDayFallback { mApi.getStations(it) }
            .onErrorResumeNext { Observable.just(ChmiDataResult()) }
        val elements = withDayFallback { mApi.getStationElements(it) }
            .onErrorResumeNext { Observable.just(ChmiDataResult()) }
        val airQuality = mApi.getAirQualityStations()
            .onErrorResumeNext { Observable.just(ChmiAirQualityMetadata()) }
        val normals = mApi.getNormalsStations().map { it.string() }
            .onErrorResumeNext { Observable.just("") }

        return Observable.zip(stations, elements, airQuality, normals) {
                stationsResult: ChmiDataResult,
                elementsResult: ChmiDataResult,
                airQualityResult: ChmiAirQualityMetadata,
                normalsResult: String,
            ->
            buildMap {
                // Worked out offline, so alerts survive a portal outage
                getOrpCode(location.latitude, location.longitude)?.let { put(PARAMETER_ORP, it) }
                putAll(getObservationStations(location, stationsResult, elementsResult))
                putAll(getAirQualityRegistrations(location, airQualityResult))
                putAll(getNormalsStation(location, normalsResult))
            }
        }
    }

    /**
     * Most of the 750-odd stations are rain gauges. Only those reporting ten-minute air
     * temperature can answer for the current conditions, so the nearest is picked among
     * those — there are close to 300, never more than a few tens of kilometres apart. The
     * three dozen professional stations are resolved separately, and often to nothing.
     */
    private fun getObservationStations(
        location: Location,
        stations: ChmiDataResult,
        elements: ChmiDataResult,
    ): Map<String, String> {
        val elementsTable = elements.data?.data ?: return emptyMap()
        val stationsTable = stations.data?.data ?: return emptyMap()
        if (elementsTable.rows().isEmpty() || stationsTable.rows().isEmpty()) return emptyMap()

        val reporting = mutableSetOf<String>()
        val synoptic = mutableSetOf<String>()
        elementsTable.rows().forEach { row ->
            val wsi = elementsTable.string(row, COLUMN_WSI) ?: return@forEach
            val observationType = elementsTable.string(row, COLUMN_OBSERVATION_TYPE)
            val element = elementsTable.string(row, COLUMN_ELEMENT_ABBREVIATION) ?: return@forEach
            if (observationType == OBSERVATION_TYPE_10M && element == ELEMENT_TEMPERATURE) {
                reporting.add(wsi)
            }
            if (observationType == OBSERVATION_TYPE_1H && element in SYNOPTIC_ELEMENTS) {
                synoptic.add(wsi)
            }
        }

        val coordinates = mutableMapOf<String, LatLng>()
        stationsTable.rows().forEach { row ->
            val wsi = stationsTable.string(row, COLUMN_WSI) ?: return@forEach
            val longitude = stationsTable.double(row, COLUMN_LONGITUDE) ?: return@forEach
            val latitude = stationsTable.double(row, COLUMN_LATITUDE) ?: return@forEach
            coordinates[wsi] = LatLng(latitude, longitude)
        }

        val point = LatLng(location.latitude, location.longitude)
        val station = point
            .getNearestLocation(coordinates.filterKeys { it in reporting }, MAXIMUM_STATION_DISTANCE)
        return mapOf(
            PARAMETER_STATION to station.orEmpty(),
            // Where the ten-minute station is a professional one it answers for both, so the
            // dew point belongs to the same thermometer as the temperature. Only when it is
            // not do the two streams come from different places.
            PARAMETER_STATION_HOURLY to if (station != null && station in synoptic) {
                station
            } else {
                point.getNearestLocation(coordinates.filterKeys { it in synoptic }, MAXIMUM_STATION_DISTANCE)
                    .orEmpty()
            }
        )
    }

    /**
     * The hourly air-quality file identifies its rows only by registration id, so the ids
     * belonging to the nearest station are resolved once and remembered.
     *
     * Background stations are preferred over the traffic and industrial ones, which sit in
     * kerbside canyons and by factory fences and read far worse than the air most of the
     * town is breathing. Four fifths of the network is background, so this rarely costs any
     * distance — in Prague it moves the reading from a hot spot on Legerova to Riegrovy sady,
     * 200 m further away.
     */
    private fun getAirQualityRegistrations(
        location: Location,
        metadata: ChmiAirQualityMetadata,
    ): Map<String, String> {
        val localities = metadata.data?.localities
        if (localities.isNullOrEmpty()) return emptyMap()

        val coordinates = buildMap {
            localities.forEach { locality ->
                val code = locality.code ?: return@forEach
                val latitude = locality.localization?.latitude ?: return@forEach
                val longitude = locality.localization.longitude ?: return@forEach
                put(code, LatLng(latitude, longitude))
            }
        }
        val background = localities.mapNotNullTo(mutableSetOf()) { locality ->
            locality.code?.takeIf {
                locality.classification?.abbreviation?.startsWith(STATION_TYPE_BACKGROUND) == true
            }
        }
        val point = LatLng(location.latitude, location.longitude)
        val nearest = point
            .getNearestLocation(coordinates.filterKeys { it in background }, MAXIMUM_AIR_QUALITY_DISTANCE)
            ?: point.getNearestLocation(coordinates, MAXIMUM_AIR_QUALITY_DISTANCE)
            ?: return mapOf(PARAMETER_AIR_QUALITY to "")

        return buildMap {
            put(PARAMETER_AIR_QUALITY, nearest)
            localities.first { it.code == nearest }
                .measuringPrograms.orEmpty()
                .flatMap { it.measurements.orEmpty() }
                .forEach { measurement ->
                    val pollutant = measurement.componentCode?.takeIf { it in POLLUTANTS } ?: return@forEach
                    measurement.idRegistration?.let {
                        put(PARAMETER_AIR_QUALITY_PREFIX + pollutant, it.toString())
                    }
                }
        }
    }

    /**
     * `WSI;GH ID;BEGIN_DATE;END_DATE;FULL_NAME;GEOGR1;GEOGR2;ELEVATION`, semicolon separated,
     * with a comma for the decimal point.
     */
    private fun getNormalsStation(
        location: Location,
        csv: String,
    ): Map<String, String> {
        if (csv.isBlank()) return emptyMap()
        val coordinates = mutableMapOf<String, LatLng>()
        csv.lineSequence().drop(1).forEach { line ->
            val cells = line.split(";").map { it.trim() }
            if (cells.size < NORMALS_STATION_COLUMNS) return@forEach
            val longitude = cells[5].replace(',', '.').toDoubleOrNull() ?: return@forEach
            val latitude = cells[6].replace(',', '.').toDoubleOrNull() ?: return@forEach
            coordinates[cells[0]] = LatLng(latitude, longitude)
        }
        if (coordinates.isEmpty()) return emptyMap()
        return mapOf(
            PARAMETER_STATION_NORMALS to
                LatLng(location.latitude, location.longitude)
                    .getNearestLocation(coordinates, MAXIMUM_STATION_DISTANCE)
                    .orEmpty()
        )
    }

    private fun getOrpCode(
        latitude: Double,
        longitude: Double,
    ): String? {
        orpBoundaries.features.firstOrNull { contains(it, latitude, longitude) }?.let {
            return it.getProperty(PROPERTY_ORP)
        }
        // Simplifying the boundaries down to something a phone can carry leaves hairline gaps
        // where three of them meet; a point landing in one belongs to whichever ORP's outline
        // passes closest to it.
        val point = LatLng(latitude, longitude)
        var nearest: String? = null
        var nearestDistance = Double.POSITIVE_INFINITY
        orpBoundaries.features.forEach { feature ->
            rings(feature).forEach { ring ->
                ring.forEach { vertex ->
                    val distance = SphericalUtil.computeDistanceBetween(point, vertex)
                    if (distance < nearestDistance) {
                        nearestDistance = distance
                        nearest = feature.getProperty(PROPERTY_ORP)
                    }
                }
            }
        }
        return nearest
    }

    private fun contains(
        feature: GeoJsonFeature,
        latitude: Double,
        longitude: Double,
    ): Boolean {
        return parts(feature).any { part ->
            val outer = part.firstOrNull() ?: return@any false
            PolyUtil.containsLocation(latitude, longitude, outer, true) &&
                part.drop(1).none { hole ->
                    PolyUtil.containsLocation(latitude, longitude, hole, true)
                }
        }
    }

    /**
     * A feature as a list of polygons, each a list of rings, the first being its outline.
     */
    private fun parts(
        feature: GeoJsonFeature,
    ): List<List<List<LatLng>>> {
        return when (val geometry = feature.geometry) {
            is GeoJsonPolygon -> listOf(geometry.coordinates)
            is GeoJsonMultiPolygon -> geometry.polygons.map { it.coordinates }
            else -> emptyList()
        }
    }

    private fun rings(
        feature: GeoJsonFeature,
    ): List<List<LatLng>> = parts(feature).flatten()

    /**
     * The kraj a CISORP code belongs to is its first two digits, and ČHMÚ names its regional
     * products after the same fourteen regions.
     */
    private fun textForecastRegion(
        cisorp: String,
    ): String? = TEXT_FORECAST_REGIONS[cisorp.toIntOrNull()?.div(100)]

    /**
     * The day's file is written from 00:00 UTC onwards, so for the first minutes of a UTC day
     * it is missing or still empty and yesterday's holds the newest reading.
     */
    private fun withDayFallback(
        request: (String) -> Observable<ChmiDataResult>,
    ): Observable<ChmiDataResult> {
        return request(utcDay(0))
            .onErrorResumeNext { Observable.just(ChmiDataResult()) }
            .flatMap {
                if (it.data?.data?.rows().isNullOrEmpty()) request(utcDay(-1)) else Observable.just(it)
            }
    }

    private fun utcDay(
        offset: Int,
    ): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.DAY_OF_YEAR, offset)
        }
        return SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(calendar.time)
    }

    private fun parseDate(
        value: String,
    ): Date? {
        return try {
            ISO8601Utils.parse(value)
        } catch (ignored: ParseException) {
            null
        }
    }

    private data class Observation(
        val date: Date,
        val value: Double,
        val flag: String?,
    )

    /**
     * One day of the national outlook, gathered from its pair of rows.
     *
     * [minimum] is the night this day *opens* with, which is the night of the day before —
     * ČHMÚ's filing, not Breezy Weather's.
     */
    private data class OutlookDay(
        val date: String,
        val maximum: Double?,
        val minimum: Double?,
        val icon: Int?,
        val cloudCover: Double?,
    )

    /**
     * One whole hour as the nearest station measured it, out of the ten-minute stream.
     */
    private data class MeasuredHour(
        val temperature: Double? = null,
        val humidity: Double? = null,
        val precipitation: Double? = null,
        val windSpeed: Double? = null,
        val windGusts: Double? = null,
        val windDirection: Double? = null,
        val sunshine: Duration? = null,
    )

    private data class CurrentPieces(
        val observations: ChmiDataResult = ChmiDataResult(),
        val hourly: ChmiDataResult = ChmiDataResult(),
        val text: ChmiTextForecastResult = ChmiTextForecastResult(),
    )

    override val testingLocations: List<Location> = emptyList()

    companion object {
        private const val CHMI_BASE_URL = "https://opendata.chmi.cz/"
        private const val CHMI_CAP_BASE_URL = "https://vystrahy-cr.chmi.cz/"
        private const val CHMI_DATA_PROVIDER_BASE_URL = "https://data-provider.chmi.cz/"

        private const val PARAMETER_ORP = "cisorp"
        private const val PARAMETER_STATION = "station"
        private const val PARAMETER_STATION_HOURLY = "stationHourly"
        private const val PARAMETER_STATION_NORMALS = "stationNormals"
        private const val PARAMETER_AIR_QUALITY = "airQuality"
        private const val PARAMETER_AIR_QUALITY_PREFIX = "airQuality_"
        private const val PROPERTY_ORP = "cisorp"
        private const val GEOCODE_ORP = "CISORP"
        private const val PARAMETER_SITUATION = "situation"

        private val ALERT_CATEGORIES = arrayOf("Met", "Env")

        private const val COLUMN_WSI = "WSI"
        private const val COLUMN_OBSERVATION_TYPE = "OBS_TYPE"
        private const val COLUMN_ELEMENT_ABBREVIATION = "EG_EL_ABBREVIATION"
        private const val COLUMN_LONGITUDE = "GEOGR1"
        private const val COLUMN_LATITUDE = "GEOGR2"
        private const val COLUMN_ELEMENT = "ELEMENT"
        private const val COLUMN_DATE = "DT"
        private const val COLUMN_VALUE = "VAL"
        private const val COLUMN_FLAG = "FLAG"
        private const val COLUMN_QUALITY = "QUALITY"

        private const val OBSERVATION_TYPE_10M = "10M"
        private const val OBSERVATION_TYPE_1H = "1H"
        private const val ELEMENT_TEMPERATURE = "T"
        private const val ELEMENT_HUMIDITY = "H"
        private const val ELEMENT_WIND_SPEED = "F"
        private const val ELEMENT_WIND_SPEED_MEAN = "Fprum"
        private const val ELEMENT_WIND_GUSTS = "Fmax"
        private const val ELEMENT_WIND_DIRECTION = "D"
        private const val ELEMENT_WIND_DIRECTION_MEAN = "Dprum"
        private const val ELEMENT_DEW_POINT = "Td"
        private const val ELEMENT_PRESSURE_SEA_LEVEL = "P_hm"
        private const val ELEMENT_CLOUD_COVER = "N"
        private const val ELEMENT_TEMPERATURE_MAXIMUM = "TMA"
        private const val ELEMENT_TEMPERATURE_MINIMUM = "TMI"
        private const val ELEMENT_PRECIPITATION = "SRA10M"
        private const val ELEMENT_SUNSHINE = "SSV10M"

        /**
         * What the forecast's past hours are rebuilt from. Pressure is missing on purpose: the
         * ten-minute stream reports it at the station, and the hourly row it would replace is
         * reduced to sea level.
         */
        private val MEASURED_ELEMENTS = arrayOf(
            ELEMENT_TEMPERATURE,
            ELEMENT_HUMIDITY,
            ELEMENT_PRECIPITATION,
            ELEMENT_SUNSHINE,
            ELEMENT_WIND_SPEED,
            ELEMENT_WIND_SPEED_MEAN,
            ELEMENT_WIND_GUSTS,
            ELEMENT_WIND_DIRECTION,
            ELEMENT_WIND_DIRECTION_MEAN
        )

        private val ELEMENTS_WORTH_SHOWING = arrayOf(
            ELEMENT_TEMPERATURE,
            ELEMENT_HUMIDITY,
            ELEMENT_WIND_SPEED,
            ELEMENT_WIND_SPEED_MEAN
        )

        /**
         * What makes a station worth reading the hourly stream for.
         */
        private val SYNOPTIC_ELEMENTS = arrayOf(ELEMENT_PRESSURE_SEA_LEVEL, ELEMENT_DEW_POINT)

        private const val STATION_TYPE_BACKGROUND = "B"

        private const val POLLUTANT_PM25 = "PM2_5"
        private const val POLLUTANT_PM10 = "PM10"
        private const val POLLUTANT_SO2 = "SO2"
        private const val POLLUTANT_NO2 = "NO2"
        private const val POLLUTANT_O3 = "O3"
        private const val POLLUTANT_CO = "CO"
        private val POLLUTANTS = arrayOf(
            POLLUTANT_PM25,
            POLLUTANT_PM10,
            POLLUTANT_SO2,
            POLLUTANT_NO2,
            POLLUTANT_O3,
            POLLUTANT_CO
        )

        /**
         * From ČHMÚ's value-type list: 8 and 9 are operational and verified readings, 10 and
         * 11 are readings substituted because they fell below the detection limit. The rest
         * are error codes, or the air quality index rather than a concentration.
         */
        private val AIR_QUALITY_VALUE_TYPES = intArrayOf(8, 9, 10, 11)

        /**
         * From ČHMÚ's own quality code list: 2 is "do not use yet", 4 is "missing".
         */
        private const val QUALITY_POOR = 2.0
        private const val QUALITY_MISSING = 4.0

        /**
         * ČHMÚ flags a variable wind direction with V; Breezy Weather encodes that as -1.
         */
        private const val FLAG_VARIABLE = "V"
        private const val VARIABLE_WIND_DEGREE = -1.0

        private const val OKTAS = 8.0
        private const val FULL_CIRCLE = 360.0
        private const val HOUR_IN_MILLIS = 3600000L

        /**
         * How much of a local day the meteogram has to cover before that day is built from it
         * rather than taken from the national outlook. Its first day loses the hours before
         * 00:00 UTC and its last is a stub of two or three, so what qualifies is counted
         * rather than assumed — the horizon shrinks as the day goes on.
         */
        private const val HOURS_FOR_A_LOCAL_DAY = 20

        /**
         * The night of an icon is its day plus a hundred. Overcast and fog have no night of
         * their own, since they look the same either way.
         */
        private const val NIGHT_ICON_OFFSET = 100

        private const val CLOUD_CLEAR = 1
        private const val CLOUD_MOSTLY_CLEAR = 2
        private const val CLOUD_PARTLY_CLOUDY = 4
        private const val CLOUD_CLOUDY = 6
        private const val CLOUD_MOSTLY_OVERCAST = 7
        private const val CLOUD_OVERCAST = 8
        private const val CLOUD_FOG = 9

        private const val PRECIPITATION_RAIN = 1
        private const val PRECIPITATION_FREEZING_RAIN = 2
        private const val PRECIPITATION_SLEET = 3
        private const val PRECIPITATION_SNOW = 4
        private const val PRECIPITATION_SNOW_SHOWER = 5
        private const val PRECIPITATION_THUNDERSTORM = 6
        private const val PRECIPITATION_HAIL = 9

        private const val MONTHS_IN_YEAR = 12
        private const val NORMALS_STATION_COLUMNS = 7
        private const val MAXIMUM_STATION_DISTANCE = 50000.0
        private const val MAXIMUM_AIR_QUALITY_DISTANCE = 50000.0

        /**
         * When the today-forecast for a region is published, newest first. ČHMÚ's own product
         * list claims three issues a day, but every region has been issuing at exactly these
         * two; probing for a third would cost a 404 on every single refresh, and the worst a
         * missing one can do is leave the morning text showing a few hours longer.
         */
        private val TEXT_FORECAST_ISSUE_TIMES = intArrayOf(1000, 500)

        /**
         * Kraj code (the first two digits of a CISORP) to ČHMÚ's own region abbreviation.
         */
        private val TEXT_FORECAST_REGIONS = mapOf(
            11 to "RPPH",
            21 to "RPSC",
            31 to "RPCB",
            32 to "RPPL",
            41 to "RPKV",
            42 to "RPUL",
            51 to "RPLB",
            52 to "RPHK",
            53 to "RPPU",
            61 to "RPVY",
            62 to "RPJM",
            71 to "RPOL",
            72 to "RPZL",
            81 to "RPMS"
        )
    }
}
