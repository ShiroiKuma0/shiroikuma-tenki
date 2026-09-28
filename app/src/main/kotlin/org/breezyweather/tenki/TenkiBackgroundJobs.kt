/*
 * 白い熊 天気 (shiroikuma-tenki) fork: keeps the background jobs registered.
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package org.breezyweather.tenki

import android.content.Context
import org.breezyweather.background.forecast.TodayForecastNotificationJob
import org.breezyweather.background.forecast.TomorrowForecastNotificationJob
import org.breezyweather.background.weather.WeatherUpdateJob
import org.breezyweather.common.extensions.workManager
import org.breezyweather.domain.settings.SettingsManager
import kotlin.concurrent.thread

/**
 * Upstream schedules its background jobs in exactly two places: `Migrations.upgrade`, which runs
 * only when the installed version is newer than the stored `last_version_code`, and the settings
 * screens when a value changes. Nothing else ever puts a job back.
 *
 * That leaves a hole that cost 白い熊 19 days of background updates (6.2.2+008, from 2026-09-09):
 * once the jobs are gone from WorkManager's own database — the app's data cleared and a backup
 * restored before the first launch, which carried `last_version_code` along and so made the
 * migration believe it had already run — no launch ever reschedules them, until the next version
 * bump. WorkManager itself cannot help: it re-registers force-stopped jobs from its database, and
 * the database was empty.
 *
 * So the app checks on every start, and after every settings restore, that each job the settings
 * ask for is actually enqueued, and schedules only the missing ones. An existing job is left
 * untouched, so its timing is never reset by merely opening the app.
 */
object TenkiBackgroundJobs {

    /** Off the main thread: the WorkManager queries block on its database. */
    fun ensureScheduled(context: Context) {
        val app = context.applicationContext
        thread(name = "tenki-ensure-jobs", isDaemon = true) {
            runCatching { ensureScheduledBlocking(app) }
        }
    }

    private fun ensureScheduledBlocking(context: Context) {
        val settings = SettingsManager.getInstance(context)
        if (settings.updateInterval.interval != null && !isPending(context, WeatherUpdateJob.WORK_NAME_AUTO)) {
            WeatherUpdateJob.setupTask(context)
        }
        if (settings.isTodayForecastEnabled && !isPending(context, TodayForecastNotificationJob.TAG)) {
            TodayForecastNotificationJob.setupTask(context, false)
        }
        if (settings.isTomorrowForecastEnabled && !isPending(context, TomorrowForecastNotificationJob.TAG)) {
            TomorrowForecastNotificationJob.setupTask(context, false)
        }
    }

    /**
     * After a settings restore: apply whatever interval and forecast times came back, as the
     * settings screens would have done had they been changed by hand. Unconditional, unlike
     * [ensureScheduled] — the restored values may differ from the ones the jobs were built with.
     */
    fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        WeatherUpdateJob.setupTask(app)
        TodayForecastNotificationJob.setupTask(app, false)
        TomorrowForecastNotificationJob.setupTask(app, false)
    }

    private fun isPending(context: Context, uniqueName: String): Boolean =
        context.workManager.getWorkInfosForUniqueWork(uniqueName).get().any { !it.state.isFinished }
}

