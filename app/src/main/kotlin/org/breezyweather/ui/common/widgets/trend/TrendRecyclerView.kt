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

package org.breezyweather.ui.common.widgets.trend

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.annotation.ColorInt
import androidx.core.view.isNotEmpty
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.breezyweather.R
import org.breezyweather.common.extensions.dpToPx
import org.breezyweather.common.extensions.getTypefaceFromTextAppearance
import org.breezyweather.tenki.TenkiUiConfig
import org.breezyweather.tenki.TenkiViewTheme
import org.breezyweather.ui.common.widgets.trend.item.AbsTrendItemView
import org.breezyweather.ui.main.widgets.NestedHorizontalRecyclerView

/**
 * Trend recycler view.
 */
class TrendRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : NestedHorizontalRecyclerView(context, attrs, defStyle) {
    private val mPaint = Paint().apply {
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    @ColorInt
    private var mLineColor = 0
    private var mTextColor = 0
    private var mDrawingBoundaryTop: Int
    private var mDrawingBoundaryBottom: Int
    private var mKeyLineList: List<KeyLine>? = null
    private var mKeyLineVisibility = true
    private var mHighestData: Float? = null
    private var mLowestData: Float? = null
    private val mTextSize: Int
    private val mTextMargin: Int
    private val mLineWidth: Int

    class KeyLine(
        var value: Float,
        var contentLeft: String?,
        var contentRight: String?,
        var contentPosition: ContentPosition,
    ) {
        enum class ContentPosition {
            ABOVE_LINE,
            BELOW_LINE,
        }
    }

    /**
     * shiroikuma fork: which of the two zoom levels this chart pinches — the hourly and the daily
     * cards remember their own, since a day column and an hour column are nothing like the same
     * width to begin with. Set by the holder that builds the chart.
     */
    var zoomKind: ZoomKind = ZoomKind.HOURLY

    enum class ZoomKind { HOURLY, DAILY }

    /**
     * shiroikuma fork: how far the columns have been pinched apart, 1 being the width the settings
     * ask for. Read by the item views when they measure themselves.
     */
    val columnScale: Float
        get() = storedZoom / 100f

    private var storedZoom: Int
        get() = TenkiViewTheme.state(context).let {
            if (zoomKind == ZoomKind.HOURLY) it.hourlyColumnZoom else it.dailyColumnZoom
        }
        set(v) = TenkiViewTheme.state(context).let {
            if (zoomKind == ZoomKind.HOURLY) it.updateHourlyColumnZoom(v) else it.updateDailyColumnZoom(v)
        }

    /**
     * Pinch to widen or narrow the columns.
     *
     * The zoom is persisted rather than held here, so it survives the card being recycled — and it
     * is applied by re-binding rather than by scaling the canvas, which would blow the text and the
     * icons up with it instead of simply fitting fewer hours on the screen.
     */
    private val mScaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val next = (storedZoom * detector.scaleFactor)
                    .toInt()
                    .coerceIn(TenkiUiConfig.MINIMUM_COLUMN_ZOOM, TenkiUiConfig.MAXIMUM_COLUMN_ZOOM)
                if (next == storedZoom) return true
                storedZoom = next
                refreshColumns()
                onColumnZoomChanged?.invoke()
                return true
            }
        }
    )

    /**
     * shiroikuma fork: told that this chart's zoom level has moved.
     *
     * The level is one stored value for the whole card, so the chart being pinched is never the only
     * one measuring the wrong columns — the card puts its other charts on the same footing here.
     */
    var onColumnZoomChanged: (() -> Unit)? = null

    /**
     * shiroikuma fork: re-measure the columns at the level currently stored.
     *
     * Re-binding is what re-measures them — never while a layout pass is under way, which
     * RecyclerView refuses outright.
     */
    fun refreshColumns() {
        if (isComputingLayout) {
            post { adapter?.notifyDataSetChanged() }
        } else {
            adapter?.notifyDataSetChanged()
        }
    }

    /**
     * shiroikuma fork: put the columns back to the width the settings ask for.
     *
     * The caller scrolls afterwards — the two together are what "restore the default view" means,
     * and neither of them fetches anything.
     *
     * The re-measure is unconditional, since the level is shared: with several charts on one card
     * the first of them puts the stored level back and every other one would find nothing left to
     * change, keeping the columns it was last measured at while its neighbour widened.
     */
    fun resetZoom() {
        if (storedZoom != TenkiUiConfig.DEFAULT_COLUMN_ZOOM) {
            storedZoom = TenkiUiConfig.DEFAULT_COLUMN_ZOOM
        }
        refreshColumns()
    }

    /**
     * shiroikuma fork: the column the chart opens on, and the empty room after the last one that
     * lets it get there.
     *
     * A [RecyclerView] never scrolls past the end of its content: on a series short enough to fit on
     * the screen the layout manager pulls the last column back to the edge once it has laid the
     * anchor out at the left, and the history in front of the anchor rides back in with it — so the
     * chart opens on yesterday however loudly it was told to open on today. Reserving as much room
     * after the last column as the columns from the anchor on leave empty gives the scroll somewhere
     * to go: the anchor reaches the left edge whatever the length of the series, there is no end gap
     * left to pull back, and the past stays one swipe to the left of it.
     *
     * The room is re-measured on every layout, since a pinch changes what one column is worth.
     */
    private var anchorPosition = RecyclerView.NO_POSITION
    private var anchorScrollPending = false

    /**
     * Open on [position], reserving the room it needs to actually get there.
     *
     * The alternative to [scrollToPosition] for a chart that carries history: the anchor is
     * remembered, so a re-measure that changes the column width keeps it reachable.
     */
    fun scrollToAnchor(position: Int) {
        anchorPosition = position
        anchorScrollPending = true
        scrollToAnchorPosition()
    }

    private fun scrollToAnchorPosition() {
        if (anchorPosition < 0) return

        // NOT scrollToPosition: that one only promises to bring the column on screen, and leaves a
        // column already visible exactly where it is — which on a short series the anchor always is,
        // so the chart would keep opening on the history in front of it
        val manager = layoutManager
        if (manager is LinearLayoutManager) {
            manager.scrollToPositionWithOffset(anchorPosition, 0)
        } else {
            scrollToPosition(anchorPosition)
        }
    }

    /**
     * shiroikuma fork: told which columns are on screen, first and last, whenever that changes.
     *
     * The chart's vertical scale is fitted to them: a scale worked out once from the settings is
     * only right until the first pinch or the first swipe, and after either one the hours brought
     * into view fall outside it and run off the bottom of the pane — at both ends, since scrolling
     * uncovers a colder night on one side and a colder morning on the other.
     */
    var onVisibleColumnsChanged: ((first: Int, last: Int) -> Unit)? = null
    private var reportedFirst = NO_POSITION
    private var reportedLast = NO_POSITION

    /**
     * Which columns are on the screen, worked out from where the columns actually are.
     *
     * NOT [LinearLayoutManager.findFirstVisibleItemPosition]: that one measures against the padded
     * box, and this chart reserves padding at its end for the opening anchor which — clipToPadding
     * being off — is scrolled through and drawn in, not held empty. Columns sitting in it are on the
     * screen while the layout manager does not count them, and a scale fitted to what it reports
     * leaves everything outside that box running off the bottom of the pane. It also answers with
     * the first child in ITS order rather than the leftmost, which after a few recycles is not
     * always the same thing.
     */
    private fun reportVisibleColumns() {
        val report = onVisibleColumnsChanged
        var first = NO_POSITION
        var last = NO_POSITION
        for (i in 0..<childCount) {
            val child = getChildAt(i) ?: continue
            if (child.right <= 0 || child.left >= width) continue
            val position = getChildAdapterPosition(child)
            if (position == NO_POSITION) continue
            if (first == NO_POSITION || position < first) first = position
            if (last == NO_POSITION || position > last) last = position
        }

        if (report == null) return
        if (first == NO_POSITION || last == NO_POSITION) return
        if (first == reportedFirst && last == reportedLast) return
        reportedFirst = first
        reportedLast = last
        // Never from inside the layout or the scroll that discovered it — an adapter told anything
        // in either is an immediate IllegalStateException
        post { report(first, last) }
    }

    override fun onScrolled(dx: Int, dy: Int) {
        super.onScrolled(dx, dy)
        reportVisibleColumns()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        reportVisibleColumns()

        val room = anchorRoom()
        if (room != paddingEnd) {
            // Never from inside the pass that measured it — a padding change is a new layout, and
            // the scroll that could not reach the anchor before is only worth repeating after it
            post {
                updatePaddingRelative(end = room)
                if (anchorScrollPending) {
                    scrollToAnchorPosition()
                }
            }
        } else {
            anchorScrollPending = false
        }
    }

    private fun anchorRoom(): Int {
        val count = adapter?.itemCount ?: 0
        if (anchorPosition <= 0 || anchorPosition >= count || childCount == 0) return 0

        // Every column is the same width, whatever the zoom has made of it
        val columnWidth = getChildAt(0).measuredWidth
        return (width - columnWidth * (count - anchorPosition)).coerceAtLeast(0)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        mScaleDetector.onTouchEvent(ev)
        // A second finger is always a pinch, never a drag: taking the gesture here stops the
        // horizontal scroll from running away underneath it.
        if (ev.pointerCount > 1) {
            parent.requestDisallowInterceptTouchEvent(true)
            return true
        }
        return super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        mScaleDetector.onTouchEvent(e)
        if (mScaleDetector.isInProgress || e.pointerCount > 1) return true
        return super.onTouchEvent(e)
    }

    init {
        setWillNotDraw(false)
        // The room reserved by [scrollToAnchor] is scrolled through, not held empty: the columns
        // beyond it are drawn and laid out rather than cut off at the padding
        clipToPadding = false
        mPaint.typeface = getContext().getTypefaceFromTextAppearance(R.style.subtitle_text)
        mTextSize = getContext().dpToPx(TEXT_SIZE_DIP.toFloat()).toInt()
        mTextMargin = getContext().dpToPx(TEXT_MARGIN_DIP.toFloat()).toInt()
        mLineWidth = getContext().dpToPx(LINE_WIDTH_DIP.toFloat()).toInt()
        mDrawingBoundaryTop = -1
        mDrawingBoundaryBottom = -1
        setLineColor(Color.GRAY)
        setTextColor(Color.GRAY)
        mKeyLineList = mutableListOf()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawKeyLines(canvas)
    }

    private fun drawKeyLines(canvas: Canvas) {
        if (!mKeyLineVisibility ||
            mKeyLineList == null ||
            mKeyLineList!!.isEmpty() ||
            mHighestData == null ||
            mLowestData == null
        ) {
            return
        }
        if (isNotEmpty()) {
            mDrawingBoundaryTop = (getChildAt(0) as AbsTrendItemView).chartTop
            mDrawingBoundaryBottom = (getChildAt(0) as AbsTrendItemView).chartBottom
        }
        if (mDrawingBoundaryTop < 0 || mDrawingBoundaryBottom < 0) {
            return
        }
        val dataRange = mHighestData!! - mLowestData!!
        val boundaryRange = (mDrawingBoundaryBottom - mDrawingBoundaryTop).toFloat()
        for (line in mKeyLineList!!) {
            if (line.value > mHighestData!! || line.value < mLowestData!!) {
                continue
            }
            val y = (mDrawingBoundaryBottom - (line.value - mLowestData!!) / dataRange * boundaryRange).toInt()
            mPaint.apply {
                style = Paint.Style.STROKE
                strokeWidth = mLineWidth.toFloat()
                color = mLineColor
            }
            canvas.drawLine(0f, y.toFloat(), measuredWidth.toFloat(), y.toFloat(), mPaint)
            mPaint.apply {
                style = Paint.Style.FILL
                textSize = mTextSize.toFloat()
                color = mTextColor
            }
            when (line.contentPosition) {
                KeyLine.ContentPosition.ABOVE_LINE -> {
                    if (!line.contentLeft.isNullOrEmpty()) {
                        mPaint.textAlign = Paint.Align.LEFT
                        canvas.drawText(
                            line.contentLeft!!,
                            (2 * mTextMargin).toFloat(),
                            y - mPaint.fontMetrics.bottom - mTextMargin,
                            mPaint
                        )
                    }
                    if (!line.contentRight.isNullOrEmpty()) {
                        mPaint.textAlign = Paint.Align.RIGHT
                        canvas.drawText(
                            line.contentRight!!,
                            (measuredWidth - 2 * mTextMargin).toFloat(),
                            y - mPaint.fontMetrics.bottom - mTextMargin,
                            mPaint
                        )
                    }
                }

                KeyLine.ContentPosition.BELOW_LINE -> {
                    if (!line.contentLeft.isNullOrEmpty()) {
                        mPaint.textAlign = Paint.Align.LEFT
                        canvas.drawText(
                            line.contentLeft!!,
                            (2 * mTextMargin).toFloat(),
                            y - mPaint.fontMetrics.top + mTextMargin,
                            mPaint
                        )
                    }
                    if (!line.contentRight.isNullOrEmpty()) {
                        mPaint.textAlign = Paint.Align.RIGHT
                        canvas.drawText(
                            line.contentRight!!,
                            (measuredWidth - 2 * mTextMargin).toFloat(),
                            y - mPaint.fontMetrics.top + mTextMargin,
                            mPaint
                        )
                    }
                }
            }
        }
    }

    // control.
    fun setData(keyLineList: List<KeyLine>?, highestData: Float, lowestData: Float) {
        mKeyLineList = keyLineList
        mHighestData = highestData
        mLowestData = lowestData
        invalidate()
    }

    fun setKeyLineVisibility(visibility: Boolean) {
        mKeyLineVisibility = visibility
        invalidate()
    }

    fun setLineColor(@ColorInt lineColor: Int) {
        mLineColor = lineColor
        invalidate()
    }

    fun setTextColor(@ColorInt textColor: Int) {
        mTextColor = textColor
        invalidate()
    }

    companion object {
        /**
         * shiroikuma fork: make a card's stacked charts scroll as one.
         *
         * One shared column axis puts the same hour at the same x, which lasts exactly until one of
         * the charts is swiped on its own. Every chart passes its own scroll on to the others, and
         * the pass-on is guarded so the second-hand scrolls it causes are not passed on again.
         *
         * Only a scroll the finger is behind — a drag or the fling after it — travels. A
         * programmatic one leaves the chart IDLE, which is what keeps a card opening on its anchor
         * from dragging every neighbour along with it.
         */
        fun syncScrolling(charts: List<TrendRecyclerView>) {
            if (charts.size < 2) return
            var passingOn = false
            charts.forEach { chart ->
                chart.addOnScrollListener(
                    object : OnScrollListener() {
                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            if (passingOn || recyclerView.scrollState == SCROLL_STATE_IDLE) return
                            passingOn = true
                            charts.forEach { other -> if (other !== recyclerView) other.scrollBy(dx, 0) }
                            passingOn = false
                        }
                    }
                )
            }
        }

        private const val LINE_WIDTH_DIP = 1
        private const val TEXT_SIZE_DIP = 12
        private const val TEXT_MARGIN_DIP = 2
        const val ITEM_MARGIN_BOTTOM_DIP = 16
    }
}
