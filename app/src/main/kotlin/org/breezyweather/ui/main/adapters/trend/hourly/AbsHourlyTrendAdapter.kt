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
import org.breezyweather.ui.common.widgets.trend.TrendRecyclerView
import org.breezyweather.ui.common.widgets.trend.TrendRecyclerViewAdapter
import org.breezyweather.ui.common.widgets.trend.item.HourlyTrendItemView
import kotlin.time.Duration.Companion.days

abstract class AbsHourlyTrendAdapter(
    val activity: BreezyActivity,
    location: Location,
) : TrendRecyclerViewAdapter<AbsHourlyTrendAdapter.ViewHolder>(location) {

    open class ViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val hourlyItem: HourlyTrendItemView = itemView.findViewById(R.id.item_trend_hourly)

        /**
         * @param hourlyList shiroikuma fork: which window of hours this adapter is plotting.
         *   Defaults to upstream's "from the current hour", so the tabs that have not been
         *   rebuilt keep their existing behaviour; the temperature tab passes a window that
         *   reaches back a few hours, and the labels have to follow the same list or they
         *   would be indexed against the wrong hours.
         */
        fun onBindView(
            activity: BreezyActivity,
            location: Location,
            talkBackBuilder: StringBuilder,
            position: Int,
            hourlyList: List<Hourly> = location.weather!!.nextHourlyForecast,
        ) {
            val context = itemView.context
            val hourly = hourlyList[position]
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
                val hourlyDate = location.weather!!.nextHourlyForecast[adapterPosition].date
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

    companion object {
        /** shiroikuma fork: label and ice one column in this many — they are only 23dp wide. */
        const val HOUR_LABEL_EVERY = 3
    }
}
