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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Size
import androidx.core.content.ContextCompat
import breezyweather.domain.location.model.Location
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.common.extensions.formatPercent
import org.breezyweather.common.extensions.getCalendarMonth
import org.breezyweather.common.extensions.getIsoFormattedDate
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.ui.common.charts.TemperatureColorScale
import org.breezyweather.ui.common.widgets.trend.TrendRecyclerView
import org.breezyweather.ui.common.widgets.trend.chart.PolylineAndHistogramView
import org.breezyweather.ui.theme.ThemeManager
import org.breezyweather.ui.theme.resource.ResourceHelper
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.ui.theme.weatherView.WeatherViewController
import org.breezyweather.unit.formatting.UnitWidth
import org.breezyweather.unit.precipitation.Precipitation.Companion.millimeters
import org.breezyweather.unit.temperature.TemperatureUnit
import java.util.Date
import kotlin.math.max

/**
 * Daily temperature adapter.
 */
class DailyTemperatureAdapter(
    activity: BreezyActivity,
    location: Location,
    provider: ResourceProvider,
    private val temperatureUnit: TemperatureUnit,
    private val showPrecipitationProbability: Boolean = true,
) : AbsDailyTrendAdapter(activity, location) {
    private val mResourceProvider: ResourceProvider = provider
    private val mDaytimeTemperatures: Array<Float?>
    private val mNighttimeTemperatures: Array<Float?>
    private var mHighestTemperature: Float? = null
    private var mLowestTemperature: Float? = null

    /**
     * shiroikuma fork: each day's rain at HOURLY resolution, keyed by the day's ISO date.
     *
     * The daily forecast carries one probability per half-day, which drew as blocks a day wide.
     * The hourly list has the shape the rain actually has, so the bars follow the shower rather
     * than the calendar.
     */
    private val mHourlyPrecipitationByDay: Map<String, FloatArray>

    /**
     * What a full-height bar means: 100 for a probability, or the wettest hour of the week when the
     * source reports amounts instead. Null when there is no rain to draw at all.
     *
     * Not every source reports a probability — MET Norway gives Prague an amount and nothing else,
     * which is why its chart came out dry while its icons showed rain.
     */
    private val mPrecipitationCeiling: Float?
    private val mPrecipitationIsAmount: Boolean

    init {
        val byDay = location.weather!!.hourlyForecast.groupBy { it.date.getIsoFormattedDate(location) }
        val probabilities = byDay.mapValues { (_, hours) ->
            FloatArray(hours.size) { hours[it].precipitationProbability?.total?.inPercent?.toFloat() ?: 0f }
        }
        if (probabilities.values.any { day -> day.any { it > 0f } }) {
            mHourlyPrecipitationByDay = probabilities
            mPrecipitationCeiling = 100f
            mPrecipitationIsAmount = false
        } else {
            val amounts = byDay.mapValues { (_, hours) ->
                FloatArray(hours.size) { hours[it].precipitation?.total?.inMillimeters?.toFloat() ?: 0f }
            }
            val wettest = amounts.values.flatMap { it.asIterable() }.maxOrNull() ?: 0f
            mHourlyPrecipitationByDay = amounts
            mPrecipitationIsAmount = true
            // A floor under the ceiling, so a single drizzly hour does not fill the whole band
            mPrecipitationCeiling = if (wettest > 0f) max(wettest, MIN_AMOUNT_CEILING_MM) else null
        }
    }

    private val mHasPrecipitation: Boolean get() = mPrecipitationCeiling != null

    inner class ViewHolder(itemView: View) : AbsDailyTrendAdapter.ViewHolder(itemView) {
        private val mPolylineAndHistogramView = PolylineAndHistogramView(itemView.context)

        init {
            dailyItem.chartItemView = mPolylineAndHistogramView
        }

        @SuppressLint("SetTextI18n, InflateParams")
        fun onBindView(activity: BreezyActivity, location: Location, position: Int) {
            val talkBackBuilder = StringBuilder(activity.getString(R.string.tag_temperature))
            super.onBindView(activity, location, talkBackBuilder, position)
            val daily = location.weather!!.dailyForecast[position]
            daily.day?.let { day ->
                talkBackBuilder.append(activity.getString(org.breezyweather.unit.R.string.locale_separator))
                    .append(activity.getString(R.string.daytime))
                    .append(activity.getString(R.string.colon_separator))
                day.temperature?.temperature?.let {
                    talkBackBuilder.append(it.formatMeasure(activity, temperatureUnit, unitWidth = UnitWidth.LONG))
                        .append(activity.getString(org.breezyweather.unit.R.string.locale_separator))
                }
                if (!day.weatherText.isNullOrEmpty()) {
                    talkBackBuilder.append(day.weatherText)
                }
                if (showPrecipitationProbability) {
                    day.precipitationProbability?.total?.let { p ->
                        talkBackBuilder.append(activity.getString(org.breezyweather.unit.R.string.locale_separator))
                            .append(activity.getString(R.string.precipitation_probability))
                            .append(activity.getString(R.string.colon_separator))
                            .append(p.formatPercent(activity))
                    }
                }
            }
            daily.night?.let { night ->
                talkBackBuilder.append(activity.getString(org.breezyweather.unit.R.string.locale_separator))
                    .append(activity.getString(R.string.nighttime))
                    .append(activity.getString(R.string.colon_separator))
                night.temperature?.temperature?.let {
                    talkBackBuilder.append(it.formatMeasure(activity, temperatureUnit, unitWidth = UnitWidth.LONG))
                        .append(activity.getString(org.breezyweather.unit.R.string.locale_separator))
                }
                if (!night.weatherText.isNullOrEmpty()) {
                    talkBackBuilder.append(night.weatherText)
                }
                if (showPrecipitationProbability) {
                    night.precipitationProbability?.total?.let { p ->
                        talkBackBuilder.append(activity.getString(org.breezyweather.unit.R.string.locale_separator))
                            .append(activity.getString(R.string.precipitation_probability))
                            .append(activity.getString(R.string.colon_separator))
                            .append(p.formatPercent(activity))
                    }
                }
            }
            dailyItem.setDayIconDrawable(
                daily.day?.weatherCode?.let { ResourceHelper.getWeatherIcon(mResourceProvider, it, true) },
                missingIconVisibility = View.INVISIBLE
            )
            val daytimePrecipitationProbability = daily.day?.precipitationProbability?.total
            val nighttimePrecipitationProbability = daily.night?.precipitationProbability?.total
            val p = listOfNotNull(daytimePrecipitationProbability, nighttimePrecipitationProbability)
                .takeIf { it.isNotEmpty() }?.maxBy { it.value }
            // shiroikuma fork: one trace per day instead of two curves — the boundary shared with
            // yesterday's night, today's high, tonight's low, and the boundary shared with
            // tomorrow's high. Consecutive days join at those boundaries into a single line that
            // rises through each day and falls through each night.
            val dayValue = mDaytimeTemperatures.getOrNull(position * 2)
            val nightValue = mNighttimeTemperatures.getOrNull(position * 2)
            val previousNight = mNighttimeTemperatures.getOrNull(position * 2 - 2)
            val nextDay = mDaytimeTemperatures.getOrNull(position * 2 + 2)
            mPolylineAndHistogramView.setDualPolylineData(
                arrayOf(
                    if (previousNight != null && dayValue != null) {
                        (previousNight + dayValue) / 2f
                    } else {
                        dayValue
                    },
                    dayValue,
                    nightValue,
                    if (nextDay != null && nightValue != null) (nightValue + nextDay) / 2f else nightValue
                ),
                daily.day?.temperature?.temperature?.formatMeasure(
                    activity,
                    temperatureUnit,
                    valueWidth = UnitWidth.NARROW,
                    unitWidth = UnitWidth.NARROW
                ),
                daily.night?.temperature?.temperature?.formatMeasure(
                    activity,
                    temperatureUnit,
                    valueWidth = UnitWidth.NARROW,
                    unitWidth = UnitWidth.NARROW
                ),
                mHighestTemperature,
                mLowestTemperature
            )
            // shiroikuma fork: one bar per hour of this day, and the day's own figure at the foot.
            val hours = mHourlyPrecipitationByDay[daily.date.getIsoFormattedDate(location)]
            mPolylineAndHistogramView.setPrecipitationBars(
                hours?.takeIf { showPrecipitationProbability },
                if (mPrecipitationIsAmount) {
                    // The day's total, since a probability is not on offer from this source
                    listOfNotNull(daily.day?.precipitation?.total, daily.night?.precipitation?.total)
                        .takeIf { it.isNotEmpty() }
                        ?.sumOf { it.inMillimeters }
                        ?.takeIf { it > 0.0 }
                        ?.millimeters
                        ?.formatMeasure(activity, valueWidth = UnitWidth.NARROW, unitWidth = UnitWidth.NARROW)
                } else {
                    p?.takeIf { it.value > 0 && showPrecipitationProbability }
                        ?.formatPercent(activity, UnitWidth.NARROW)
                },
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
                themeColors[1],
                themeColors[2],
                activity.getThemeColor(com.google.android.material.R.attr.colorOutline)
            )
            // shiroikuma fork: the curves are painted by temperature, the same scale the details
            // screen uses. Values here are deci-Celsius, which is what Temperature.value stores.
            mPolylineAndHistogramView.setPolylineGradientStops(TemperatureColorScale.stopsInDeciCelsius)
            mPolylineAndHistogramView.setPolylineTextSizeDip(DAILY_READING_SIZE_DIP)
            // shiroikuma fork: banded days instead of a rule through the chart. No solid fill here:
            // the daily card draws a high AND a low curve, and filling under the high one would
            // bury the low one.
            // shiroikuma fork: the band belongs to the whole column — labels and icons included —
            // so the item view draws it rather than the chart.
            dailyItem.bandShaded = position % 2 == 0
            mPolylineAndHistogramView.setChartChrome(
                solidFill = true,
                banded = false,
                dayDivider = false,
                isNow = false
            )
            // shiroikuma fork: the wash under the curve is tinted by the warm end of the range, so
            // it reads with the curve above it instead of against it. Both arguments get the same
            // colour because setShadowColors picks one of them by theme and fades it to transparent.
            val wash = mHighestTemperature?.let { TemperatureColorScale.colorAt(it) }
            mPolylineAndHistogramView.setShadowColors(
                wash ?: themeColors[1],
                wash ?: themeColors[2],
                lightTheme
            )
            mPolylineAndHistogramView.setTextColors(
                activity.getThemeColor(R.attr.colorTitleText),
                activity.getThemeColor(R.attr.colorBodyText),
                activity.getThemeColor(R.attr.colorPrecipitationProbability)
            )
            mPolylineAndHistogramView.setHistogramAlpha(if (lightTheme) 0.2f else 0.5f)
            dailyItem.setNightIconDrawable(
                daily.night?.weatherCode?.let { ResourceHelper.getWeatherIcon(mResourceProvider, it, false) },
                missingIconVisibility = View.INVISIBLE
            )
            dailyItem.contentDescription = talkBackBuilder.toString()
            dailyItem.setOnClickListener {
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
        mDaytimeTemperatures = arrayOfNulls(max(0, weather.dailyForecast.size * 2 - 1))
        run {
            var i = 0
            while (i < mDaytimeTemperatures.size) {
                mDaytimeTemperatures[i] =
                    weather.dailyForecast.getOrNull(i / 2)?.day?.temperature?.temperature?.value?.toFloat()
                i += 2
            }
        }
        run {
            var i = 1
            while (i < mDaytimeTemperatures.size) {
                if (mDaytimeTemperatures[i - 1] != null && mDaytimeTemperatures[i + 1] != null) {
                    mDaytimeTemperatures[i] = (mDaytimeTemperatures[i - 1]!! + mDaytimeTemperatures[i + 1]!!) * 0.5f
                } else {
                    mDaytimeTemperatures[i] = null
                }
                i += 2
            }
        }
        mNighttimeTemperatures = arrayOfNulls(max(0, weather.dailyForecast.size * 2 - 1))
        run {
            var i = 0
            while (i < mNighttimeTemperatures.size) {
                mNighttimeTemperatures[i] =
                    weather.dailyForecast.getOrNull(i / 2)?.night?.temperature?.temperature?.value?.toFloat()
                i += 2
            }
        }
        run {
            var i = 1
            while (i < mNighttimeTemperatures.size) {
                if (mNighttimeTemperatures[i - 1] != null && mNighttimeTemperatures[i + 1] != null) {
                    mNighttimeTemperatures[i] =
                        (mNighttimeTemperatures[i - 1]!! + mNighttimeTemperatures[i + 1]!!) * 0.5f
                } else {
                    mNighttimeTemperatures[i] = null
                }
                i += 2
            }
        }
        // shiroikuma fork: the range fits THIS source's own data rather than the monthly normals,
        // so a source whose forecast diverges is not squashed onto someone else's scale.
        weather.dailyForecast.forEach { daily ->
            daily.day?.temperature?.temperature?.value?.let {
                if (mHighestTemperature == null || it > mHighestTemperature!!) {
                    mHighestTemperature = it.toFloat()
                }
                if (mLowestTemperature == null || it < mLowestTemperature!!) {
                    mLowestTemperature = it.toFloat()
                }
            }
            daily.night?.temperature?.temperature?.value?.let {
                if (mHighestTemperature == null || it > mHighestTemperature!!) {
                    mHighestTemperature = it.toFloat()
                }
                if (mLowestTemperature == null || it < mLowestTemperature!!) {
                    mLowestTemperature = it.toFloat()
                }
            }
        }

        // shiroikuma fork: room BELOW the coldest night for its reading, which sits under the
        // trough. Nothing is reserved above the warmest day: the peak runs to the top of the pane
        // like the hourly chart's, and its plate tucks under the top edge rather than being kept
        // clear by headroom that would otherwise sit there empty all week.
        val high = mHighestTemperature
        val low = mLowestTemperature
        if (high != null && low != null && high > low) {
            mLowestTemperature = low - (high - low) * RANGE_PADDING_BOTTOM
        }
    }

    companion object {
        /** Share of the range left free BELOW the coldest night, for the reading under its trough. */
        private const val RANGE_PADDING_BOTTOM = 0.18f

        /** The lightest week that still fills the band, so a drizzle does not read as a downpour. */
        private const val MIN_AMOUNT_CEILING_MM = 3f

        /** A day column is wide enough to carry two readings at this size. */
        private const val DAILY_READING_SIZE_DIP = 33f
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trend_daily, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: AbsDailyTrendAdapter.ViewHolder, position: Int) {
        (holder as ViewHolder).onBindView(activity, location, position)
    }

    override fun getItemCount() = location.weather!!.dailyForecast.size

    // FIXME
    override fun isValid(location: Location) = true

    override fun getDisplayName(context: Context) = context.getString(R.string.tag_temperature)

    override fun bindBackgroundForHost(host: TrendRecyclerView) {
        // shiroikuma fork: no key lines and no left-hand scale, matching the hourly chart.
        // TrendRecyclerView draws the "Normal" rules and the temperature labels together, so
        // passing null removes both.
        host.setData(null, 0f, 0f)
    }
}
