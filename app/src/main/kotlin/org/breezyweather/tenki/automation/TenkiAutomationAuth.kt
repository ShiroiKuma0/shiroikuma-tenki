/*
 * 白い熊 天気 (shiroikuma-tenki) fork: the 保存復元 automation gate — v2, where the switch ships ON
 * and the token became something a caller may be asked for rather than the gate itself.
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package org.breezyweather.tenki.automation

import android.content.Context
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The gate in front of every automation surface this app exposes — the 保存復元 export, the band's
 * weather reads, and the data door of [AutomationProvider].
 *
 * ## Why the defaults inverted in v2 (2026-09-04)
 *
 * v1 shipped closed: the switch defaulted to false and a caller also had to present a 48-character
 * secret 白い熊 had pasted from this app's settings into the caller's. That is the wrong shape for
 * where the family is going — **a pasted secret cannot survive a wipe**, and the case this now
 * exists to serve is 白い熊 応用管理 restoring apps *and their data* onto a clean phone, where
 * nothing has been configured and nobody has pasted anything. A gate that only works once the phone
 * is already set up is no gate for setting the phone up.
 *
 * So the switch defaults ON, the token defaults to not-required, and what actually protects the
 * dangerous half — the descriptor door, where a caller says where this app's data is written — is
 * [AutomationCallers], which asks the framework who is calling instead of asking the caller.
 *
 * Deliberately in **its own preferences file**: the token must never travel inside a backup, and
 * [org.breezyweather.tenki.TenkiBackup] only ever reads the app's own settings file and our
 * `tenki_ui` store.
 */
object TenkiAutomationAuth {

    private const val PREFS = "tenki_automation"
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_REQUIRE_TOKEN = "automation_require_token"
    private const val KEY_TOKEN = "automation_token"
    private const val TOKEN_BYTES = 24

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Default **true** in v2. It stays a switch rather than being removed because it is the only
     * way to close this app off, and a feature that can be turned on but never off is one 白い熊
     * cannot retreat from.
     */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, value) }
    }

    /** Default **false** — a caller is not asked for the token unless 白い熊 asks for it. */
    fun requireToken(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REQUIRE_TOKEN, false)

    fun setRequireToken(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_REQUIRE_TOKEN, value) }
    }

    /**
     * **The one gate.** Every entry point calls this and nothing writes the two checks out by hand:
     * two checks spelled out at each call site is how "disabled" and "bad token" drift apart across
     * forty-two apps.
     *
     * A token handed to an app that does not require one is **IGNORED, never an error**. Tokens
     * live in task arguments and workspace variables that outlive the setting they were pasted for,
     * so a caller still sending one — because it was configured last year, or because another app
     * on the batch does want one — must be served. Refusing it would turn "白い熊 turned a switch
     * off" into "half the batch mysteriously fails".
     *
     * @return null to proceed, otherwise the exact `ERROR:` line to answer with.
     */
    fun refuse(context: Context, candidate: String?): String? = when {
        !enabled(context) -> "ERROR:automation disabled"
        requireToken(context) && !isTokenValid(context, candidate) -> "ERROR:bad token"
        else -> null
    }

    /** Generated lazily on first read, so the settings row always shows a value. */
    fun token(context: Context): String {
        val stored = prefs(context).getString(KEY_TOKEN, null)
        if (!stored.isNullOrBlank()) return stored
        return regenerate(context)
    }

    fun regenerate(context: Context): String {
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs(context).edit { putString(KEY_TOKEN, token) }
        return token
    }

    /**
     * Constant-time comparison — kept for the case where the token *is* required. A token check
     * must not leak its answer through timing.
     */
    fun isTokenValid(context: Context, candidate: String?): Boolean {
        if (candidate.isNullOrEmpty()) return false
        return MessageDigest.isEqual(candidate.toByteArray(), token(context).toByteArray())
    }

    /** `80922d8c…4c49a87c` — what the settings row shows. */
    fun abbreviated(token: String): String =
        if (token.length <= 20) token else "${token.take(8)}…${token.takeLast(8)}"
}
