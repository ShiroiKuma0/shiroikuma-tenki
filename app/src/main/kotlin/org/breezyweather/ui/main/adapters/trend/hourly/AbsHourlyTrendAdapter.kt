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

package org.breezyweather.ui.main.adapters.trend.hourly

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.model.Hourly
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.getHour
import org.breezyweather.common.extensions.getHourIn24Format
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.tenki.TenkiViewTheme
import org.breezyweather.ui.common.widgets.trend.TrendRecyclerView
import org.breezyweather.ui.common.widgets.trend.TrendRecyclerViewAdapter
import org.breezyweather.ui.common.widgets.trend.item.HourlyTrendItemView
import org.breezyweather.ui.main.adapters.main.holder.isBlankColumn
import kotlin.time.Duration.Companion.days

abstract class AbsHourlyTrendAdapter(
    val activity: BreezyActivity,
    location: Location,
) : TrendRecyclerViewAdapter<AbsHourlyTrendAdapter.ViewHolder>(location) {

    open class ViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val hourlyItem: HourlyTrendItemView = itemView.findViewById(R.id.item_trend_hourly)

        /**
         * @param hourlyList shiroikuma fork: the hours this adapter is plotting, which is the whole
         *   stored series — a month of history included — so the card can be scrolled back through
         *   what the weather actually did. Every tab plots this same list, so a tab switch keeps
         *   its place; an adapter passing a different one would index its labels against the wrong
         *   hours.
         */
        fun onBindView(
            activity: BreezyActivity,
            location: Location,
            talkBackBuilder: StringBuilder,
            position: Int,
            hourlyList: List<Hourly> = location.weather!!.hourlyForecast,
        ) {
            val context = itemView.context
            val hourly = hourlyList[position]
            // shiroikuma fork: every hourly tab plots the same series and shares this column width,
            // so the scroll position keeps its meaning when the tab is switched. Set here rather
            // than per adapter, since a tab that sized its columns differently would appear to jump
            // to another hour the moment it was selected.
            hourlyItem.visibleColumns = TenkiViewTheme.state(context).let {
                (it.hourlyHoursBack + it.hourlyHoursAhead).coerceAtLeast(2)
            }
            talkBackBuilder
                .append(context.getString(org.breezyweather.unit.R.string.locale_separator))
                .append(hourly.date.getHour(location, activity))
            hourlyItem.setHourText(hourly.date.getHour(location, activity))
            val useAccentColorForDate = position == 0 || hourly.date.getHourIn24Format(location) == "0"
            hourlyItem.setTextColor(
                context.getThemeColor(if (useAccentColorForDate) R.attr.colorTitleText else R.attr.colorBodyText)
            )
        }

        protected fun onItemClicked(
            activity: BreezyActivity,
            location: Location,
            adapterPosition: Int,
            detailScreen: DetailScreen,
        ) {
            if (activity.isActivityResumed) {
                // shiroikuma fork: a blank column is this source's place on the shared axis and has
                // no hour of its own to open
                val hourly = location.weather!!.hourlyForecast.getOrNull(adapterPosition) ?: return
                if (hourly.isBlankColumn()) return
                val hourlyDate = hourly.date
                // Might not work with sources like AccuWeather not starting the day at 00:00
                val dailyIndex = location.weather!!.dailyForecast.indexOfFirst {
                    it.date.time > hourlyDate.time - 1.days.inWholeMilliseconds
                }.let { if (it == -1) null else it }
                IntentHelper.startDailyWeatherActivity(activity, location.formattedId, dailyIndex, detailScreen)
            }
        }
    }

    abstract fun isValid(location: Location): Boolean
    abstract fun getDisplayName(context: Context): String
    abstract fun bindBackgroundForHost(host: TrendRecyclerView)

    /**
     * shiroikuma fork: fit the vertical scale to the columns [first]..[last], the ones on screen.
     *
     * Answers whether the scale actually moved. Charts with a scale of their own — a percentage, an
     * index, a rain total — have nothing to fit and say no.
     */
    open fun fitToVisible(first: Int, last: Int): Boolean = false

    /** The fitted scale, highest to lowest, for handing straight to the columns already drawn. */
    open val polylineRange: Pair<Float, Float>? get() = null

    companion object {
        /** shiroikuma fork: label and ice one column in this many — they are only 23dp wide. */
        const val HOUR_LABEL_EVERY = 3
    }
}
