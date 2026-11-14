package com.speedbump.core.data.usage

import com.speedbump.core.data.db.dao.AppSessionAverage
import com.speedbump.core.data.db.dao.InterceptionEventDao
import com.speedbump.core.data.db.dao.UsageDao
import com.speedbump.core.data.db.entity.DailyAppUsageEntity
import com.speedbump.core.data.db.entity.DailyDeviceUsageEntity
import com.speedbump.core.data.db.entity.HourlyUsageEntity
import com.speedbump.core.data.db.entity.InterceptionEventEntity
import com.speedbump.core.data.model.InterceptionOutcome
import com.speedbump.core.data.time.SpeedbumpClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Hand-rolled rather than mocked. The upsert semantics are the interesting part
 * of what the repository asks of the DAO - re-ingesting a day has to replace it,
 * not duplicate it - and a mock that returns whatever it is told would not catch
 * a repository that stopped relying on that.
 */
class FakeUsageDao : UsageDao {

    private val appUsage = MutableStateFlow<List<DailyAppUsageEntity>>(emptyList())
    private val deviceUsage = MutableStateFlow<List<DailyDeviceUsageEntity>>(emptyList())
    private val hourly = MutableStateFlow<List<HourlyUsageEntity>>(emptyList())

    val appUsageRows: List<DailyAppUsageEntity> get() = appUsage.value
    val deviceUsageRows: List<DailyDeviceUsageEntity> get() = deviceUsage.value
    val hourlyRows: List<HourlyUsageEntity> get() = hourly.value

    override suspend fun upsertAppUsage(rows: List<DailyAppUsageEntity>) {
        appUsage.value = appUsage.value
            .filterNot { existing ->
                rows.any { it.dayEpochDay == existing.dayEpochDay && it.packageName == existing.packageName }
            }
            .plus(rows)
    }

    override suspend fun upsertDeviceUsage(row: DailyDeviceUsageEntity) {
        deviceUsage.value = deviceUsage.value.filterNot { it.dayEpochDay == row.dayEpochDay } + row
    }

    override suspend fun upsertHourly(rows: List<HourlyUsageEntity>) {
        hourly.value = hourly.value
            .filterNot { existing ->
                rows.any { it.dayEpochDay == existing.dayEpochDay && it.hour == existing.hour }
            }
            .plus(rows)
    }

    override fun observeAppUsage(fromDay: Long, toDay: Long): Flow<List<DailyAppUsageEntity>> =
        appUsage.map { rows ->
            rows.filter { it.dayEpochDay in fromDay..toDay }.sortedByDescending { it.foregroundMs }
        }

    override fun observeDeviceUsage(fromDay: Long, toDay: Long): Flow<List<DailyDeviceUsageEntity>> =
        deviceUsage.map { rows ->
            rows.filter { it.dayEpochDay in fromDay..toDay }.sortedBy { it.dayEpochDay }
        }

    override fun observeHourly(fromDay: Long, toDay: Long): Flow<List<HourlyUsageEntity>> =
        hourly.map { rows -> rows.filter { it.dayEpochDay in fromDay..toDay } }

    override suspend fun averageSessionLengths(fromDay: Long, toDay: Long): List<AppSessionAverage> =
        appUsage.value
            .filter { it.dayEpochDay in fromDay..toDay }
            .groupBy { it.packageName }
            .map { (pkg, rows) ->
                AppSessionAverage(pkg, rows.sumOf { it.foregroundMs }, rows.sumOf { it.opens })
            }
            .filter { it.totalOpens > 0 }

    override suspend fun lastIngestedDay(): Long? =
        deviceUsage.value.maxOfOrNull { it.dayEpochDay }

    override suspend fun pruneAppUsage(beforeDay: Long) {
        appUsage.value = appUsage.value.filter { it.dayEpochDay >= beforeDay }
    }

    fun seedAppUsage(vararg rows: DailyAppUsageEntity) {
        appUsage.value = appUsage.value + rows
    }

    fun seedDeviceUsage(vararg rows: DailyDeviceUsageEntity) {
        deviceUsage.value = deviceUsage.value + rows
    }

    fun seedHourly(vararg rows: HourlyUsageEntity) {
        hourly.value = hourly.value + rows
    }
}

/** Only the abandoned-count queries matter to the stats snapshot. */
class FakeInterceptionEventDao(
    private val abandonedIn: (fromMs: Long, toMs: Long) -> Int = { _, _ -> 0 },
) : InterceptionEventDao {

    override fun observeAbandonedCount(fromMs: Long, toMs: Long): Flow<Int> =
        MutableStateFlow(abandonedIn(fromMs, toMs))

    override suspend fun insert(event: InterceptionEventEntity): Long = 0
    override suspend fun resolve(
        id: Long,
        outcome: InterceptionOutcome,
        resolvedAtMs: Long,
        intention: String?,
    ) = Unit

    override suspend fun findById(id: Long): InterceptionEventEntity? = null
    override fun observeBetween(fromMs: Long, toMs: Long): Flow<List<InterceptionEventEntity>> =
        MutableStateFlow(emptyList())

    override suspend fun countForPackageBetween(
        packageName: String,
        fromMs: Long,
        toMs: Long,
    ): Int = 0

    override suspend fun recentIntentions(packageName: String, limit: Int): List<String> = emptyList()
    override suspend fun deleteOlderThan(beforeMs: Long): Int = 0
}

class FakeUsageStatsSource(
    var access: Boolean = true,
    var events: List<RawUsageEvent> = emptyList(),
) : UsageStatsSource {

    /** The window the repository last asked for, so backfill can be asserted. */
    var lastRequestedRange: Pair<Long, Long>? = null
        private set

    override fun hasAccess(): Boolean = access

    override suspend fun readEvents(fromMs: Long, toMs: Long): List<RawUsageEvent> {
        lastRequestedRange = fromMs to toMs
        return events.filter { it.timestampMs in fromMs..toMs }
    }
}

class FixedClock(private val nowMs: Long) : SpeedbumpClock {
    override fun nowMs(): Long = nowMs
    override fun elapsedRealtimeMs(): Long = nowMs
}
