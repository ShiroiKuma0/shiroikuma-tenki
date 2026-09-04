/*
 * 白い熊 天気 (shiroikuma-tenki) fork: the sister-app contract's v2 data door.
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package org.breezyweather.tenki.automation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import org.breezyweather.tenki.TenkiBackup

/**
 * The data door: export this app's own state, and put it back, for a caller we can identify.
 *
 * ## Why a provider and not the broadcast receiver next to it
 *
 * **A broadcast cannot tell you who sent it.** v1's answer to that was the shared token, which
 * cannot survive the wipe this feature exists to recover from. A provider gets the caller's
 * identity from the framework for free — see [AutomationCallers] for what is actually checked, and
 * why a `shiroikuma.*` prefix would have been *weaker* than the token it replaced.
 *
 * **And a list needs a synchronous answer.** 白い熊 応用管理 draws a row per installed app before
 * any export exists; a broadcast round trip per app to fill a list is the wrong shape entirely.
 *
 * ## What does NOT happen here
 *
 * The payload. [call] validates, starts a foreground service and returns — megabytes over minutes
 * inside a binder call would block the caller, report no progress, refuse cancellation and die
 * silently if this process were killed. The bytes go through a descriptor the caller opened, and
 * the terminal answer comes back on the broadcast this family already proved on EMUI.
 *
 * ## `import` lives ONLY here
 *
 * It never gets a broadcast action. An import overwrites this app's locations, sources and API
 * keys, and [StateExportReceiver] is `exported="true"` with no permission — an import there would
 * let any app on the phone wipe this one.
 */
class AutomationProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    /**
     * Every method answers a [Bundle] with [KEY_RESULT] — `OK…` or `ERROR:…`, the same vocabulary
     * [StateExportReceiver] uses, so a caller has one grammar to parse rather than two.
     *
     * A refusal is **returned, never thrown**: an exception across a binder reaches the caller as a
     * `RuntimeException` carrying our stack trace, which tells 白い熊 nothing and tells a
     * misbehaving caller rather more than it should.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context?.applicationContext ?: return fail("ERROR:not ready")

        // WHO, before WHAT. A caller we cannot identify gets the same answer whatever it asked for.
        when (val verdict = AutomationCallers.verify(ctx, callingPackage)) {
            is AutomationCallers.Verdict.Refused -> return fail(verdict.why)
            AutomationCallers.Verdict.Allowed -> Unit
        }
        // Then this app's own switches — the token is ignored unless this app asks for one.
        TenkiAutomationAuth.refuse(ctx, extras?.getString(KEY_TOKEN))?.let { return fail(it) }

        return when (method) {
            METHOD_DESCRIBE -> ok(describe(ctx))
            METHOD_EXPORT -> start(ctx, extras, importing = false)
            METHOD_IMPORT -> start(ctx, extras, importing = true)
            METHOD_CANCEL -> {
                AutomationJobs.cancel(extras?.getString(KEY_JOB_ID))
                ok("OK:cancelled")
            }
            else -> fail("ERROR:unknown method: $method")
        }
    }

    /**
     * What this app would export, answered without exporting anything.
     *
     * Returned from the call rather than written into the archive, deliberately: 応用管理 must draw
     * a row **before** an export exists, and at restore must judge compatibility before streaming
     * tens of megabytes into an app that would reject them — which it cannot do if the header is
     * buried inside an encrypted archive.
     */
    private fun describe(ctx: Context): String {
        val pkg = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION")
        val code = pkg.versionCode
        // The default set, not the catalogue: `contains` describes what a backup of this app would
        // actually hold, and imported fonts are opt-out here exactly as they are in the picker.
        val contains = TenkiBackup.Cat.defaults.joinToString(",") { "\"${it.label}\"" }
        return "OK:" + """
            {"app_id":"${ctx.packageName}","version_code":$code,
             "version_name":"${pkg.versionName.orEmpty()}","format":$FORMAT,
             "min_format_readable":$MIN_FORMAT_READABLE,"requires_launch_first":false,
             "contains":[$contains]}
        """.trimIndent().replace("\n", "")
    }

    /**
     * Hand the descriptor to a foreground service and get out of the way.
     *
     * The descriptor is **duplicated** before it leaves this method. The one in [extras] belongs to
     * the binder transaction and is closed the moment `call()` returns; a service reading it
     * afterwards would find it shut. That is a bug you only see under load, so it is not left to
     * the service to remember.
     *
     * The service start is caught rather than allowed to propagate. Android 12+ refuses a
     * background foreground-service start with a `ForegroundServiceStartNotAllowedException`, and
     * this door is called precisely when this app is not running — letting that escape would break
     * the rule above about never throwing across the binder, and would reach 白い熊 as a stack
     * trace instead of a line 応用管理 can print on the failed row.
     */
    private fun start(ctx: Context, extras: Bundle?, importing: Boolean): Bundle {
        val fd = extras?.getParcelable<ParcelFileDescriptor>(KEY_FD)
            ?: return fail("ERROR:no descriptor")
        val dup = runCatching { fd.dup() }.getOrNull() ?: return fail("ERROR:descriptor unusable")
        val jobId = AutomationJobs.begin()
        // ONE flag, cleaned up in one place. The alternative — remembering to close the descriptor
        // on each failure branch — is a leak waiting for the next branch somebody adds, and a
        // leaked descriptor holds the caller's file open so it can never be checksummed or
        // encrypted. Ownership passes to the service at exactly one point, and until it does this
        // method still owns it.
        var handedOff = false
        try {
            AutomationDataService.start(ctx, jobId, dup, importing, extras)
            handedOff = true
            return ok("OK:$jobId")
        } catch (e: Throwable) {
            return fail("ERROR:${e.message ?: e.javaClass.simpleName}")
        } finally {
            if (!handedOff) {
                AutomationJobs.finish(jobId)
                runCatching { dup.close() }
            }
        }
    }

    private fun ok(result: String) = Bundle().apply { putString(KEY_RESULT, result) }
    private fun fail(why: String) = Bundle().apply { putString(KEY_RESULT, why) }

    // A provider that is only ever `call()`ed still has to answer these. Refusing loudly beats
    // returning an empty cursor, which reads downstream as "there is no data" rather than "wrong
    // door".
    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? =
        throw UnsupportedOperationException("automation is call() only")

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("automation is call() only")

    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    companion object {
        const val METHOD_DESCRIBE = "describe"
        const val METHOD_EXPORT = "export"
        const val METHOD_IMPORT = "import"
        const val METHOD_CANCEL = "cancel"

        const val KEY_RESULT = "result"
        const val KEY_FD = "fd"
        const val KEY_TOKEN = "token"
        const val KEY_JOB_ID = "job_id"
        const val KEY_ITEMS = "items"
        const val KEY_REPLY_ACTION = "reply_action"
        const val KEY_REPLY_PACKAGE = "reply_package"
        const val KEY_PROGRESS_ACTION = "progress_action"

        /**
         * This app's archive format — [TenkiBackup.VERSION], so the two can never disagree. Bumped
         * when an older build could no longer read what we write.
         */
        const val FORMAT = TenkiBackup.VERSION

        /**
         * The oldest archive this build can still read.
         *
         * Version skew has a direction: old data into a newer app is normally fine, because an app
         * migrates its own storage; newer data into an older app is not. This field is what lets a
         * caller refuse the second case at discovery time, before anything is streamed.
         */
        const val MIN_FORMAT_READABLE = 1
    }
}
