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

package breezyweather.domain.weather.model

import java.io.Serializable

/**
 * shiroikuma fork: one extra forecast source's arrays, drawn under the primary one on the main view.
 *
 * Only what a trend chart reads. Everything else on the screen — current conditions, air quality,
 * pollen, minutely, alerts, normals — stays the primary source's, so this deliberately carries no
 * [Base]: an alternate has no refresh time of its own to show anywhere.
 */
data class AlternateForecast(
    val dailyForecast: List<Daily> = emptyList(),
    val hourlyForecast: List<Hourly> = emptyList(),
) : Serializable {

    val isEmpty: Boolean
        get() = dailyForecast.isEmpty() && hourlyForecast.isEmpty()
}
