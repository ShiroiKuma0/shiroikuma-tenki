/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package breezyweather.domain.location.model

import android.os.Parcel
import android.os.Parcelable
import breezyweather.domain.weather.model.Weather
import java.util.Locale
import java.util.TimeZone

data class Location(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timeZone: TimeZone = TimeZone.getTimeZone("GMT"),

    val country: String = "",
    val countryCode: String? = null,
    val admin1: String? = null,
    val admin1Code: String? = null,
    val admin2: String? = null,
    val admin2Code: String? = null,
    val admin3: String? = null,
    val admin3Code: String? = null,
    val admin4: String? = null,
    val admin4Code: String? = null,
    val city: String = "",
    val cityId: String? = null,
    val district: String? = null,

    val needsGeocodeRefresh: Boolean = false,

    val customName: String? = null,

    val weather: Weather? = null,

    val forecastSource: String = "openmeteo",

    /**
     * shiroikuma fork: the ordered forecast sources for the **hourly** charts, the first one being
     * [forecastSource]. Empty means "only [forecastSource]", which is what every location built
     * before this feature says. Read it through [orderedHourlyForecastSources], never directly.
     */
    val forecastSources: List<String> = emptyList(),

    /**
     * shiroikuma fork: the ordered forecast sources for the **daily** charts, chosen independently
     * of [forecastSources] — a source good at hours is not always the one you want for days.
     *
     * Empty means "whatever the hourly charts use", which is what every location built before the
     * two lists were split says. Read it through [orderedDailyForecastSources], never directly.
     */
    val dailyForecastSources: List<String> = emptyList(),
    val currentSource: String? = null,
    val airQualitySource: String? = null,
    val pollenSource: String? = null,
    val minutelySource: String? = null,
    val alertSource: String? = null,
    val normalsSource: String? = null,
    val reverseGeocodingSource: String? = null,

    val isCurrentPosition: Boolean = false,

    /**
     * "accu": {"cityId": "230"}
     * "nws": {"gridId": "8", "gridX": "20", "gridY": "30"}
     * etc
     */
    val parameters: Map<String, Map<String, String>> = emptyMap(),
) : Parcelable {

    val formattedId: String
        get() = if (isCurrentPosition) {
            CURRENT_POSITION_ID
        } else {
            String.format(Locale.US, "%f", latitude) +
                "&" +
                String.format(Locale.US, "%f", longitude) +
                "&" +
                forecastSource
        }

    val isUsable: Boolean
        // Sorry people living exactly at 0,0
        get() = latitude != 0.0 || longitude != 0.0

    /**
     * shiroikuma fork: the forecast sources the **hourly** charts draw, in the order arranged.
     *
     * Self-healing on purpose: [forecastSource] is the identity source baked into [formattedId], so it
     * always has to be in there. A location that predates the feature, or a `copy(forecastSource = …)`
     * that did not touch [forecastSources], still comes out with a sensible list.
     */
    val orderedHourlyForecastSources: List<String>
        get() {
            val ordered = forecastSources.filter { it.isNotEmpty() }.distinct()
            return when {
                ordered.isEmpty() -> listOf(forecastSource)
                forecastSource in ordered -> ordered
                else -> listOf(forecastSource) + ordered
            }
        }

    /**
     * shiroikuma fork: the forecast sources the **daily** charts draw, in their own order.
     *
     * Falls back to the hourly list when never set, so a location from before the split keeps
     * drawing exactly what it drew. Unlike the hourly list this one is **not** forced to contain
     * [forecastSource]: the identity source has to be drawn somewhere, and the hourly list is where
     * that is guaranteed — so the daily charts are free to leave it out entirely.
     */
    val orderedDailyForecastSources: List<String>
        get() = dailyForecastSources.filter { it.isNotEmpty() }.distinct()
            .ifEmpty { orderedHourlyForecastSources }

    /**
     * shiroikuma fork: every source either set of charts needs, which is what has to be fetched.
     *
     * Hourly first, so the identity source leads and the refresh order matches the way the charts
     * are stacked; then whatever only the daily charts asked for.
     */
    val orderedForecastSources: List<String>
        get() = (orderedHourlyForecastSources + orderedDailyForecastSources).distinct()

    val isTimeZoneInvalid: Boolean
        get() = timeZone.id == "GMT"

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(cityId)
        parcel.writeDouble(latitude)
        parcel.writeDouble(longitude)
        parcel.writeString(timeZone.id)
        parcel.writeString(customName)
        parcel.writeString(country)
        parcel.writeString(countryCode)
        parcel.writeString(admin1)
        parcel.writeString(admin1Code)
        parcel.writeString(admin2)
        parcel.writeString(admin2Code)
        parcel.writeString(admin3)
        parcel.writeString(admin3Code)
        parcel.writeString(admin4)
        parcel.writeString(admin4Code)
        parcel.writeString(city)
        parcel.writeString(district)
        parcel.writeString(forecastSource)
        parcel.writeStringList(forecastSources)
        parcel.writeStringList(dailyForecastSources)
        parcel.writeString(currentSource)
        parcel.writeString(airQualitySource)
        parcel.writeString(pollenSource)
        parcel.writeString(minutelySource)
        parcel.writeString(alertSource)
        parcel.writeString(normalsSource)
        parcel.writeString(reverseGeocodingSource)
        parcel.writeByte(if (isCurrentPosition) 1 else 0)
        parcel.writeByte(if (needsGeocodeRefresh) 1 else 0)
    }

    override fun describeContents() = 0

    constructor(parcel: Parcel) : this(
        cityId = parcel.readString(),
        latitude = parcel.readDouble(),
        longitude = parcel.readDouble(),
        timeZone = TimeZone.getTimeZone(parcel.readString()!!),
        customName = parcel.readString(),
        country = parcel.readString()!!,
        countryCode = parcel.readString(),
        admin1 = parcel.readString(),
        admin1Code = parcel.readString(),
        admin2 = parcel.readString(),
        admin2Code = parcel.readString(),
        admin3 = parcel.readString(),
        admin3Code = parcel.readString(),
        admin4 = parcel.readString(),
        admin4Code = parcel.readString(),
        city = parcel.readString()!!,
        district = parcel.readString(),
        forecastSource = parcel.readString()!!,
        forecastSources = parcel.createStringArrayList() ?: emptyList(),
        dailyForecastSources = parcel.createStringArrayList() ?: emptyList(),
        currentSource = parcel.readString(),
        airQualitySource = parcel.readString(),
        pollenSource = parcel.readString(),
        minutelySource = parcel.readString(),
        alertSource = parcel.readString(),
        normalsSource = parcel.readString(),
        reverseGeocodingSource = parcel.readString(),
        isCurrentPosition = parcel.readByte() != 0.toByte(),
        needsGeocodeRefresh = parcel.readByte() != 0.toByte()
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || other !is Location) {
            return false
        }

        if (formattedId != other.formattedId) {
            return false
        }

        if (customName != other.customName) {
            return false
        }

        if (forecastSource != other.forecastSource) {
            return false
        }

        if (orderedHourlyForecastSources != other.orderedHourlyForecastSources) {
            return false
        }

        if (orderedDailyForecastSources != other.orderedDailyForecastSources) {
            return false
        }

        if (currentSource != other.currentSource) {
            return false
        }

        if (airQualitySource != other.airQualitySource) {
            return false
        }

        if (pollenSource != other.pollenSource) {
            return false
        }

        if (minutelySource != other.minutelySource) {
            return false
        }

        if (alertSource != other.alertSource) {
            return false
        }

        if (normalsSource != other.normalsSource) {
            return false
        }

        if (needsGeocodeRefresh != other.needsGeocodeRefresh) {
            return false
        }

        if (parameters != other.parameters) {
            return false
        }

        val thisWeather = weather
        val otherWeather = other.weather
        if (thisWeather == null && otherWeather == null) {
            return true
        }
        return if (thisWeather != null && otherWeather != null) {
            thisWeather.base.refreshTime?.time == otherWeather.base.refreshTime?.time
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return formattedId.hashCode()
    }

    override fun toString(): String {
        val builder = StringBuilder("$country $admin1 $admin2 $admin3 $admin4")
        if (admin4 != city && city.isNotEmpty()) {
            builder.append(" ").append(city)
        }
        if (city != district && !district.isNullOrEmpty()) {
            builder.append(" ").append(district)
        }
        return builder.toString()
    }

    fun administrationLevels(): String {
        val builder = StringBuilder()
        if (country.isNotEmpty()) {
            builder.append(country)
        }
        if (!admin1.isNullOrEmpty()) {
            if (builder.toString().isNotEmpty()) {
                builder.append(", ")
            }
            builder.append(admin1)
        }
        if (!admin2.isNullOrEmpty()) {
            if (builder.toString().isNotEmpty()) {
                builder.append(", ")
            }
            builder.append(admin2)
        }
        if (!admin3.isNullOrEmpty() && (!admin4.isNullOrEmpty() || admin3 != city)) {
            if (builder.toString().isNotEmpty()) {
                builder.append(", ")
            }
            builder.append(admin3)
        }
        if (!admin4.isNullOrEmpty() && admin4 != city) {
            if (builder.toString().isNotEmpty()) {
                builder.append(", ")
            }
            builder.append(admin4)
        }
        return builder.toString()
    }

    val cityAndDistrict: String
        get() {
            val builder = StringBuilder()
            if (city.isNotEmpty()) {
                builder.append(city)
            }
            if (!district.isNullOrEmpty() && district != city) {
                if (builder.toString().isNotEmpty()) {
                    builder.append(", ")
                }
                builder.append(district)
            }
            return builder.toString()
        }

    fun toLocationWithAddressInfo(
        currentLocale: Locale,
        locationAddressInfo: LocationAddressInfo,
        overwriteCoordinates: Boolean,
    ): Location {
        return copy(
            latitude = if (overwriteCoordinates &&
                locationAddressInfo.latitude != null &&
                locationAddressInfo.longitude != null
            ) {
                locationAddressInfo.latitude
            } else {
                latitude
            },
            longitude = if (overwriteCoordinates &&
                locationAddressInfo.latitude != null &&
                locationAddressInfo.longitude != null
            ) {
                locationAddressInfo.longitude
            } else {
                longitude
            },
            timeZone = TimeZone.getTimeZone(locationAddressInfo.timeZoneId ?: "GMT"),
            country = if (locationAddressInfo.country.isNullOrEmpty() && !locationAddressInfo.country.isNullOrEmpty()) {
                Locale.Builder()
                    .setLanguage(currentLocale.language)
                    .setRegion(countryCode)
                    .build()
                    .displayCountry
            } else {
                locationAddressInfo.country ?: ""
            },
            countryCode = locationAddressInfo.countryCode,
            admin1 = locationAddressInfo.admin1 ?: "",
            admin1Code = locationAddressInfo.admin1Code ?: "",
            admin2 = locationAddressInfo.admin2 ?: "",
            admin2Code = locationAddressInfo.admin2Code ?: "",
            admin3 = locationAddressInfo.admin3 ?: "",
            admin3Code = locationAddressInfo.admin3Code ?: "",
            admin4 = locationAddressInfo.admin4 ?: "",
            admin4Code = locationAddressInfo.admin4Code ?: "",
            city = locationAddressInfo.city ?: "",
            cityId = locationAddressInfo.cityCode ?: "",
            district = locationAddressInfo.district ?: "",
            needsGeocodeRefresh = false
        )
    }

    /**
     * It is not intended to be used by the reverse geocoding source
     * You're supposed to call the LocationAddressInfo constructor
     *
     * Currently only used by Natural Earth Service for a very special case
     * Use with precaution!
     */
    // @Delicate
    fun toAddressInfo(): LocationAddressInfo {
        return LocationAddressInfo(
            latitude = latitude,
            longitude = longitude,
            timeZoneId = timeZone.id,
            country = country,
            countryCode = countryCode ?: "",
            admin1 = admin1,
            admin1Code = admin1Code,
            admin2 = admin2,
            admin2Code = admin2Code,
            admin3 = admin3,
            admin3Code = admin3Code,
            admin4 = admin4,
            admin4Code = admin4Code,
            city = city,
            cityCode = cityId,
            district = district
        )
    }

    val hasValidCountryCode: Boolean
        get() {
            return !countryCode.isNullOrEmpty() && countryCode.matches(Regex("[A-Za-z]{2}"))
        }

    companion object {

        const val CURRENT_POSITION_ID = "CURRENT_POSITION"
        const val CLOSE_DISTANCE = 5000 // 5 km

        fun isEquals(a: String?, b: String?): Boolean {
            return if (a.isNullOrEmpty() && b.isNullOrEmpty()) {
                true
            } else if (!a.isNullOrEmpty() && !b.isNullOrEmpty()) {
                a == b
            } else {
                false
            }
        }

        @JvmField
        val CREATOR = object : Parcelable.Creator<Location> {

            override fun createFromParcel(parcel: Parcel): Location {
                return Location(parcel)
            }

            override fun newArray(size: Int): Array<Location?> {
                return arrayOfNulls(size)
            }
        }
    }
}
