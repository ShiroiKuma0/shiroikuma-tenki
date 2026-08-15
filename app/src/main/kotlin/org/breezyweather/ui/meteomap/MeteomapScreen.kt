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

package org.breezyweather.ui.meteomap

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.breezyweather.R
import org.breezyweather.sources.chmi.map.ChmiMapProduct
import org.breezyweather.sources.chmi.map.ChmiMapTab
import org.breezyweather.tenki.LocalTenkiUi
import org.breezyweather.ui.common.widgets.Material3Scaffold
import org.breezyweather.ui.common.widgets.insets.BWCenterAlignedTopAppBar
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * shiroikuma fork: ČHMÚ's radar and forecast maps, animated over a border we draw ourselves.
 *
 * Two tabs, because the two are different things: the radar is what the sky **did**, at five-minute
 * steps with an hour of nowcast on the end, and the forecast is what ALADIN says it **will** do,
 * hour by hour for three days.
 */
@Composable
fun MeteomapScreen(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeteomapViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // ČHMÚ publishes a new radar frame every few minutes; poll for them only while the map is
    // actually in front of somebody, and stop dead when it is not.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.watchForNewFrames()
        }
    }

    val accent = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val background = MaterialTheme.colorScheme.background

    Material3Scaffold(
        modifier = modifier,
        topBar = {
            BWCenterAlignedTopAppBar(
                title = stringResource(R.string.meteomap),
                onBackPressed = onBackPressed
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = ChmiMapTab.entries.indexOf(state.tab)) {
                ChmiMapTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == state.tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }

            if (state.tab == ChmiMapTab.FORECAST) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ChmiMapProduct.forTab(ChmiMapTab.FORECAST).forEach { product ->
                        FilterChip(
                            selected = product == state.forecastProduct,
                            onClick = { viewModel.selectProduct(product) },
                            label = { Text(stringResource(product.labelRes)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent,
                                selectedLabelColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                MeteomapCanvas(
                    manifest = state.current.manifest,
                    frame = state.current.frame,
                    background = background,
                    accent = accent,
                    cities = MeteomapCity.shown(LocalTenkiUi.current.meteomapHiddenCities),
                    valueSizeDp = LocalTenkiUi.current.meteomapValueSize.toFloat(),
                    nameSizeDp = LocalTenkiUi.current.meteomapNameSize.toFloat(),
                    modifier = Modifier.fillMaxSize(),
                    resetKey = state.product.id
                )
                MeteomapClock(
                    time = state.current.manifest?.frames?.getOrNull(state.current.index)?.time,
                    timeZone = state.timeZone,
                    accent = accent,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                )
                when {
                    state.current.failed -> Text(
                        text = stringResource(R.string.meteomap_unavailable),
                        color = dim,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    state.current.frame == null -> CircularProgressIndicator(
                        color = accent,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            MeteomapKey(product = state.product, dim = dim)

            MeteomapPlaybackBar(
                manifest = state.current.manifest,
                index = state.current.index,
                playing = state.playing,
                looping = state.looping,
                speed = state.speed,
                series = MeteomapPalette.seriesFor(state.product, state.hourly),
                colorAt = MeteomapPalette.seriesRampFor(state.product),
                timeZone = state.timeZone,
                accent = accent,
                dim = dim,
                onSeek = viewModel::seek,
                onTogglePlay = viewModel::togglePlay,
                onToggleLoop = viewModel::toggleLoop,
                onSpeed = viewModel::setSpeed
            )

            Text(
                text = stringResource(R.string.meteomap_attribution),
                color = dim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

/** The scale in a line: a few swatches off the product's own ramp, with what they stand for. */
@Composable
private fun MeteomapKey(
    product: ChmiMapProduct,
    dim: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MeteomapPalette.keyFor(product).forEach { (value, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(color), RoundedCornerShape(2.dp))
                )
                Text(
                    text = " " + formatKeyValue(value),
                    color = dim,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun formatKeyValue(value: Double): String =
    if (value == value.toInt().toDouble()) value.toInt().toString() else value.toString()

/**
 * shiroikuma fork: the frame's own clock, over the map's top corner.
 *
 * Outlined in black rather than merely coloured: it sits on whatever the field happens to be doing
 * underneath, and a yellow numeral on a yellow-orange heatwave would otherwise vanish. Reads
 * 24-hour by default, which the 白い熊 天気 UI page can switch.
 */
@Composable
private fun MeteomapClock(
    time: Date?,
    timeZone: TimeZone,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    if (time == null) return
    val ui = LocalTenkiUi.current
    val calendar = Calendar.getInstance(timeZone).apply { this.time = time }
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val text = if (ui.meteomapClock24h) {
        "%02d:%02d".format(hour, calendar.get(Calendar.MINUTE))
    } else {
        val twelve = if (hour % 12 == 0) 12 else hour % 12
        "%d:%02d %s".format(twelve, calendar.get(Calendar.MINUTE), if (hour < 12) "AM" else "PM")
    }
    val style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
    Box(modifier = modifier) {
        Text(text = text, style = style.copy(drawStyle = Stroke(width = CLOCK_OUTLINE)), color = Color.Black)
        Text(text = text, style = style, color = accent)
    }
}

private const val CLOCK_OUTLINE = 7f
