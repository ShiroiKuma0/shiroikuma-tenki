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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Size
import breezyweather.domain.location.model.Location
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.common.extensions.formatPercent
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.ui.common.widgets.trend.TrendRecyclerView
import org.breezyweather.ui.common.widgets.trend.chart.PolylineAndHistogramView
import org.breezyweather.ui.theme.ThemeManager
import org.breezyweather.ui.theme.resource.ResourceHelper
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.ui.theme.weatherView.WeatherViewController
import org.breezyweather.unit.formatting.UnitWidth
import org.breezyweather.unit.temperature.TemperatureUnit
import kotlin.math.max

/**
 * Hourly feels like adapter.
 */
class HourlyFeelsLikeAdapter(
    activity: BreezyActivity,
    location: Location,
    provider: ResourceProvider,
    private val temperatureUnit: TemperatureUnit,
    private val showPrecipitationProbability: Boolean = true,
) : AbsHourlyTrendAdapter(activity, location) {
    private val mResourceProvider: ResourceProvider = provider
    private val mTemperatures: Array<Float?>
    private var mHighestTemperature: Float? = null
    private var mLowestTemperature: Float? = null

    inner class ViewHolder(itemView: View) : AbsHourlyTrendAdapter.ViewHolder(itemView) {
        private val mPolylineAndHistogramView = PolylineAndHistogramView(itemView.context)

        init {
            hourlyItem.chartItemView = mPolylineAndHistogramView
        }

        fun onBindView(activity: BreezyActivity, location: Location, position: Int) {
            val talkBackBuilder = StringBuilder(activity.getString(R.string.tag_feels_like))
            super.onBindView(activity, location, talkBackBuilder, position, location.weather!!.hourlyForecast)
            val weather = location.weather!!
            val hourly = weather.hourlyForecast[position]
            hourly.temperature?.feelsLikeTemperature?.let {
                talkBackBuilder.append(activity.getString(org.breezyweather.unit.R.string.locale_separator))
                    .append(it.formatMeasure(activity, temperatureUnit, unitWidth = UnitWidth.LONG))
            }
            hourlyItem.setIconDrawable(
                hourly.weatherCode?.let {
                    ResourceHelper.getWeatherIcon(mResourceProvider, it, hourly.isDaylight)
                },
                missingIconVisibility = View.INVISIBLE
            )
            val p = hourly.precipitationProbability?.total
            if (showPrecipitationProbability && hourly.precipitationProbability?.total != null) {
                talkBackBuilder.append(activity.getString(org.breezyweather.unit.R.string.locale_separator))
                    .append(activity.getString(R.string.precipitation_probability))
                    .append(activity.getString(R.string.colon_separator))
                    .append(hourly.precipitationProbability!!.total!!.formatPercent(activity, UnitWidth.NARROW))
            }
            mPolylineAndHistogramView.setData(
                buildTemperatureArrayForItem(mTemperatures, position),
                null,
                (hourly.temperature?.feelsLikeTemperature ?: hourly.temperature?.temperature)
                    ?.formatMeasure(
                        activity,
                        temperatureUnit,
                        valueWidth = UnitWidth.NARROW,
                        unitWidth = UnitWidth.NARROW
                    ),
                null,
                mHighestTemperature,
                mLowestTemperature,
                p?.takeIf { it.value > 0 && showPrecipitationProbability }?.inPercent?.toFloat(),
                p?.takeIf { it.value > 0 && showPrecipitationProbability }?.formatPercent(activity, UnitWidth.NARROW),
                100f,
                0f
            )
            val themeColors = ThemeManager
                .getInstance(itemView.context)
                .weatherThemeDelegate
                .getThemeColors(
                    itemView.context,
                    WeatherViewController.getWeatherKind(location),
                    WeatherViewController.isDaylight(location)
                )
            val lightTheme = ThemeManager.isLightTheme(itemView.context, location)
            mPolylineAndHistogramView.setLineColors(
                themeColors[if (lightTheme) 1 else 2],
                themeColors[2],
                activity.getThemeColor(com.google.android.material.R.attr.colorOutline)
            )
            mPolylineAndHistogramView.setShadowColors(
                themeColors[if (lightTheme) 1 else 2],
                themeColors[2],
                lightTheme
            )
            mPolylineAndHistogramView.setTextColors(
                activity.getThemeColor(R.attr.colorTitleText),
                activity.getThemeColor(R.attr.colorBodyText),
                activity.getThemeColor(R.attr.colorPrecipitationProbability)
            )
            mPolylineAndHistogramView.setHistogramAlpha(if (lightTheme) 0.2f else 0.5f)
            hourlyItem.contentDescription = talkBackBuilder.toString()
            hourlyItem.setOnClickListener {
                onItemClicked(activity, location, bindingAdapterPosition, DetailScreen.TAG_FEELS_LIKE)
            }
        }

        @Size(3)
        private fun buildTemperatureArrayForItem(temps: Array<Float?>, adapterPosition: Int): Array<Float?> {
            val a = arrayOfNulls<Float>(3)
            a[1] = temps[2 * adapterPosition]
            if (2 * adapterPosition - 1 < 0) {
                a[0] = null
            } else {
                a[0] = temps[2 * adapterPosition - 1]
            }
            if (2 * adapterPosition + 1 >= temps.size) {
                a[2] = null
            } else {
                a[2] = temps[2 * adapterPosition + 1]
            }
            return a
        }
    }

    init {
        val weather = location.weather!!
        mTemperatures = arrayOfNulls(max(0, weather.hourlyForecast.size * 2 - 1))
        run {
            var i = 0
            while (i < mTemperatures.size) {
                mTemperatures[i] =
                    weather.hourlyForecast.getOrNull(i / 2)?.temperature?.feelsLikeTemperature?.value?.toFloat()
                        ?: weather.hourlyForecast.getOrNull(i / 2)?.temperature?.temperature?.value?.toFloat()
                i += 2
            }
        }
        run {
            var i = 1
            while (i < mTemperatures.size) {
                if (mTemperatures[i - 1] != null && mTemperatures[i + 1] != null) {
                    mTemperatures[i] = (mTemperatures[i - 1]!! + mTemperatures[i + 1]!!) * 0.5f
                } else {
                    mTemperatures[i] = null
                }
                i += 2
            }
        }
        // shiroikuma fork: only the opening guess — the scale is refitted to the hours actually on
        // screen as soon as the chart has been laid out, and again on every scroll and pinch
        fitRange(0, weather.hourlyForecast.size)
    }

    /** Where the scale was last fitted, so an unchanged view is not re-fitted on every scroll. */
    private var mFittedFirst = -1
    private var mFittedLast = -1

    override val polylineRange: Pair<Float, Float>?
        get() = mHighestTemperature?.let { high -> mLowestTemperature?.let { low -> high to low } }

    override fun fitToVisible(first: Int, last: Int): Boolean {
        if (first == mFittedFirst && last == mFittedLast) return false
        mFittedFirst = first
        mFittedLast = last
        val high = mHighestTemperature
        val low = mLowestTemperature
        // One column of slack either side: a column's trace is drawn from the midpoints it shares
        // with its neighbours, so what those two read decides where its own ends are.
        fitRange(first - 1, last + 2)
        return mHighestTemperature != high || mLowestTemperature != low
    }

    /** Fit the scale to the hours in `[from, until)`, keeping the last fit where there are none. */
    private fun fitRange(from: Int, until: Int) {
        val hourly = location.weather!!.hourlyForecast
        var high: Float? = null
        var low: Float? = null
        val start = from.coerceIn(0, hourly.size)
        for (i in start..<until.coerceIn(start, hourly.size)) {
            val value = (hourly[i].temperature?.feelsLikeTemperature ?: hourly[i].temperature?.temperature)
                ?.value?.toFloat() ?: continue
            if (high == null || value > high) high = value
            if (low == null || value < low) low = value
        }
        val highest = high ?: return
        val lowest = low ?: return
        if (highest - lowest < MIN_RANGE) {
            val middle = (highest + lowest) / 2f
            mHighestTemperature = middle + MIN_RANGE / 2f
            mLowestTemperature = middle - MIN_RANGE / 2f
        } else {
            mHighestTemperature = highest
            mLowestTemperature = lowest
        }
    }

    companion object {
        /** The narrowest scale a chart is fitted to, in deci-degrees — two degrees top to bottom. */
        private const val MIN_RANGE = 20f
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trend_hourly, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: AbsHourlyTrendAdapter.ViewHolder, position: Int) {
        (holder as ViewHolder).onBindView(activity, location, position)
    }

    override fun getItemCount() = location.weather!!.hourlyForecast.size

    override fun isValid(location: Location): Boolean {
        return location.weather?.hourlyForecast?.any {
            it.temperature?.feelsLikeTemperature != null
        } == true
    }

    override fun getDisplayName(context: Context) = context.getString(R.string.tag_feels_like)

    override fun bindBackgroundForHost(host: TrendRecyclerView) {
        host.setData(null, 0f, 0f)
    }
}
