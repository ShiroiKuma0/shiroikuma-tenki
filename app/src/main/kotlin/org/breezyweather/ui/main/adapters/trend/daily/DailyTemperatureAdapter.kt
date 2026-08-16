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
import org.breezyweather.tenki.TenkiViewTheme
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

    /**
     * shiroikuma fork: the half-days whose temperature we filled in ourselves, by day.
     *
     * They are plotted like any other knot but drawn faded and left without a reading, so a shape
     * we extrapolated is never read as one the source forecast.
     */
    private val mDaytimeEstimated: BooleanArray
    private val mNighttimeEstimated: BooleanArray
    private var mHighestTemperature: Float? = null
    private var mLowestTemperature: Float? = null

    /**
     * shiroikuma fork: how many day columns actually fit on the screen — the days the settings page
     * asks for, widened or narrowed by whatever the chart has been pinched to.
     */
    private val mVisibleColumns: Int = TenkiViewTheme.state(activity).let {
        // Deliberately NOT narrowed by the pinch zoom: this is the guess the chart draws with for
        // the one frame before it can say which columns it is really showing, and a guess that is
        // too wide only wastes a little of the pane, while one that is too narrow drops the days
        // outside it off the bottom.
        it.dailyDaysVisible.coerceAtLeast(2)
    }

    /** The column the card opens on, which is where the vertical scale is measured from. */
    private val mOpeningIndex: Int = location.weather!!.todayIndex ?: 0

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
            // shiroikuma fork: from which knot on this column's trace is ours rather than the
            // source's — the fall after the peak when the night was filled in, the whole column
            // when the day was. Each column answers for its own halves only: the boundary a day
            // shares with a filled-in neighbour is half invented either way, and fading a column
            // of real readings for it would say far more than that seam is worth.
            val estimatedFrom = when {
                mDaytimeEstimated.getOrElse(position) { false } -> 0f
                mNighttimeEstimated.getOrElse(position) { false } -> PolylineAndHistogramView.DUAL_HIGH_X
                else -> null
            }
            mPolylineAndHistogramView.setDualPolylineData(
                arrayOf(
                    if (previousNight != null && dayValue != null) {
                        (previousNight + dayValue) / 2f
                    } else {
                        dayValue
                    },
                    dayValue,
                    nightValue,
                    // Null, not the night again: with no day after this one there is nothing for
                    // the fall to reach, and holding it level to the column's edge drew a night
                    // that stopped getting colder at 18:00
                    if (nextDay != null && nightValue != null) (nightValue + nextDay) / 2f else null
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
                mLowestTemperature,
                estimatedFrom
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
        val days = weather.dailyForecast.size
        mDaytimeTemperatures = arrayOfNulls(max(0, days * 2 - 1))
        mNighttimeTemperatures = arrayOfNulls(max(0, days * 2 - 1))
        mDaytimeEstimated = BooleanArray(days)
        mNighttimeEstimated = BooleanArray(days)
        run {
            var i = 0
            while (i < mDaytimeTemperatures.size) {
                mDaytimeTemperatures[i] =
                    weather.dailyForecast.getOrNull(i / 2)?.day?.temperature?.temperature?.value?.toFloat()
                mNighttimeTemperatures[i] =
                    weather.dailyForecast.getOrNull(i / 2)?.night?.temperature?.temperature?.value?.toFloat()
                i += 2
            }
        }
        // Before the midpoints between them are worked out, so a knot we filled in joins its
        // neighbours the way a published one would
        estimateMissingHalfDays(days)
        run {
            var i = 1
            while (i < mDaytimeTemperatures.size) {
                if (mDaytimeTemperatures[i - 1] != null && mDaytimeTemperatures[i + 1] != null) {
                    mDaytimeTemperatures[i] = (mDaytimeTemperatures[i - 1]!! + mDaytimeTemperatures[i + 1]!!) * 0.5f
                } else {
                    mDaytimeTemperatures[i] = null
                }
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
        // so a source whose forecast diverges is not squashed onto someone else's scale — and only
        // the days visible when the card OPENS, exactly as the hourly chart does it. Measured over
        // the whole stored series, a hot day scrolled off to either side set the ceiling and left
        // the week you can actually see sitting well below the top of the pane; with a month of
        // history behind today, that day is usually one nobody will ever scroll back to.
        //
        // From the knots rather than from the days, so a half-day we filled in — which IS drawn —
        // has room instead of being clipped flat against the floor.
        // Only the opening guess, for the frame before the chart has been laid out and can say
        // which days it is actually showing
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
     * Fit the scale to the days in `[from, until)`.
     *
     * A stretch of nothing but a source's blank columns keeps the scale it had: there is nothing to
     * fit to there, and a null range draws no chart at all. The half-days we filled in ourselves
     * count — they ARE drawn, and a trough clipped against the floor would come out flat again.
     */
    private fun fitRange(from: Int, until: Int) {
        var high: Float? = null
        var low: Float? = null
        var i = (from * 2).coerceAtLeast(0)
        val end = (until * 2).coerceAtMost(mDaytimeTemperatures.size)
        while (i < end) {
            listOfNotNull(mDaytimeTemperatures[i], mNighttimeTemperatures[i]).forEach {
                if (high == null || it > high) high = it
                if (low == null || it < low) low = it
            }
            i += 2
        }
        var highest = high ?: return
        var lowest = low ?: return
        // A week that never changes temperature still leaves the chart something to divide by
        if (highest - lowest < MIN_RANGE) {
            val middle = (highest + lowest) / 2f
            highest = middle + MIN_RANGE / 2f
            lowest = middle - MIN_RANGE / 2f
        }
        mHighestTemperature = highest
        // shiroikuma fork: room BELOW the coldest night for its reading, which sits under the
        // trough. Nothing is reserved above the warmest day: the peak runs to the top of the pane
        // like the hourly chart's, and its plate tucks under the top edge rather than being kept
        // clear by headroom that would otherwise sit there empty all week.
        mLowestTemperature = lowest - (highest - lowest) * RANGE_PADDING_BOTTOM
    }

    /**
     * shiroikuma fork: the half-days a source published without their other half, filled in from
     * the swing of the days around them.
     *
     * ČHMÚ's national outlook files a night's minimum under the day it *precedes*, so its last day
     * arrives with a maximum and no minimum — and a trace with nowhere to fall ran flat from that
     * day's peak to the end of the week, which read as an afternoon that never cooled off. The
     * missing half takes the day-to-night swing of the nearest days that have both, preferring the
     * ones BEFORE it, so the curve comes down the way this source's own week comes down rather than
     * by some fixed number of degrees.
     *
     * Only the value is invented, never a reading: the plate over a knot still comes from the
     * source's own figure, and stays away when there is none.
     */
    private fun estimateMissingHalfDays(days: Int) {
        val swings = (0 until days).mapNotNull { day ->
            val high = mDaytimeTemperatures.getOrNull(day * 2)
            val low = mNighttimeTemperatures.getOrNull(day * 2)
            if (high != null && low != null) day to (high - low) else null
        }
        if (swings.isEmpty()) return

        (0 until days).forEach { day ->
            val high = mDaytimeTemperatures.getOrNull(day * 2)
            val low = mNighttimeTemperatures.getOrNull(day * 2)
            if ((high == null) == (low == null)) return@forEach
            // The days just before this one, or the ones just after when it opens the week
            val nearby = swings.filter { it.first < day }.takeLast(SWING_DAYS)
                .ifEmpty { swings.filter { it.first > day }.take(SWING_DAYS) }
            if (nearby.isEmpty()) return@forEach
            val swing = nearby.map { it.second }.average().toFloat()
            if (high != null) {
                mNighttimeTemperatures[day * 2] = high - swing
                mNighttimeEstimated[day] = true
            } else {
                mDaytimeTemperatures[day * 2] = low!! + swing
                mDaytimeEstimated[day] = true
            }
        }
    }

    companion object {
        /** Share of the range left free BELOW the coldest night, for the reading under its trough. */
        private const val RANGE_PADDING_BOTTOM = 0.18f

        /** How many neighbouring days a filled-in half-day takes its rise or fall from. */
        private const val SWING_DAYS = 3

        /** The narrowest scale a chart is fitted to, in deci-degrees — two degrees top to bottom. */
        private const val MIN_RANGE = 20f

        /** The lightest week that still fills the band, so a drizzle does not read as a downpour. */
        private const val MIN_AMOUNT_CEILING_MM = 3f

        /** A day column is wide enough to carry two readings at this size. */
        private const val DAILY_READING_SIZE_DIP = 24.75f
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
