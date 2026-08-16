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

package org.breezyweather.ui.main.adapters.trend.daily

import breezyweather.domain.weather.model.Daily
import breezyweather.domain.weather.model.Hourly
import breezyweather.domain.weather.model.Weather
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * shiroikuma fork: where the current moment falls across the column of the day at [position] — 0 at
 * its leading edge and 1 at its trailing one — or null for every column but the one we are in.
 *
 * The daily trace is not drawn against a clock. A column carries two knots, the day's high at ¼ and
 * the night's low at ¾, and where they sit on the column says nothing on its own about when they
 * happen. So a "now" rule has to be put where the trace is, which means anchoring it to the moments
 * those two knots stand for.
 *
 * Reading the column as a plain 06:00-to-06:00 span, as +057 did, puts noon on ¼ and midnight on ¾ —
 * the CENTRES of the two halves a daily forecast is made of. But a knot is its half's EXTREME, not
 * its middle, and a summer maximum lands near the end of the 06:00–18:00 window rather than in the
 * middle of it: on 2026-08-16 ČHMÚ had Prague topping out at 30° at 17:00, five hours after that
 * rule's noon. At 13:50 the rule therefore stood past the peak, saying the day's heat was already
 * spent, while the hourly card on the same screen still had it four hours ahead.
 *
 * So the anchors are read off the forecast instead: ¼ goes on the hour the day half actually tops
 * out, ¾ on the hour the night half bottoms out, and the moment is interpolated between them — and
 * between the neighbouring columns' knots out on the limbs, since the rise into a morning begins in
 * the column before it. Each stacked source anchors on its OWN hours, so every pane's rule crosses
 * that pane's trace at the temperature that source has happening now; two sources an hour apart on
 * when the peak comes get their rules an hour apart too, which is that disagreement, drawn.
 */
internal fun nowAcrossColumn(weather: Weather, position: Int): Float? {
    val days = weather.dailyForecast
    val start = days.getOrNull(position)?.date?.time ?: return null
    val end = startOf(days, position + 1)
    val now = System.currentTimeMillis()
    // No anchor of this column's can reach further out than its neighbours' knots do. Answering the
    // week's other columns from that alone keeps the forecast unread for all but the two in question
    if (now < start - DAY_STARTS_AT.inWholeMilliseconds || now > end + NIGHT_STARTS_AT.inWholeMilliseconds) {
        return null
    }

    val hourly = weather.hourlyForecast
    // The column's own two knots, plus the knots either side of them: the trace runs on across the
    // boundary, and between last night's low and this morning's rise the moment belongs to the
    // column BEFORE this one, which only its neighbour's anchor can say
    val anchors = longArrayOf(
        lowAt(days, hourly, position - 1),
        highAt(days, hourly, position),
        lowAt(days, hourly, position),
        highAt(days, hourly, position + 1)
    )
    // A half's extreme can sit on the very boundary it shares with the next half — a night that
    // bottoms out at 18:00 sharp — and two anchors on the same instant have no span to interpolate
    // across. Held one millisecond apart, that column draws its rule on the knot instead of not at all
    for (i in 1..anchors.lastIndex) {
        if (anchors[i] <= anchors[i - 1]) anchors[i] = anchors[i - 1] + 1
    }
    for (i in 0 until anchors.lastIndex) {
        val from = anchors[i]
        val to = anchors[i + 1]
        if (now in from..to) {
            val fromAt = KNOT_AT[i]
            val at = fromAt + (KNOT_AT[i + 1] - fromAt) * (now - from).toFloat() / (to - from)
            return at.takeIf { it in 0f..1f }
        }
    }
    return null
}

/**
 * The instant a day's column opens on, its own 00:00 — stepped whole days off the nearest day we
 * have when [index] falls off either end of [days], which is never empty here.
 *
 * Taking the next day's own midnight rather than adding a fixed twenty-four hours is what keeps a
 * day the clocks change on 23 or 25 hours wide.
 */
private fun startOf(days: List<Daily>, index: Int): Long {
    days.getOrNull(index)?.let { return it.date.time }
    val nearest = if (index < 0) 0 else days.lastIndex
    return days[nearest].date.time + (index - nearest) * 1.days.inWholeMilliseconds
}

/**
 * When the day half of the day at [index] tops out — the moment its column's ¼ knot stands for.
 *
 * Straight off the source's own hours where it has them. Failing that, off the sun: the ground goes
 * on gaining heat well past solar noon, and three-quarters of the way from sunrise to sunset lands
 * within the hour of the afternoon peak through most of the year. Failing even that, mid-afternoon.
 */
private fun highAt(days: List<Daily>, hourly: List<Hourly>, index: Int): Long {
    val opens = startOf(days, index)
    val from = opens + DAY_STARTS_AT.inWholeMilliseconds
    val to = opens + NIGHT_STARTS_AT.inWholeMilliseconds
    extremeIn(hourly, from, to, highest = true)?.let { return it }
    val sun = days.getOrNull(index)?.sun
    val rise = sun?.riseDate?.time
    val set = sun?.setDate?.time
    if (rise != null && set != null && set > rise) {
        return (rise + ((set - rise) * PEAK_THROUGH_DAYLIGHT).toLong()).coerceIn(from, to)
    }
    return opens + PEAKS_AT.inWholeMilliseconds
}

/**
 * When the night half of the day at [index] bottoms out — the moment its column's ¾ knot stands for.
 *
 * The night's low is not midnight but the end of the night: the ground keeps losing heat until the
 * sun is back on it, so the sunrise of the morning AFTER is the fallback, and the small hours the
 * fallback behind that.
 */
private fun lowAt(days: List<Daily>, hourly: List<Hourly>, index: Int): Long {
    val from = startOf(days, index) + NIGHT_STARTS_AT.inWholeMilliseconds
    val opensNext = startOf(days, index + 1)
    val to = opensNext + DAY_STARTS_AT.inWholeMilliseconds
    extremeIn(hourly, from, to, highest = false)?.let { return it }
    days.getOrNull(index + 1)?.sun?.riseDate?.time?.let { return it.coerceIn(from, to) }
    return opensNext + TROUGHS_AT.inWholeMilliseconds
}

/**
 * The middle of the [highest] — or lowest — stretch of hourly temperature in [from] until [to], or
 * null when the source reports no hour there at all.
 *
 * The middle of the stretch, because an afternoon commonly reads the same degree two or three hours
 * running: taking the first of them would put the peak an hour or more early, while the trace's own
 * top is a single point.
 *
 * The far end is left out, as a half's own definition leaves it out: a daily forecast's day half is
 * 06:00–17:59 and its night half 18:00–05:59, so the hour a window closes on belongs to the NEXT
 * knot, and the knot whose value we are placing was never taken over it.
 */
private fun extremeIn(hourly: List<Hourly>, from: Long, to: Long, highest: Boolean): Long? {
    var found = false
    var best = 0L
    var first = 0L
    var last = 0L
    for (hour in hourly) {
        val at = hour.date.time
        if (at < from || at >= to) continue
        val value = hour.temperature?.temperature?.value ?: continue
        when {
            !found || (if (highest) value > best else value < best) -> {
                found = true
                best = value
                first = at
                last = at
            }
            value == best -> last = at
        }
    }
    return if (found) first + (last - first) / 2 else null
}

/** Where the four anchors sit across the column: the knots either side of it, and its own two. */
private val KNOT_AT = floatArrayOf(-0.25f, 0.25f, 0.75f, 1.25f)

/** Where a daily forecast's day half opens, and with it the column. */
private val DAY_STARTS_AT = 6.hours

/** Where its night half opens. */
private val NIGHT_STARTS_AT = 18.hours

/** Mid-afternoon: where the day's high goes when neither the hours nor the sun can place it. */
private val PEAKS_AT = 16.hours

/** The small hours: where the night's low goes on the same terms, counted into the morning after. */
private val TROUGHS_AT = 5.hours

/** How far from sunrise to sunset the afternoon peak falls, when the hours themselves are missing. */
private const val PEAK_THROUGH_DAYLIGHT = 0.75f
