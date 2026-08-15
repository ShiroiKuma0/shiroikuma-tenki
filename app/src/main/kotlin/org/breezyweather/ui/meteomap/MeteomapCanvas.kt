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

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.breezyweather.sources.chmi.map.ChmiMapBounds
import org.breezyweather.sources.chmi.map.ChmiMapFrameImage
import org.breezyweather.sources.chmi.map.ChmiMapManifest
import org.breezyweather.sources.chmi.map.ChmiMapProduct
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * shiroikuma fork: the map itself — one frame over ground we draw, pannable and pinchable.
 *
 * The country beyond Czechia is drawn too, only dimmed: a shower crossing from Saxony matters long
 * before it arrives, so clipping it away would hide the very thing worth watching (白い熊). Its
 * boundaries between the third countries are drawn so it is not floating in a void, the water is
 * picked out in blue as the only landmarks a weather map needs, and Prague is brightest of all.
 */
@Composable
fun MeteomapCanvas(
    manifest: ChmiMapManifest?,
    frame: ChmiMapFrameImage?,
    background: Color,
    accent: Color,
    cities: List<MeteomapCity>,
    valueSizeDp: Float,
    nameSizeDp: Float,
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
) {
    val context = LocalContext.current
    var scale by remember(resetKey) { mutableFloatStateOf(1f) }
    var offset by remember(resetKey) { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = modifier.pointerInput(resetKey) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                // Held loosely inside the viewport: at 1x there is nowhere to go, and further in
                // the slack grows with the zoom so the whole country stays reachable.
                val slackX = size.width * (scale - 1f) / 2f
                val slackY = size.height * (scale - 1f) / 2f
                offset = Offset(
                    (offset.x + pan.x).coerceIn(-slackX, slackX),
                    (offset.y + pan.y).coerceIn(-slackY, slackY)
                )
            }
        }
    ) {
        drawRect(color = background)
        val bounds = manifest?.bounds ?: return@Canvas
        val image = frame?.bitmap?.asImageBitmap()

        // The frame covers its bounding box exactly, so fitting the bitmap fits the ground. With
        // no frame yet, the ground is laid out from the box's own proportions instead.
        val aspect = image?.let { it.width.toFloat() / it.height } ?: DEFAULT_ASPECT
        val fit = min(size.width / aspect, size.height)
        val width = fit * aspect
        val height = fit
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f

        withTransform({
            translate(offset.x, offset.y)
            scale(scale, scale, pivot = Offset(center.x, center.y))
        }) {
            translate(left, top) {
                val target = IntSize(width.roundToInt(), height.roundToInt())
                // The ground is cut a little wider than the frame so its lines run off the edge
                // rather than stopping short of it — which means it has to be cut back here, or it
                // spills over the tabs above and the timeline below.
                clipRect(0f, 0f, width, height) {
                    val layers = MeteomapBasemap.layers(context, bounds, target.width, target.height)
                    val country = layers.silhouette.asComposePath()
                    val line = { dp: Float -> Stroke(width = dp.dp.toPx() / scale) }

                    if (image != null) {
                        // Everything, faint — this is what keeps the weather beyond the border visible.
                        drawImage(image, dstOffset = IntOffset.Zero, dstSize = target, alpha = OUTSIDE_ALPHA)
                        // Then Czechia again at full strength, so the country reads as the subject.
                        clipPath(country) {
                            drawImage(image = image, dstOffset = IntOffset.Zero, dstSize = target)
                        }
                    }

                    // Water. It sits over the field on purpose — a river is a landmark, and losing it
                    // under a rain band would defeat the point of drawing one.
                    drawPath(layers.rivers.asComposePath(), WATER.copy(alpha = WATER_ALPHA), style = line(0.9f))
                    drawPath(layers.lakes.asComposePath(), WATER.copy(alpha = WATER_ALPHA), style = line(0.9f))

                    // Where the third countries meet each other, in the accent and heavy enough not
                    // to be taken for a river — so it goes over the water, not under it, or every
                    // crossing would notch it in blue. Their frontiers with Czechia are not in the
                    // data at all: that line is the accent one below, and drawing it twice only
                    // thickened it. Round joins, because the runs are coarse and a miter spikes.
                    drawPath(
                        layers.boundaries.asComposePath(),
                        accent,
                        style = Stroke(
                            width = BOUNDARY_WIDTH_DP.dp.toPx() / scale,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // The country's own detail, kept quiet, then its border over the top of it.
                    drawPath(layers.districts.asComposePath(), accent.copy(alpha = DISTRICT_ALPHA), style = line(0.6f))
                    drawPath(country, accent, style = line(1.6f))

                    // Prague last and brightest — it is where the reader is.
                    drawPrague(layers.prague.asComposePath(), accent, line(2.2f))

                    if (manifest.product == ChmiMapProduct.TEMPERATURE) {
                        drawCityLabels(
                            cities,
                            frame?.readings.orEmpty(),
                            bounds,
                            target,
                            accent,
                            scale,
                            valueSizeDp,
                            nameSizeDp
                        )
                    }
                }
            }
        }
    }
}

/** A filled wash under a heavy outline: at a country's scale the city is only a few pixels wide. */
private fun DrawScope.drawPrague(
    path: androidx.compose.ui.graphics.Path,
    accent: Color,
    stroke: Stroke,
) {
    drawPath(path, accent.copy(alpha = PRAGUE_FILL_ALPHA))
    drawPath(path, accent, style = stroke)
}

/** Slightly dimmed, not cut away: enough to make Czechia the subject, not enough to hide a front. */
private const val OUTSIDE_ALPHA = 0.55f
private const val BOUNDARY_WIDTH_DP = 4f
private const val DISTRICT_ALPHA = 0.28f
private const val PRAGUE_FILL_ALPHA = 0.35f
private const val WATER_ALPHA = 0.75f
private val WATER = Color(0xFF4FC3F7)

/** The ALADIN and radar frames are both about this wide for their height. */
private const val DEFAULT_ASPECT = 598f / 378f
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 8f

/**
 * shiroikuma fork: the reading at a handful of towns, so the field can be read as numbers as well
 * as colour — and they change with the playback, being taken from whichever frame is on screen.
 *
 * Each sits on its own dark plate. An outline alone was not enough: the numerals land on whatever
 * the field happens to be doing beneath them, and yellow on a yellow-orange heatwave stays hard to
 * read however thickly it is outlined. The plate is the same trick the trend charts' readings use.
 */
private fun DrawScope.drawCityLabels(
    cities: List<MeteomapCity>,
    readings: Map<String, Double>,
    bounds: ChmiMapBounds,
    target: IntSize,
    accent: Color,
    zoom: Float,
    valueSizeDp: Float,
    nameSizeDp: Float,
) {
    if (cities.isEmpty() || readings.isEmpty()) return
    val south = ln(tan(PI / 4 + Math.toRadians(bounds.south) / 2))
    val north = ln(tan(PI / 4 + Math.toRadians(bounds.north) / 2))
    val spanX = bounds.east - bounds.west
    val spanY = north - south
    if (spanX == 0.0 || spanY == 0.0) return

    // Sized against the zoom so the labels stay legible without swelling as the map is pinched.
    val reading = valueSizeDp.dp.toPx() / zoom
    val name = nameSizeDp.dp.toPx() / zoom
    val dot = DOT_DP.dp.toPx() / zoom

    val outline = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = OUTLINE_DP.dp.toPx() / zoom
        color = android.graphics.Color.BLACK
        textAlign = Paint.Align.CENTER
    }
    val fill = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    cities.forEach { city ->
        val value = readings[city.id] ?: return@forEach
        val x = ((city.longitude - bounds.west) / spanX * target.width).toFloat()
        val y = ((north - ln(tan(PI / 4 + Math.toRadians(city.latitude) / 2))) / spanY * target.height).toFloat()

        drawCircle(Color.Black.copy(alpha = 0.75f), radius = dot, center = Offset(x, y))
        drawCircle(accent, radius = dot, center = Offset(x, y), style = Stroke(width = dot / 2.5f))

        val canvas = drawContext.canvas.nativeCanvas
        val pad = PLATE_PADDING_DP.dp.toPx() / zoom
        val corner = PLATE_CORNER_DP.dp.toPx() / zoom

        fill.textSize = reading
        outline.textSize = reading
        val label = "${value.roundToInt()}°"
        val labelBaseline = y - dot * 2f
        plate(canvas, fill, label, x, labelBaseline, pad, corner)
        fill.color = accent.toArgb()
        canvas.drawText(label, x, labelBaseline, outline)
        canvas.drawText(label, x, labelBaseline, fill)

        fill.textSize = name
        outline.textSize = name
        val nameBaseline = y + dot * 2f + name
        plate(canvas, fill, city.label, x, nameBaseline, pad, corner)
        fill.color = android.graphics.Color.WHITE
        canvas.drawText(city.label, x, nameBaseline, outline)
        canvas.drawText(city.label, x, nameBaseline, fill)
    }
}

/** The dark rounded plate a label sits on, sized to the text it has to carry. */
private fun plate(
    canvas: android.graphics.Canvas,
    paint: Paint,
    text: String,
    centreX: Float,
    baseline: Float,
    padding: Float,
    corner: Float,
) {
    val half = paint.measureText(text) / 2f + padding
    val metrics = paint.fontMetrics
    val was = paint.color
    val style = paint.style
    paint.color = android.graphics.Color.argb(PLATE_ALPHA, 0, 0, 0)
    paint.style = Paint.Style.FILL
    canvas.drawRoundRect(
        centreX - half,
        baseline + metrics.top - padding / 2f,
        centreX + half,
        baseline + metrics.bottom + padding / 2f,
        corner,
        corner,
        paint
    )
    paint.color = was
    paint.style = style
}

private const val DOT_DP = 3f
private const val OUTLINE_DP = 2.5f

/** How opaque the plate behind a label is, and how far it clears the glyphs. */
private const val PLATE_ALPHA = 175
private const val PLATE_PADDING_DP = 3f
private const val PLATE_CORNER_DP = 3f
