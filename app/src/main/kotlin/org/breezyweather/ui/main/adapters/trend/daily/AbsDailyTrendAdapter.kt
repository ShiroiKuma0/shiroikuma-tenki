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

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import breezyweather.domain.location.model.Location
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.getFormattedFullDayAndMonth
import org.breezyweather.common.extensions.getFormattedShortDayAndMonth
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.domain.weather.model.getWeek
import org.breezyweather.domain.weather.model.isToday
import org.breezyweather.tenki.TenkiViewTheme
import org.breezyweather.ui.common.widgets.trend.TrendRecyclerView
import org.breezyweather.ui.common.widgets.trend.TrendRecyclerViewAdapter
import org.breezyweather.ui.common.widgets.trend.item.DailyTrendItemView
import org.breezyweather.ui.main.adapters.main.holder.isBlankColumn
import java.util.Date

abstract class AbsDailyTrendAdapter(
    val activity: BreezyActivity,
    location: Location,
) : TrendRecyclerViewAdapter<AbsDailyTrendAdapter.ViewHolder>(location) {

    open class ViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dailyItem: DailyTrendItemView = itemView.findViewById(R.id.item_trend_daily)

        @SuppressLint("SetTextI18n, InflateParams", "DefaultLocale")
        fun onBindView(
            activity: BreezyActivity,
            location: Location,
            talkBackBuilder: StringBuilder,
            position: Int,
        ) {
            val context = itemView.context
            val weather = location.weather
            val daily = weather!!.dailyForecast[position]
            val todayIndex = weather.todayIndex
            // shiroikuma fork: every daily tab shares this column width, so the scroll position
            // keeps its meaning when the tab is switched — and with several sources stacked, so
            // does the column a day sits in. Set here rather than per adapter, for the same reason.
            dailyItem.visibleColumns = TenkiViewTheme.state(context).dailyDaysVisible.coerceAtLeast(1)
            // shiroikuma fork: the dashed "now" rule, on whichever column the moment falls in. Set
            // here rather than per adapter, so every daily tab marks it — and every stacked source,
            // each of which reaches this with its own copy of the day list, and its own hours to
            // anchor the rule on.
            dailyItem.nowAt = nowAcrossColumn(weather, position)
            talkBackBuilder.append(context.getString(org.breezyweather.unit.R.string.locale_separator))
            if (todayIndex != null) {
                when (position) {
                    todayIndex -> talkBackBuilder.append(context.getString(R.string.daily_today))
                    todayIndex - 1 -> talkBackBuilder.append(context.getString(R.string.daily_yesterday))
                    todayIndex + 1 -> talkBackBuilder.append(context.getString(R.string.daily_tomorrow))
                    else -> talkBackBuilder.append(daily.getWeek(location, context, full = true))
                }
            } else {
                talkBackBuilder.append(daily.getWeek(location, context, full = true))
            }
            if (position == todayIndex) {
                dailyItem.setWeekText(context.getString(R.string.daily_today_short))
            } else {
                dailyItem.setWeekText(daily.getWeek(location, context))
            }
            talkBackBuilder.append(context.getString(org.breezyweather.unit.R.string.locale_separator))
                .append(daily.date.getFormattedFullDayAndMonth(location, context))
            dailyItem.setDateText(daily.date.getFormattedShortDayAndMonth(location, context))
            val useAccentColorForDate = daily.isToday(location) || daily.date > Date()
            dailyItem.setTextColor(
                activity.getThemeColor(if (useAccentColorForDate) R.attr.colorTitleText else R.attr.colorBodyText),
                activity.getThemeColor(if (useAccentColorForDate) R.attr.colorBodyText else R.attr.colorCaptionText)
            )
        }

        protected fun onItemClicked(
            activity: BreezyActivity,
            location: Location,
            adapterPosition: Int,
            detailScreen: DetailScreen,
        ) {
            if (activity.isActivityResumed) {
                // shiroikuma fork: a blank column is this source's place on the shared axis — there
                // is no day behind it to open, and the days after it are still indexed by the
                // source's own list, so the blanks in front come off the position again
                val days = location.weather?.dailyForecast.orEmpty()
                val daily = days.getOrNull(adapterPosition) ?: return
                if (daily.isBlankColumn()) return
                IntentHelper.startDailyWeatherActivity(
                    activity,
                    location.formattedId,
                    adapterPosition - days.take(adapterPosition).count { it.isBlankColumn() },
                    detailScreen
                )
            }
        }
    }

    val key: String = javaClass.name
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
}
