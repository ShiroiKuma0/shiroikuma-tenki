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

import io.reactivex.rxjava3.core.Observable
import okhttp3.ResponseBody
import org.breezyweather.sources.chmi.json.ChmiAirQualityMetadata
import org.breezyweather.sources.chmi.json.ChmiDataResult
import org.breezyweather.sources.chmi.json.ChmiTextForecastResult
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * The ČHMÚ open data portal, https://opendata.chmi.cz/
 *
 * The observation paths are dated with a UTC `yyyyMMdd`; the day's file starts empty just
 * after midnight UTC and grows as observations come in.
 *
 * A few of these are CSV, which no converter here handles, so they come back as a
 * [ResponseBody] and are read by hand. The files are small and their shape is fixed.
 */
interface ChmiApi {

    /**
     * The station register: `WSI,GH_ID,FULL_NAME,GEOGR1,GEOGR2,ELEVATION,BEGIN_DATE`,
     * where GEOGR1 is the longitude and GEOGR2 the latitude.
     */
    @GET("meteorology/climate/now/metadata/meta1-{date}.json")
    fun getStations(
        @Path("date") date: String,
    ): Observable<ChmiDataResult>

    /**
     * What each station measures and how often:
     * `OBS_TYPE,WSI,EG_EL_ABBREVIATION,NAME,UN_DESCRIPTION,HEIGHT,SCHEDULE`.
     * Most of the network is rain gauges, so this is what tells us which stations can
     * actually answer "what is it like outside".
     */
    @GET("meteorology/climate/now/metadata/meta2-{date}.json")
    fun getStationElements(
        @Path("date") date: String,
    ): Observable<ChmiDataResult>

    /**
     * One station's ten-minute observations for a whole UTC day:
     * `STATION,ELEMENT,DT,VAL,FLAG,QUALITY`. Around 300 stations report temperature this way.
     */
    @GET("meteorology/climate/now/data/10m-{station}-{date}.json")
    fun getObservations(
        @Path("station") station: String,
        @Path("date") date: String,
    ): Observable<ChmiDataResult>

    /**
     * The same for the hourly synoptic stream. Only the three dozen professional stations
     * report it, but they add sea-level pressure, dew point and cloud cover.
     */
    @GET("meteorology/climate/now/data/1h-{station}-{date}.json")
    fun getHourlyObservations(
        @Path("station") station: String,
        @Path("date") date: String,
    ): Observable<ChmiDataResult>

    /**
     * A regional text forecast. Filenames are stamped with the issue time and there is no
     * index, so [ChmiService] builds the candidates from ČHMÚ's fixed publishing schedule.
     */
    @GET("meteorology/weather/forecast/now/{file}")
    fun getTextForecast(
        @Path("file") file: String,
    ): Observable<ChmiTextForecastResult>

    /**
     * Which locality measures which pollutant, and under what registration id.
     */
    @GET("air_quality/now/metadata/metadata.json")
    fun getAirQualityStations(): Observable<ChmiAirQualityMetadata>

    /**
     * The last hourly average of every pollutant at every station in the country, as
     * `idRegistration, startTime, idValueType, value` — about 18 KB.
     */
    @GET("air_quality/now/data/airquality_1h_avg_CZ.csv")
    fun getAirQuality(): Observable<ResponseBody>

    /**
     * The stations that have a 1991–2020 normal, as
     * `WSI;GH ID;BEGIN_DATE;END_DATE;FULL_NAME;GEOGR1;GEOGR2;ELEVATION` — semicolon
     * separated, with a comma for the decimal point.
     */
    @GET("meteorology/products/climate_normal_stations/period_1991_2020/temperature_1991_2020_list_of_stations.csv")
    fun getNormalsStations(): Observable<ResponseBody>

    /**
     * One station's monthly normal of one element, as `Eg.Gh.Id,Eg.El.Abbreviation,Month,
     * Normal.AVG`. Month 13 is the annual figure. `TMA` is the mean daily maximum and `TMI`
     * the mean daily minimum, which is exactly the pair Breezy Weather wants.
     */
    @GET(
        "meteorology/products/climate_normal_stations/period_1991_2020/temperature/{station}_{element}_1991_2020_normal.csv"
    )
    fun getNormals(
        @Path("station") station: String,
        @Path("element") element: String,
    ): Observable<ResponseBody>
}
