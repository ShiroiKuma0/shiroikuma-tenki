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
 * A regional text forecast, written by the duty forecaster.
 *
 * It arrives as a GeoJSON FeatureCollection of one feature whose geometry is the outline of
 * the region — ignored here, since the region is already chosen by the filename — carrying a
 * headline and a handful of ordered prose blocks (the weather, the maximum temperature, the
 * wind, the expected rainfall, a comment).
 */
@Serializable
data class ChmiTextForecastResult(
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val features: List<Feature>? = null,
    )

    @Serializable
    data class Feature(
        val properties: Properties? = null,
    )

    @Serializable
    data class Properties(
        @SerialName("headline-main") val headline: Headline? = null,
        val data: List<Block>? = null,
    )

    @Serializable
    data class Headline(
        val headline: String? = null,
    )

    @Serializable
    data class Block(
        val displayOrder: Int? = null,
        val headline: String? = null,
        val displayText: String? = null,
    )
}
