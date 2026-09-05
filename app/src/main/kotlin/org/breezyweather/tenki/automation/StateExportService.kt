/*
 * 白い熊 天気 (shiroikuma-tenki) fork: the 保存復元 contract's headless export.
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package org.breezyweather.tenki.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.breezyweather.R
import org.breezyweather.tenki.TenkiBackup
import org.breezyweather.tenki.TenkiUiConfig
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the 保存復元 export off the broadcast window.
 *
 * A manifest receiver cannot hold an export — `goAsync()` does not extend the broadcast timeout, and
 * overrunning it gets the process ANR'd mid-write. So [StateExportReceiver] only checks the gate and
 * starts this service, which does the whole run, reports progress, sends **exactly one** terminal
 * reply, and stops itself.
 */
class StateExportService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // **The extras are read BEFORE the notification is posted, and that order is load-bearing.**
        // `startForeground()` can itself be refused on API 31+, and a refusal we cannot answer is
        // the worst of the three outcomes: the caller sits out its whole timeout on an export that
        // never began. Reading a handful of extras costs microseconds, so it does not endanger the
        // five-second window the system gives us to post the notification.
        val request = intent ?: run {
            runCatching { startInForeground() }
            stopEverything()
            return START_NOT_STICKY
        }

        val replyAction = request.getStringExtra(StateExportReceiver.EXTRA_REPLY_ACTION)
        val replyPackage = request.getStringExtra(StateExportReceiver.EXTRA_REPLY_PACKAGE)
        val replyId = request.getStringExtra(StateExportReceiver.EXTRA_REPLY_ID)
        val progressAction = request.getStringExtra(StateExportReceiver.EXTRA_PROGRESS_ACTION)
        val pathOverride = request.getStringExtra(StateExportReceiver.EXTRA_PATH)
        val items = request.getStringExtra(StateExportReceiver.EXTRA_ITEMS)

        val replied = AtomicBoolean(false)
        fun reply(result: String) {
            if (!replied.compareAndSet(false, true)) return
            if (replyAction == null || replyPackage == null) return
            StateExportReceiver.reply(this, replyAction, replyPackage, replyId, result)
        }

        // Now the notification, guarded. Refused, we answer and leave rather than letting the
        // exception escape `onStartCommand` and take the process down — and rather than unwinding
        // silently, which the caller cannot tell apart from an app that never implemented this.
        try {
            startInForeground()
        } catch (e: Throwable) {
            reply("ERROR:${e.message ?: e.javaClass.simpleName}")
            stopEverything()
            return START_NOT_STICKY
        }

        // The guard is process-local and released in a finally: persist it and one crash wedges the
        // app for good.
        if (!running.compareAndSet(false, true)) {
            reply("ERROR:export already running")
            stopEverything()
            return START_NOT_STICKY
        }
        cancelRequested = false

        scope.launch {
            val wakeLock = acquireWakeLock()
            try {
                val categories = resolveCategories(items)
                val result = runExport(
                    categories = categories,
                    pathOverride = pathOverride,
                    progressAction = progressAction,
                    replyPackage = replyPackage,
                    replyId = replyId
                )
                reply(
                    "OK:${result.path}|${result.bytes}|${TenkiBackup.humanSize(result.bytes)}|" +
                        "${result.categories} categories"
                )
            } catch (e: Throwable) {
                reply(
                    if (cancelRequested) {
                        "ERROR:cancelled"
                    } else {
                        "ERROR:${e.message ?: e.javaClass.simpleName}"
                    }
                )
            } finally {
                running.set(false)
                runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
                stopEverything()
            }
        }
        return START_NOT_STICKY
    }

    private data class Written(val path: String, val bytes: Long, val categories: Int)

    /**
     * Directory precedence, per the contract: the `path` extra → the app's configured export
     * directory → `ERROR:no-directory`. Writing to an arbitrary absolute path needs
     * All-Files-Access, which a weather app has no business holding, so `path` is honoured only if
     * the grant happens to be there and otherwise falls through to our own SAF folder.
     */
    private suspend fun runExport(
        categories: Set<TenkiBackup.Cat>,
        pathOverride: String?,
        progressAction: String?,
        replyPackage: String?,
        replyId: String?,
    ): Written {
        val reporter = ProgressReporter(this, progressAction, replyPackage, replyId)
        val configuredDir = TenkiUiConfig(this).exportDir

        if (!pathOverride.isNullOrBlank() && Environment.isExternalStorageManager()) {
            val dir = File(pathOverride).apply { mkdirs() }
            if (!dir.isDirectory) error("no-directory")
            val finalFile = File(dir, TenkiBackup.exportFileName())
            val part = File(dir, "${finalFile.name}.part")
            try {
                FileOutputStream(part).use { out ->
                    TenkiBackup.writeZip(this, categories, out, reporter) { cancelRequested }
                }
                if (cancelRequested) error("cancelled")
                if (!part.renameTo(finalFile)) error("could not finish writing the backup")
                return Written(finalFile.absolutePath, finalFile.length(), categories.size)
            } catch (failure: Throwable) {
                part.delete()
                throw failure
            }
        }

        if (configuredDir.isBlank()) {
            // Nothing we may write to: say which of the two it is, so the caller can offer the fix.
            error(if (pathOverride.isNullOrBlank()) "no-directory" else "no-storage-access")
        }

        val treeUri = Uri.parse(configuredDir)
        val result = TenkiBackup.exportToTree(
            context = this,
            treeUri = treeUri,
            categories = categories,
            onProgress = reporter,
            isCancelled = { cancelRequested }
        )
        return Written(displayPath(treeUri, result.name), result.bytes, result.categories.size)
    }

    /** A readable path for the reply — SAF gives us a tree uri, not a filesystem path. */
    private fun displayPath(treeUri: Uri, name: String): String {
        val tree = runCatching { Uri.decode(treeUri.toString().substringAfterLast(':')) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        return if (tree != null) "/storage/emulated/0/$tree/$name" else name
    }

    private fun resolveCategories(items: String?): Set<TenkiBackup.Cat> {
        val ids = StateExportReceiver.parseItems(items)
        // "items absent means your DEFAULT set, not everything" — the contract's own wording.
        if (ids.isEmpty()) return TenkiBackup.Cat.defaults
        return ids.mapNotNull { TenkiBackup.Cat.ofId(it) }.toSet()
    }

    // --------------------------------------------------------------------- plumbing

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "保存復元",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sync)
            .setContentTitle(getString(R.string.brand_name))
            .setContentText("Exporting settings…")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )
    }

    private fun acquireWakeLock(): PowerManager.WakeLock? = runCatching {
        getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "shiroikuma-tenki:export")
            ?.apply { acquire(WAKELOCK_TIMEOUT_MS) }
    }.getOrNull()

    private fun stopEverything() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Progress broadcasts: real counts, never a percentage, and `item` on every one so the caller
     * can highlight the row actually being written. Throttled to one per 500 ms, with a heartbeat
     * so a long single step still proves the export is alive.
     */
    internal class ProgressReporter(
        private val context: Context,
        private val action: String?,
        private val replyPackage: String?,
        private val replyId: String?,
        /**
         * A second key carrying the same id, for [AutomationProvider]'s door — which correlates by
         * `job_id`, not `reply_id`. Null on the broadcast half, so its progress extras stay byte
         * for byte what 自由作業盤 already parses.
         */
        private val alsoIdKey: String? = null,
    ) : TenkiBackup.Progress {
        private var lastSentAt = 0L

        override fun report(cat: TenkiBackup.Cat, position: Int, total: Int) {
            if (action == null || replyPackage == null) return
            val now = System.currentTimeMillis()
            // A category boundary is always worth reporting; the throttle exists for chattier steps.
            if (now - lastSentAt < THROTTLE_MS && position != total) return
            lastSentAt = now
            context.sendBroadcast(
                Intent(action).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(StateExportReceiver.EXTRA_REPLY_ID, replyId)
                    alsoIdKey?.let { putExtra(it, replyId) }
                    putExtra("app", context.getString(R.string.brand_name))
                    putExtra("item", cat.id)
                    putExtra("text", "区分 $position/$total — ${cat.label}")
                    putExtra("current", position.toLong())
                    putExtra("total", total.toLong())
                    putExtra("unit", "区分")
                }
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "tenki_state_export"
        private const val NOTIFICATION_ID = 9701
        private const val THROTTLE_MS = 500L
        private const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L

        /** Process-local, never persisted: a stuck flag on disk would wedge the app permanently. */
        private val running = AtomicBoolean(false)

        @Volatile
        private var cancelRequested = false

        /**
         * Signals the running export to unwind at the next category boundary. Safe at any time: a
         * cancel with nothing running is a silent no-op.
         */
        fun requestCancel(context: Context) {
            if (running.get()) cancelRequested = true
        }
    }
}
