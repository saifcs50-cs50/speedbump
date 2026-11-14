package com.speedbump.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.speedbump.core.data.usage.UsageIngestScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * WorkManager is initialised on demand rather than by its default startup
 * provider, which the manifest removes. On-demand initialisation is what lets
 * Hilt supply the worker factory, and it also keeps WorkManager out of the
 * cold-start path of the accessibility service, which shares this process and
 * has a 400 ms interception budget to meet.
 */
@HiltAndroidApp
class SpeedbumpApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var usageIngestScheduler: UsageIngestScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        usageIngestScheduler.ensureScheduled()
    }
}
