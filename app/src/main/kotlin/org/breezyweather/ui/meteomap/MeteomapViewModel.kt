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

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import breezyweather.data.location.LocationRepository
import breezyweather.data.weather.WeatherRepository
import breezyweather.domain.weather.model.Hourly
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.breezyweather.sources.chmi.map.ChmiMapFrameImage
import org.breezyweather.sources.chmi.map.ChmiMapManifest
import org.breezyweather.sources.chmi.map.ChmiMapProduct
import org.breezyweather.sources.chmi.map.ChmiMapRepository
import org.breezyweather.sources.chmi.map.ChmiMapTab
import org.breezyweather.tenki.TenkiUiState
import java.util.TimeZone
import javax.inject.Inject

/** How fast the animation runs, as the pause between frames. */
enum class MeteomapSpeed(val label: String, val frameMillis: Long) {
    QUARTER("¼×", 1600),
    HALF("½×", 800),
    NORMAL("1×", 400),
    DOUBLE("2×", 200),
    QUAD("4×", 100),
}

/** What is known about one product: its frames, where the playhead is, and the frame in hand. */
data class MeteomapProductState(
    val manifest: ChmiMapManifest? = null,
    val index: Int = 0,
    val frame: ChmiMapFrameImage? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
)

data class MeteomapUiState(
    val tab: ChmiMapTab = ChmiMapTab.FORECAST,
    val forecastProduct: ChmiMapProduct = ChmiMapProduct.TEMPERATURE,
    val playing: Boolean = false,
    val looping: Boolean = true,
    val speed: MeteomapSpeed = MeteomapSpeed.NORMAL,
    val products: Map<String, MeteomapProductState> = emptyMap(),
    /**
     * The location's own hourly forecast, which is what the strip above the slider plots. It is
     * the same series the meteogram on the weather screen draws, so the two agree.
     */
    val hourly: List<Hourly> = emptyList(),
    val timeZone: TimeZone = TimeZone.getDefault(),
) {
    val product: ChmiMapProduct
        get() = if (tab == ChmiMapTab.RADAR) ChmiMapProduct.RADAR else forecastProduct

    val current: MeteomapProductState
        get() = products[product.id] ?: MeteomapProductState()
}

@HiltViewModel
class MeteomapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ChmiMapRepository,
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val formattedId: String? = savedStateHandle.get<String>(MeteomapActivity.KEY_FORMATTED_ID)

    /**
     * Which city labels are switched off, read once — a frame already decoded keeps whatever
     * readings it was decoded with, so changing the set takes effect on the next frame fetched.
     */
    private val hiddenCities: Set<String> = TenkiUiState.getInstance(context).meteomapHiddenCities

    private val _uiState = MutableStateFlow(MeteomapUiState())
    val uiState: StateFlow<MeteomapUiState> = _uiState.asStateFlow()

    private var playback: Job? = null

    init {
        load(ChmiMapProduct.TEMPERATURE)
        loadHourly()
    }

    /**
     * The location's hourly forecast, for the strip above the slider. A failure here costs the
     * strip and nothing else — the map itself needs no location at all.
     */
    private fun loadHourly() {
        viewModelScope.launch {
            val location = (
                formattedId?.takeIf { it.isNotEmpty() }?.let {
                    locationRepository.getLocation(it, withParameters = false)
                } ?: locationRepository.getFirstLocation(withParameters = false)
                ) ?: return@launch
            val weather = weatherRepository.getWeatherByLocationId(
                location.formattedId,
                withDaily = false,
                withHourly = true,
                withMinutely = false,
                withAlerts = false,
                withNormals = false
            ) ?: return@launch
            _uiState.update {
                it.copy(hourly = weather.hourlyForecast, timeZone = location.timeZone)
            }
        }
    }

    fun selectTab(tab: ChmiMapTab) {
        stop()
        _uiState.update { it.copy(tab = tab) }
        load(_uiState.value.product)
    }

    fun selectProduct(product: ChmiMapProduct) {
        stop()
        _uiState.update { it.copy(forecastProduct = product) }
        load(product)
    }

    fun seek(index: Int) {
        stop()
        val product = _uiState.value.product
        val state = _uiState.value.products[product.id] ?: return
        val bounded = index.coerceIn(0, (state.manifest?.frames?.size ?: 1) - 1)
        update(product) { it.copy(index = bounded) }
        showFrame(product, bounded)
    }

    fun togglePlay() {
        if (_uiState.value.playing) stop() else start()
    }

    fun toggleLoop() = _uiState.update { it.copy(looping = !it.looping) }

    fun setSpeed(speed: MeteomapSpeed) = _uiState.update { it.copy(speed = speed) }

    /** Back to the frame the map opens on — the last one that is not in the future. */
    fun resetToNow() {
        stop()
        val product = _uiState.value.product
        val manifest = _uiState.value.products[product.id]?.manifest ?: return
        update(product) { it.copy(index = manifest.nowIndex) }
        showFrame(product, manifest.nowIndex)
    }

    private fun start() {
        _uiState.update { it.copy(playing = true) }
        playback = viewModelScope.launch {
            while (true) {
                val state = _uiState.value
                val product = state.product
                val frames = state.products[product.id]?.manifest?.frames?.size ?: break
                val next = (state.products[product.id]?.index ?: 0) + 1
                if (next >= frames) {
                    if (!state.looping) {
                        _uiState.update { it.copy(playing = false) }
                        break
                    }
                    update(product) { it.copy(index = 0) }
                    showFrame(product, 0)
                } else {
                    update(product) { it.copy(index = next) }
                    showFrame(product, next)
                }
                delay(state.speed.frameMillis)
            }
        }
    }

    private fun stop() {
        playback?.cancel()
        playback = null
        if (_uiState.value.playing) _uiState.update { it.copy(playing = false) }
    }

    /**
     * Keep the manifest current for as long as the screen is in front.
     *
     * A manifest is fetched once per product and then never again, so a map left open drifts quietly
     * behind the weather — which is the one thing a radar must not do. ČHMÚ says how often it
     * republishes (180 s on the radar) and that is the interval used.
     *
     * Called from the screen under `repeatOnLifecycle(RESUMED)`, so it stops the moment the map is
     * not being looked at and resumes — with an immediate catch-up — when it is again.
     */
    suspend fun watchForNewFrames() {
        while (true) {
            val product = _uiState.value.product
            val seconds = _uiState.value.products[product.id]?.manifest?.refreshSeconds
                ?: DEFAULT_REFRESH_SECONDS
            delay(seconds.coerceAtLeast(MIN_REFRESH_SECONDS) * 1000L)
            refreshManifest(_uiState.value.product)
        }
    }

    /**
     * Re-read the index and, if it has moved on, adopt it.
     *
     * The playhead is held on the frame it was actually on **by name, not by number**: a new frame
     * published at the end shifts every index down by one, and keeping the number would step the map
     * silently backwards. Sitting on "now" is the exception — that follows the new now, which is the
     * whole point of refreshing.
     */
    private suspend fun refreshManifest(product: ChmiMapProduct) {
        val state = _uiState.value.products[product.id] ?: return
        val old = state.manifest ?: return
        val fresh = repository.loadManifest(product) ?: return
        if (fresh.frames == old.frames) return

        val wasAtNow = state.index == old.nowIndex
        val current = old.frames.getOrNull(state.index)?.dataRef
        val index = if (wasAtNow) {
            fresh.nowIndex
        } else {
            fresh.frames.indexOfFirst { it.dataRef == current }.takeIf { it >= 0 } ?: fresh.nowIndex
        }
        update(product) { it.copy(manifest = fresh, index = index) }
        showFrame(product, index)
        prefetch(fresh)
    }

    private fun load(product: ChmiMapProduct) {
        val known = _uiState.value.products[product.id]
        if (known?.manifest != null) {
            showFrame(product, known.index)
            return
        }
        update(product) { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val manifest = repository.loadManifest(product)
            if (manifest == null) {
                update(product) { it.copy(loading = false, failed = true) }
                return@launch
            }
            update(product) { it.copy(manifest = manifest, index = manifest.nowIndex) }
            showFrame(product, manifest.nowIndex)
            prefetch(manifest)
        }
    }

    /**
     * Fetch the frame and show it — but only if the playhead has not moved on in the meantime,
     * which it will have whenever the animation outruns the network.
     */
    private fun showFrame(product: ChmiMapProduct, index: Int) {
        viewModelScope.launch {
            val manifest = _uiState.value.products[product.id]?.manifest ?: return@launch
            val frame = repository.loadFrame(
                manifest,
                index,
                MeteomapPalette.rampFor(product),
                MeteomapCity.samples(hiddenCities)
            )
            val state = _uiState.value
            if (state.product != product || state.products[product.id]?.index != index) return@launch
            update(product) {
                it.copy(
                    frame = frame ?: it.frame,
                    loading = false,
                    failed = frame == null && it.frame == null
                )
            }
        }
    }

    /**
     * Warm the frames either side of the playhead so a first play does not stutter. Sequential on
     * purpose: a burst of seventy parallel requests would starve the frame actually being shown.
     */
    private fun prefetch(manifest: ChmiMapManifest) {
        viewModelScope.launch {
            val ramp = MeteomapPalette.rampFor(manifest.product)
            val order = (manifest.nowIndex..<manifest.frames.size) + (manifest.nowIndex - 1 downTo 0)
            order.take(PREFETCH_FRAMES).forEach { index ->
                if (!repository.isLoaded(manifest, index)) {
                    repository.loadFrame(manifest, index, ramp, MeteomapCity.samples(hiddenCities))
                }
            }
        }
    }

    private fun update(product: ChmiMapProduct, block: (MeteomapProductState) -> MeteomapProductState) {
        _uiState.update { state ->
            val products = state.products.toMutableMap()
            products[product.id] = block(products[product.id] ?: MeteomapProductState())
            state.copy(products = products)
        }
    }

    override fun onCleared() {
        super.onCleared()
        playback?.cancel()
    }

    companion object {
        private const val PREFETCH_FRAMES = 24

        /** What the forecasts get, which publish hourly and say nothing about it. */
        private const val DEFAULT_REFRESH_SECONDS = 600

        /** However eager a manifest claims to be, never poll faster than this. */
        private const val MIN_REFRESH_SECONDS = 60
    }
}
