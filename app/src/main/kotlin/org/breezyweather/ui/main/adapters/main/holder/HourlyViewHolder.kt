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

package org.breezyweather.ui.main.adapters.main.holder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import breezyweather.domain.location.model.Location
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonGroup
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.dpToPx
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.tenki.TenkiViewTheme
import org.breezyweather.ui.common.adapters.ButtonAdapter
import org.breezyweather.ui.common.widgets.trend.TrendLayoutManager
import org.breezyweather.ui.common.widgets.trend.TrendRecyclerView
import org.breezyweather.ui.main.MainActivity
import org.breezyweather.ui.main.adapters.trend.HourlyTrendAdapter
import org.breezyweather.ui.main.widgets.TrendRecyclerViewScrollBar
import org.breezyweather.ui.theme.ThemeManager
import org.breezyweather.ui.theme.resource.providers.ResourceProvider

class HourlyViewHolder(parent: ViewGroup) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_hourly_trend_card, parent, false)
) {
    private val subtitle: TextView = itemView.findViewById(R.id.hourly_block_subtitle)
    private val buttonGroup: MaterialButtonGroup = itemView.findViewById(R.id.hourly_block_button_group)

    // shiroikuma fork: back to the view the card opens with — zoom and scroll, no refetch
    private val resetView: View = itemView.findViewById(R.id.hourly_block_reset_view)

    // shiroikuma fork: one chart per selected forecast source, rebuilt on every bind
    private val sourceContainer: LinearLayout = itemView.findViewById(R.id.hourly_block_source_container)
    private val charts = mutableListOf<SourceChart>()

    private class SourceChart(
        val adapter: HourlyTrendAdapter,
        val recyclerView: TrendRecyclerView,
        val scrollBar: TrendRecyclerViewScrollBar,
        val location: Location,
    )

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
        selectedTab: String?,
        setSelectedTab: (String?) -> Unit,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)

        val weather = location.weather ?: return

        if (weather.current?.hourlyForecast.isNullOrEmpty()) {
            subtitle.visibility = View.GONE
        } else {
            subtitle.visibility = View.VISIBLE
            subtitle.text = weather.current?.hourlyForecast
        }

        // shiroikuma fork: build a chart per source, stacked in the arranged order and on one shared
        // column axis. With a single source this is one unlabelled chart — the card as it always was.
        val blocks = location.forecastSourceBlocks(
            (activity as? MainActivity)?.sourceManager,
            activity,
            location.orderedHourlyForecastSources,
            ForecastSeries.HOURLY
        )
        sourceContainer.removeAllViews()
        charts.clear()

        blocks.forEach { block ->
            val sourceView = LayoutInflater.from(context)
                .inflate(R.layout.container_main_hourly_trend_source, sourceContainer, false)
            val nameView: TextView = sourceView.findViewById(R.id.hourly_source_name)
            val recyclerView: TrendRecyclerView = sourceView.findViewById(R.id.hourly_source_trendRecyclerView)

            // Nothing to tell apart when there is only one, so the label stays out of the way
            nameView.visibility = if (blocks.size > 1) View.VISIBLE else View.GONE
            nameView.text = block.name

            // shiroikuma fork: the graph's height is a setting, not a fixed dimen
            recyclerView.layoutParams = recyclerView.layoutParams.apply {
                height = context.dpToPx(TenkiViewTheme.state(context).hourlyChartHeight.toFloat()).toInt()
            }

            // shiroikuma fork: which zoom level the pinch on this chart drives
            recyclerView.zoomKind = TrendRecyclerView.ZoomKind.HOURLY
            val scrollBar = TrendRecyclerViewScrollBar()
            recyclerView.setHasFixedSize(true)
            recyclerView.addItemDecoration(scrollBar)
            charts.add(
                SourceChart(
                    adapter = HourlyTrendAdapter(activity, recyclerView).apply { bindData(block.location) },
                    recyclerView = recyclerView,
                    scrollBar = scrollBar,
                    location = block.location
                )
            )
            sourceContainer.addView(sourceView)
        }

        val trendAdapter = charts.firstOrNull()?.adapter ?: return
        val buttonList: MutableList<ButtonAdapter.Button> = trendAdapter.adapters.map {
            object : ButtonAdapter.Button {
                override val name = it.getDisplayName(activity)
            }
        }.toMutableList()
        selectedTab?.let { tab ->
            buttonList.indexOfFirst { it.name == tab }.let {
                if (it >= 0) {
                    // One tab selection drives every source, so the charts always compare like
                    // with like. The adapters share a tab order, being built from the same data.
                    charts.forEach { chart -> chart.adapter.selectedIndex = it }
                } else {
                    setSelectedTab(null) // Reset
                }
            }
        }

        if (buttonList.size < 2) {
            buttonGroup.visibility = View.GONE
        } else {
            buttonGroup.visibility = View.VISIBLE
            // Dirty trick to get the button group to actually redraw with the correct styles AND the overflow menu
            while (
                buttonGroup.children
                    .filter { it is MaterialButton && it.tag != MaterialButtonGroup.OVERFLOW_BUTTON_TAG }
                    .count() != 0
            ) {
                buttonGroup.children
                    .filter { it is MaterialButton && it.tag != MaterialButtonGroup.OVERFLOW_BUTTON_TAG }
                    .forEach {
                        buttonGroup.removeView(it)
                    }
            }
            buttonGroup.children
                .filter { it is MaterialButton && it.tag == MaterialButtonGroup.OVERFLOW_BUTTON_TAG }
                .forEach {
                    it.contentDescription = context.getString(R.string.action_more)
                    // shiroikuma fork: the overflow ⋮ follows the same palette.
                    TenkiViewTheme.paintTabButton(context, it as? MaterialButton)
                }
            buttonList.forEachIndexed { index, button ->
                buttonGroup.addView(
                    MaterialButton(
                        context,
                        null,
                        com.google.android.material.R.attr.materialButtonStyle
                    ).apply {
                        text = button.name
                        isCheckable = true
                        isChecked = index == trendAdapter.selectedIndex
                        setOnClickListener {
                            // Every source's chart follows the one tab selection
                            charts.forEach { chart -> chart.adapter.selectedIndex = index }
                            setSelectedTab(button.name)
                            buttonGroup.children
                                .filter { it is MaterialButton && it.tag != MaterialButtonGroup.OVERFLOW_BUTTON_TAG }
                                .forEach { button ->
                                    (button as MaterialButton).isChecked = false
                                }
                            isChecked = true
                        }
                        // shiroikuma fork: yellow-outlined pill, reversed when selected.
                        TenkiViewTheme.paintTabButton(context, this)
                    }
                )
            }
        }
        val lineColor = context.getThemeColor(com.google.android.material.R.attr.colorOutline)
        val textColor = ContextCompat.getColor(
            context,
            if (ThemeManager.isLightTheme(context, location)) R.color.colorTextGrey else R.color.colorTextGrey2nd
        )
        val keyLinesEnabled = SettingsManager.getInstance(context).isTrendHorizontalLinesEnabled

        // shiroikuma fork: the charts plot every hour still stored, a month of history included, so
        // each opens scrolled to its configured hours of history rather than at the oldest hour we
        // happen to have kept. Each source has its own series, so each finds its own column.
        val hoursBack = TenkiViewTheme.state(context).hourlyHoursBack

        charts.forEach { chart ->
            chart.recyclerView.layoutManager = TrendLayoutManager(context)
            chart.recyclerView.setLineColor(lineColor)
            chart.recyclerView.setTextColor(textColor)
            chart.recyclerView.adapter = chart.adapter
            chart.recyclerView.setKeyLineVisibility(keyLinesEnabled)
            chart.location.weather?.let {
                chart.recyclerView.scrollToAnchor(it.hourlyOpeningIndex(hoursBack))
            }
            // One zoom level for the card: a pinch on any chart re-measures the others too, or the
            // charts stacked beside it would keep comparing columns of a different width
            chart.recyclerView.onColumnZoomChanged = {
                charts.forEach { other ->
                    if (other !== chart) other.recyclerView.refreshColumns()
                }
            }
            // shiroikuma fork: the scale follows the hours on screen, wherever the chart is scrolled
            // or pinched to
            chart.recyclerView.onVisibleColumnsChanged = { first, last ->
                chart.adapter.fitToVisible(first, last)
            }
            chart.scrollBar.resetColor(activity)
        }

        // shiroikuma fork: one axis only holds while nobody swipes a chart on its own
        if (TenkiViewTheme.state(context).chartScrollSync) {
            TrendRecyclerView.syncScrolling(charts.map { it.recyclerView })
        }

        // shiroikuma fork: the pinch zoom and the scroll are the only things this touches — the
        // selected tab is a deliberate choice and stays where it was put.
        resetView.setOnClickListener {
            charts.forEach { chart ->
                chart.recyclerView.resetZoom()
                chart.location.weather?.let { weather ->
                    // After the re-bind rather than during it, or the pending scroll is dropped
                    chart.recyclerView.post {
                        chart.recyclerView.scrollToAnchor(weather.hourlyOpeningIndex(hoursBack))
                    }
                }
            }
        }
    }
}
