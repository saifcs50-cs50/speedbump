package com.speedbump.core.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The platform's usage log.
 *
 * An interface because everything downstream of it - the rollups, the whole
 * stats dashboard - is worth testing against handcrafted event streams, and
 * UsageStatsManager cannot be driven from a unit test.
 */
interface UsageStatsSource {

    /** Whether the user has granted PACKAGE_USAGE_STATS in Settings. */
    fun hasAccess(): Boolean

    suspend fun readEvents(fromMs: Long, toMs: Long): List<RawUsageEvent>
}

/**
 * Reads raw events from UsageStatsManager. Contains no aggregation logic - that
 * lives in [UsageAggregator] where it can be tested without a device.
 */
@Singleton
class UsageStatsProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : UsageStatsSource {

    @Suppress("DEPRECATION") // No non-deprecated way to read a special-access grant.
    override fun hasAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        return appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    override suspend fun readEvents(fromMs: Long, toMs: Long): List<RawUsageEvent> =
        withContext(Dispatchers.IO) {
            val manager = context.getSystemService(UsageStatsManager::class.java)
                ?: return@withContext emptyList()

            val results = mutableListOf<RawUsageEvent>()
            val events = manager.queryEvents(fromMs, toMs)
            val event = UsageEvents.Event()

            while (events.getNextEvent(event)) {
                val type = when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventType.RESUMED
                    UsageEvents.Event.ACTIVITY_PAUSED -> UsageEventType.PAUSED
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEventType.SCREEN_OFF
                    UsageEvents.Event.KEYGUARD_SHOWN -> UsageEventType.SCREEN_OFF
                    UsageEvents.Event.KEYGUARD_HIDDEN -> UsageEventType.UNLOCKED
                    else -> null
                }
                if (type != null) {
                    results += RawUsageEvent(
                        packageName = event.packageName,
                        timestampMs = event.timeStamp,
                        type = type,
                    )
                }
            }
            results
        }
}
