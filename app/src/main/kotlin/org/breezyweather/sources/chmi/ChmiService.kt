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
import breezyweather.domain.weather.model.Normals
import breezyweather.domain.weather.model.Wind
import breezyweather.domain.weather.reference.AlertSeverity
import breezyweather.domain.weather.reference.Month
import breezyweather.domain.weather.wrappers.AirQualityWrapper
import breezyweather.domain.weather.wrappers.CurrentWrapper
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
import org.breezyweather.common.extensions.parseRawGeoJson
import org.breezyweather.common.source.HttpSource
import org.breezyweather.common.source.LocationParametersSource
import org.breezyweather.common.source.WeatherSource
import org.breezyweather.common.source.WeatherSource.Companion.PRIORITY_HIGHEST
import org.breezyweather.common.source.WeatherSource.Companion.PRIORITY_NONE
import org.breezyweather.common.utils.ISO8601Utils
import org.breezyweather.sources.chmi.json.ChmiAirQualityMetadata
import org.breezyweather.sources.chmi.json.ChmiDataResult
import org.breezyweather.sources.chmi.json.ChmiTextForecastResult
import org.breezyweather.sources.common.xml.CapAlert
import org.breezyweather.unit.pollutant.PollutantConcentration.Companion.microgramsPerCubicMeter
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
 *
 * There is deliberately **no forecast**: ČHMÚ's own point forecasts are text only and its
 * ALADIN output is GRIB2, neither of which a phone can use. ALADIN reaches the app through
 * the Open-Meteo source instead, as the `chmi_aladin_*` models.
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

    /**
     * Parsed on first use only — [requestLocationParameters] resolves the ORP once per
     * location, so a refresh never touches this.
     */
    private val orpBoundaries: GeoJsonParser by lazy {
        context.parseRawGeoJson(R.raw.chmi_orp)
    }

    override val supportedFeatures = mapOf(
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

        val current = if (SourceFeature.CURRENT in requestedFeatures) {
            requestCurrent(parameters, failedFeatures)
        } else {
            Observable.just(CurrentPieces())
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

        return Observable.zip(current, alerts, airQuality, normals) {
                currentResult: CurrentPieces,
                alertsResult: List<CapAlert>,
                airQualityResult: String,
                normalsResult: Map<Month, Normals>,
            ->
            WeatherWrapper(
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

    // Current

    /**
     * The three ingredients of a current observation: the ten-minute stream from the nearest
     * station, the hourly synoptic stream if a professional station is close enough, and the
     * regional text forecast.
     */
    private fun requestCurrent(
        parameters: Map<String, String>,
        failedFeatures: MutableMap<SourceFeature, Throwable>,
    ): Observable<CurrentPieces> {
        val station = parameters[PARAMETER_STATION]
        val observations = if (station.isNullOrEmpty()) {
            failedFeatures[SourceFeature.CURRENT] = InvalidLocationException()
            Observable.just(ChmiDataResult())
        } else {
            withDayFallback { mApi.getObservations(station, it) }.onErrorResumeNext {
                failedFeatures[SourceFeature.CURRENT] = it
                Observable.just(ChmiDataResult())
            }
        }

        // Absent whenever the nearest professional station is out of range, which is most of
        // the country — the ten-minute stream still carries temperature, humidity and wind.
        val hourlyStation = parameters[PARAMETER_STATION_HOURLY]
        val hourly = if (hourlyStation.isNullOrEmpty()) {
            Observable.just(ChmiDataResult())
        } else {
            withDayFallback { mApi.getHourlyObservations(hourlyStation, it) }
                .onErrorResumeNext { Observable.just(ChmiDataResult()) }
        }

        val cisorp = parameters[PARAMETER_ORP]
        val text = if (cisorp.isNullOrEmpty()) {
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

    private data class CurrentPieces(
        val observations: ChmiDataResult = ChmiDataResult(),
        val hourly: ChmiDataResult = ChmiDataResult(),
        val text: ChmiTextForecastResult = ChmiTextForecastResult(),
    )

    override val testingLocations: List<Location> = emptyList()

    companion object {
        private const val CHMI_BASE_URL = "https://opendata.chmi.cz/"
        private const val CHMI_CAP_BASE_URL = "https://vystrahy-cr.chmi.cz/"

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
