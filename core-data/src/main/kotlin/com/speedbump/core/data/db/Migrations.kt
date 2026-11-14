package com.speedbump.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema history.
 *
 * Speedbump never uses destructive migration. The interception log IS the
 * product - a user's abandoned-launch history is the thing that makes them
 * keep the app - so losing it to a schema bump is not an acceptable trade
 * even in development.
 *
 * The SQL below is copied verbatim from the exported schema in
 * core-data/schemas/, because Room validates the live schema against that JSON
 * on open and a hand-written approximation fails at runtime, on a user's
 * device, with their data already in the file.
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_rules` (" +
                "`package_name` TEXT NOT NULL, " +
                "`enabled` INTEGER NOT NULL, " +
                "`base_delay_seconds` INTEGER NOT NULL, " +
                "`challenge` TEXT NOT NULL, " +
                "`ask_intention` INTEGER NOT NULL, " +
                "`daily_budget_minutes` INTEGER, " +
                "`daily_launch_quota` INTEGER, " +
                "`session_grant_minutes` INTEGER NOT NULL, " +
                "`created_at_ms` INTEGER NOT NULL, " +
                "PRIMARY KEY(`package_name`))"
        )
    }
}

/** Adds the local usage rollups that back the stats dashboard. */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_app_usage` (" +
                "`day_epoch_day` INTEGER NOT NULL, " +
                "`package_name` TEXT NOT NULL, " +
                "`foreground_ms` INTEGER NOT NULL, " +
                "`opens` INTEGER NOT NULL, " +
                "PRIMARY KEY(`day_epoch_day`, `package_name`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_daily_app_usage_day_epoch_day` " +
                "ON `daily_app_usage` (`day_epoch_day`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_device_usage` (" +
                "`day_epoch_day` INTEGER NOT NULL, " +
                "`pickups` INTEGER NOT NULL, " +
                "`first_pickup_minute` INTEGER, " +
                "`total_foreground_ms` INTEGER NOT NULL, " +
                "PRIMARY KEY(`day_epoch_day`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `hourly_usage` (" +
                "`day_epoch_day` INTEGER NOT NULL, " +
                "`hour` INTEGER NOT NULL, " +
                "`foreground_ms` INTEGER NOT NULL, " +
                "PRIMARY KEY(`day_epoch_day`, `hour`))"
        )
    }
}

internal val SPEEDBUMP_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
)
