package com.speedbump.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.speedbump.core.data.db.entity.DailyAppUsageEntity
import com.speedbump.core.data.db.entity.DailyDeviceUsageEntity
import com.speedbump.core.data.db.entity.HourlyUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {

    @Upsert
    suspend fun upsertAppUsage(rows: List<DailyAppUsageEntity>)

    @Upsert
    suspend fun upsertDeviceUsage(row: DailyDeviceUsageEntity)

    @Upsert
    suspend fun upsertHourly(rows: List<HourlyUsageEntity>)

    @Query(
        """
        SELECT * FROM daily_app_usage
        WHERE day_epoch_day BETWEEN :fromDay AND :toDay
        ORDER BY foreground_ms DESC
        """
    )
    fun observeAppUsage(fromDay: Long, toDay: Long): Flow<List<DailyAppUsageEntity>>

    @Query(
        """
        SELECT * FROM daily_device_usage
        WHERE day_epoch_day BETWEEN :fromDay AND :toDay
        ORDER BY day_epoch_day
        """
    )
    fun observeDeviceUsage(fromDay: Long, toDay: Long): Flow<List<DailyDeviceUsageEntity>>

    @Query(
        """
        SELECT * FROM hourly_usage
        WHERE day_epoch_day BETWEEN :fromDay AND :toDay
        """
    )
    fun observeHourly(fromDay: Long, toDay: Long): Flow<List<HourlyUsageEntity>>

    /** Median-ish session length: total foreground time over opens, per app. */
    @Query(
        """
        SELECT package_name AS packageName,
               SUM(foreground_ms) AS totalMs,
               SUM(opens) AS totalOpens
        FROM daily_app_usage
        WHERE day_epoch_day BETWEEN :fromDay AND :toDay
        GROUP BY package_name
        HAVING totalOpens > 0
        """
    )
    suspend fun averageSessionLengths(fromDay: Long, toDay: Long): List<AppSessionAverage>

    @Query("SELECT MAX(day_epoch_day) FROM daily_device_usage")
    suspend fun lastIngestedDay(): Long?

    @Query("DELETE FROM daily_app_usage WHERE day_epoch_day < :beforeDay")
    suspend fun pruneAppUsage(beforeDay: Long)
}

data class AppSessionAverage(
    val packageName: String,
    val totalMs: Long,
    val totalOpens: Int,
) {
    val averageSessionMs: Long get() = if (totalOpens == 0) 0 else totalMs / totalOpens
}
