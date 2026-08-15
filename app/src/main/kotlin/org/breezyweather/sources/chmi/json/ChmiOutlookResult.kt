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
 * The nine-day national outlook, as eighteen rows: two per day, paired by [ChmiOutlookRow.number].
 */
@Serializable
data class ChmiOutlookResult(
    val data: List<ChmiOutlookRow>? = null,
)

/**
 * Half of a day. The row at 22:00 UTC carries [minimumTemperature] and the one at 10:00 UTC
 * [maximumTemperature]; both belong to the same local day, because 22:00 UTC is already
 * midnight in Czechia.
 *
 * There is no precipitation here in any form — not an amount, not a probability.
 */
@Serializable
data class ChmiOutlookRow(
    /** Which day of the outlook, 0 being today. Pairs the two rows of a day. */
    val number: Int? = null,
    /** ISO 8601 in UTC. */
    val time: String? = null,
    /**
     * The overnight minimum, °C — the night *before* the day it is filed under, which is the
     * opposite of the half-day Breezy Weather calls a night.
     */
    val minimumTemperature: Double? = null,
    /** That afternoon's maximum, °C. */
    val maximumTemperature: Double? = null,
    /** ČHMÚ's own weather icon code, the same vocabulary the meteogram uses. */
    val weatherIcon: Int? = null,
    /** Total cloud cover in **oktas**, unlike the meteogram's percentage. */
    val totalCloudCover: Double? = null,
)
