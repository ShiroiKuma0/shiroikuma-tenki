/*
 * 白い熊 天気 (shiroikuma-tenki) fork: the 保存復元 contract's exported receiver.
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package org.breezyweather.tenki.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import org.breezyweather.tenki.TenkiBackup

/**
 * The three token-gated actions of the sister-app 保存復元 contract:
 *
 *  * `<pkg>.action.LIST_CATEGORIES` — instant, answers with the pickable categories;
 *  * `<pkg>.action.EXPORT_STATE` — hands straight off to [StateExportService] and returns, because
 *    a manifest receiver must reach `finish()` inside Android's broadcast window (~10 s foreground,
 *    ~60 s otherwise) or the system ANRs the app **mid-export**;
 *  * `<pkg>.action.CANCEL_EXPORT` — signals the running export and replies nothing at all.
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
        ) {
            context.sendBroadcast(
                Intent(replyAction).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(EXTRA_REPLY_ID, replyId)
                    putExtra("result", result)
                }
            )
        }
    }
}
