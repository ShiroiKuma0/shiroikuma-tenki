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
import org.breezyweather.common.source.getName
import org.breezyweather.sources.SourceManager

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

/**
 * shiroikuma fork: the selected forecast sources for one set of charts, in the arranged order.
 *
 * [sourceIds] is the hourly or the daily list — the two are chosen independently, so the hourly card
 * and the daily card can stack different sources, or the same ones in a different order.
 *
 * The identity source uses the weather as-is. An alternate with no data yet — never refreshed, or
 * its last refresh failed — is dropped rather than drawn as an empty chart.
 */
internal fun Location.forecastSourceBlocks(
    sourceManager: SourceManager?,
    context: Context,
    sourceIds: List<String>,
): List<ForecastSourceBlock> {
    val weather = weather ?: return emptyList()

    return sourceIds.mapNotNull { sourceId ->
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
}
