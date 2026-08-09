/*
 * 白い熊 天気 (shiroikuma-tenki) fork: the 保存復元 automation gate — a switch that defaults to OFF
 * and a token compared in constant time.
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package org.breezyweather.tenki.automation

import android.content.Context
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Token infrastructure for the sister-app 保存復元 contract (白い熊 自由作業盤 drives this app's
 * export through a token-gated intent).
 *
 * Deliberately in **its own preferences file**: the token must never travel inside a backup, and
 * [org.breezyweather.tenki.TenkiBackup] only ever reads the app's own settings file and our
 * `tenki_ui` store. Nothing here is reachable until 白い熊 turns [enabled] on.
 */
object TenkiAutomationAuth {

    private const val PREFS = "tenki_automation"
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_TOKEN = "automation_token"
    private const val TOKEN_BYTES = 24

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Default **false** — the contract's first rule. */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, value) }
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

    /** Constant-time comparison — a token check must not leak its answer through timing. */
    fun isTokenValid(context: Context, candidate: String?): Boolean {
        if (candidate.isNullOrEmpty()) return false
        return MessageDigest.isEqual(candidate.toByteArray(), token(context).toByteArray())
    }

    /** `80922d8c…4c49a87c` — what the settings row shows. */
    fun abbreviated(token: String): String =
        if (token.length <= 20) token else "${token.take(8)}…${token.takeLast(8)}"
}
