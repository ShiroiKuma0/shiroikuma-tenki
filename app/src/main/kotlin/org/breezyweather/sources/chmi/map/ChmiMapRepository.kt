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

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.util.LruCache
import androidx.core.content.getSystemService
import androidx.core.graphics.createBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.breezyweather.sources.chmi.map.json.ChmiMapFrame
import org.breezyweather.sources.chmi.map.json.ChmiMapInit
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.tan

/** A place to read the frame's own value at, on the way through. */
data class ChmiMapSample(
    val id: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * One frame, repainted, with whatever readings were asked for taken from it.
 *
 * The readings come off the **source** pixels, before the repaint — our own ramp is not invertible,
 * so a value cannot be recovered from the picture once it has been recoloured.
 */
class ChmiMapFrameImage(
    val bitmap: Bitmap,
    val readings: Map<String, Double>,
)

/**
 * ČHMÚ's map imagery: the manifest that says which frames exist, and the frames themselves.
 *
 * Frames arrive as a PNG inside a JSON envelope, painted with ČHMÚ's own scale. They are decoded,
 * repainted in ours — see [ChmiMapScale] for why that is exact rather than approximate — and kept
 * in memory, because scrubbing a slider back and forth over seventy frames must not go near the
 * network.
 */
@Singleton
class ChmiMapRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The shared client's 50 MiB cache belongs to the weather sources; a session's worth of frames
     * would evict their responses wholesale. So: our own cache, and the connection pool shared.
     *
     * ČHMÚ sends `max-age=180` on frames whose URLs are stamped with the minute they depict and can
     * therefore never change, so the response is re-stamped on the way in. Without that the cache
     * would expire everything after three minutes and be of no use at all.
     *
     * ⚠ **Frames only.** The manifest is the index of *which* frames exist — the one thing here that
     * does change — and ČHMÚ marks it `max-age=60`. Re-stamping that too froze the radar at whatever
     * hour it was first opened, for a week, across app updates (the cache lives in `cacheDir`, which
     * an install does not clear).
     */
    private val client: OkHttpClient = okHttpClient.newBuilder()
        .cache(Cache(File(context.cacheDir, CACHE_DIR), CACHE_BYTES))
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (request.url.toString().startsWith(INIT_URL)) {
                response
            } else {
                response.newBuilder()
                    .removeHeader("Pragma")
                    .header("Cache-Control", "public, max-age=${TimeUnit.DAYS.toSeconds(7)}")
                    .build()
            }
        }
        .build()

    /**
     * The last manifest that parsed, per product.
     *
     * With the manifest no longer cached for a week, a fetch that fails leaves nothing to draw at
     * all. This keeps the map up on the frames already in hand instead of blanking it — the same
     * bargain the playback makes when a single frame cannot be fetched.
     */
    private val lastManifest = mutableMapOf<String, ChmiMapManifest>()

    /**
     * Recoloured frames, keyed by product and minute.
     *
     * A frame is about 900 KB decoded, so this is bounded by the heap rather than by a count. The
     * source bitmap is recycled as soon as it has been repainted; only the repainted one is kept.
     */
    private val frames: LruCache<String, ChmiMapFrameImage> =
        object : LruCache<String, ChmiMapFrameImage>(cacheBytes()) {
            override fun sizeOf(key: String, value: ChmiMapFrameImage) = value.bitmap.byteCount
        }

    /**
     * One `source colour -> our colour` table per product.
     *
     * [BitmapFactory] gives no access to the PNG's palette, so without this every pixel would be
     * searched against the whole legend — fifty million comparisons a frame. A frame holds at most
     * 256 distinct colours, so the table fills during the first one and every pixel after that is a
     * single lookup.
     */
    private val recolourTables = mutableMapOf<String, MutableMap<Int, Int>>()

    suspend fun loadManifest(
        product: ChmiMapProduct,
    ): ChmiMapManifest? = withContext(Dispatchers.IO) {
        // Always off the network, never off the disk. Two reasons: the index is the one thing that
        // moves, and — since the old build stored it stamped with a week's freshness — a cache that
        // may still hold that entry has to be stepped over rather than trusted to expire.
        val fresh = fetch("$INIT_URL${product.topic}", CacheControl.FORCE_NETWORK)
            ?.let { body -> runCatching { json.decodeFromString<ChmiMapInit>(body) }.getOrNull() }
            ?.let { ChmiMapManifest.of(product, it) }
            ?: return@withContext lastManifest[product.id]
        lastManifest[product.id] = fresh
        fresh
    }

    /**
     * One frame, repainted. [ramp] turns a reading into a colour; it is the app's own scale, and is
     * passed in rather than known here so this stays a fetcher rather than a painter.
     */
    suspend fun loadFrame(
        manifest: ChmiMapManifest,
        index: Int,
        ramp: (Double) -> Int,
        samples: List<ChmiMapSample> = emptyList(),
    ): ChmiMapFrameImage? = withContext(Dispatchers.IO) {
        val frame = manifest.frames.getOrNull(index) ?: return@withContext null
        val key = "${manifest.product.id}/${frame.dataRef}"
        frames[key]?.let { return@withContext it }

        val body = fetch(manifest.dataRefBase + frame.dataRef) ?: return@withContext null
        val payload = runCatching { json.decodeFromString<ChmiMapFrame>(body) }.getOrNull()
        val encoded = payload?.img?.substringAfter(',', "")?.takeIf { it.isNotEmpty() }
            ?: return@withContext null
        val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
            ?: return@withContext null
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null

        val readings = read(manifest, source, samples)
        val painted = ChmiMapFrameImage(repaint(manifest, source, ramp), readings)
        source.recycle()
        frames.put(key, painted)
        painted
    }

    /**
     * What the frame says at each asked-for place — decoded from the pixel it projects onto, so a
     * label costs no request of its own.
     */
    private fun read(
        manifest: ChmiMapManifest,
        source: Bitmap,
        samples: List<ChmiMapSample>,
    ): Map<String, Double> {
        if (samples.isEmpty()) return emptyMap()
        val bounds = manifest.bounds
        val south = mercator(bounds.south)
        val north = mercator(bounds.north)
        val spanX = bounds.east - bounds.west
        val spanY = north - south
        if (spanX == 0.0 || spanY == 0.0) return emptyMap()
        val out = mutableMapOf<String, Double>()
        samples.forEach { sample ->
            val x = ((sample.longitude - bounds.west) / spanX * source.width).toInt()
            val y = ((north - mercator(sample.latitude)) / spanY * source.height).toInt()
            if (x !in 0..<source.width || y !in 0..<source.height) return@forEach
            manifest.scale.readingOf(source.getPixel(x, y))?.let { out[sample.id] = it }
        }
        return out
    }

    private fun mercator(latitude: Double): Double =
        ln(tan(PI / 4 + Math.toRadians(latitude) / 2))

    /** Whether a frame is already in hand, so the player can decide to wait or to skip. */
    fun isLoaded(manifest: ChmiMapManifest, index: Int): Boolean {
        val frame = manifest.frames.getOrNull(index) ?: return false
        return frames["${manifest.product.id}/${frame.dataRef}"] != null
    }

    fun clear() {
        frames.evictAll()
        recolourTables.clear()
    }

    private fun fetch(url: String, cacheControl: CacheControl? = null): String? {
        val request = Request.Builder().url(url).apply { cacheControl?.let { cacheControl(it) } }.build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull()
    }

    /**
     * ČHMÚ's colours out, ours in.
     *
     * A pixel is matched to the nearest colour on the scale it was painted with, which gives the
     * reading; the reading is then coloured by [ramp]. Anything too far from every colour on that
     * scale is not a reading at all — the grey no-data margin around the domain is the usual case —
     * and is dropped, because by nearest colour it lands near the top of the scale and would paint
     * the edge of the map as a downpour.
     */
    private fun repaint(
        manifest: ChmiMapManifest,
        source: Bitmap,
        ramp: (Double) -> Int,
    ): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val table = recolourTables.getOrPut(manifest.product.id) { mutableMapOf() }
        val scale = manifest.scale
        for (i in pixels.indices) {
            val argb = pixels[i]
            if (argb ushr 24 == 0) {
                pixels[i] = Color.TRANSPARENT
                continue
            }
            pixels[i] = table.getOrPut(argb) {
                val value = scale.readingOf(argb)
                if (value == null) Color.TRANSPARENT else ramp(value)
            }
        }

        val out = createBitmap(width, height)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    private fun cacheBytes(): Int {
        val megabytes = context.getSystemService<ActivityManager>()?.memoryClass ?: DEFAULT_HEAP_MB
        return min(megabytes / HEAP_FRACTION, MAX_CACHE_MB) * BYTES_PER_MB
    }

    companion object {
        private const val INIT_URL = "https://data-provider.chmi.cz/api/map/init/"
        private const val CACHE_DIR = "meteomap"
        private const val CACHE_BYTES = 48L * 1024L * 1024L

        private const val DEFAULT_HEAP_MB = 96
        private const val HEAP_FRACTION = 5
        private const val MAX_CACHE_MB = 64
        private const val BYTES_PER_MB = 1024 * 1024

        /** `dataRef` is `yyyyMMddHHmm`, always UTC — never the manifest's own `startTime`. */
        fun parseDataRef(dataRef: String): Date? {
            return runCatching {
                SimpleDateFormat("yyyyMMddHHmm", Locale.ENGLISH).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }.parse(dataRef)
            }.getOrNull()
        }
    }
}
