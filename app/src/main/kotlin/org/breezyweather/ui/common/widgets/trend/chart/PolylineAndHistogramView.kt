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

package org.breezyweather.ui.common.widgets.trend.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.annotation.Size
import androidx.core.graphics.ColorUtils
import org.breezyweather.R
import org.breezyweather.common.extensions.dpToPx
import org.breezyweather.common.extensions.getTypefaceFromTextAppearance
import org.breezyweather.ui.common.widgets.DayNightShaderWrapper
import kotlin.math.max
import kotlin.math.min

/**
 * Polyline and histogram view.
 */
class PolylineAndHistogramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbsChartItemView(context, attrs, defStyleAttr) {
    private val mPaint = Paint().apply {
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        isFilterBitmap = true
    }
    private val mPath = Path()
    private val mShaderWrapper: DayNightShaderWrapper

    @Size(3)
    private var mHighPolylineValues: Array<Float?>? = arrayOfNulls(3)

    @Size(3)
    private var mLowPolylineValues: Array<Float?>? = arrayOfNulls(3)
    private var mHighPolylineValueStr: String? = null
    private var mLowPolylineValueStr: String? = null
    private var mHighestPolylineValue: Float? = null
    private var mLowestPolylineValue: Float? = null
    private var mHistogramValue: Float? = null
    private var mHistogramValueStr: String? = null
    private var mHighestHistogramValue: Float? = null
    private var mLowestHistogramValue: Float? = null
    private val mHighPolylineY = IntArray(3)
    private val mLowPolylineY = IntArray(3)
    private var mHistogramY = 0

    // shiroikuma fork: an opt-in vertical gradient for the polyline stroke, plus the precipitation
    // line along the bottom. Both stay null for the charts that do not ask for them.
    private var mPolylineGradientStops: List<Pair<Float, Int>>? = null
    private var mPolylineShader: Shader? = null
    private var mPolylineShaderKey: String? = null

    // shiroikuma fork: the meteogram-style chrome — a solid fill under the curve coloured by the
    // temperature at each point, banded hours, a midnight divider and a "now" marker.
    private var mSolidFill = false

    /**
     * Whether this chart uses the banded chrome at all. Distinct from [mHourBanded], which says
     * whether THIS item is one of the shaded ones — conflating the two left every unshaded item
     * falling through to the old vertical rule, which is the yellow line down each odd hour.
     */
    private var mChromeEnabled = false
    private var mHourBanded = false
    private var mDayDivider = false
    private var mIsNow = false
    private var mIsHistory = false
    private val mFillStripWidth: Int
    private val mDashLength: Int

    /**
     * shiroikuma fork: the daily chart's four knots — the boundary shared with the previous day,
     * this day's high, this day's night low, and the boundary shared with the next day. Drawing
     * these as ONE line turns two disconnected curves into a single trace that rises through each
     * day and falls through each night.
     */
    @Size(4)
    private var mDualValues: Array<Float?>? = null

    @Size(3)
    private var mPrecipitationValues: Array<Float?>? = null

    /**
     * shiroikuma fork: the daily chart's rain — one value per hour of the day, drawn as that many
     * thin bars across the column. A single figure per day stepped in blocks a day wide, which read
     * as though the rain switched on and off at midnight.
     */
    private var mPrecipitationBars: FloatArray? = null
    private var mPrecipitationLabel: String? = null
    private var mHighestPrecipitationValue: Float? = null
    private var mPrecipitationColor = Color.TRANSPARENT

    private val mPrecipitationColumnWidth: Int
    override val marginTop: Int
    override val marginBottom: Int
    private val mPolylineWidth: Int

    /** shiroikuma fork: a var — the hourly chart sets a smaller size than the daily card's. */
    private var mPolylineTextSize: Int
    private val mHistogramWidth: Int
    private val mHistogramTextSize: Int
    private val mChartLineWidth: Int
    private val mTextMargin: Int
    private val mLineColors: IntArray = intArrayOf(Color.BLACK, Color.DKGRAY, Color.LTGRAY)
    private val mShadowColors: IntArray = intArrayOf(Color.BLACK, Color.WHITE)
    private var mHighTextColor = 0
    private var mLowTextColor = 0
    private var mTextShadowColor = 0
    private var mHistogramTextColor = 0
    private var mHistogramAlpha = 0f

    init {
        setTextColors(Color.BLACK, Color.DKGRAY, Color.GRAY)
        setHistogramAlpha(0.33f)
        marginTop = getContext().dpToPx(MARGIN_TOP_DIP).toInt()
        marginBottom = getContext().dpToPx(MARGIN_BOTTOM_DIP).toInt()
        mPolylineTextSize = getContext().dpToPx(POLYLINE_TEXT_SIZE_DIP).toInt()
        mHistogramTextSize = getContext().dpToPx(HISTOGRAM_TEXT_SIZE_DIP).toInt()
        mPolylineWidth = getContext().dpToPx(POLYLINE_SIZE_DIP).toInt()
        mHistogramWidth = getContext().dpToPx(HISTOGRAM_WIDTH_DIP).toInt()
        mChartLineWidth = getContext().dpToPx(CHART_LINE_SIZE_DIP).toInt()
        mTextMargin = getContext().dpToPx(TEXT_MARGIN_DIP).toInt()

        mPrecipitationColumnWidth = getContext().dpToPx(PRECIPITATION_COLUMN_WIDTH_DIP).toInt()
        mFillStripWidth = getContext().dpToPx(FILL_STRIP_WIDTH_DIP).toInt().coerceAtLeast(1)
        mDashLength = getContext().dpToPx(DASH_LENGTH_DIP).toInt().coerceAtLeast(1)
        mPaint.typeface = getContext().getTypefaceFromTextAppearance(R.style.title_text)
        mShaderWrapper = DayNightShaderWrapper(measuredWidth, measuredHeight)
        setShadowColors(Color.BLACK, Color.GRAY, true)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        ensureShader(mShaderWrapper.isLightTheme)
        computeCoordinates()
        ensurePolylineShader()
        // shiroikuma fork: the banding moved to the trend item view, which owns the full column
        // height including the hour label and the icon. All this chart has to do is stop drawing
        // the old vertical rule.
        if (!mChromeEnabled) {
            drawTimeLine(canvas)
        }
        if (mDualValues != null) {
            drawDualPolyline(canvas)
        } else if (mSolidFill) {
            drawSolidFill(canvas)
        }
        if (mHistogramValue != null &&
            (mHistogramValue != 0f || (mHighestPolylineValue == null && mLowestPolylineValue == null)) &&
            mHistogramValueStr != null &&
            mHighestHistogramValue != null &&
            mLowestHistogramValue != null
        ) {
            drawHistogram(canvas)
        }
        // The dual trace replaces the separate high and low curves, and draws its own readings
        if (mDualValues == null && mHighestPolylineValue != null && mLowestPolylineValue != null) {
            if (mHighPolylineValues != null && mHighPolylineValueStr != null) {
                drawHighPolyLine(canvas)
            }
            if (mLowPolylineValues != null && mLowPolylineValueStr != null) {
                drawLowPolyline(canvas)
            }
        }
        // shiroikuma fork: rain sits over the fill but UNDER the readings, so a wet night never
        // buries the figures.
        if (mHighestPrecipitationValue != null) {
            if (mPrecipitationBars != null) drawPrecipitationBars(canvas) else drawPrecipitationColumns(canvas)
        }
        if (mDualValues != null) {
            drawDualReadings(canvas)
        }
        // Last, so the divider and the now marker sit over the fill rather than under it
        drawChromeMarkers(canvas)
    }

    private fun drawTimeLine(canvas: Canvas) {
        mPaint.apply {
            // shiroikuma fork: the paint outlives the draw, and the polylines may now leave a
            // gradient on it, so clear it rather than inherit last frame's
            shader = null
            style = Paint.Style.STROKE
            strokeWidth = mChartLineWidth.toFloat()
            color = mLineColors[2]
        }
        canvas.drawLine(
            measuredWidth / 2f,
            marginTop.toFloat(),
            measuredWidth / 2f,
            (measuredHeight - marginBottom).toFloat(),
            mPaint
        )
    }

    /**
     * shiroikuma fork: build this item's slice of a polyline as a SMOOTH curve.
     *
     * Each item draws three points — the midpoint it shares with the previous item, its own value,
     * and the midpoint it shares with the next — so consecutive items join up into one line. A
     * quadratic through those three replaces upstream's two straight segments; its control point is
     * lifted to `2*y1 - (y0+y2)/2` so the curve passes exactly THROUGH the item's own value rather
     * than being pulled off it, which would misplace the reading printed alongside.
     *
     * Edge items, with a neighbour missing, keep a straight half-segment: two points cannot bend.
     */
    private fun buildPolylinePath(values: Array<Float?>, ys: IntArray, fillToBottom: Boolean) {
        val right = measuredWidth.toFloat()
        val middle = (measuredWidth / 2.0).toFloat()
        val hasLeft = values[0] != null
        val hasRight = values[2] != null
        val startX = if (hasLeft) 0f else middle
        val endX = if (hasRight) right else middle

        mPath.reset()
        mPath.moveTo(getRTLCompactX(startX), (if (hasLeft) ys[0] else ys[1]).toFloat())
        if (hasLeft && hasRight) {
            mPath.quadTo(
                getRTLCompactX(middle),
                2f * ys[1] - (ys[0] + ys[2]) / 2f,
                getRTLCompactX(right),
                ys[2].toFloat()
            )
        } else if (hasRight) {
            mPath.lineTo(getRTLCompactX(right), ys[2].toFloat())
        } else {
            mPath.lineTo(getRTLCompactX(middle), ys[1].toFloat())
        }

        if (fillToBottom) {
            val floor = (measuredHeight - marginBottom).toFloat()
            mPath.lineTo(getRTLCompactX(endX), floor)
            mPath.lineTo(getRTLCompactX(startX), floor)
            mPath.close()
        }
    }

    private fun drawHighPolyLine(canvas: Canvas) {
        val values = mHighPolylineValues ?: return

        // shadow — skipped under a solid fill, which already owns everything below the curve
        if (!mSolidFill) {
            mPaint.apply {
                color = Color.BLACK
                shader = mShaderWrapper.shader
                style = Paint.Style.FILL
            }
            buildPolylinePath(values, mHighPolylineY, fillToBottom = true)
            canvas.drawPath(mPath, mPaint)
        }

        // line.
        mPaint.apply {
            shader = mPolylineShader
            style = Paint.Style.STROKE
            strokeWidth = mPolylineWidth.toFloat()
            color = mLineColors[0]
        }
        buildPolylinePath(values, mHighPolylineY, fillToBottom = false)
        canvas.drawPath(mPath, mPaint)

        // text.
        mPaint.apply {
            // shiroikuma fork: the reading takes the curve's own colour, brightened so the cold
            // and hot ends of the scale stay legible on black. Explicitly shader-free: the stroke
            // above leaves a gradient on this paint, which would otherwise colour the glyphs by
            // where they happen to sit rather than by what they read.
            shader = null
            textSize = mPolylineTextSize.toFloat()
        }
        val highText = mHighPolylineValueStr ?: ""
        drawReading(
            canvas,
            highText,
            getRTLCompactX((measuredWidth / 2.0).toFloat()),
            // shiroikuma fork: above the curve's highest point ACROSS THE PLATE, not just at the
            // column's centre — on a falling stretch the two are not the same, and the difference
            // is the plate corner clipping the line.
            curveExtremeAcross(
                mHighPolylineY,
                mPaint.measureText(highText) / 2f + mTextMargin * 2f,
                wantTop = true
            ),
            above = true,
            labelColor(values[1], mHighTextColor)
        )
    }

    private fun drawLowPolyline(canvas: Canvas) {
        val values = mLowPolylineValues ?: return

        mPaint.apply {
            shader = mPolylineShader
            style = Paint.Style.STROKE
            strokeWidth = mPolylineWidth.toFloat()
            color = mLineColors[1]
        }
        buildPolylinePath(values, mLowPolylineY, fillToBottom = false)
        canvas.drawPath(mPath, mPaint)

        // text.
        mPaint.apply {
            shader = null
            textSize = mPolylineTextSize.toFloat()
        }
        val lowText = mLowPolylineValueStr ?: ""
        drawReading(
            canvas,
            lowText,
            getRTLCompactX((measuredWidth / 2.0).toFloat()),
            curveExtremeAcross(
                mLowPolylineY,
                mPaint.measureText(lowText) / 2f + mTextMargin * 2f,
                wantTop = true
            ),
            above = true,
            labelColor(values[1], mLowTextColor)
        )
    }

    private fun drawHistogram(canvas: Canvas) {
        assert(mHistogramValueStr != null)
        mPaint.apply {
            color = mLineColors[1]
            alpha = (255 * mHistogramAlpha).toInt()
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            RectF(
                (measuredWidth / 2.0 - mHistogramWidth).toFloat(),
                mHistogramY.toFloat(),
                (measuredWidth / 2.0 + mHistogramWidth).toFloat(),
                (measuredHeight - marginBottom).toFloat()
            ),
            mHistogramWidth.toFloat(),
            mHistogramWidth.toFloat(),
            mPaint
        )
        mPaint.apply {
            color = mHistogramTextColor
            alpha = 255
            textAlign = Paint.Align.CENTER
            textSize = mHistogramTextSize.toFloat()
        }
        canvas.drawText(
            mHistogramValueStr ?: "",
            (measuredWidth / 2.0).toFloat(),
            // shiroikuma fork: no longer reserves a polyline-text height below the chart — the low
            // reading moved above its curve, so nothing else occupies the bottom margin now.
            measuredHeight - marginBottom - mPaint.fontMetrics.top + mTextMargin,
            mPaint
        )
        mPaint.alpha = 255
    }

    // control.
    fun setData(
        @Size(3) highPolylineValues: Array<Float?>?,
        @Size(3) lowPolylineValues: Array<Float?>?,
        highPolylineValueStr: String?,
        lowPolylineValueStr: String?,
        highestPolylineValue: Float?,
        lowestPolylineValue: Float?,
        histogramValue: Float?,
        histogramValueStr: String?,
        highestHistogramValue: Float?,
        lowestHistogramValue: Float?,
    ) {
        mHighPolylineValues = highPolylineValues
        mLowPolylineValues = lowPolylineValues
        mHighPolylineValueStr = highPolylineValueStr
        mLowPolylineValueStr = lowPolylineValueStr
        mHighestPolylineValue = highestPolylineValue
        mLowestPolylineValue = lowestPolylineValue
        mHistogramValue = histogramValue
        mHistogramValueStr = histogramValueStr
        mHighestHistogramValue = highestHistogramValue
        mLowestHistogramValue = lowestHistogramValue
        invalidate()
    }

    fun setLineColors(
        @ColorInt colorHigh: Int,
        @ColorInt colorLow: Int,
        @ColorInt colorSubLine: Int,
    ) {
        mLineColors[0] = colorHigh
        mLineColors[1] = colorLow
        mLineColors[2] = colorSubLine
        invalidate()
    }

    fun setShadowColors(
        @ColorInt colorHigh: Int,
        @ColorInt colorLow: Int,
        lightTheme: Boolean,
    ) {
        mShadowColors[0] = if (lightTheme) {
            ColorUtils.setAlphaComponent(colorHigh, (255 * SHADOW_ALPHA_FACTOR_LIGHT).toInt())
        } else {
            ColorUtils.setAlphaComponent(colorLow, (255 * SHADOW_ALPHA_FACTOR_DARK).toInt())
        }
        mShadowColors[1] = Color.TRANSPARENT
        ensureShader(lightTheme)
        invalidate()
    }

    fun setTextColors(
        @ColorInt highTextColor: Int,
        @ColorInt lowTextColor: Int,
        @ColorInt histogramTextColor: Int,
    ) {
        mHighTextColor = highTextColor
        mLowTextColor = lowTextColor
        // shiroikuma fork: upstream's 20 % black was a no-op against a dark card. At this strength
        // it is a real halo, which is what lets a coloured reading sit on top of its own wash.
        mTextShadowColor = Color.argb((255 * 0.85).toInt(), 0, 0, 0)
        mHistogramTextColor = histogramTextColor
        invalidate()
    }

    fun setHistogramAlpha(
        @FloatRange(from = 0.0, to = 1.0) histogramAlpha: Float,
    ) {
        mHistogramAlpha = histogramAlpha
        invalidate()
    }

    /**
     * shiroikuma fork: paint the polylines with a vertical gradient instead of a flat colour.
     *
     * @param stops (value, colour) pairs in the polyline's OWN domain — deci-Celsius for the
     *   temperature charts — ordered highest value first. Null restores the flat colours.
     *
     * The gradient is keyed to the chart's shared high/low range rather than to this item's own
     * values, which is what makes the ramp continuous across the whole scrolling chart: every
     * item view maps the same value to the same y, so it maps it to the same colour too.
     */
    fun setPolylineTextSizeDip(sizeDip: Float) {
        val size = context.dpToPx(sizeDip).toInt()
        if (size != mPolylineTextSize) {
            mPolylineTextSize = size
            invalidate()
        }
    }

    fun setPolylineGradientStops(stops: List<Pair<Float, Int>>?) {
        mPolylineGradientStops = stops
        mPolylineShaderKey = null
        invalidate()
    }

    /**
     * shiroikuma fork: the daily chart's rain — one bar per hour across this day's column, plus the
     * day's figure printed small at the foot.
     */
    fun setPrecipitationBars(
        values: FloatArray?,
        label: String?,
        highestValue: Float?,
        @ColorInt color: Int,
    ) {
        mPrecipitationBars = values
        mPrecipitationLabel = label
        mHighestPrecipitationValue = highestValue
        mPrecipitationColor = color
        invalidate()
    }

    /**
     * shiroikuma fork: the hourly chart's rain — this item's slice of a band drawn as many narrow
     * columns tiling edge to edge, interpolated across the item so the silhouette flows rather than
     * stepping from one hour to the next.
     *
     * @param values three points — the midpoint shared with the previous item, this item's own
     *   reading, and the midpoint shared with the next — exactly like the polyline
     * @param highestValue what counts as a full-height column (100 for a probability percentage);
     *   null switches the band off entirely and gives its height back to the chart
     */
    fun setPrecipitationColumns(
        @Size(3) values: Array<Float?>?,
        highestValue: Float?,
        @ColorInt color: Int,
    ) {
        mPrecipitationValues = values
        mHighestPrecipitationValue = highestValue
        mPrecipitationColor = color
        invalidate()
    }

    private fun ensurePolylineShader() {
        val stops = mPolylineGradientStops
        val highest = mHighestPolylineValue
        val lowest = mLowestPolylineValue
        if (stops.isNullOrEmpty() || highest == null || lowest == null || highest <= lowest) {
            mPolylineShader = null
            mPolylineShaderKey = null
            return
        }

        val key = "$measuredHeight/$highest/$lowest/${stops.hashCode()}"
        if (key == mPolylineShaderKey) return

        val topY = marginTop.toFloat()
        val bottomY = (measuredHeight - marginBottom).toFloat()
        // Position 0 sits at the top of the chart, which is the HIGHEST value
        val positionOf = { value: Float -> ((highest - value) / (highest - lowest)).coerceIn(0f, 1f) }

        val colors = mutableListOf<Int>()
        val positions = mutableListOf<Float>()
        // Both ends are interpolated rather than clamped, so a range narrower than the gap between
        // two stops still gets the right colour instead of a single flat one.
        colors.add(colorFromStops(stops, highest))
        positions.add(0f)
        stops.filter { it.first < highest && it.first > lowest }
            .sortedByDescending { it.first }
            .forEach { (value, color) ->
                colors.add(color)
                positions.add(positionOf(value))
            }
        colors.add(colorFromStops(stops, lowest))
        positions.add(1f)

        mPolylineShader = LinearGradient(
            0f,
            topY,
            0f,
            bottomY,
            colors.toIntArray(),
            positions.toFloatArray(),
            Shader.TileMode.CLAMP
        )
        mPolylineShaderKey = key
    }

    /**
     * shiroikuma fork: how high the curve climbs across the width a plate will occupy.
     *
     * Anchoring a reading to the curve's height at the column's CENTRE is not enough: on a falling
     * stretch the line is higher at the plate's left edge than at the middle, so the plate's corner
     * bites into it. Sampling the span the plate actually covers and taking its extreme is what
     * clears the line whatever the slope.
     *
     * @param ys the three y coordinates of this item's slice
     * @param halfWidth half the plate's width, in pixels
     * @param wantTop true for the highest point on screen (smallest y), false for the lowest
     */
    private fun curveExtremeAcross(ys: IntArray, halfWidth: Float, wantTop: Boolean): Float {
        val width = measuredWidth.toFloat()
        if (width <= 0f) return ys[1].toFloat()
        val control = 2f * ys[1] - (ys[0] + ys[2]) / 2f
        val centre = width / 2f
        var extreme = ys[1].toFloat()
        var x = centre - halfWidth
        while (x <= centre + halfWidth) {
            val t = (x / width).coerceIn(0f, 1f)
            val inverse = 1f - t
            val y = inverse * inverse * ys[0] + 2f * inverse * t * control + t * t * ys[2]
            extreme = if (wantTop) min(extreme, y) else max(extreme, y)
            x += mFillStripWidth
        }
        return extreme
    }

    /**
     * shiroikuma fork: a reading, on a plate.
     *
     * A shadow alone was not enough where a reading sits on its own fill — a yellow-green numeral
     * over a yellow-green band all but vanished. A dark rounded plate behind it separates the two
     * whatever the fill happens to be doing underneath.
     */
    private fun drawReading(
        canvas: Canvas,
        text: String,
        centreX: Float,
        anchorY: Float,
        above: Boolean,
        @ColorInt color: Int,
    ) {
        mPaint.apply {
            shader = null
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            textSize = mPolylineTextSize.toFloat()
        }
        val half = mPaint.measureText(text) / 2f + mTextMargin * 2f
        val metrics = mPaint.fontMetrics
        var baseline = if (above) {
            anchorY - metrics.bottom - mTextMargin * READING_CLEARANCE
        } else {
            anchorY - metrics.top + mTextMargin * READING_CLEARANCE
        }
        // shiroikuma fork: where the curve has run to the top of the drawable there is no room
        // above it, so the plate tucks under the top edge instead of being drawn off the card.
        if (baseline + metrics.top - mTextMargin < 0f) {
            baseline = mTextMargin * 2f - metrics.top
        }
        mPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, READING_PLATE_ALPHA)
        canvas.drawRoundRect(
            RectF(
                centreX - half,
                baseline + metrics.top - mTextMargin,
                centreX + half,
                baseline + metrics.bottom + mTextMargin
            ),
            mTextMargin * 2f,
            mTextMargin * 2f,
            mPaint
        )
        mPaint.color = color
        canvas.drawText(text, centreX, baseline, mPaint)
    }

    /**
     * shiroikuma fork: what colour to write a reading in.
     *
     * The curve's own colour, so the number matches the line it labels, but lifted to a minimum
     * lightness first — the hot end of the temperature scale bottoms out near #470E00, which is all
     * but invisible against a black card. Raising lightness rather than blending towards white
     * keeps the hue, so a hot reading still reads as red.
     *
     * Falls back to [fallback] whenever no gradient is set, which is every chart but temperature.
     */
    @ColorInt
    private fun labelColor(value: Float?, @ColorInt fallback: Int): Int {
        val stops = mPolylineGradientStops
        if (stops.isNullOrEmpty() || value == null) return fallback

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(colorFromStops(stops, value), hsl)
        hsl[2] = hsl[2].coerceAtLeast(LABEL_MIN_LIGHTNESS)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun colorFromStops(stops: List<Pair<Float, Int>>, value: Float): Int {
        stops.first().let { if (value >= it.first) return it.second }
        stops.last().let { if (value <= it.first) return it.second }
        for (i in 0..<stops.lastIndex) {
            val (highValue, highColor) = stops[i]
            val (lowValue, lowColor) = stops[i + 1]
            if (value in lowValue..highValue) {
                return ColorUtils.blendARGB(highColor, lowColor, (highValue - value) / (highValue - lowValue))
            }
        }
        return stops.last().second
    }

    /**
     * shiroikuma fork: the meteogram look — everything under the curve filled with the colour of
     * the temperature AT THAT POINT, so the day's shape reads as a band of colour rather than a
     * thin line. Each strip is one flat colour: the fill varies left to right, not top to bottom,
     * which is what stops it looking like a gradient wash of temperatures that never occurred.
     *
     * @param banded shade this item's column, so alternating hours are legible without gridlines
     * @param dayDivider draw a divider down this item's leading edge — midnight
     * @param isNow mark this item with the dashed "now" line
     */
    fun setChartChrome(
        solidFill: Boolean,
        banded: Boolean,
        dayDivider: Boolean,
        isNow: Boolean,
        isHistory: Boolean = false,
    ) {
        mChromeEnabled = true
        mSolidFill = solidFill
        mHourBanded = banded
        mDayDivider = dayDivider
        mIsNow = isNow
        mIsHistory = isHistory
        invalidate()
    }

    /**
     * shiroikuma fork: plot this day as one high-then-low trace rather than two separate curves.
     *
     * @param values four knots at x = 0, ¼, ¾ and 1 — the boundary with the previous day, the
     *   day's high, the night's low, and the boundary with the next day
     */
    fun setDualPolylineData(
        @Size(4) values: Array<Float?>?,
        highValueStr: String?,
        lowValueStr: String?,
        highestValue: Float?,
        lowestValue: Float?,
    ) {
        mDualValues = values
        mHighPolylineValueStr = highValueStr
        mLowPolylineValueStr = lowValueStr
        mHighestPolylineValue = highestValue
        mLowestPolylineValue = lowestValue
        invalidate()
    }

    /**
     * The trace between the knots. Eased rather than straight, so each day's high and each night's
     * low round over into a peak and a trough instead of meeting at a spike.
     */
    private fun dualValueAt(values: Array<Float?>, t: Float): Float? {
        val a = values[0] ?: values[1] ?: return null
        val b = values[1] ?: return null
        val c = values[2] ?: b
        val d = values[3] ?: c
        fun ease(u: Float) = u * u * (3f - 2f * u)
        return when {
            t <= DUAL_HIGH_X -> a + (b - a) * ease((t / DUAL_HIGH_X).coerceIn(0f, 1f))
            t <= DUAL_LOW_X ->
                b + (c - b) * ease(((t - DUAL_HIGH_X) / (DUAL_LOW_X - DUAL_HIGH_X)).coerceIn(0f, 1f))
            else -> c + (d - c) * ease(((t - DUAL_LOW_X) / (1f - DUAL_LOW_X)).coerceIn(0f, 1f))
        }
    }

    private fun drawDualPolyline(canvas: Canvas) {
        val values = mDualValues ?: return
        val highest = mHighestPolylineValue ?: return
        val lowest = mLowestPolylineValue ?: return
        if (highest <= lowest) return
        val stops = mPolylineGradientStops
        val canvasHeight = (measuredHeight - marginTop - marginBottom).toFloat()
        val floor = (measuredHeight - marginBottom).toFloat()
        val width = measuredWidth.toFloat()
        val step = mFillStripWidth.toFloat()

        fun yAt(t: Float): Float? = dualValueAt(values, t)
            ?.let { computeSingleCoordinate(canvasHeight, it, highest, lowest).toFloat() }

        // the fill, one flat colour per strip
        if (mSolidFill && stops != null) {
            mPaint.apply {
                shader = null
                style = Paint.Style.FILL
            }
            var x = 0f
            while (x < width) {
                val next = (x + step).coerceAtMost(width)
                val t = ((x + next) / 2f / width).coerceIn(0f, 1f)
                val value = dualValueAt(values, t)
                val y = value?.let { computeSingleCoordinate(canvasHeight, it, highest, lowest).toFloat() }
                if (value != null && y != null) {
                    mPaint.color = colorFromStops(stops, value)
                    val a = getRTLCompactX(x)
                    val b = getRTLCompactX(next)
                    canvas.drawRect(min(a, b), y, max(a, b), floor, mPaint)
                }
                x = next
            }
        }

        // the trace itself
        mPaint.apply {
            shader = mPolylineShader
            style = Paint.Style.STROKE
            strokeWidth = mPolylineWidth.toFloat()
            color = mLineColors[0]
        }
        mPath.reset()
        var started = false
        var x = 0f
        while (x <= width) {
            val y = yAt((x / width).coerceIn(0f, 1f))
            if (y != null) {
                if (started) mPath.lineTo(getRTLCompactX(x), y) else mPath.moveTo(getRTLCompactX(x), y)
                started = true
            }
            x += step
        }
        if (started) canvas.drawPath(mPath, mPaint)

        // the two readings, each above its own knot
    }

    /**
     * shiroikuma fork: the day's high reads ABOVE its peak and the night's low BELOW its trough, so
     * neither plate ever sits on the trace — the whole rise and fall stays visible.
     *
     * Drawn separately from the trace, and after the rain, because the night's reading lives in the
     * band the rain occupies: painted before it, the bars would bury exactly the figures that
     * matter most on a wet night.
     */
    private fun drawDualReadings(canvas: Canvas) {
        val values = mDualValues ?: return
        val highest = mHighestPolylineValue ?: return
        val lowest = mLowestPolylineValue ?: return
        if (highest <= lowest) return
        val canvasHeight = (measuredHeight - marginTop - marginBottom).toFloat()
        val width = measuredWidth.toFloat()

        listOf(
            Triple(DUAL_HIGH_X, mHighPolylineValueStr, values[1]) to true,
            Triple(DUAL_LOW_X, mLowPolylineValueStr, values[2]) to false
        ).forEach { (knot, above) ->
            val (t, text, value) = knot
            if (text.isNullOrEmpty() || value == null) return@forEach
            mPaint.apply {
                shader = null
                textSize = mPolylineTextSize.toFloat()
            }
            // The trace's extreme across the plate's own width, so neither plate clips the line
            val half = mPaint.measureText(text) / 2f + mTextMargin * 2f
            val centre = width * t
            var anchor = computeSingleCoordinate(canvasHeight, value, highest, lowest).toFloat()
            var x = centre - half
            while (x <= centre + half) {
                dualValueAt(values, (x / width).coerceIn(0f, 1f))?.let {
                    val sampled = computeSingleCoordinate(canvasHeight, it, highest, lowest).toFloat()
                    anchor = if (above) min(anchor, sampled) else max(anchor, sampled)
                }
                x += mFillStripWidth
            }
            drawReading(
                canvas,
                text,
                getRTLCompactX(centre),
                anchor,
                above,
                labelColor(value, mHighTextColor)
            )
        }
    }

    private fun drawHourBand(canvas: Canvas) {
        mPaint.apply {
            shader = null
            style = Paint.Style.FILL
            color = ColorUtils.setAlphaComponent(Color.WHITE, HOUR_BAND_ALPHA)
        }
        // shiroikuma fork: the PLOTTING area only. Running the band the full height of the view
        // carried it below the curve's baseline, which read as dead space under the graph.
        canvas.drawRect(
            0f,
            marginTop.toFloat(),
            measuredWidth.toFloat(),
            (measuredHeight - marginBottom).toFloat(),
            mPaint
        )
    }

    private fun drawSolidFill(canvas: Canvas) {
        val values = mHighPolylineValues ?: return
        val stops = mPolylineGradientStops ?: return
        val middle = values[1] ?: return
        val highest = mHighestPolylineValue ?: return
        val lowest = mLowestPolylineValue ?: return
        if (highest <= lowest) return

        val left = values[0] ?: middle
        val right = values[2] ?: middle
        val control = 2f * middle - (left + right) / 2f
        val canvasHeight = (measuredHeight - marginTop - marginBottom).toFloat()
        val floor = (measuredHeight - marginBottom).toFloat()
        val width = measuredWidth.toFloat()
        val step = mFillStripWidth.toFloat()

        mPaint.apply {
            shader = null
            style = Paint.Style.FILL
        }

        var x = 0f
        while (x < width) {
            val next = (x + step).coerceAtMost(width)
            val t = ((x + next) / 2f / width).coerceIn(0f, 1f)
            val inverse = 1f - t
            val value = inverse * inverse * left + 2f * inverse * t * control + t * t * right
            val y = computeSingleCoordinate(canvasHeight, value, highest, lowest).toFloat()
            mPaint.color = colorFromStops(stops, value)
            val a = getRTLCompactX(x)
            val b = getRTLCompactX(next)
            canvas.drawRect(min(a, b), y, max(a, b), floor, mPaint)
            x = next
        }
    }

    private fun drawChromeMarkers(canvas: Canvas) {
        if (mDayDivider) {
            mPaint.apply {
                shader = null
                style = Paint.Style.STROKE
                strokeWidth = mChartLineWidth.toFloat()
                color = ColorUtils.setAlphaComponent(Color.WHITE, DAY_DIVIDER_ALPHA)
            }
            val edge = getRTLCompactX(0f)
            canvas.drawLine(edge, marginTop.toFloat(), edge, (measuredHeight - marginBottom).toFloat(), mPaint)
        }
        if (mIsNow) {
            mPaint.apply {
                shader = null
                style = Paint.Style.STROKE
                strokeWidth = mChartLineWidth * 2f
                color = ColorUtils.setAlphaComponent(Color.WHITE, NOW_MARKER_ALPHA)
            }
            val x = getRTLCompactX((measuredWidth / 2.0).toFloat())
            val floor = (measuredHeight - marginBottom).toFloat()
            var y = marginTop.toFloat()
            while (y < floor) {
                canvas.drawLine(x, y, x, (y + mDashLength).coerceAtMost(floor), mPaint)
                y += mDashLength * 2f
            }
        }
    }

    /** shiroikuma fork: one thin bar per hour of the day, plus the day's figure at the foot. */
    private fun drawPrecipitationBars(canvas: Canvas) {
        val highest = mHighestPrecipitationValue ?: return
        val bars = mPrecipitationBars ?: return
        if (highest <= 0f || bars.isEmpty()) return

        val floor = (measuredHeight - marginBottom).toFloat()
        val band = (measuredHeight - marginTop - marginBottom) * PRECIPITATION_BAND_FRACTION
        val width = measuredWidth.toFloat()
        val step = width / bars.size

        mPaint.apply {
            shader = null
            style = Paint.Style.FILL
            color = mPrecipitationColor
        }
        bars.forEachIndexed { index, value ->
            val height = band * (value / highest).coerceIn(0f, 1f)
            if (height > 0f) {
                val a = getRTLCompactX(step * index)
                val b = getRTLCompactX(step * (index + 1))
                canvas.drawRect(min(a, b), floor - height, max(a, b), floor, mPaint)
            }
        }

        mPrecipitationLabel?.takeIf { it.isNotEmpty() }?.let { label ->
            mPaint.apply {
                color = mHistogramTextColor
                textAlign = Paint.Align.CENTER
                textSize = mHistogramTextSize.toFloat()
            }
            canvas.drawText(
                label,
                (measuredWidth / 2.0).toFloat(),
                measuredHeight - marginBottom + mPaint.fontMetrics.let { -it.top } + mTextMargin,
                mPaint
            )
        }
    }

    private fun drawPrecipitationColumns(canvas: Canvas) {
        val highest = mHighestPrecipitationValue ?: return
        val values = mPrecipitationValues ?: return
        val middle = values[1] ?: return
        if (highest <= 0f) return

        // A missing neighbour flattens to this item's own reading, so the ends of the chart taper
        // off rather than dropping to nothing.
        val left = values[0] ?: middle
        val right = values[2] ?: middle
        // The control value that makes the quadratic pass through this item's own reading — the
        // same construction the temperature curve uses, so rain and temperature bend alike.
        val control = 2f * middle - (left + right) / 2f

        // shiroikuma fork: rain OVERLAYS the foot of the temperature area rather than taking a
        // zone of its own. A separate zone halved the chart, so the temperature had only the top
        // half to live in — and a chart with no rain ended up a different height from one with.
        val floor = (measuredHeight - marginBottom).toFloat()
        val band = (measuredHeight - marginTop - marginBottom) * PRECIPITATION_BAND_FRACTION
        val width = measuredWidth.toFloat()
        val step = mPrecipitationColumnWidth.toFloat().coerceAtLeast(1f)

        mPaint.apply {
            shader = null
            style = Paint.Style.FILL
            color = mPrecipitationColor
        }

        var x = 0f
        while (x < width) {
            val next = (x + step).coerceAtMost(width)
            // Sample at the slice's centre so a column stands for the rain across its own width
            val t = ((x + next) / 2f / width).coerceIn(0f, 1f)
            val inverse = 1f - t
            val value = inverse * inverse * left + 2f * inverse * t * control + t * t * right
            val height = band * (value / highest).coerceIn(0f, 1f)
            if (height > 0f) {
                // getRTLCompactX mirrors, so the two edges swap over in a right-to-left layout
                val a = getRTLCompactX(x)
                val b = getRTLCompactX(next)
                canvas.drawRect(min(a, b), floor - height, max(a, b), floor, mPaint)
            }
            x = next
        }
    }

    private fun ensureShader(lightTheme: Boolean) {
        if (mShaderWrapper.isDifferent(measuredWidth, measuredHeight, lightTheme, mShadowColors)) {
            mShaderWrapper.setShader(
                LinearGradient(
                    0f,
                    marginTop.toFloat(),
                    0f,
                    (measuredHeight - marginBottom).toFloat(),
                    mShadowColors[0],
                    mShadowColors[1],
                    Shader.TileMode.CLAMP
                ),
                measuredWidth,
                measuredHeight,
                lightTheme,
                mShadowColors
            )
        }
    }

    private fun computeCoordinates() {
        val canvasHeight = (measuredHeight - marginTop - marginBottom).toFloat()
        if (mHighestPolylineValue != null && mLowestPolylineValue != null) {
            mHighPolylineValues?.let {
                for (i in it.indices) {
                    if (it[i] == null) {
                        mHighPolylineY[i] = 0
                    } else {
                        mHighPolylineY[i] = computeSingleCoordinate(
                            canvasHeight,
                            it[i]!!,
                            mHighestPolylineValue!!,
                            mLowestPolylineValue!!
                        )
                    }
                }
            }
            mLowPolylineValues?.let {
                for (i in it.indices) {
                    if (it[i] == null) {
                        mLowPolylineY[i] = 0
                    } else {
                        mLowPolylineY[i] = computeSingleCoordinate(
                            canvasHeight,
                            it[i]!!,
                            mHighestPolylineValue!!,
                            mLowestPolylineValue!!
                        )
                    }
                }
            }
        }
        if (mHistogramValue != null && mHighestHistogramValue != null && mLowestHistogramValue != null) {
            mHistogramY = computeSingleCoordinate(
                canvasHeight,
                mHistogramValue!!,
                mHighestHistogramValue!!,
                mLowestHistogramValue!!
            )
        }
    }

    private fun computeSingleCoordinate(
        canvasHeight: Float,
        value: Float,
        max: Float,
        min: Float,
    ): Int {
        // shiroikuma fork: clamped to the DRAWABLE, not to the plotting area. The hourly chart
        // scales to its opening window, so a later hour can exceed that range — it should fill to
        // the very top of the chart view rather than stopping short at marginTop and leaving a gap
        // under the icons. Values inside the range never reach this bound.
        return (measuredHeight - marginBottom - (canvasHeight * (value - min) / (max - min)))
            .coerceIn(0f, (measuredHeight - marginBottom).toFloat())
            .toInt()
    }

    private fun getRTLCompactX(x: Float): Float {
        return if (layoutDirection == LAYOUT_DIRECTION_RTL) (measuredWidth - x) else x
    }

    companion object {
        // shiroikuma fork: upstream's 24dp was sized for a 14dp reading; a 30dp one needs about
        // 38dp of clearance above its point (ascent + descent + margin), and BOTH readings now sit
        // above their curves. The bottom therefore only has to hold the histogram percentage, so it
        // is far smaller than the top — anything more is dead space at the foot of every card.
        private const val MARGIN_TOP_DIP = 40f
        private const val MARGIN_BOTTOM_DIP = 24f
        private const val POLYLINE_SIZE_DIP = 5f

        // shiroikuma fork: the readings are the headline figure on the card, so they are set large
        // — more than twice upstream's 14dp. MARGIN_TOP/BOTTOM above are sized to match; raising
        // this without raising those clips the topmost and bottommost readings.
        private const val POLYLINE_TEXT_SIZE_DIP = 30f

        // How light a reading is allowed to get, and the dark halo that separates it from the
        // curve and the wash underneath.
        private const val LABEL_MIN_LIGHTNESS = 0.62f

        /** How opaque the plate behind a reading is, and how far it clears the curve. */
        private const val READING_PLATE_ALPHA = 170
        private const val READING_CLEARANCE = 4f
        private const val LABEL_SHADOW_RADIUS_DIP = 4f
        private const val HISTOGRAM_WIDTH_DIP = 4.5f
        private const val HISTOGRAM_TEXT_SIZE_DIP = 12f
        private const val CHART_LINE_SIZE_DIP = 1f
        private const val TEXT_MARGIN_DIP = 2f

        // shiroikuma fork: the precipitation columns' zone at the foot of the chart, and the
        // columns themselves. The band is deliberately generous — at 28dp a column barely moved
        // between light drizzle and a downpour, which told the reader nothing.
        // How much of the plotting area a 100 % column reaches up into, overlaying the temperature
        private const val PRECIPITATION_BAND_FRACTION = 0.4f

        // Narrow enough that the columns read as a texture rather than as bars, and they tile
        // edge to edge — any gap would read as the rain stopping and starting again.
        private const val PRECIPITATION_COLUMN_WIDTH_DIP = 3f

        // How finely the solid fill is sliced, and the "now" marker's dash.
        private const val FILL_STRIP_WIDTH_DIP = 2f
        private const val DASH_LENGTH_DIP = 4f
        private const val HOUR_BAND_ALPHA = 42
        private const val DAY_DIVIDER_ALPHA = 130
        private const val NOW_MARKER_ALPHA = 220
        private const val HISTORY_DIM_ALPHA = 140

        // Where the day's high and the night's low sit across a day's column
        private const val DUAL_HIGH_X = 0.25f
        private const val DUAL_LOW_X = 0.75f
        private const val SHADOW_ALPHA_FACTOR_LIGHT = 0.15f
        private const val SHADOW_ALPHA_FACTOR_DARK = 0.3f
    }
}
