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

package org.breezyweather.ui.common.widgets.trend.item

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.annotation.IntDef
import androidx.core.graphics.ColorUtils
import org.breezyweather.R
import org.breezyweather.common.extensions.dpToPx
import org.breezyweather.common.extensions.fontScaleToApply
import org.breezyweather.common.extensions.getTypefaceFromTextAppearance
import org.breezyweather.ui.common.widgets.trend.TrendRecyclerView
import org.breezyweather.ui.common.widgets.trend.chart.AbsChartItemView
import kotlin.math.roundToInt

/**
 * Daily trend item view.
 */
class DailyTrendItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : AbsTrendItemView(context, attrs, defStyleAttr, defStyleRes) {
    private var mChartItem: AbsChartItemView? = null
    private val mBandPaint = Paint().apply { isAntiAlias = false }
    private val mNowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    private val mNowDashLength: Float
    private val mWeekTextPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val mDateTextPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private var mWeekText: String? = null
    private var mDateText: String? = null

    @IntDef(INVISIBLE, GONE)
    internal annotation class IconVisibility

    @IconVisibility
    private var mMissingDayIconVisibility: Int = GONE
    private var mDayIconDrawable: Drawable? = null

    @IconVisibility
    private var mMissingNightIconVisibility: Int = GONE
    private var mNightIconDrawable: Drawable? = null

    @ColorInt
    private var mContentColor = 0

    @ColorInt
    private var mSubTitleColor = 0
    private var mWeekTextBaseLine = 0f
    private var mDateTextBaseLine = 0f
    private var mDayIconLeft = 0f
    private var mDayIconTop = 0f
    private var mTrendViewTop = 0f
    private var mNightIconLeft = 0f
    private var mNightIconTop = 0f
    private val mIconSize: Int
    override var chartTop: Int = 0
        private set
    override var chartBottom: Int = 0
        private set

    init {
        setWillNotDraw(false)
        mWeekTextPaint.apply {
            typeface = getContext().getTypefaceFromTextAppearance(R.style.title_text)
            textSize = getContext().resources.getDimensionPixelSize(R.dimen.title_text_size).toFloat()
        }
        mDateTextPaint.apply {
            typeface = getContext().getTypefaceFromTextAppearance(R.style.content_text)
            textSize = getContext().resources.getDimensionPixelSize(R.dimen.content_text_size).toFloat()
        }
        setTextColor(Color.BLACK, Color.GRAY)
        mIconSize = getContext().dpToPx(ICON_SIZE_DIP.toFloat()).toInt()
        mNowPaint.strokeWidth = getContext().dpToPx(NOW_MARKER_WIDTH_DIP)
        mNowDashLength = getContext().dpToPx(NOW_DASH_LENGTH_DIP).coerceAtLeast(1f)
    }

    /**
     * shiroikuma fork: how many columns share the host's width — the days the settings page asks
     * the daily graph to open with. A fixed dp width instead left the card showing whatever number
     * of days happened to fit the screen it was on.
     */
    var visibleColumns: Int = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val host = parent as? TrendRecyclerView
        val hostWidth = host?.measuredWidth ?: 0
        // shiroikuma fork: the pinch widens the column rather than scaling the canvas, so fewer
        // days fit on the screen and the labels and icons keep their own size.
        val zoom = host?.columnScale ?: 1f
        val width = if (hostWidth > 0 && visibleColumns > 0) {
            (hostWidth / visibleColumns * zoom).roundToInt().coerceAtLeast(1)
        } else {
            context.resources
                .getDimensionPixelSize(R.dimen.trend_item_width)
                .times(context.fontScaleToApply)
                .times(zoom)
                .roundToInt()
                .coerceAtLeast(1)
        }
        val height = MeasureSpec.getSize(heightMeasureSpec)
        var y = 0f
        val textMargin = context.dpToPx(TEXT_MARGIN_DIP.toFloat())
        val iconMargin = context.dpToPx(ICON_MARGIN_DIP.toFloat())

        // week text.
        var fontMetrics = mWeekTextPaint.fontMetrics
        y += textMargin
        mWeekTextBaseLine = y - fontMetrics.top
        y += fontMetrics.bottom - fontMetrics.top
        y += textMargin

        // date text.
        fontMetrics = mDateTextPaint.fontMetrics
        y += textMargin
        mDateTextBaseLine = y - fontMetrics.top
        y += fontMetrics.bottom - fontMetrics.top
        y += textMargin

        // day icon.
        if (mDayIconDrawable != null || mMissingDayIconVisibility == INVISIBLE) {
            y += iconMargin
            mDayIconLeft = (width - mIconSize) / 2f
            mDayIconTop = y
            y += mIconSize.toFloat()
            y += iconMargin
        }
        var consumedHeight: Float = y

        // margin bottom.
        val marginBottom = context.dpToPx(TrendRecyclerView.ITEM_MARGIN_BOTTOM_DIP.toFloat())
        consumedHeight += marginBottom

        // night icon.
        if (mNightIconDrawable != null || mMissingNightIconVisibility == INVISIBLE) {
            mNightIconLeft = (width - mIconSize) / 2f
            mNightIconTop = height - marginBottom - iconMargin - mIconSize
            consumedHeight += mIconSize + 2 * iconMargin
        }

        // chartItem item view.
        mChartItem?.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((height - consumedHeight).toInt(), MeasureSpec.EXACTLY)
        )
        mTrendViewTop = y
        chartTop = (mTrendViewTop + mChartItem!!.marginTop).toInt()
        chartBottom = (mTrendViewTop + mChartItem!!.measuredHeight - mChartItem!!.marginBottom).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        mChartItem?.layout(
            0,
            mTrendViewTop.toInt(),
            mChartItem!!.measuredWidth,
            mTrendViewTop.toInt() + mChartItem!!.measuredHeight
        )
    }

    /**
     * shiroikuma fork: shade this day's column across the WHOLE item — the day and date labels and
     * the icons above and below the chart included — so the day split is legible without a rule.
     */
    var bandShaded: Boolean = false

    /**
     * shiroikuma fork: where the current moment falls across this column, 0 at its leading edge and
     * 1 at its trailing one, or null for every column but the one we are actually in.
     *
     * Drawn as a dashed rule through the plotting area, exactly like the hourly card's "now" — the
     * daily trace is one continuous line across the week, and without it there is nothing to say how
     * far along today's rise or tonight's fall the week has already got.
     */
    var nowAt: Float? = null

    override fun onDraw(canvas: Canvas) {
        if (bandShaded) {
            mBandPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, BAND_ALPHA)
            canvas.drawRect(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat(), mBandPaint)
        }

        // week text.
        mWeekText?.let {
            mWeekTextPaint.color = mContentColor
            canvas.drawText(it, measuredWidth / 2f, mWeekTextBaseLine, mWeekTextPaint)
        }

        // date text.
        mDateText?.let {
            mDateTextPaint.color = mSubTitleColor
            canvas.drawText(it, measuredWidth / 2f, mDateTextBaseLine, mDateTextPaint)
        }
        var restoreCount: Int

        // day icon.
        mDayIconDrawable?.let {
            restoreCount = canvas.save()
            canvas.translate(mDayIconLeft, mDayIconTop)
            it.draw(canvas)
            canvas.restoreToCount(restoreCount)
        }

        // night icon.
        mNightIconDrawable?.let {
            restoreCount = canvas.save()
            canvas.translate(mNightIconLeft, mNightIconTop)
            it.draw(canvas)
            canvas.restoreToCount(restoreCount)
        }
    }

    /**
     * shiroikuma fork: the "now" rule, over the children rather than under them — the chart fills
     * everything below its curve, so a marker drawn in [onDraw] would be buried by it.
     */
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val at = nowAt?.coerceIn(0f, 1f) ?: return
        val floor = chartBottom.toFloat()
        if (floor <= chartTop) return

        mNowPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, NOW_MARKER_ALPHA)
        // Held half a stroke clear of both edges: a rule landing exactly on a column boundary is
        // otherwise clipped down its middle, and 06:00 lands there every morning
        val half = mNowPaint.strokeWidth / 2f
        val offset = (measuredWidth * at).coerceAtLeast(half).coerceAtMost(measuredWidth - half)
        val x = if (layoutDirection == LAYOUT_DIRECTION_RTL) measuredWidth - offset else offset
        var y = chartTop.toFloat()
        while (y < floor) {
            canvas.drawLine(x, y, x, (y + mNowDashLength).coerceAtMost(floor), mNowPaint)
            y += mNowDashLength * 2f
        }
    }

    fun setWeekText(weekText: String?) {
        mWeekText = weekText
        invalidate()
    }

    fun setDateText(dateText: String?) {
        mDateText = dateText
        invalidate()
    }

    fun setTextColor(@ColorInt contentColor: Int, @ColorInt subTitleColor: Int) {
        mContentColor = contentColor
        mSubTitleColor = subTitleColor
        invalidate()
    }

    fun setDayIconDrawable(d: Drawable?, @IconVisibility missingIconVisibility: Int) {
        val nullDrawable = mDayIconDrawable == null
        mDayIconDrawable = d
        mMissingDayIconVisibility = missingIconVisibility
        if (d != null) {
            d.setVisible(true, true)
            d.callback = this
            d.setBounds(0, 0, mIconSize, mIconSize)
        }
        if (nullDrawable != (d == null)) {
            requestLayout()
        } else {
            invalidate()
        }
    }

    fun setNightIconDrawable(d: Drawable?, @IconVisibility missingIconVisibility: Int) {
        val nullDrawable = mNightIconDrawable == null
        mNightIconDrawable = d
        mMissingNightIconVisibility = missingIconVisibility
        if (d != null) {
            d.setVisible(true, true)
            d.callback = this
            d.setBounds(0, 0, mIconSize, mIconSize)
        }
        if (nullDrawable != (d == null)) {
            requestLayout()
        } else {
            invalidate()
        }
    }

    override var chartItemView: AbsChartItemView?
        get() = mChartItem
        set(t) {
            mChartItem = t
            removeAllViews()
            addView(mChartItem)
            requestLayout()
        }

    companion object {
        private const val ICON_SIZE_DIP = 32
        private const val TEXT_MARGIN_DIP = 2
        private const val ICON_MARGIN_DIP = 8

        /** shiroikuma fork: the alternating day band, matching the hourly card's. */
        private const val BAND_ALPHA = 42

        // shiroikuma fork: the "now" rule, dashed exactly like the hourly chart's — same width,
        // same dash, same strength, so the two cards mark the moment the same way.
        private const val NOW_MARKER_WIDTH_DIP = 2f
        private const val NOW_DASH_LENGTH_DIP = 4f
        private const val NOW_MARKER_ALPHA = 220
    }
}
