/*
 * 白い熊 天気 (shiroikuma-tenki) fork: the 保存復元 contract's exported receiver.
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package org.breezyweather.tenki.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.breezyweather.tenki.TenkiBackup

/**
 * The token-gated actions of the sister-app automation contract — the 保存復元 export, and the reads
 * that feed 白い熊's HUAWEI band its weather:
 *
 *  * `<pkg>.action.LIST_CATEGORIES` — instant, answers with the pickable categories;
 *  * `<pkg>.action.EXPORT_STATE` — hands straight off to [StateExportService] and returns, because
 *    a manifest receiver must reach `finish()` inside Android's broadcast window (~10 s foreground,
 *    ~60 s otherwise) or the system ANRs the app **mid-export**;
 *  * `<pkg>.action.CANCEL_EXPORT` — signals the running export and replies nothing at all;
 *  * `<pkg>.action.LIST_LOCATIONS` / `LIST_PROVIDERS` / `QUERY_WEATHER` — cache reads, answered off
 *    the main thread through [TenkiWeatherQuery] under a timeout that keeps them inside that same
 *    window. See that file for why an alternate source can never report an observation.
 *
 * The receiver never exports anything itself and never blocks.
 */
class StateExportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: return
        val token = intent.getStringExtra(EXTRA_TOKEN)

        val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION)
        val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE)
        val replyId = intent.getStringExtra(EXTRA_REPLY_ID)

        when (action) {
            "${app.packageName}.action.CANCEL_EXPORT" -> {
                // Fire and forget: no reply of its own, and a silent no-op when nothing is running.
                if (!TenkiAutomationAuth.enabled(app)) return
                if (!TenkiAutomationAuth.isTokenValid(app, token)) return
                StateExportService.requestCancel(app)
            }

            "${app.packageName}.action.LIST_CATEGORIES" -> {
                if (replyAction == null || replyPackage == null) return
                val error = gate(app, token)
                if (error != null) {
                    reply(app, replyAction, replyPackage, replyId, error)
                    return
                }
                reply(app, replyAction, replyPackage, replyId, "OK:" + categoryLines())
            }

            "${app.packageName}.action.EXPORT_STATE" -> {
                if (replyAction == null || replyPackage == null) return
                val error = gate(app, token)
                if (error != null) {
                    reply(app, replyAction, replyPackage, replyId, error)
                    return
                }

                val items = intent.getStringExtra(EXTRA_ITEMS)
                val unknown = unknownItems(items)
                if (unknown.isNotEmpty()) {
                    reply(
                        app,
                        replyAction,
                        replyPackage,
                        replyId,
                        "ERROR:unknown category in items: ${unknown.joinToString(",")}"
                    )
                    return
                }

                ContextCompat.startForegroundService(
                    app,
                    Intent(app, StateExportService::class.java).apply {
                        putExtra(EXTRA_PATH, intent.getStringExtra(EXTRA_PATH))
                        putExtra(EXTRA_ITEMS, items)
                        putExtra(EXTRA_PROGRESS_ACTION, intent.getStringExtra(EXTRA_PROGRESS_ACTION))
                        putExtra(EXTRA_REPLY_ACTION, replyAction)
                        putExtra(EXTRA_REPLY_PACKAGE, replyPackage)
                        putExtra(EXTRA_REPLY_ID, replyId)
                    }
                )
            }

            "${app.packageName}.action.LIST_LOCATIONS",
            "${app.packageName}.action.LIST_PROVIDERS",
            "${app.packageName}.action.QUERY_WEATHER" -> {
                if (replyAction == null || replyPackage == null) return
                val error = gate(app, token)
                if (error != null) {
                    reply(app, replyAction, replyPackage, replyId, error)
                    return
                }

                // Unlike the export, a cache read is milliseconds and comfortably fits the broadcast
                // window — but it still must not run on the main thread, and goAsync() does NOT
                // extend that window, so the timeout is what guarantees we always reach finish().
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    val answer = try {
                        withTimeout(READ_TIMEOUT_MS) { readAnswer(app, action, intent) }
                    } catch (e: Throwable) {
                        TenkiWeatherQuery.Answer("ERROR:${e.message ?: e.javaClass.simpleName}")
                    }
                    try {
                        reply(app, replyAction, replyPackage, replyId, answer.result, answer.extras)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    /** Routes one read action to [TenkiWeatherQuery]. Off the main thread, already gated. */
    private suspend fun readAnswer(
        app: Context,
        action: String,
        intent: Intent,
    ): TenkiWeatherQuery.Answer {
        // Deliberately refused rather than half-served: going out to a source would need the
        // foreground-service path the export uses, and a network fetch inside a broadcast window is
        // precisely the ANR this contract was shaped to avoid. The cache is what a watch face wants.
        if (intent.getStringExtra(EXTRA_REFRESH).isTrue()) {
            return TenkiWeatherQuery.Answer("ERROR:refresh not supported")
        }

        val location = intent.getStringExtra(EXTRA_LOCATION)
        val latitude = intent.getStringExtra(EXTRA_LATITUDE)
        val longitude = intent.getStringExtra(EXTRA_LONGITUDE)

        return when (action) {
            "${app.packageName}.action.LIST_LOCATIONS" -> TenkiWeatherQuery.listLocations(app)
            "${app.packageName}.action.LIST_PROVIDERS" -> TenkiWeatherQuery.listProviders(
                app,
                location,
                latitude,
                longitude,
                all = intent.getStringExtra(EXTRA_ALL).isTrue()
            )
            else -> TenkiWeatherQuery.queryWeather(
                app,
                location,
                latitude,
                longitude,
                intent.getStringExtra(EXTRA_PROVIDER)
            )
        }
    }

    /** @return the `ERROR:` line to answer with, or null when the request may proceed. */
    private fun gate(context: Context, token: String?): String? = when {
        !TenkiAutomationAuth.enabled(context) -> "ERROR:automation disabled"
        !TenkiAutomationAuth.isTokenValid(context, token) -> "ERROR:bad token"
        else -> null
    }

    companion object {
        const val EXTRA_TOKEN = "token"
        const val EXTRA_PATH = "path"
        const val EXTRA_ITEMS = "items"
        const val EXTRA_PROGRESS_ACTION = "progress_action"
        const val EXTRA_REPLY_ACTION = "reply_action"
        const val EXTRA_REPLY_PACKAGE = "reply_package"
        const val EXTRA_REPLY_ID = "reply_id"

        // The read half of the contract
        const val EXTRA_LOCATION = "location"
        const val EXTRA_PROVIDER = "provider"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_ALL = "all"
        const val EXTRA_REFRESH = "refresh"

        /** Comfortably inside Android's ~10 s foreground broadcast window, which goAsync() cannot extend. */
        private const val READ_TIMEOUT_MS = 8_000L

        /** Tasker-shaped booleans arrive as whatever the task author typed. */
        private fun String?.isTrue(): Boolean =
            this?.trim()?.lowercase() in setOf("1", "true", "yes", "on")

        /** `id<TAB>label[<TAB>parent[<TAB>on|off]]`, one per line. Flat list: no parents here. */
        fun categoryLines(): String = TenkiBackup.Cat.entries.joinToString("\n") { cat ->
            if (cat.onByDefault) "${cat.id}\t${cat.label}" else "${cat.id}\t${cat.label}\t\toff"
        }

        /** The ids in an `items` extra that this app does not know. */
        fun unknownItems(items: String?): List<String> = parseItems(items)
            .filter { TenkiBackup.Cat.ofId(it) == null }

        fun parseItems(items: String?): List<String> = items
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        /**
         * A fresh broadcast is the ONLY reply channel that survives EMUI: no ResultReceiver, no
         * PendingIntent, no reliance on the ordered-broadcast result.
         */
        fun reply(
            context: Context,
            replyAction: String,
            replyPackage: String,
            replyId: String?,
            result: String,
            extras: Map<String, String> = emptyMap(),
        ) {
            context.sendBroadcast(
                Intent(replyAction).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(EXTRA_REPLY_ID, replyId)
                    putExtra("result", result)
                    // Every figure additionally broken out, so nothing has to be parsed back out of
                    // the status line. Strings only: that is what survives EMUI.
                    extras.forEach { (key, value) -> putExtra(key, value) }
                }
            )
        }
    }
}
