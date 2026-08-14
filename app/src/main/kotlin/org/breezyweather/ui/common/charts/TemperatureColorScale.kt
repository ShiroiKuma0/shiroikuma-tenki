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

package org.breezyweather.ui.common.charts

import android.graphics.Color
import androidx.core.graphics.ColorUtils

/**
 * shiroikuma fork: the one temperature → colour scale, shared by the details charts and the
 * main screen's trend charts.
 *
 * The scale is semantic — the colour IS the reading — so it deliberately ignores the fork's
 * black-yellow palette, the same way the air-quality and UV scales do.
 *
 * Stops are in whole degrees Celsius, hottest first. [stopsInDeciCelsius] is what the View-world
 * charts want, since `Temperature.value` is stored in deci-Celsius.
 */
object TemperatureColorScale {

    private val STOPS_CELSIUS: List<Pair<Int, Int>> = listOf(
        40 to Color.rgb(150, 20, 20),
        30 to Color.rgb(220, 80, 30),
        23 to Color.rgb(245, 180, 40),
        18 to Color.rgb(215, 215, 90),
        16 to Color.rgb(120, 190, 140),
        11 to Color.rgb(40, 175, 190),
        3 to Color.rgb(30, 120, 215),
        -12 to Color.rgb(60, 80, 200)
    )

    /** Whole degrees Celsius, hottest first — for callers converting to a display unit themselves. */
    val stopsCelsius: List<Pair<Int, Int>> get() = STOPS_CELSIUS

    /** Hottest first, in deci-Celsius — the domain the trend charts' polyline values live in. */
    val stopsInDeciCelsius: List<Pair<Float, Int>> = STOPS_CELSIUS.map { (celsius, color) ->
        celsius * 10f to color
    }

    /**
     * The colour at an arbitrary temperature, interpolated between the two stops bracketing it
     * and clamped to the end stops beyond the scale.
     */
    fun colorAt(deciCelsius: Float): Int {
        val stops = stopsInDeciCelsius
        stops.first().let { if (deciCelsius >= it.first) return it.second }
        stops.last().let { if (deciCelsius <= it.first) return it.second }

        for (i in 0..<stops.lastIndex) {
            val (hotValue, hotColor) = stops[i]
            val (coldValue, coldColor) = stops[i + 1]
            if (deciCelsius in coldValue..hotValue) {
                // 0 at the hot stop, 1 at the cold one
                val ratio = (hotValue - deciCelsius) / (hotValue - coldValue)
                return ColorUtils.blendARGB(hotColor, coldColor, ratio)
            }
        }
        return stops.last().second
    }
}
