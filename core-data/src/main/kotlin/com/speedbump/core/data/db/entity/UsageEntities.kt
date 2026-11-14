package com.speedbump.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Local rollups of UsageStatsManager data.
 *
 * These exist because the platform does NOT keep raw usage events for long -
 * queryEvents() only reaches back about a week, and the daily buckets it keeps
 * beyond that are coarser. So the history has to be ours: we ingest into these
 * tables and never depend on the system retaining anything.
 *
 * Keyed by epoch day rather than a timestamp so that a day is a day regardless
 * of DST, and so re-ingesting a day is an upsert rather than a duplicate.
 */
@Entity(
    tableName = "daily_app_usage",
    primaryKeys = ["day_epoch_day", "package_name"],
    indices = [Index("day_epoch_day")],
)
data class DailyAppUsageEntity(
    @ColumnInfo(name = "day_epoch_day")
    val dayEpochDay: Long,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "foreground_ms")
    val foregroundMs: Long,

    /** Distinct times this app came to the foreground that day. */
    @ColumnInfo(name = "opens")
    val opens: Int,
)

@Entity(tableName = "daily_device_usage")
data class DailyDeviceUsageEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "day_epoch_day")
    val dayEpochDay: Long,

    /** Times the device was unlocked. */
    @ColumnInfo(name = "pickups")
    val pickups: Int,

    /** Minutes past local midnight of the first unlock. Null if never unlocked. */
    @ColumnInfo(name = "first_pickup_minute")
    val firstPickupMinute: Int?,

    @ColumnInfo(name = "total_foreground_ms")
    val totalForegroundMs: Long,
)

@Entity(
    tableName = "hourly_usage",
    primaryKeys = ["day_epoch_day", "hour"],
)
data class HourlyUsageEntity(
    @ColumnInfo(name = "day_epoch_day")
    val dayEpochDay: Long,

    /** 0-23, local time. */
    @ColumnInfo(name = "hour")
    val hour: Int,

    @ColumnInfo(name = "foreground_ms")
    val foregroundMs: Long,
)
