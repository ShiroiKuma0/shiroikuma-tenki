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

package org.breezyweather.ui.meteomap

import android.graphics.Color
import androidx.annotation.ColorInt
import breezyweather.domain.weather.model.Hourly
import org.breezyweather.sources.chmi.map.ChmiMapProduct
import org.breezyweather.ui.common.charts.TemperatureColorScale

/**
 * shiroikuma fork: what a reading looks like on our map.
 *
 * ČHMÚ's frames arrive in ČHMÚ's colours; [org.breezyweather.sources.chmi.map.ChmiMapValueScale]
 * turns those back into readings, and this turns readings into the app's own palette. Temperature
 * deliberately borrows [TemperatureColorScale] unchanged, so a colour on the map is the same colour
 * that temperature has on the hourly meteogram — the map and the charts agree by construction
 * rather than by eye.
 *
 * Everything that falls out of the sky is blue (白い熊), and the overlays fade out towards the
 * bottom of their range so a trace of drizzle tints the map instead of blanketing it.
 */
object MeteomapPalette {

    /** value (in the product's own unit) -> ARGB. */
    fun rampFor(product: ChmiMapProduct): (Double) -> Int = when (product) {
        // Deci-Celsius: the domain the trend charts' polyline values live in.
        ChmiMapProduct.TEMPERATURE -> { value -> TemperatureColorScale.colorAt((value * 10).toFloat()) }
        ChmiMapProduct.PRECIPITATION -> { value -> interpolate(RAIN, value) }
        ChmiMapProduct.RADAR -> { value -> interpolate(REFLECTIVITY, value) }
        ChmiMapProduct.SNOW -> { _ -> SNOW_COLOR }
        ChmiMapProduct.CLOUD_COVER -> { value -> interpolate(CLOUD, value) }
        ChmiMapProduct.HUMIDITY -> { value -> interpolate(HUMIDITY, value) }
        ChmiMapProduct.WIND -> { value -> interpolate(WIND, value) }
        ChmiMapProduct.SUNSHINE -> { value -> interpolate(SUNSHINE, value) }
    }

    /**
     * The location's own hourly forecast as the series the strip under the map plots, in whatever
     * unit that layer's ramp expects.
     *
     * This is the same data the hourly meteogram draws, so the strip and the chart on the weather
     * screen agree — and the strip's colours are the map's, since both go through [rampFor].
     */
    fun seriesFor(product: ChmiMapProduct, hourly: List<Hourly>): List<Pair<Long, Double>> {
        val reading: (Hourly) -> Double? = when (product) {
            ChmiMapProduct.TEMPERATURE -> { h -> h.temperature?.temperature?.inCelsius }
            ChmiMapProduct.PRECIPITATION, ChmiMapProduct.RADAR ->
                { h -> h.precipitation?.total?.inMillimeters }
            ChmiMapProduct.SNOW -> { h -> h.precipitation?.snow?.inMillimeters }
            ChmiMapProduct.CLOUD_COVER -> { h -> h.cloudCover?.inPercent?.div(OKTA_PERCENT) }
            ChmiMapProduct.WIND -> { h -> h.wind?.speed?.inMetersPerSecond }
            ChmiMapProduct.HUMIDITY -> { h -> h.relativeHumidity?.inPercent }
            // Sunshine duration is kept per day, not per hour, so there is no series to plot and
            // the strip stays empty rather than showing a stand-in for it.
            ChmiMapProduct.SUNSHINE -> { _ -> null }
        }
        return hourly.mapNotNull { h -> reading(h)?.let { h.date.time to it } }.sortedBy { it.first }
    }

    /**
     * Radar is in dBZ, which no forecast reports — so its strip plots the hourly rain instead,
     * and has to be coloured by the rain ramp rather than the reflectivity one.
     */
    fun seriesRampFor(product: ChmiMapProduct): (Double) -> Int =
        if (product == ChmiMapProduct.RADAR) rampFor(ChmiMapProduct.PRECIPITATION) else rampFor(product)

    /**
     * A few stops off a product's ramp, lowest first — for the key drawn beside the map.
     */
    fun keyFor(product: ChmiMapProduct): List<Pair<Double, Int>> {
        val ramp = rampFor(product)
        return when (product) {
            ChmiMapProduct.TEMPERATURE -> listOf(-10.0, 0.0, 10.0, 20.0, 30.0, 40.0)
            ChmiMapProduct.PRECIPITATION -> listOf(0.1, 1.0, 5.0, 20.0, 100.0)
            ChmiMapProduct.RADAR -> listOf(4.0, 20.0, 32.0, 44.0, 60.0)
            ChmiMapProduct.SNOW -> listOf(1.0)
            ChmiMapProduct.CLOUD_COVER -> listOf(1.0, 4.0, 8.0)
            ChmiMapProduct.HUMIDITY -> listOf(0.0, 50.0, 100.0)
            ChmiMapProduct.WIND -> listOf(0.0, 10.0, 20.0, 35.0, 50.0)
            ChmiMapProduct.SUNSHINE -> listOf(0.0, 30.0, 60.0)
        }.map { it to ramp(it) }
    }

    /**
     * Linear between the stops, alpha included, so a ramp can fade in as well as change hue.
     * Stops are lowest first.
     */
    @ColorInt
    private fun interpolate(stops: List<Pair<Double, Int>>, value: Double): Int {
        if (value <= stops.first().first) return stops.first().second
        if (value >= stops.last().first) return stops.last().second
        for (i in 0..<stops.lastIndex) {
            val (low, lowColor) = stops[i]
            val (high, highColor) = stops[i + 1]
            if (value in low..high) {
                val ratio = if (high == low) 0.0 else (value - low) / (high - low)
                return blend(lowColor, highColor, ratio)
            }
        }
        return stops.last().second
    }

    @ColorInt
    private fun blend(@ColorInt from: Int, @ColorInt to: Int, ratio: Double): Int {
        val inverse = 1.0 - ratio
        return Color.argb(
            (Color.alpha(from) * inverse + Color.alpha(to) * ratio).toInt(),
            (Color.red(from) * inverse + Color.red(to) * ratio).toInt(),
            (Color.green(from) * inverse + Color.green(to) * ratio).toInt(),
            (Color.blue(from) * inverse + Color.blue(to) * ratio).toInt()
        )
    }

    /** mm/h. */
    private val RAIN = listOf(
        0.1 to Color.argb(120, 25, 55, 150),
        1.0 to Color.argb(170, 30, 100, 220),
        5.0 to Color.argb(205, 60, 150, 245),
        20.0 to Color.argb(235, 120, 210, 255),
        100.0 to Color.argb(255, 235, 250, 255)
    )

    /** dBZ. The same family as the rain, since it is the same thing seen another way. */
    private val REFLECTIVITY = listOf(
        4.0 to Color.argb(110, 22, 45, 130),
        20.0 to Color.argb(170, 30, 100, 220),
        32.0 to Color.argb(210, 60, 150, 245),
        44.0 to Color.argb(240, 130, 215, 255),
        60.0 to Color.argb(255, 240, 250, 255)
    )

    /** Snow is a mask, not a scale — it says where, never how much. */
    private val SNOW_COLOR = Color.argb(220, 205, 235, 255)

    /** Oktas. */
    private val CLOUD = listOf(
        1.0 to Color.argb(35, 200, 200, 190),
        4.0 to Color.argb(100, 225, 225, 215),
        8.0 to Color.argb(165, 245, 245, 240)
    )

    /** Per cent. Dry reads warm and thin, humid reads cool and solid. */
    private val HUMIDITY = listOf(
        0.0 to Color.argb(50, 150, 110, 40),
        50.0 to Color.argb(120, 120, 170, 130),
        100.0 to Color.argb(200, 60, 200, 190)
    )

    /** m/s. */
    private val WIND = listOf(
        0.0 to Color.argb(30, 90, 90, 20),
        10.0 to Color.argb(120, 215, 215, 90),
        20.0 to Color.argb(180, 245, 180, 40),
        35.0 to Color.argb(225, 220, 80, 30),
        50.0 to Color.argb(255, 150, 20, 20)
    )

    /** ČHMÚ's cloud legend is in oktas, and the app's readings are per cent. */
    private const val OKTA_PERCENT = 12.5

    /** Minutes of sunshine in the hour. */
    private val SUNSHINE = listOf(
        0.0 to Color.argb(25, 60, 60, 20),
        30.0 to Color.argb(130, 200, 200, 60),
        60.0 to Color.argb(225, 255, 255, 90)
    )
}
