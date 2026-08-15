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
import androidx.core.graphics.withTranslation
import org.breezyweather.R
import org.breezyweather.common.extensions.dpToPx
import org.breezyweather.common.extensions.fontScaleToApply
import org.breezyweather.common.extensions.getTypefaceFromTextAppearance
import org.breezyweather.ui.common.widgets.trend.TrendRecyclerView
import org.breezyweather.ui.common.widgets.trend.chart.AbsChartItemView
import kotlin.math.roundToInt

/**
 * Hourly trend item view.
 */
class HourlyTrendItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : AbsTrendItemView(context, attrs, defStyleAttr, defStyleRes) {
    private var mChartItem: AbsChartItemView? = null
    private val mHourTextPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private var mHourText: String? = null
    private val mBandPaint = Paint().apply { isAntiAlias = false }
    private var mHourTextSize = 0f

    @IntDef(INVISIBLE, GONE)
    internal annotation class IconVisibility

    @IconVisibility
    private var mMissingIconVisibility: Int = GONE
    private var mIconDrawable: Drawable? = null

    @ColorInt
    private var mContentColor = 0

    private var mHourTextBaseLine = 0f
    private var mIconLeft = 0f
    private var mIconTop = 0f
    private var mTrendViewTop = 0f
    private val mIconSize: Int
    override var chartTop: Int = 0
        private set
    override var chartBottom: Int = 0
        private set

    init {
        setWillNotDraw(false)
        mHourTextPaint.apply {
            typeface = getContext().getTypefaceFromTextAppearance(R.style.title_text)
            textSize = getContext().resources.getDimensionPixelSize(R.dimen.title_text_size).toFloat()
            mHourTextSize = textSize
        }
        setTextColor(Color.BLACK)
        mIconSize = getContext().dpToPx(ICON_SIZE_DIP.toFloat()).toInt()
    }

    /**
     * shiroikuma fork: how many columns share the host's width — the opening window from the
     * settings page. Everything beyond it scrolls off to the right. Sizing by the TOTAL count
     * instead would shrink the columns as the forecast got longer.
     */
    var visibleColumns: Int = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val host = parent as? TrendRecyclerView
        val hostWidth = host?.measuredWidth ?: 0
        // shiroikuma fork: the pinch widens the column rather than scaling the canvas, so fewer
        // hours fit on the screen and the labels and icons keep their own size.
        val zoom = host?.columnScale ?: 1f
        val width = if (hostWidth > 0 && visibleColumns > 0) {
            (hostWidth / visibleColumns * zoom).roundToInt().coerceAtLeast(1)
        } else {
            context.resources
                .getDimensionPixelSize(R.dimen.hourly_trend_item_width)
                .times(context.fontScaleToApply)
                .times(zoom)
                .roundToInt()
        }
        val height = MeasureSpec.getSize(heightMeasureSpec)
        var y = 0f
        val textMargin = context.dpToPx(TEXT_MARGIN_DIP.toFloat())
        val iconMargin = context.dpToPx(ICON_MARGIN_DIP.toFloat())

        // hour text — two rows, the meridiem under the numeral
        val fontMetrics = mHourTextPaint.fontMetrics
        y += textMargin
        mHourTextBaseLine = y - fontMetrics.top
        y += fontMetrics.bottom - fontMetrics.top
        y += mHourTextSize * MERIDIEM_SCALE
        y += textMargin

        // hourly icon.
        if (mIconDrawable != null || mMissingIconVisibility == INVISIBLE) {
            y += iconMargin
            mIconLeft = (width - mIconSize) / 2f
            mIconTop = y
            y += mIconSize.toFloat()
            y += iconMargin
        }

        // margin bottom.
        val marginBottom = context.dpToPx(TrendRecyclerView.ITEM_MARGIN_BOTTOM_DIP.toFloat())

        // chartItem item view.
        mChartItem?.measure(
            MeasureSpec.makeMeasureSpec(
                width,
                MeasureSpec.EXACTLY
            ),
            MeasureSpec.makeMeasureSpec(
                (height - marginBottom - y).toInt(),
                MeasureSpec.EXACTLY
            )
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
     * shiroikuma fork: shade this column, and how far down. The band is drawn HERE rather than in
     * the chart view so it runs the full height of the item — past the hour label and the icon —
     * which is what makes the hour split legible without a rule through the chart.
     */
    var bandShaded: Boolean = false

    /** Knock this column back as past. Drawn after the children, so it covers the chart's fill. */
    var dimmed: Boolean = false

    /**
     * shiroikuma fork: midnight. Drawn down this column's leading edge, over the chart and the full
     * height of the item, in the same accent as the hour labels — a day boundary is worth more than
     * the hairline the chart used to give it.
     */
    var dayDivider: Boolean = false

    override fun onDraw(canvas: Canvas) {
        if (bandShaded) {
            mBandPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, BAND_ALPHA)
            canvas.drawRect(0f, 0f, measuredWidth.toFloat(), chartBottom.toFloat(), mBandPaint)
        }

        // hour text — the numeral on one line, the meridiem under it at 60 %, so a narrow column
        // holds "7 AM" without clipping either half.
        mHourText?.let { text ->
            mHourTextPaint.color = mContentColor
            val parts = text.split(' ', limit = 2)
            mHourTextPaint.textSize = mHourTextSize
            canvas.drawText(parts[0], measuredWidth / 2f, mHourTextBaseLine, mHourTextPaint)
            if (parts.size > 1) {
                mHourTextPaint.textSize = mHourTextSize * MERIDIEM_SCALE
                canvas.drawText(
                    parts[1],
                    measuredWidth / 2f,
                    mHourTextBaseLine + mHourTextSize * MERIDIEM_SCALE,
                    mHourTextPaint
                )
                mHourTextPaint.textSize = mHourTextSize
            }
        }

        // day icon.
        mIconDrawable?.let {
            canvas.withTranslation(mIconLeft, mIconTop) {
                it.draw(canvas)
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (dimmed) {
            mBandPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, DIM_ALPHA)
            canvas.drawRect(0f, 0f, measuredWidth.toFloat(), chartBottom.toFloat(), mBandPaint)
        }
        if (dayDivider) {
            mBandPaint.color = mContentColor
            canvas.drawRect(
                0f,
                0f,
                context.dpToPx(DAY_DIVIDER_WIDTH_DIP),
                chartBottom.toFloat(),
                mBandPaint
            )
        }
    }

    fun setHourText(hourText: String?) {
        mHourText = hourText
        invalidate()
    }

    fun setTextColor(@ColorInt contentColor: Int) {
        mContentColor = contentColor
        invalidate()
    }

    fun setIconDrawable(d: Drawable?, @IconVisibility missingIconVisibility: Int) {
        val nullDrawable = mIconDrawable == null
        mIconDrawable = d
        mMissingIconVisibility = missingIconVisibility
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

        // shiroikuma fork: the meridiem row's size relative to the numeral above it, and the
        // banding drawn across the whole column.
        private const val MERIDIEM_SCALE = 0.6f
        private const val BAND_ALPHA = 42
        private const val DIM_ALPHA = 140
        private const val DAY_DIVIDER_WIDTH_DIP = 3f

        /** How many columns fit the screen — 3 hours behind, now, and 8 ahead. */
        const val VISIBLE_COLUMNS = 12
    }
}
