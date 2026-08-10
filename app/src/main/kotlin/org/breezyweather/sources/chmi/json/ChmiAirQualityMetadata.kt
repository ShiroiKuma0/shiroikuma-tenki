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

package org.breezyweather.sources.chmi.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The air-quality station catalogue. Large (about 1.5 MB) and almost entirely made of things
 * we do not need — laboratories, instruments, postal addresses — so only the path from a
 * locality to a measurement's registration id is modelled here.
 *
 * It is read once per location, to learn which rows of the hourly file belong to the nearest
 * station; a refresh then only fetches those 18 KB.
 */
@Serializable
data class ChmiAirQualityMetadata(
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        @SerialName("Localities") val localities: List<Locality>? = null,
    )

    @Serializable
    data class Locality(
        @SerialName("LocalityCode") val code: String? = null,
        @SerialName("Classification") val classification: Classification? = null,
        @SerialName("Localization") val localization: Localization? = null,
        @SerialName("MeasuringPrograms") val measuringPrograms: List<MeasuringProgram>? = null,
    )

    @Serializable
    data class Classification(
        /**
         * `<type>/<zone>/<characteristics>`, where the type is B for a background station,
         * T for a traffic one and I for an industrial one — e.g. `B/U/R` for an urban
         * residential background station.
         */
        @SerialName("Abbreviation") val abbreviation: String? = null,
    )

    @Serializable
    data class Localization(
        @SerialName("LatAsNumber") val latitude: Double? = null,
        @SerialName("LonAsNumber") val longitude: Double? = null,
    )

    @Serializable
    data class MeasuringProgram(
        @SerialName("Measurements") val measurements: List<Measurement>? = null,
    )

    @Serializable
    data class Measurement(
        @SerialName("IdRegistration") val idRegistration: Int? = null,
        @SerialName("ComponentCode") val componentCode: String? = null,
    )
}
