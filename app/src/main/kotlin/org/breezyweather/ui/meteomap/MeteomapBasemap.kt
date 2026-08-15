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
import android.graphics.Path
import com.google.maps.android.data.geojson.GeoJsonFeature
import com.google.maps.android.data.geojson.GeoJsonLineString
import com.google.maps.android.data.geojson.GeoJsonMultiLineString
import com.google.maps.android.data.geojson.GeoJsonMultiPolygon
import com.google.maps.android.data.geojson.GeoJsonPolygon
import org.breezyweather.R
import org.breezyweather.common.extensions.parseRawGeoJson
import org.breezyweather.sources.chmi.map.ChmiMapBounds
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

/**
 * shiroikuma fork: the ground under the weather — drawn by us, from data we ship.
 *
 * ČHMÚ's frames are bare fields: colour and nothing else, meant to sit under the website's own
 * basemap. Rather than borrow anybody's tiles, and their terms with them, the map is built from
 * two bundled files:
 *
 *  - `chmi_orp.json`, the 206 ORP districts already carried for alert geocoding — the country's
 *    internal detail, and Prague picked out of it.
 *  - `meteomap_basemap.json`, cut by `tools/chmi/build_meteomap_basemap.py` from **Natural Earth**
 *    (public domain) — the rivers, the lakes, the national border traced from the union of those
 *    same districts, and the boundaries where the third countries meet **each other**. Their
 *    frontiers with Czechia are left out: that line is already drawn, in the accent.
 *
 * Nothing here touches the network.
 */
object MeteomapBasemap {

    /** Every layer projected onto one canvas. Stroke them; fill [silhouette] to mask. */
    class Layers(
        val districts: Path,
        val border: Path,
        val prague: Path,
        val boundaries: Path,
        val rivers: Path,
        val lakes: Path,
    ) {
        /** The country as one solid shape — what decides "inside Czechia" when dimming. */
        val silhouette: Path get() = border
    }

    private class Source(
        val districts: List<DoubleArray>,
        val prague: List<DoubleArray>,
        val border: List<DoubleArray>,
        val boundaries: List<DoubleArray>,
        val rivers: List<DoubleArray>,
        val lakes: List<DoubleArray>,
    )

    @Volatile
    private var source: Source? = null

    private var cachedKey: String? = null
    private var cached: Layers? = null

    fun layers(context: Context, bounds: ChmiMapBounds, width: Int, height: Int): Layers {
        val key = "${bounds.west},${bounds.south},${bounds.east},${bounds.north},$width,$height"
        synchronized(this) {
            cached?.takeIf { cachedKey == key }?.let { return it }
        }
        val data = source ?: load(context).also { source = it }

        val south = mercator(bounds.south)
        val north = mercator(bounds.north)
        val spanX = bounds.east - bounds.west
        val spanY = north - south

        fun path(rings: List<DoubleArray>, close: Boolean): Path {
            val path = Path().apply { fillType = Path.FillType.WINDING }
            if (spanX == 0.0 || spanY == 0.0) return path
            rings.forEach { ring ->
                var i = 0
                while (i + 1 < ring.size) {
                    val x = ((ring[i] - bounds.west) / spanX * width).toFloat()
                    val y = ((north - mercator(ring[i + 1])) / spanY * height).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    i += 2
                }
                if (close) path.close()
            }
            return path
        }

        val layers = Layers(
            districts = path(data.districts, true),
            border = path(data.border, true),
            prague = path(data.prague, true),
            boundaries = path(data.boundaries, false),
            rivers = path(data.rivers, false),
            lakes = path(data.lakes, true)
        )
        synchronized(this) {
            cachedKey = key
            cached = layers
        }
        return layers
    }

    private fun load(context: Context): Source {
        val districts = mutableListOf<DoubleArray>()
        val prague = mutableListOf<DoubleArray>()
        runCatching {
            context.parseRawGeoJson(R.raw.chmi_orp).features.forEach { feature ->
                val isPrague = feature.getProperty(PROPERTY_ORP) == PRAGUE_ORP
                rings(feature).forEach { if (isPrague) prague.add(it) else districts.add(it) }
            }
        }

        val border = mutableListOf<DoubleArray>()
        val boundaries = mutableListOf<DoubleArray>()
        val rivers = mutableListOf<DoubleArray>()
        val lakes = mutableListOf<DoubleArray>()
        runCatching {
            context.parseRawGeoJson(R.raw.meteomap_basemap).features.forEach { feature ->
                val bucket = when (feature.getProperty(PROPERTY_KIND)) {
                    "border" -> border
                    "boundary" -> boundaries
                    "river" -> rivers
                    "lake" -> lakes
                    else -> null
                } ?: return@forEach
                bucket.addAll(rings(feature))
            }
        }
        return Source(districts, prague, border, boundaries, rivers, lakes)
    }

    /** Every ring or line of a feature, flattened to `[lon, lat, lon, lat, …]`. */
    private fun rings(feature: GeoJsonFeature): List<DoubleArray> {
        val out = mutableListOf<DoubleArray>()
        fun add(points: List<com.google.maps.android.model.LatLng>) {
            if (points.size < 2) return
            val flat = DoubleArray(points.size * 2)
            points.forEachIndexed { index, point ->
                flat[index * 2] = point.longitude
                flat[index * 2 + 1] = point.latitude
            }
            out.add(flat)
        }
        when (val geometry = feature.geometry) {
            is GeoJsonPolygon -> geometry.coordinates.forEach { add(it) }
            is GeoJsonMultiPolygon -> geometry.polygons.forEach { p -> p.coordinates.forEach { add(it) } }
            is GeoJsonLineString -> add(geometry.coordinates)
            is GeoJsonMultiLineString ->
                @Suppress("UNCHECKED_CAST")
                (geometry.geometryObject as? List<GeoJsonLineString>)
                    ?.forEach { add(it.coordinates) }
            else -> Unit
        }
        return out
    }

    /** Web Mercator's y, unscaled — the frames are EPSG:3857 and the ground has to match. */
    private fun mercator(latitude: Double): Double = ln(tan(PI / 4 + Math.toRadians(latitude) / 2))

    private const val PROPERTY_ORP = "cisorp"
    private const val PROPERTY_KIND = "kind"
    private const val PRAGUE_ORP = "1100"
}
