/*
 * 白い熊 天気 (shiroikuma-tenki) fork: the Export / Import engine — the category ZIP behind the
 * UI page's panel.
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package org.breezyweather.tenki

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import breezyweather.data.location.LocationRepository
import breezyweather.domain.location.model.Location
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Category-based backup in the family shape: one `.zip` per export holding a `manifest.json` plus
 * one `<id>.json` per category, absent categories skipped on import, imports merging per key.
 *
 * Filenames follow the family convention — `shiroikuma-tenki_<yyyy-MM-dd_HH-mm-ss>.zip`, no
 * version and no suffix — because every sister app backs up into one directory.
 *
 * The core is [writeZip]: `(categories, OutputStream, onProgress, isCancelled)`. Anything that
 * wants an export — the panel today, an automation contract tomorrow — is a thin caller of it, so
 * the export logic exists exactly once.
 */
object TenkiBackup {

    const val FORMAT = "shiroikuma-tenki-backup"
    const val VERSION = 1
    const val FILE_PREFIX = "shiroikuma-tenki_"
    const val MIME_ZIP = "application/zip"

    /** A tickable unit of the backup. [id] is both the zip entry base and the automation id. */
    enum class Cat(val id: String, val label: String, val onByDefault: Boolean = true) {
        UI("ui", "白い熊 天気 UI (colours, fonts, sizes)"),
        SETTINGS("settings", "App settings (appearance, units, notifications, widgets)"),
        SOURCES("sources", "Weather source configuration (including API keys)"),
        LOCATIONS("locations", "Locations"),
        FONTS("fonts", "Imported fonts", onByDefault = false),
        ;

        /** Entry name inside the zip. Fonts are a directory of the original files. */
        val entry: String get() = if (this == FONTS) "fonts/" else "$id.json"

        companion object {
            fun ofId(id: String): Cat? = entries.firstOrNull { it.id == id }

            fun ofEntry(name: String): Cat? = entries.firstOrNull {
                if (it == FONTS) name.startsWith("fonts/") else name == it.entry
            }

            val defaults: Set<Cat> get() = entries.filter { it.onByDefault }.toSet()
        }
    }

    /** Progress callback: the category being written, plus a display count of what is finished. */
    fun interface Progress {
        fun report(cat: Cat, position: Int, total: Int)
    }

    fun exportFileName(): String =
        FILE_PREFIX + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"

    // ------------------------------------------------------------------ the core

    /**
     * Writes the ticked [categories] into [out]. Nothing here touches a UI, a SAF uri or a
     * broadcast — callers own the destination.
     *
     * @param isCancelled checked between categories, so a cancel unwinds at a boundary rather
     *        than tearing a write in half.
     * @return the categories actually written.
     */
    suspend fun writeZip(
        context: Context,
        categories: Set<Cat>,
        out: OutputStream,
        onProgress: Progress? = null,
        isCancelled: () -> Boolean = { false },
    ): Set<Cat> {
        val ordered = Cat.entries.filter { it in categories }
        val written = linkedSetOf<Cat>()

        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifestJson(context, ordered).toString(2).toByteArray())
            zip.closeEntry()

            ordered.forEachIndexed { index, cat ->
                if (isCancelled()) return written
                // The contract's rule: `position` is the POSITION of the one being written.
                onProgress?.report(cat, index + 1, ordered.size)

                when (cat) {
                    Cat.UI -> zip.jsonEntry(cat, TenkiUiConfig(context).toJson())
                    Cat.SETTINGS -> zip.jsonEntry(cat, prefsJson(context, settingsPrefsName(context)))
                    Cat.SOURCES -> zip.jsonEntry(cat, sourcesJson(context))
                    Cat.LOCATIONS -> zip.jsonEntry(cat, locationsJson(context))
                    Cat.FONTS -> TenkiFonts.imported(context).forEach { font ->
                        if (isCancelled()) return written
                        zip.putNextEntry(ZipEntry("fonts/${font.name}"))
                        zip.write(font.readBytes())
                        zip.closeEntry()
                    }
                }
                written += cat
            }
        }
        return written
    }

    private fun ZipOutputStream.jsonEntry(cat: Cat, json: JSONObject) {
        putNextEntry(ZipEntry(cat.entry))
        write(json.toString(2).toByteArray())
        closeEntry()
    }

    private fun manifestJson(context: Context, cats: List<Cat>) = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("app", context.packageName)
        put(
            "appVersion",
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: ""
        )
        put("createdTs", System.currentTimeMillis())
        put("categories", JSONArray(cats.map { it.id }))
    }

    // ------------------------------------------------------------- SAF destination

    /** What an export produced: the file it wrote, its real size, and the categories in it. */
    data class ExportResult(val name: String, val bytes: Long, val categories: Set<Cat>)

    /**
     * The one export-to-a-folder implementation — the UI panel and the headless automation service
     * are both thin callers of it.
     *
     * Streams [writeZip] straight into the SAF tree **atomically**: a `.part` document first,
     * renamed to the final name only once the archive is closed and complete, and deleted if
     * anything fails or the run is cancelled. A killed export must never leave something that looks
     * like a backup — 白い熊 keeps every app's archives in one folder sorted by date, so a truncated
     * one would silently become "the latest backup".
     */
    suspend fun exportToTree(
        context: Context,
        treeUri: Uri,
        categories: Set<Cat>,
        onProgress: Progress? = null,
        isCancelled: () -> Boolean = { false },
    ): ExportResult {
        val finalName = exportFileName()
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val part = DocumentsContract.createDocument(
            context.contentResolver,
            parent,
            MIME_ZIP,
            "$finalName.part"
        ) ?: error("The backup folder could not be written to.")

        return try {
            val written = context.contentResolver.openOutputStream(part)?.use { out ->
                writeZip(context, categories, out, onProgress, isCancelled)
            } ?: error("The backup folder could not be written to.")

            if (isCancelled()) error("cancelled")

            val size = documentSize(context, part)
            DocumentsContract.renameDocument(context.contentResolver, part, finalName)
            ExportResult(finalName, size, written)
        } catch (failure: Throwable) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, part) }
            throw failure
        }
    }

    private fun documentSize(context: Context, document: Uri): Long = runCatching {
        context.contentResolver.query(
            document,
            arrayOf(DocumentsContract.Document.COLUMN_SIZE),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L } ?: 0L
    }.getOrDefault(0L)

    // ------------------------------------------------------------------ import

    fun categoriesIn(bytes: ByteArray): Set<Cat> {
        val found = mutableSetOf<Cat>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                Cat.ofEntry(entry.name)?.let { found += it }
                entry = zip.nextEntry
            }
        }
        return found
    }

    /**
     * Applies the ticked [categories]. Absent ones are skipped, present ones merge per key.
     * @return how many categories were restored.
     */
    suspend fun restore(context: Context, bytes: ByteArray, categories: Set<Cat>): Int {
        val seen = mutableSetOf<Cat>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val cat = Cat.ofEntry(entry.name)
                if (cat != null && cat in categories) {
                    val content = zip.readBytes()
                    when (cat) {
                        Cat.UI -> TenkiUiConfig(context).fromJson(JSONObject(String(content)))
                        Cat.SETTINGS -> restorePrefs(
                            context,
                            settingsPrefsName(context),
                            JSONObject(String(content))
                        )
                        Cat.SOURCES -> restoreSources(context, JSONObject(String(content)))
                        Cat.LOCATIONS -> restoreLocations(context, JSONObject(String(content)))
                        Cat.FONTS -> {
                            val name = entry.name.removePrefix("fonts/")
                            if (name.isNotBlank()) {
                                File(TenkiFonts.fontsDir(context), name).writeBytes(content)
                                TenkiFonts.invalidate()
                            }
                        }
                    }
                    seen += cat
                }
                entry = zip.nextEntry
            }
        }
        return seen.size
    }

    // ----------------------------------------------------- SharedPreferences files

    /**
     * Upstream's own settings all live in one preferences file — `ConfigStore`'s default name,
     * `<packageName>_preferences` — so this single category carries appearance, units,
     * notifications, widgets and background updates together.
     */
    private fun settingsPrefsName(context: Context) = context.packageName + "_preferences"

    private fun prefsJson(context: Context, name: String): JSONObject {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        return JSONObject().apply {
            prefs.all.forEach { (key, value) ->
                when (value) {
                    is Int, is Boolean, is String, is Long, is Float -> put(key, value)
                    is Set<*> -> put(key, JSONArray(value.filterIsInstance<String>()))
                    else -> Unit
                }
            }
        }
    }

    /**
     * **`commit = true`, not `apply()`.** 応用管理 force-stops this app the instant we reply that an
     * import succeeded — deliberately, because a live process writes its cached preferences back
     * out at orderly shutdown and would silently undo the restore. That force-stop is a `SIGKILL`,
     * so an `apply()` still queued on the disk-write thread is simply lost, and the app comes back
     * with some of its files restored and some not. A synchronous commit is what makes the reply
     * true at the moment it is sent. The live setters elsewhere stay on `apply()` — they run while
     * 白い熊 drags a slider and must not block.
     */
    private fun restorePrefs(context: Context, name: String, json: JSONObject) {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit(commit = true) {
            json.keys().forEach { key ->
                when (val value = json.get(key)) {
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                    is Long -> putLong(key, value)
                    is Double -> putFloat(key, value.toFloat())
                    is JSONArray -> putStringSet(
                        key,
                        (0 until value.length()).map { value.optString(it) }.toSet()
                    )
                    else -> Unit
                }
            }
        }
    }

    /**
     * Every per-source config file (`source_<id>_preferences`), keyed by file name. These hold the
     * API keys 白い熊 typed in, which is exactly why this is its own ticked category rather than
     * being folded into "App settings" — carrying keys to another install is a conscious choice.
     */
    private fun sourcesJson(context: Context): JSONObject = JSONObject().apply {
        sourcePrefsNames(context).forEach { name -> put(name, prefsJson(context, name)) }
    }

    private fun restoreSources(context: Context, json: JSONObject) {
        json.keys().forEach { name ->
            if (!name.startsWith(SOURCE_PREFS_PREFIX)) return@forEach
            (json.opt(name) as? JSONObject)?.let { restorePrefs(context, name, it) }
        }
    }

    private fun sourcePrefsNames(context: Context): List<String> {
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".xml") }
            ?.map { it.name.removeSuffix(".xml") }
            ?.filter { it.startsWith(SOURCE_PREFS_PREFIX) }
            ?.sorted()
            ?: emptyList()
    }

    // ------------------------------------------------------------------ locations

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LocationEntryPoint {
        fun locationRepository(): LocationRepository
    }

    private fun locationRepository(context: Context): LocationRepository =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            LocationEntryPoint::class.java
        ).locationRepository()

    /**
     * The locations, as identity only: where they are, what they are called and which source
     * answers for each feature. The cached weather itself is deliberately **not** in the archive —
     * it is a download, it goes stale in an hour, and it would dwarf everything else in the zip.
     */
    private suspend fun locationsJson(context: Context): JSONObject {
        val locations = locationRepository(context).getAllLocations(withParameters = false)
        return JSONObject().apply {
            put(
                "locations",
                JSONArray(
                    locations.map { location ->
                        JSONObject().apply {
                            put("latitude", location.latitude)
                            put("longitude", location.longitude)
                            put("timeZone", location.timeZone.id)
                            put("country", location.country)
                            location.countryCode?.let { put("countryCode", it) }
                            location.admin1?.let { put("admin1", it) }
                            location.admin1Code?.let { put("admin1Code", it) }
                            location.admin2?.let { put("admin2", it) }
                            location.admin2Code?.let { put("admin2Code", it) }
                            location.admin3?.let { put("admin3", it) }
                            location.admin3Code?.let { put("admin3Code", it) }
                            location.admin4?.let { put("admin4", it) }
                            location.admin4Code?.let { put("admin4Code", it) }
                            put("city", location.city)
                            location.cityId?.let { put("cityId", it) }
                            location.district?.let { put("district", it) }
                            location.customName?.let { put("customName", it) }
                            put("forecastSource", location.forecastSource)
                            location.currentSource?.let { put("currentSource", it) }
                            location.airQualitySource?.let { put("airQualitySource", it) }
                            location.pollenSource?.let { put("pollenSource", it) }
                            location.minutelySource?.let { put("minutelySource", it) }
                            location.alertSource?.let { put("alertSource", it) }
                            location.normalsSource?.let { put("normalsSource", it) }
                            location.reverseGeocodingSource?.let { put("reverseGeocodingSource", it) }
                            put("isCurrentPosition", location.isCurrentPosition)
                        }
                    }
                )
            )
        }
    }

    private suspend fun restoreLocations(context: Context, json: JSONObject) {
        val array = json.optJSONArray("locations") ?: return
        val locations = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            Location(
                latitude = item.optDouble("latitude", 0.0),
                longitude = item.optDouble("longitude", 0.0),
                timeZone = TimeZone.getTimeZone(item.optString("timeZone", "GMT")),
                country = item.optString("country", ""),
                countryCode = item.optStringOrNull("countryCode"),
                admin1 = item.optStringOrNull("admin1"),
                admin1Code = item.optStringOrNull("admin1Code"),
                admin2 = item.optStringOrNull("admin2"),
                admin2Code = item.optStringOrNull("admin2Code"),
                admin3 = item.optStringOrNull("admin3"),
                admin3Code = item.optStringOrNull("admin3Code"),
                admin4 = item.optStringOrNull("admin4"),
                admin4Code = item.optStringOrNull("admin4Code"),
                city = item.optString("city", ""),
                cityId = item.optStringOrNull("cityId"),
                district = item.optStringOrNull("district"),
                customName = item.optStringOrNull("customName"),
                forecastSource = item.optString("forecastSource", "openmeteo"),
                currentSource = item.optStringOrNull("currentSource"),
                airQualitySource = item.optStringOrNull("airQualitySource"),
                pollenSource = item.optStringOrNull("pollenSource"),
                minutelySource = item.optStringOrNull("minutelySource"),
                alertSource = item.optStringOrNull("alertSource"),
                normalsSource = item.optStringOrNull("normalsSource"),
                reverseGeocodingSource = item.optStringOrNull("reverseGeocodingSource"),
                isCurrentPosition = item.optBoolean("isCurrentPosition", false)
            )
        }
        // addAll upserts on the location's formatted id, which is the merge-per-key the family
        // expects: re-importing the same archive updates rows instead of duplicating them.
        if (locations.isNotEmpty()) locationRepository(context).addAll(locations)
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null

    // ------------------------------------------------------- newest backup scan

    /** Newest `shiroikuma-tenki_*.zip` in the tree, as `name to lastModified`. */
    fun newestBackup(context: Context, treeUri: Uri): Pair<String, Long>? = runCatching {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        var best: Pair<String, Long>? = null
        context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: continue
                // Half-written archives carry .part and are never "the latest backup".
                if (!name.startsWith(FILE_PREFIX) || !name.endsWith(".zip")) continue
                val modified = cursor.getLong(1)
                val current = best
                if (current == null || modified > current.second) best = name to modified
            }
        }
        best
    }.getOrNull()

    /** The chosen folder's own display name, for the "Backup folder" box. */
    fun treeDisplayName(context: Context, treeUri: Uri): String? = runCatching {
        val document = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        context.contentResolver.query(
            document,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    fun formatTimestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(millis))

    /** Human size for the automation reply — `4.6 MB`, `1.20 GB`. */
    fun humanSize(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> String.format(Locale.ROOT, "%.2f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024L -> String.format(Locale.ROOT, "%.1f kB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private const val SOURCE_PREFS_PREFIX = "source_"
}
