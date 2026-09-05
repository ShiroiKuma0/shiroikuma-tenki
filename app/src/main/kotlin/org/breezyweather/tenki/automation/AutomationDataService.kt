/*
 * 白い熊 天気 (shiroikuma-tenki) fork: where the v2 data door's export and import actually run.
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
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.breezyweather.R
import org.breezyweather.tenki.TenkiBackup
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Where a data export or import actually runs.
 *
 * ## Why a foreground service and not the provider call
 *
 * The call returns in milliseconds; this can run for as long as the archive takes. Two hard reasons
 * it cannot be done anywhere cheaper:
 *
 *  * **A binder call holds the caller.** 応用管理 is drawing a list; a multi-second synchronous call
 *    would freeze its UI, report no progress and refuse cancellation.
 *  * **A backgrounded app writing for minutes is frozen mid-stream on this phone**, which yields a
 *    truncated archive underneath a success reply — the worst possible failure, because it is
 *    indistinguishable from a good backup until the day it is restored.
 *
 * ## The descriptor
 *
 * Already duplicated by [AutomationProvider] before it got here, because the original belongs to
 * the binder transaction and is closed the moment `call()` returns. This service owns the copy and
 * closes it in a `finally` — leaking one holds the caller's file open, and a caller cannot checksum
 * or encrypt a file that is still open.
 */
class AutomationDataService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val importing = intent?.getBooleanExtra(EXTRA_IMPORTING, false) == true

        // **Extras first, notification second, early returns third** — the order satisfies two
        // constraints that pull against each other.
        //
        // We were started with `startForegroundService()`, so the system gives us five seconds to
        // post a notification and kills the app with `ForegroundServiceDidNotStartInTimeException`
        // if we do not: hence the notification must come before any `return`. But `startForeground`
        // can itself be REFUSED on API 31+, and a refusal we cannot answer is worse here than
        // anywhere else in this app — [AutomationProvider] has already told the caller `OK:<job_id>`,
        // so dying quietly leaves it waiting on a job that no longer exists. Hence the extras, which
        // carry the only channel we have to say so, must be read before the notification.
        //
        // Reading extras is microseconds and returns nothing, so it endangers neither.
        val jobId = intent?.getStringExtra(EXTRA_JOB)
        val replyAction = intent?.getStringExtra(AutomationProvider.KEY_REPLY_ACTION)
        val replyPackage = intent?.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE)
        val progressAction = intent?.getStringExtra(AutomationProvider.KEY_PROGRESS_ACTION)

        try {
            startForeground(importing)
        } catch (e: Throwable) {
            // Answer on the job the provider already handed out, then release everything the job
            // owns: the descriptor is ours the moment the provider handed it over, and a leaked one
            // holds the caller's file open.
            if (jobId != null) {
                AutomationJobs.finish(jobId)
                HANDOVER.remove(jobId)?.let { pfd -> runCatching { pfd.close() } }
                if (!replyAction.isNullOrEmpty() && !replyPackage.isNullOrEmpty()) {
                    sendBroadcast(
                        Intent(replyAction).apply {
                            setPackage(replyPackage)
                            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                            putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                            putExtra(
                                AutomationProvider.KEY_RESULT,
                                "ERROR:${e.message ?: e.javaClass.simpleName}"
                            )
                        }
                    )
                }
            }
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (jobId == null) return stop(startId)
        val fd = HANDOVER.remove(jobId) ?: return stop(startId)

        val replied = AtomicBoolean(false)
        fun reply(result: String) {
            // Exactly one terminal answer per job, whatever path got here — a synchronous failure
            // and an asynchronous success must never both fire. The same guard the broadcast half
            // of the contract has carried since the first sister app.
            if (!replied.compareAndSet(false, true)) return
            AutomationJobs.finish(jobId)
            if (replyAction.isNullOrEmpty() || replyPackage.isNullOrEmpty()) return
            sendBroadcast(
                Intent(replyAction).apply {
                    setPackage(replyPackage)
                    // Without this a backgrounded caller never hears the answer, and on a clean
                    // phone the caller may not have been launched at all.
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                    putExtra(AutomationProvider.KEY_RESULT, result)
                }
            )
        }

        scope.launch {
            try {
                fd.use { open ->
                    if (importing) {
                        runImport(open, ::reply)
                    } else {
                        runExport(
                            jobId = jobId,
                            fd = open,
                            items = intent.getStringExtra(AutomationProvider.KEY_ITEMS),
                            progressAction = progressAction,
                            replyPackage = replyPackage,
                            reply = ::reply
                        )
                    }
                }
            } catch (e: Throwable) {
                reply("ERROR:${e.message ?: e.javaClass.simpleName}")
            } finally {
                ServiceCompat.stopForeground(this@AutomationDataService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runExport(
        jobId: String,
        fd: ParcelFileDescriptor,
        items: String?,
        progressAction: String?,
        replyPackage: String?,
        reply: (String) -> Unit,
    ) {
        val cats = resolve(items) ?: run {
            reply("ERROR:unknown category in items: ${items.orEmpty()}")
            return
        }
        var written = 0L
        ParcelFileDescriptor.AutoCloseOutputStream(fd).use { out ->
            // Counted as it goes rather than stat'ed afterwards: the caller owns the file and we may
            // not be able to see it at all — it can be an anonymous pipe, or a descriptor into a
            // directory this app cannot list.
            //
            // A named class rather than an anonymous `object :` capturing a local, because AGP's
            // lint has been seen to crash on that shape when the same method also declares a local
            // function — which the reply path next door does.
            val counting = CountingOutputStream(out)
            TenkiBackup.writeZip(
                context = this,
                categories = cats,
                out = counting,
                onProgress = StateExportService.ProgressReporter(
                    this, progressAction, replyPackage, jobId, AutomationProvider.KEY_JOB_ID
                ),
                isCancelled = { AutomationJobs.isCancelled(jobId) }
            )
            written = counting.written
        }
        if (AutomationJobs.isCancelled(jobId)) reply("ERROR:cancelled")
        else reply("OK:$written|${cats.size} categories")
    }

    /**
     * Read the whole archive before touching anything.
     *
     * [TenkiBackup.restore] wants the bytes, and that is the right shape here for a reason beyond
     * convenience: a partial read that failed halfway would import half an archive, and a
     * half-restored app — locations back, sources and their API keys not — is worse than one that
     * refused.
     */
    private suspend fun runImport(fd: ParcelFileDescriptor, reply: (String) -> Unit) {
        // In memory rather than spooled to disk, deliberately and with a ceiling. Both
        // [TenkiBackup.categoriesIn] and [TenkiBackup.restore] walk the archive, so a single
        // non-seekable descriptor cannot serve them — something has to hold the bytes. Spooling to
        // cacheDir would only move where they sit, since `restore` still wants them all at once.
        // What spooling WOULD buy is protection from a caller handing us something enormous, and
        // the cap buys that directly: this app's archive is settings, locations and a handful of
        // imported fonts, so anything past the cap is a mistake, and failing with a line 応用管理
        // can print beats an OutOfMemoryError that kills the process mid-restore.
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                if (buffer.size() + read > MAX_IMPORT_BYTES) return@use null
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        } ?: run {
            reply("ERROR:archive larger than ${MAX_IMPORT_BYTES / (1024 * 1024)} MB")
            return
        }
        if (bytes.isEmpty()) {
            reply("ERROR:empty archive")
            return
        }
        // Every category the archive actually carries, not every category we know about: asking for
        // one the archive lacks is how a restore ends up reporting success over nothing.
        val present = runCatching { TenkiBackup.categoriesIn(bytes) }.getOrDefault(emptySet())
        if (present.isEmpty()) {
            reply("ERROR:archive carries no categories")
            return
        }
        val restored = TenkiBackup.restore(this, bytes, present)
        // 応用管理 force-stops us straight after this. That is deliberate and belongs on its side: a
        // running process writes its cached SharedPreferences back out at orderly shutdown and
        // would silently undo the import that just happened.
        reply("OK:$restored restored")
    }

    /** Counts what it forwards. See [runExport] for why the byte total cannot be stat'ed. */
    private class CountingOutputStream(private val out: OutputStream) : OutputStream() {
        var written = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            written++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            written += len
        }

        override fun flush() = out.flush()
    }

    /** Absent/empty `items` means this app's DEFAULT set, which is not the same as everything. */
    private fun resolve(items: String?): Set<TenkiBackup.Cat>? {
        if (items.isNullOrBlank()) return TenkiBackup.Cat.defaults
        val wanted = items.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val found = wanted.mapNotNull { TenkiBackup.Cat.ofId(it) }
        return if (found.size == wanted.size) found.toSet() else null
    }

    /** `startForeground()` must land inside 5 s of the service starting or the system kills us. */
    private fun startForeground(importing: Boolean) {
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
            .setContentText(if (importing) "Restoring settings…" else "Exporting settings…")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        )
    }

    /** Reachable with the notification already posted, so it comes down here too. */
    private fun stop(startId: Int): Int {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "tenki_automation_data"

        /** 9701 is [StateExportService]'s; the two can legitimately be up at the same time. */
        private const val NOTIFICATION_ID = 9702
        private const val EXTRA_JOB = "job"
        private const val EXTRA_IMPORTING = "importing"

        /**
         * The ceiling on an incoming archive. Generous next to what this app actually writes —
         * settings, locations and imported fonts — and far below what `largeHeap` would survive,
         * so it only ever catches a caller handing us the wrong file.
         */
        private const val MAX_IMPORT_BYTES = 128 * 1024 * 1024

        /**
         * The descriptor's way across, because an Intent is the wrong vehicle for one.
         *
         * A `ParcelFileDescriptor` in an Intent extra is duplicated by the system on delivery and
         * the copy's lifetime stops being ours to reason about. Handing it through a map keyed by
         * the job id keeps exactly one open descriptor with exactly one owner — this service, which
         * closes it in a `finally`.
         */
        private val HANDOVER = ConcurrentHashMap<String, ParcelFileDescriptor>()

        fun start(
            context: Context,
            jobId: String,
            fd: ParcelFileDescriptor,
            importing: Boolean,
            extras: Bundle?,
        ) {
            HANDOVER[jobId] = fd
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AutomationDataService::class.java).apply {
                        putExtra(EXTRA_JOB, jobId)
                        putExtra(EXTRA_IMPORTING, importing)
                        putExtra(
                            AutomationProvider.KEY_ITEMS,
                            extras?.getString(AutomationProvider.KEY_ITEMS)
                        )
                        putExtra(
                            AutomationProvider.KEY_REPLY_ACTION,
                            extras?.getString(AutomationProvider.KEY_REPLY_ACTION)
                        )
                        putExtra(
                            AutomationProvider.KEY_REPLY_PACKAGE,
                            extras?.getString(AutomationProvider.KEY_REPLY_PACKAGE)
                        )
                        putExtra(
                            AutomationProvider.KEY_PROGRESS_ACTION,
                            extras?.getString(AutomationProvider.KEY_PROGRESS_ACTION)
                        )
                    }
                )
            } catch (e: Throwable) {
                // The service will never come for it, so the descriptor must not sit in the map
                // holding the caller's file open until the process dies.
                HANDOVER.remove(jobId)
                throw e
            }
        }
    }
}
