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

import kotlinx.serialization.Serializable

/**
 * One ALADIN meteogram, hour by hour.
 *
 * The reply also carries a `parameters` block naming and dimensioning every field, and a `z`
 * with the grid point's elevation; neither is needed here, since the units are fixed.
 */
@Serializable
data class ChmiMeteogramResult(
    val data: List<ChmiMeteogramHour>? = null,
)

/**
 * A single hour. Every field is optional: the reply for a point outside the ALADIN domain is
 * an error object, which lands here as nothing at all.
 */
@Serializable
data class ChmiMeteogramHour(
    /**
     * ISO 8601 with an explicit `+00:00` offset — the observation streams use `Z` for the same
     * thing, and both parse the same way.
     */
    val validityTime: String? = null,
    /** Air temperature 2 m above the ground, °C. */
    val t2m: Double? = null,
    /** Relative humidity 2 m above the ground, %. */
    val rh2m: Double? = null,
    /**
     * Total precipitation, mm/h. The step is one hour, so the figure is also that hour's
     * accumulation.
     */
    val prec: Double? = null,
    /** The snow part of [prec], mm/h. */
    val snow: Double? = null,
    /** Pressure reduced to sea level, hPa. */
    val mslp: Double? = null,
    /** Total cloud cover, % — not oktas, unlike the observation streams. */
    val cloudsTot: Double? = null,
    /** Wind speed 10 m above the ground, m/s. */
    val windSpeed: Double? = null,
    /** Gusts 10 m above the ground, m/s. */
    val windGustSpeed: Double? = null,
    /** Wind direction 10 m above the ground, degrees, measured as the direction it blows from. */
    val windDirection: Double? = null,
    /**
     * ČHMÚ's own weather icon code, whose vocabulary is published at
     * https://www.chmi.cz/predpoved-pocasi/ikony-pocasi
     */
    val icon: Int? = null,
)
