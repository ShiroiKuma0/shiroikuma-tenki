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

package org.breezyweather.sources.chmi.map

import android.graphics.Color
import org.breezyweather.sources.chmi.map.json.ChmiMapInit
import org.breezyweather.sources.chmi.map.json.ChmiMapLegend
import java.util.Date

/** The ground a product's frames cover, in degrees. The frames themselves are EPSG:3857. */
data class ChmiMapBounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
)

/** One frame: the minute it depicts, and the suffix that fetches it. */
data class ChmiMapFrameRef(
    val dataRef: String,
    val time: Date,
)

/**
 * What the app needs from a product's manifest, with the parts it does not need left behind.
 */
data class ChmiMapManifest(
    val product: ChmiMapProduct,
    val dataRefBase: String,
    val bounds: ChmiMapBounds,
    val frames: List<ChmiMapFrameRef>,
    /**
     * The last frame that is not in the future — where the card opens, and where the observed part
     * of a radar sequence ends and its nowcast begins.
     */
    val nowIndex: Int,
    val scale: ChmiMapValueScale,
    /** Seconds, when the product says how often it is refreshed. 180 on the radar. */
    val refreshSeconds: Int?,
) {
    companion object {
        fun of(product: ChmiMapProduct, init: ChmiMapInit): ChmiMapManifest? {
            val layer = init.layers?.firstOrNull { it.id == product.layerId } ?: return null
            val base = layer.dataRefBase ?: return null
            val box = init.boundingBoxes?.firstOrNull { it.id == layer.boundingBox }
                ?: init.boundingBoxes?.firstOrNull()
                ?: return null
            val bounds = ChmiMapBounds(
                west = box.xmin ?: return null,
                south = box.ymin ?: return null,
                east = box.xmax ?: return null,
                north = box.ymax ?: return null
            )
            val frames = layer.dataParts.orEmpty().mapNotNull { part ->
                part.dataRef?.let { ref ->
                    ChmiMapRepository.parseDataRef(ref)?.let { ChmiMapFrameRef(ref, it) }
                }
            }
            if (frames.isEmpty()) return null

            // Derived from the frames' own UTC stamps. The manifest's startTime is local on the
            // radar and UTC on the forecasts, so it is no use for this.
            val now = System.currentTimeMillis()
            val nowIndex = frames.indexOfLast { it.time.time <= now }.coerceAtLeast(0)

            val legend = init.legends?.firstOrNull { it.id == layer.legend }
                ?: init.legends?.firstOrNull()
            return ChmiMapManifest(
                product = product,
                dataRefBase = base,
                bounds = bounds,
                frames = frames,
                nowIndex = nowIndex,
                scale = ChmiMapValueScale.of(product.scale, legend),
                refreshSeconds = init.mapSetup?.refreshInterval
            )
        }
    }
}

/**
 * ČHMÚ's own scale for one product, as a colour-to-reading lookup.
 *
 * This is the piece that makes recolouring honest rather than decorative: the legend prints ticks
 * against its colours, so a colour can be turned back into the number it stands for, and that
 * number coloured again however we like.
 */
class ChmiMapValueScale private constructor(
    private val kind: ChmiMapScale,
    private val colors: IntArray,
    private val values: DoubleArray,
    private val maxDistance: Int,
) {
    val isEmpty: Boolean get() = kind != ChmiMapScale.MASK && colors.isEmpty()

    val lowest: Double get() = values.minOrNull() ?: 0.0
    val highest: Double get() = values.maxOrNull() ?: 1.0

    /**
     * What this pixel reads, or null if it is not a reading at all.
     *
     * The nearest colour on the scale wins, which absorbs the antialiasing and dithering along a
     * band's edge. Beyond [maxDistance] nothing on the scale is close enough and the pixel is
     * something else — the frame's border, the grey margin where the domain has no data.
     */
    fun readingOf(argb: Int): Double? {
        if (kind == ChmiMapScale.MASK) return 1.0
        if (colors.isEmpty()) return null
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        var best = 0
        var bestDistance = Int.MAX_VALUE
        for (i in colors.indices) {
            val c = colors[i]
            val dr = r - ((c shr 16) and 0xFF)
            val dg = g - ((c shr 8) and 0xFF)
            val db = b - (c and 0xFF)
            val d = dr * dr + dg * dg + db * db
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return if (bestDistance > maxDistance * maxDistance) null else values[best]
    }

    companion object {
        fun of(kind: ChmiMapScale, legend: ChmiMapLegend?): ChmiMapValueScale {
            if (kind == ChmiMapScale.MASK || legend == null) {
                return ChmiMapValueScale(kind, IntArray(0), DoubleArray(0), MAX_DISTANCE)
            }
            val bands = legend.items
                ?.firstNotNullOfOrNull { it.series }
                ?.firstNotNullOfOrNull { it.entries }
            if (bands != null) {
                // The radar's shape: each band carries its own bounds, so nothing to interpolate.
                val pairs = bands.mapNotNull { entry ->
                    val colour = parseColour(entry.barva) ?: return@mapNotNull null
                    val value = entry.min ?: return@mapNotNull null
                    colour to value
                }
                return ChmiMapValueScale(
                    kind,
                    pairs.map { it.first }.toIntArray(),
                    pairs.map { it.second }.toDoubleArray(),
                    RADAR_MAX_DISTANCE
                )
            }

            // The usual shape: a long run of colours with a printed tick every so often. A colour
            // between two ticks takes the value its position implies.
            val items = legend.items.orEmpty()
            val ticks = items.mapIndexedNotNull { index, item ->
                kind.readTick(item.label)?.let { index to it }
            }
            if (ticks.isEmpty()) {
                return ChmiMapValueScale(kind, IntArray(0), DoubleArray(0), MAX_DISTANCE)
            }
            val colours = mutableListOf<Int>()
            val readings = mutableListOf<Double>()
            items.forEachIndexed { index, item ->
                val colour = parseColour(item.color) ?: return@forEachIndexed
                val below = ticks.lastOrNull { it.first <= index } ?: ticks.first()
                val above = ticks.firstOrNull { it.first >= index } ?: ticks.last()
                val value = if (above.first == below.first) {
                    below.second
                } else {
                    below.second +
                        (above.second - below.second) *
                        (index - below.first) / (above.first - below.first).toDouble()
                }
                colours.add(colour)
                readings.add(value)
            }
            return ChmiMapValueScale(
                kind,
                colours.toIntArray(),
                readings.toDoubleArray(),
                MAX_DISTANCE
            )
        }

        /** ČHMÚ writes `#RRGGBB` or `#RRGGBBAA`; the alpha is the legend swatch's, not the data's. */
        private fun parseColour(value: String?): Int? {
            val hex = value?.removePrefix("#")?.takeIf { it.length >= 6 } ?: return null
            return runCatching {
                Color.rgb(
                    hex.substring(0, 2).toInt(16),
                    hex.substring(2, 4).toInt(16),
                    hex.substring(4, 6).toInt(16)
                )
            }.getOrNull()
        }

        private const val MAX_DISTANCE = 70
        private const val RADAR_MAX_DISTANCE = 55
    }
}
