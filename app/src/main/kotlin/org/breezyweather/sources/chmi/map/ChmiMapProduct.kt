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

package org.breezyweather.sources.chmi.map

import androidx.annotation.StringRes
import org.breezyweather.R

/**
 * What a product's colours actually mean, and how to read a value off its printed tick labels.
 *
 * ČHMÚ paints every field with its own scale and publishes that scale alongside the frames, ticks
 * included — so a pixel's colour can be turned back into a **number** rather than merely a rank.
 * That is what lets the map be repainted in the app's own colours and still mean something: a
 * temperature on the map is the same colour as that temperature on the meteogram.
 */
enum class ChmiMapScale(
    private val tick: Regex?,
) {
    /** `46 °C` */
    TEMPERATURE(Regex("""(-?\d+(?:[.,]\d+)?)\s*°C""")),

    /** `100 mm/h` */
    PRECIPITATION(Regex("""(\d+(?:[.,]\d+)?)\s*mm""")),

    /** `8/8 (zataženo)` — oktas. */
    CLOUD(Regex("""(\d+)\s*/\s*8""")),

    /** `100 %` */
    HUMIDITY(Regex("""(\d+(?:[.,]\d+)?)\s*%""")),

    /** `60 min` of sunshine in the hour. */
    SUNSHINE(Regex("""(\d+(?:[.,]\d+)?)\s*min""")),

    /** `50 m/s (180 km/h)` — the first figure is the one wanted. */
    WIND(Regex("""(\d+(?:[.,]\d+)?)\s*m/s""")),

    /**
     * Radar reflectivity in dBZ. Its legend is a different shape altogether — bands carrying their
     * own bounds rather than colours with the occasional printed tick — so there is no label to
     * parse.
     */
    REFLECTIVITY(null),

    /** No scale at all: an overlay that only says where something is, not how much of it. */
    MASK(null),
    ;

    /**
     * The value a printed tick stands for, or null if this label is not one.
     *
     * ČHMÚ writes its ticks with a non-breaking space and, here and there, a decimal comma.
     */
    fun readTick(label: String?): Double? {
        val text = label?.replace(' ', ' ') ?: return null
        return tick?.find(text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
    }
}

/**
 * Which map the Meteomap can draw.
 *
 * [topic] names the manifest and [layerId] the sequence within it — two products share a manifest
 * where ČHMÚ draws them on one page, as rain and snow do.
 */
enum class ChmiMapProduct(
    val id: String,
    val topic: String,
    val layerId: String,
    @StringRes val labelRes: Int,
    val scale: ChmiMapScale,
    val tab: ChmiMapTab,
) {
    RADAR("radar", "radary.radary", "radary", R.string.meteomap_radar, ChmiMapScale.REFLECTIVITY, ChmiMapTab.RADAR),
    TEMPERATURE(
        "temperature",
        "pocasi.teplota",
        "teplota",
        R.string.temperature,
        ChmiMapScale.TEMPERATURE,
        ChmiMapTab.FORECAST
    ),
    PRECIPITATION(
        "precipitation",
        "pocasi.srazky",
        "srazky",
        R.string.precipitation,
        ChmiMapScale.PRECIPITATION,
        ChmiMapTab.FORECAST
    ),
    SNOW(
        "snow",
        "pocasi.srazky",
        "snih",
        R.string.common_weather_text_snow,
        ChmiMapScale.MASK,
        ChmiMapTab.FORECAST
    ),
    CLOUD_COVER(
        "cloud",
        "pocasi.oblacnost",
        "oblacnost",
        R.string.cloud_cover,
        ChmiMapScale.CLOUD,
        ChmiMapTab.FORECAST
    ),
    WIND("wind", "pocasi.vitr-smer", "vitr", R.string.wind, ChmiMapScale.WIND, ChmiMapTab.FORECAST),
    HUMIDITY(
        "humidity",
        "pocasi.vlhkost",
        "vlhkost",
        R.string.humidity,
        ChmiMapScale.HUMIDITY,
        ChmiMapTab.FORECAST
    ),
    SUNSHINE(
        "sunshine",
        "pocasi.slunecni-svit-1h",
        "slunecni-svit-1h",
        R.string.sunshine_duration,
        ChmiMapScale.SUNSHINE,
        ChmiMapTab.FORECAST
    ),
    ;

    companion object {
        fun forTab(tab: ChmiMapTab): List<ChmiMapProduct> = entries.filter { it.tab == tab }

        fun of(id: String?): ChmiMapProduct? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Forecast first: it is what the screen is usually opened for, and the radar is empty whenever it
 * is not raining — which is most days.
 */
enum class ChmiMapTab(@StringRes val labelRes: Int) {
    FORECAST(R.string.forecast),
    RADAR(R.string.meteomap_radar),
}
