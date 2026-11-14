package com.speedbump.core.data.usage

import android.content.Context
import android.os.SystemClock
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns when [UsageIngestWorker] runs.
 *
 * Two triggers, for two different reasons:
 *  - a slow periodic run so history is captured before the platform drops it,
 *  - a run on app open so the dashboard is current at the moment it is read.
 */
@Singleton
class UsageIngestScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val lastRequestedAt = AtomicLong(Long.MIN_VALUE)

    /** Idempotent. Safe to call on every process start. */
    fun ensureScheduled() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UsageIngestWorker>(
                PERIOD_HOURS, TimeUnit.HOURS,
                FLEX_HOURS, TimeUnit.HOURS,
            ).build(),
        )
    }

    /**
     * Refreshes the dashboard's data now.
     *
     * Throttled because this is called from `onResume`, and a user bouncing
     * between Speedbump and another app would otherwise queue a rollup per
     * bounce. An app whose pitch includes battery cost should not spend it
     * recomputing numbers that have not changed.
     */
    fun ingestNow() {
        val now = SystemClock.elapsedRealtime()
        val previous = lastRequestedAt.get()
        if (previous != Long.MIN_VALUE && now - previous < MIN_INTERVAL_MS) return
        if (!lastRequestedAt.compareAndSet(previous, now)) return

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<UsageIngestWorker>().build(),
        )
    }

    private companion object {
        const val PERIODIC_WORK_NAME = "speedbump-usage-ingest-periodic"
        const val IMMEDIATE_WORK_NAME = "speedbump-usage-ingest-now"

        // Six hours keeps every day well inside the platform's ~7-day event
        // window at four wakeups a day. Anything faster buys nothing: the
        // numbers are only ever read when the app is open, and opening it
        // triggers an ingest of its own.
        const val PERIOD_HOURS = 6L
        const val FLEX_HOURS = 1L

        const val MIN_INTERVAL_MS = 2 * 60 * 1000L
    }
}
