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

import org.breezyweather.sources.chmi.map.ChmiMapSample

/**
 * shiroikuma fork: the places the Meteomap prints a reading for.
 *
 * Chosen for even coverage rather than for population — the point is that a glance takes in the
 * whole country, so the far west and the Moravian east are represented even where nobody large
 * lives. Every one can be switched off on the 白い熊 天気 UI page.
 *
 * The reading itself is not fetched: it is decoded out of the frame already on screen, at the
 * pixel the city projects onto, which is why adding a city costs nothing.
 */
enum class MeteomapCity(
    val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
) {
    PRAHA("praha", "Praha", 50.0755, 14.4378),
    BRNO("brno", "Brno", 49.1951, 16.6068),
    OSTRAVA("ostrava", "Ostrava", 49.8209, 18.2625),
    PLZEN("plzen", "Plzeň", 49.7384, 13.3736),
    LIBEREC("liberec", "Liberec", 50.7663, 15.0543),
    OLOMOUC("olomouc", "Olomouc", 49.5938, 17.2509),
    BUDEJOVICE("budejovice", "Č. Budějovice", 48.9745, 14.4747),
    HRADEC("hradec", "Hradec Králové", 50.2092, 15.8328),
    USTI("usti", "Ústí n. L.", 50.6607, 14.0323),
    JIHLAVA("jihlava", "Jihlava", 49.3961, 15.5912),
    KARLOVY_VARY("karlovyvary", "Karlovy Vary", 50.2306, 12.8712),
    ZLIN("zlin", "Zlín", 49.2265, 17.6678),
    ;

    val sample: ChmiMapSample get() = ChmiMapSample(id, latitude, longitude)

    companion object {
        /**
         * Those still switched on. Stored as the ones switched **off**, so a city added in a later
         * build appears without anybody having to opt into it.
         */
        fun shown(hidden: Set<String>): List<MeteomapCity> = entries.filter { it.id !in hidden }

        fun samples(hidden: Set<String>): List<ChmiMapSample> = shown(hidden).map { it.sample }
    }
}
