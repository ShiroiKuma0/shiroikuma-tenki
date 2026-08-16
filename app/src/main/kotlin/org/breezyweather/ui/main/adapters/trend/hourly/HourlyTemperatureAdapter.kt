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
import androidx.core.content.ContextCompat
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.model.Hourly
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.common.extensions.formatPercent
import org.breezyweather.common.extensions.getCalendarMonth
import org.breezyweather.common.extensions.getHourIn24Format
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.tenki.TenkiViewTheme
import org.breezyweather.ui.common.charts.TemperatureColorScale
import org.breezyweather.ui.common.widgets.trend.TrendRecyclerView
import org.breezyweather.ui.common.widgets.trend.chart.PolylineAndHistogramView
import org.breezyweather.ui.common.widgets.trend.item.HourlyTrendItemView
import org.breezyweather.ui.theme.ThemeManager
import org.breezyweather.ui.theme.resource.ResourceHelper
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.ui.theme.weatherView.WeatherViewController
import org.breezyweather.unit.formatting.UnitWidth
import org.breezyweather.unit.temperature.TemperatureUnit
import java.util.Date
import kotlin.math.max
import kotlin.time.Duration.Companion.hours

/**
 * Hourly temperature adapter.
 */
class HourlyTemperatureAdapter(
    activity: BreezyActivity,
    location: Location,
    provider: ResourceProvider,
    private val temperatureUnit: TemperatureUnit,
    private val showPrecipitationProbability: Boolean = true,
) : AbsHourlyTrendAdapter(activity, location) {
    private val mResourceProvider: ResourceProvider = provider

    /**
     * shiroikuma fork: how many columns actually fit on the screen — the window from the settings
     * page, widened or narrowed by whatever the chart has been pinched to. The rest stays
     * scrollable, so this decides the vertical scale, not how much forecast is available.
     */
    private val mVisibleColumns: Int = TenkiViewTheme.state(activity).let {
        // Deliberately NOT narrowed by the pinch zoom: this is the guess the chart draws with for
        // the one frame before it can say which columns it is really showing, and a guess that is
        // too wide only wastes a little of the pane, while one that is too narrow drops the hours
        // outside it off the bottom.
        (it.hourlyHoursBack + it.hourlyHoursAhead).coerceAtLeast(2)
    }

    /**
     * The plotted series: everything still stored, the past included, so the card can be scrolled
     * back through what the weather actually did. Every hourly tab plots this same list, which is
     * what lets the scroll position survive a tab switch.
     */
    private val mHourlyList: List<Hourly> = location.weather!!.hourlyForecast

    /** Where the past ends: the first hour at or after the current one. */
    private val mNowIndex: Int = mHourlyList
        .indexOfFirst { it.date.time >= System.currentTimeMillis() - 1.hours.inWholeMilliseconds }
        .coerceAtLeast(0)

    /** The column the card opens on, which is where the vertical scale is measured from. */
    private val mOpeningIndex: Int = location.weather!!.hourlyOpeningIndex(
        TenkiViewTheme.state(activity).hourlyHoursBack
    )

    private val mTemperatures: Array<Float?>
    private var mHighestTemperature: Float? = null
    private var mLowestTemperature: Float? = null

    /**
     * shiroikuma fork: whether this source forecasts any rain at all over the window.
     *
     * The precipitation band is only reserved when it is, because reserving 120dp for columns that
     * are all zero left a black void under every chart on a dry day.
     */
    private val mPrecipitationCeiling: Float?

    /**
     * shiroikuma fork: precipitation probability interpolated to 2n-1, the same shape as
     * [mTemperatures], so each item can draw its slice of a smooth band from three points.
     */
    private val mPrecipitationProbabilities: Array<Float?>

    inner class ViewHolder(itemView: View) : AbsHourlyTrendAdapter.ViewHolder(itemView) {
        private val mPolylineAndHistogramView = PolylineAndHistogramView(itemView.context)

        init {
            hourlyItem.chartItemView = mPolylineAndHistogramView
        }

        fun onBindView(activity: BreezyActivity, location: Location, position: Int) {
            val talkBackBuilder = StringBuilder(activity.getString(R.string.tag_temperature))
            super.onBindView(activity, location, talkBackBuilder, position, mHourlyList)
            val weather = location.weather!!
            val hourly = mHourlyList[position]
            hourly.temperature?.temperature?.let {
                talkBackBuilder.append(activity.getString(org.breezyweather.unit.R.string.locale_separator))
                    .append(it.formatMeasure(activity, temperatureUnit, unitWidth = UnitWidth.LONG))
            }
            if (!hourly.weatherText.isNullOrEmpty()) {
                talkBackBuilder.append(activity.getString(org.breezyweather.unit.R.string.locale_separator))
                    .append(hourly.weatherText)
            }
            // shiroikuma fork: banding and the past wash belong to the whole column, hour label
            // and icon included, so the item view draws them rather than the chart. The column
            // width is set by the base adapter, which every tab shares.
            hourlyItem.bandShaded = position % 2 == 0
            hourlyItem.dimmed = position < mNowIndex
            hourlyItem.dayDivider = hourly.date.getHourIn24Format(location) == "0"
            hourlyItem.setIconDrawable(
                hourly.weatherCode?.let {
                    ResourceHelper.getWeatherIcon(mResourceProvider, it, hourly.isDaylight)
                },
                missingIconVisibility = View.INVISIBLE
            )
            if (showPrecipitationProbability && hourly.precipitationProbability?.total != null) {
                talkBackBuilder.append(activity.getString(org.breezyweather.unit.R.string.locale_separator))
                    .append(activity.getString(R.string.precipitation_probability))
                    .append(activity.getString(R.string.colon_separator))
                    .append(hourly.precipitationProbability!!.total!!.formatPercent(activity, UnitWidth.NARROW))
            }
            mPolylineAndHistogramView.setData(
                buildTemperatureArrayForItem(mTemperatures, position),
                null,
                hourly.temperature?.temperature?.formatMeasure(
                    activity,
                    temperatureUnit,
                    valueWidth = UnitWidth.NARROW,
                    unitWidth = UnitWidth.NARROW
                ),
                null,
                mHighestTemperature,
                mLowestTemperature,
                // shiroikuma fork: no histogram — precipitation is the line along the foot instead
                null,
                null,
                null,
                null
            )
            // shiroikuma fork: the curve is painted by temperature, on the same scale the details
            // screen uses. Values here are deci-Celsius, which is what Temperature.value stores.
            mPolylineAndHistogramView.setPolylineGradientStops(TemperatureColorScale.stopsInDeciCelsius)
            // shiroikuma fork: a reading on every hour needs to fit one column's width, so the
            // hourly chart sets its own smaller size rather than the daily card's headline one.
            mPolylineAndHistogramView.setPolylineTextSizeDip(HOURLY_READING_SIZE_DIP)
            // shiroikuma fork: the meteogram chrome. The window opens before now, so the current
            // hour is at mNowIndex rather than at 0, and everything left of it is the past.
            mPolylineAndHistogramView.setChartChrome(
                solidFill = true,
                banded = false,
                // The divider moved to the item view, which spans the whole column
                dayDivider = false,
                isNow = position == mNowIndex
            )
            // shiroikuma fork: one column per hour. A null ceiling switches the band off entirely,
            // so turning the setting off gives its height back to the temperature curve.
            mPolylineAndHistogramView.setPrecipitationColumns(
                buildTemperatureArrayForItem(mPrecipitationProbabilities, position),
                if (showPrecipitationProbability) mPrecipitationCeiling else null,
                ContextCompat.getColor(activity, R.color.precipitationProbabilityLine)
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
            // shiroikuma fork: the wash under the curve is tinted by the warm end of the range, so
            // it reads with the curve above it instead of against it. Both arguments get the same
            // colour because setShadowColors picks one of them by theme and fades it to transparent.
            val wash = mHighestTemperature?.let { TemperatureColorScale.colorAt(it) }
            mPolylineAndHistogramView.setShadowColors(
                wash ?: themeColors[if (lightTheme) 1 else 2],
                wash ?: themeColors[2],
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
                onItemClicked(activity, location, bindingAdapterPosition, DetailScreen.TAG_CONDITIONS)
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
        mTemperatures = arrayOfNulls(max(0, mHourlyList.size * 2 - 1))
        run {
            var i = 0
            while (i < mTemperatures.size) {
                mTemperatures[i] =
                    mHourlyList.getOrNull(i / 2)?.temperature?.temperature?.value?.toFloat()
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
        // shiroikuma fork: probability where the source offers one, amount where it does not.
        // MET Norway gives Prague an amount and no probability, which left its band empty while
        // its icons showed rain.
        val hasProbability = mHourlyList.any {
            (it.precipitationProbability?.total?.inPercent?.toFloat() ?: 0f) > 0f
        }
        val wettest = mHourlyList.maxOfOrNull {
            it.precipitation?.total?.inMillimeters?.toFloat() ?: 0f
        } ?: 0f
        mPrecipitationCeiling = when {
            hasProbability -> 100f
            wettest > 0f -> max(wettest, MIN_AMOUNT_CEILING_MM)
            else -> null
        }
        mPrecipitationProbabilities = arrayOfNulls(max(0, mHourlyList.size * 2 - 1))
        run {
            var i = 0
            while (i < mPrecipitationProbabilities.size) {
                val hourly = mHourlyList.getOrNull(i / 2)
                mPrecipitationProbabilities[i] = if (hasProbability) {
                    hourly?.precipitationProbability?.total?.inPercent?.toFloat() ?: 0f
                } else {
                    hourly?.precipitation?.total?.inMillimeters?.toFloat() ?: 0f
                }
                i += 2
            }
        }
        run {
            var i = 1
            while (i < mPrecipitationProbabilities.size) {
                val before = mPrecipitationProbabilities[i - 1]
                val after = mPrecipitationProbabilities[i + 1]
                mPrecipitationProbabilities[i] = if (before != null && after != null) {
                    (before + after) * 0.5f
                } else {
                    null
                }
                i += 2
            }
        }
        // shiroikuma fork: the range fits the hours ON SCREEN, not the whole scrollable window —
        // otherwise tomorrow afternoon's peak, which you cannot see, dictates the scale and leaves
        // the visible half empty. This is only the opening guess, for the frame before the chart
        // has been laid out and can say which columns it is actually showing.
        fitRange(mOpeningIndex, mOpeningIndex + mVisibleColumns)
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

    /**
     * Fit the scale to the hours in `[from, until)`.
     *
     * A stretch of nothing but a source's blank columns keeps the scale it had: there is nothing to
     * fit to there, and a null range draws no chart at all.
     */
    private fun fitRange(from: Int, until: Int) {
        var high: Float? = null
        var low: Float? = null
        val start = from.coerceIn(0, mHourlyList.size)
        for (i in start..<until.coerceIn(start, mHourlyList.size)) {
            val value = mHourlyList[i].temperature?.temperature?.value?.toFloat() ?: continue
            if (high == null || value > high) high = value
            if (low == null || value < low) low = value
        }
        val highest = high ?: return
        val lowest = low ?: return
        // An hour that never changes temperature still leaves the chart something to divide by
        if (highest - lowest < MIN_RANGE) {
            val middle = (highest + lowest) / 2f
            mHighestTemperature = middle + MIN_RANGE / 2f
            mLowestTemperature = middle - MIN_RANGE / 2f
        } else {
            mHighestTemperature = highest
            mLowestTemperature = lowest
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trend_hourly, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: AbsHourlyTrendAdapter.ViewHolder, position: Int) {
        (holder as ViewHolder).onBindView(activity, location, position)
    }

    override fun getItemCount() = mHourlyList.size

    companion object {
        /** Sized to fill a column at 12 visible hours without spilling into its neighbours. */
        private const val HOURLY_READING_SIZE_DIP = 22.5f

        /** The lightest window that still fills the band, when a source reports amounts only. */
        private const val MIN_AMOUNT_CEILING_MM = 3f

        /** The narrowest scale a chart is fitted to, in deci-degrees — two degrees top to bottom. */
        private const val MIN_RANGE = 20f
    }

    // FIXME
    override fun isValid(location: Location) = true

    override fun getDisplayName(context: Context) = context.getString(R.string.tag_temperature)

    override fun bindBackgroundForHost(host: TrendRecyclerView) {
        // shiroikuma fork: no key lines and no left-hand scale. The "Normal" rules and the
        // temperature labels they carry were the horizontal lines and the axis 白い熊 asked to be
        // rid of; passing null is what removes both, since TrendRecyclerView draws them together.
        host.setData(null, 0f, 0f)
    }
}
