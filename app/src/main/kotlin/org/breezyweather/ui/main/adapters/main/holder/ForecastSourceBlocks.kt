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

package org.breezyweather.ui.main.adapters.main.holder

import android.content.Context
import breezyweather.domain.location.model.Location
import breezyweather.domain.source.SourceFeature
import breezyweather.domain.weather.model.Daily
import breezyweather.domain.weather.model.Hourly
import org.breezyweather.common.extensions.getIsoFormattedDate
import org.breezyweather.common.extensions.toDateNoHour
import org.breezyweather.common.source.getName
import org.breezyweather.sources.SourceManager
import java.util.Date

/**
 * shiroikuma fork: one selected forecast source, ready to draw.
 *
 * [location] is the trick that keeps this cheap: a copy of the real location whose weather holds
 * THIS source's daily and hourly arrays. Every trend adapter already takes a Location and reads
 * `location.weather`, so they all work on an alternate source without knowing one exists.
 */
internal data class ForecastSourceBlock(
    val sourceId: String,
    val name: String,
    val location: Location,
)

/** shiroikuma fork: which of the two series a card draws, and therefore the one it puts on one axis. */
internal enum class ForecastSeries { HOURLY, DAILY }

/**
 * shiroikuma fork: the selected forecast sources for one set of charts, in the arranged order.
 *
 * [sourceIds] is the hourly or the daily list — the two are chosen independently, so the hourly card
 * and the daily card can stack different sources, or the same ones in a different order.
 *
 * The identity source uses the weather as-is. An alternate with no data yet — never refreshed, or
 * its last refresh failed — is dropped rather than drawn as an empty chart. [series] is the one the
 * card is about to plot, and the only one put on a shared axis.
 */
internal fun Location.forecastSourceBlocks(
    sourceManager: SourceManager?,
    context: Context,
    sourceIds: List<String>,
    series: ForecastSeries,
): List<ForecastSourceBlock> {
    val weather = weather ?: return emptyList()

    val blocks = sourceIds.mapNotNull { sourceId ->
        val blockLocation = if (sourceId == forecastSource) {
            this
        } else {
            weather.alternateForecasts[sourceId]
                ?.takeIf { !it.isEmpty }
                ?.let {
                    copy(
                        weather = weather.copy(
                            dailyForecast = it.dailyForecast,
                            hourlyForecast = it.hourlyForecast
                        )
                    )
                }
                ?: return@mapNotNull null
        }

        ForecastSourceBlock(
            sourceId = sourceId,
            // Falls back to the bare id, which is all we can say about a source no longer installed
            name = sourceManager?.getFeatureSource(sourceId)
                ?.getName(context, SourceFeature.FORECAST, this)
                ?: sourceId,
            location = blockLocation
        )
    }

    return when (series) {
        ForecastSeries.HOURLY -> blocks.onOneHourlyAxis()
        ForecastSeries.DAILY -> blocks.onOneDailyAxis(this)
    }
}

/**
 * shiroikuma fork: the stacked hourly charts put on ONE column axis, by handing each source the
 * hours it does not reach as blank columns.
 *
 * A source added today has no history at all, so its first column is the current hour while the
 * source above it opens three hours earlier — and the two charts then compare a different hour at
 * the same x, which is the one thing a stack of them exists to avoid. The blanks carry their hour
 * label and their banding but nothing else, so the missing history reads as missing rather than as
 * a chart that starts somewhere else.
 *
 * Only the range OUTSIDE each source's own hours is filled. A source that reports six-hourly out in
 * the week keeps its own spacing rather than being broken into a dotted line by a neighbour's
 * hourly grid — MET Norway is exactly that case past its second day.
 */
private fun List<ForecastSourceBlock>.onOneHourlyAxis(): List<ForecastSourceBlock> {
    if (size < 2) return this

    val axis = flatMap { block -> block.location.weather?.hourlyForecast.orEmpty().map { it.date.time } }
        .distinct()
        .sorted()

    return map { block ->
        val weather = block.location.weather ?: return@map block
        val series = weather.hourlyForecast
        if (series.isEmpty()) return@map block

        val before = axis.filter { it < series.first().date.time }
        val after = axis.filter { it > series.last().date.time }
        if (before.isEmpty() && after.isEmpty()) return@map block

        block.copy(
            location = block.location.copy(
                weather = weather.copy(
                    hourlyForecast = before.map { Hourly(date = Date(it)) } +
                        series +
                        after.map { Hourly(date = Date(it)) }
                )
            )
        )
    }
}

/**
 * shiroikuma fork: the same shared axis for the stacked daily charts, by local day.
 *
 * By the day rather than by the timestamp, because a source is free to start its day at something
 * other than 00:00 — matching those on the raw date would give one calendar day two columns.
 */
private fun List<ForecastSourceBlock>.onOneDailyAxis(location: Location): List<ForecastSourceBlock> {
    if (size < 2) return this

    val axis = flatMap { block ->
        block.location.weather?.dailyForecast.orEmpty().map { it.date.getIsoFormattedDate(location) }
    }
        .distinct()
        .sorted() // ISO dates sort as dates

    return map { block ->
        val weather = block.location.weather ?: return@map block
        val series = weather.dailyForecast
        if (series.isEmpty()) return@map block

        val before = axis.filter { it < series.first().date.getIsoFormattedDate(location) }
        val after = axis.filter { it > series.last().date.getIsoFormattedDate(location) }
        if (before.isEmpty() && after.isEmpty()) return@map block

        fun blanks(days: List<String>) = days.mapNotNull { day ->
            day.toDateNoHour(location.timeZone)?.let { Daily(date = it) }
        }
        block.copy(
            location = block.location.copy(
                weather = weather.copy(dailyForecast = blanks(before) + series + blanks(after))
            )
        )
    }
}

/**
 * shiroikuma fork: a column that exists only to hold a source's place on the shared axis.
 *
 * Told by having nothing in it but its date, which no stored day ever is: the refresh fills every
 * one of them with the computed sun, twilight, moon and moon phase. An hour is allowed to be empty
 * for real, and one that is may as well be treated as a blank — there is nothing to open for it.
 */
internal fun Daily.isBlankColumn() = this == Daily(date = date)

internal fun Hourly.isBlankColumn() = this == Hourly(date = date, isDaylight = isDaylight)
