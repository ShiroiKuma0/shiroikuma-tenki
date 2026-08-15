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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import org.breezyweather.sources.chmi.map.ChmiMapFrameRef
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * shiroikuma fork: the strip under the map — where you are in time, and what the weather is doing
 * across the whole of it.
 *
 * A miniature of the field above, plotted against the same clock as the track beneath it: the
 * curve is the location's own hourly forecast, painted column by column with the layer's own
 * colours, so a glance says which hour the temperature climbs or the rain arrives. Day boundaries
 * and hour marks are ruled through **both** the chart and the track, which is what ties a bump in
 * the curve to a position on the slider.
 *
 * It replaces a Material slider because a slider cannot be ruled through.
 */
@Composable
fun MeteomapTimeline(
    frames: List<ChmiMapFrameRef>,
    index: Int,
    nowIndex: Int,
    series: List<Pair<Long, Double>>,
    colorAt: (Double) -> Int,
    accent: Color,
    dim: Color,
    timeZone: TimeZone,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = frames.size
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(TOTAL_HEIGHT.dp)
            .pointerInput(count) {
                if (count < 2) return@pointerInput
                detectTapGestures { onSeek(indexAt(it.x, size.width.toFloat(), count)) }
            }
            .pointerInput(count) {
                if (count < 2) return@pointerInput
                detectDragGestures { change, _ ->
                    onSeek(indexAt(change.position.x, size.width.toFloat(), count))
                }
            }
    ) {
        if (count < 2) return@Canvas
        val chartHeight = size.height * CHART_FRACTION
        val trackTop = chartHeight + GAP.dp.toPx()
        val trackHeight = size.height - trackTop

        drawSeries(frames, series, colorAt, accent, chartHeight)
        drawRules(frames, timeZone, accent, dim, chartHeight, trackTop + trackHeight)
        drawTrack(count, index, nowIndex, accent, dim, trackTop, trackHeight)
        drawHourLabels(frames, timeZone, dim, size, trackTop + trackHeight)
    }
}

private fun indexAt(x: Float, width: Float, count: Int): Int =
    ((x / width) * (count - 1)).roundToInt().coerceIn(0, count - 1)

/**
 * The curve, filled column by column so each one carries the colour its reading would have on the
 * map — the miniature of the field, not merely a line beside it.
 */
private fun DrawScope.drawSeries(
    frames: List<ChmiMapFrameRef>,
    series: List<Pair<Long, Double>>,
    colorAt: (Double) -> Int,
    accent: Color,
    chartHeight: Float,
) {
    if (series.size < 2) return
    val first = frames.first().time.time
    val last = frames.last().time.time
    if (last <= first) return

    val values = FloatArray(size.width.toInt().coerceAtLeast(1))
    var lowest = Float.MAX_VALUE
    var highest = -Float.MAX_VALUE
    for (x in values.indices) {
        val at = first + (last - first) * x.toDouble() / (values.size - 1).coerceAtLeast(1)
        val value = interpolate(series, at.toLong())
        values[x] = value?.toFloat() ?: Float.NaN
        if (value != null) {
            lowest = minOf(lowest, value.toFloat())
            highest = maxOf(highest, value.toFloat())
        }
    }
    if (highest <= -Float.MAX_VALUE) return
    // A flat series still has to be drawn somewhere sensible rather than dividing by nothing.
    if (highest - lowest < 0.5f) {
        highest = lowest + 0.5f
    }
    // Rain starts at zero: a trace should read as a trace, not fill the strip because it is the
    // only rain there is. A temperature curve is fitted to its own range instead.
    val floor = if (lowest >= 0f && lowest < highest * 0.35f) 0f else lowest
    val span = (highest - floor).coerceAtLeast(0.5f)

    val top = TOP_PADDING.dp.toPx()
    val usable = chartHeight - top
    val outline = Path()
    var started = false
    for (x in values.indices) {
        val value = values[x]
        if (value.isNaN()) continue
        val y = top + usable * (1f - ((value - floor) / span)).coerceIn(0f, 1f)
        drawLine(
            color = Color(colorAt(value.toDouble())),
            start = Offset(x.toFloat(), y),
            end = Offset(x.toFloat(), chartHeight),
            strokeWidth = 1.2f
        )
        if (started) outline.lineTo(x.toFloat(), y) else outline.moveTo(x.toFloat(), y).also { started = true }
    }
    drawPath(outline, accent.copy(alpha = 0.9f), style = Stroke(width = 1.5.dp.toPx()))
}

/** Linear between the two readings either side of [at]. */
private fun interpolate(series: List<Pair<Long, Double>>, at: Long): Double? {
    if (series.isEmpty()) return null
    if (at <= series.first().first) return series.first().second
    if (at >= series.last().first) return series.last().second
    for (i in 0..<series.lastIndex) {
        val (t0, v0) = series[i]
        val (t1, v1) = series[i + 1]
        if (at in t0..t1) {
            if (t1 == t0) return v0
            return v0 + (v1 - v0) * (at - t0).toDouble() / (t1 - t0)
        }
    }
    return null
}

/**
 * Day boundaries and hour marks, ruled through the chart and the track alike — the tie between a
 * bump in the curve and a place on the slider.
 */
private fun DrawScope.drawRules(
    frames: List<ChmiMapFrameRef>,
    timeZone: TimeZone,
    accent: Color,
    dim: Color,
    chartHeight: Float,
    bottom: Float,
) {
    val calendar = Calendar.getInstance(timeZone)
    val hourStep = hourStepFor(frames)
    var previousDay = -1
    frames.forEachIndexed { i, frame ->
        calendar.time = frame.time
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val day = calendar.get(Calendar.DAY_OF_YEAR)
        val x = size.width * i / (frames.size - 1).coerceAtLeast(1)
        if (previousDay != -1 && day != previousDay) {
            // Midnight: the one rule worth seeing at a glance.
            drawLine(accent.copy(alpha = 0.55f), Offset(x, 0f), Offset(x, bottom), strokeWidth = 1.4.dp.toPx())
        } else if (minute == 0 && hour % hourStep == 0) {
            drawLine(dim.copy(alpha = 0.22f), Offset(x, 0f), Offset(x, chartHeight), strokeWidth = 1f)
            drawLine(dim.copy(alpha = 0.22f), Offset(x, chartHeight), Offset(x, bottom), strokeWidth = 1f)
        }
        previousDay = day
    }
}

/** The playhead track: how far through, where "now" is, and where you are. */
private fun DrawScope.drawTrack(
    count: Int,
    index: Int,
    nowIndex: Int,
    accent: Color,
    dim: Color,
    top: Float,
    height: Float,
) {
    val middle = top + height / 2f
    val thickness = TRACK_THICKNESS.dp.toPx()
    drawLine(
        color = dim.copy(alpha = 0.35f),
        start = Offset(0f, middle),
        end = Offset(size.width, middle),
        strokeWidth = thickness
    )
    val playhead = size.width * index / (count - 1).coerceAtLeast(1)
    drawLine(
        color = accent,
        start = Offset(0f, middle),
        end = Offset(playhead, middle),
        strokeWidth = thickness
    )
    // Where the observed part ends and the forecast begins.
    val now = size.width * nowIndex / (count - 1).coerceAtLeast(1)
    drawLine(
        color = accent.copy(alpha = 0.8f),
        start = Offset(now, top),
        end = Offset(now, top + height),
        strokeWidth = 1.5.dp.toPx()
    )
    drawCircle(color = accent, radius = THUMB_RADIUS.dp.toPx(), center = Offset(playhead, middle))
}

/**
 * Clock labels under the track — on the hour marks themselves, so every label names a rule.
 *
 * They used to be placed every `frames.size / LABEL_COUNT` frames, which on the five-minute radar
 * is every forty minutes. Only the hour was printed, so the axis read `06 06 07 08 08 09 10 10 11`
 * — an hour named twice, another missing entirely, and not one of them under the rule it belonged
 * to. Walking the same hours the rules do fixes all three at once; [LABEL_COUNT] then only decides
 * how many of those hours are roomy enough to name.
 */
private fun DrawScope.drawHourLabels(
    frames: List<ChmiMapFrameRef>,
    timeZone: TimeZone,
    dim: Color,
    size: Size,
    bottom: Float,
) {
    val paint = Paint().apply {
        isAntiAlias = true
        color = dim.toArgb()
        textSize = LABEL_SIZE.dp.toPx()
        textAlign = Paint.Align.CENTER
    }
    val calendar = Calendar.getInstance(timeZone)
    val hourStep = hourStepFor(frames)
    val margin = LABEL_SIZE.dp.toPx()
    val spacing = size.width / LABEL_COUNT
    var previous = -Float.MAX_VALUE
    frames.forEachIndexed { i, frame ->
        calendar.time = frame.time
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        if (calendar.get(Calendar.MINUTE) != 0 || hour % hourStep != 0) return@forEachIndexed
        val x = size.width * i / (frames.size - 1).coerceAtLeast(1)
        if (x < margin || x > size.width - margin || x - previous < spacing) return@forEachIndexed
        previous = x
        drawContext.canvas.nativeCanvas.drawText("%02d".format(hour), x, bottom, paint)
    }
}

/** Enough rules to read, never so many that they become a hatch. */
private fun hourStepFor(frames: List<ChmiMapFrameRef>): Int {
    val hours = (frames.last().time.time - frames.first().time.time) / 3_600_000.0
    return when {
        hours <= 8 -> 1
        hours <= 30 -> 3
        else -> 6
    }
}

private const val TOTAL_HEIGHT = 112
private const val CHART_FRACTION = 0.62f
private const val GAP = 2
private const val TOP_PADDING = 6
private const val TRACK_THICKNESS = 6
private const val THUMB_RADIUS = 8
private const val LABEL_SIZE = 11
private const val LABEL_COUNT = 10
