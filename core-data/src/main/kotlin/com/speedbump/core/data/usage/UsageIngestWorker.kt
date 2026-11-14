package com.speedbump.core.data.usage

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.CancellationException

/**
 * Rolls UsageStatsManager events into Speedbump's own tables.
 *
 * This exists because the platform does not keep raw usage events for long -
 * `queryEvents()` reaches back about a week - so a user who does not open
 * Speedbump for ten days would permanently lose those days. The worker keeps
 * the history ours rather than the system's.
 *
 * Deliberately declares no constraints. A network constraint would make
 * WorkManager's ACCESS_NETWORK_STATE permission load-bearing, and Speedbump
 * promises to have no network permissions at all; there is nothing to wait for
 * anyway, since every byte this reads is already on the device.
 */
@HiltWorker
class UsageIngestWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val usageRepository: UsageRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        when (val result = usageRepository.ingest()) {
            // Not a failure: usage access is optional and interception works
            // without it. Retrying would just burn wakeups until the user
            // grants it, and app open re-runs this anyway.
            is IngestResult.NoAccess -> Result.success()

            is IngestResult.Ingested -> {
                Log.d(TAG, "ingested ${result.events} events into ${result.appDays} app-days")
                Result.success()
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Log.w(TAG, "usage ingest failed (attempt $runAttemptCount)", error)
        if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
    }

    private companion object {
        const val TAG = "UsageIngestWorker"

        /** Past this, the next periodic run will pick the data up regardless. */
        const val MAX_ATTEMPTS = 3
    }
}
