package com.speedbump.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.speedbump.core.data.db.dao.AppRuleDao
import com.speedbump.core.data.db.dao.InterceptionEventDao
import com.speedbump.core.data.db.dao.UsageDao
import com.speedbump.core.data.db.entity.AppRuleEntity
import com.speedbump.core.data.db.entity.DailyAppUsageEntity
import com.speedbump.core.data.db.entity.DailyDeviceUsageEntity
import com.speedbump.core.data.db.entity.HourlyUsageEntity
import com.speedbump.core.data.db.entity.InterceptionEventEntity

@Database(
    entities = [
        InterceptionEventEntity::class,
        AppRuleEntity::class,
        DailyAppUsageEntity::class,
        DailyDeviceUsageEntity::class,
        HourlyUsageEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SpeedbumpDatabase : RoomDatabase() {
    abstract fun interceptionEventDao(): InterceptionEventDao
    abstract fun appRuleDao(): AppRuleDao
    abstract fun usageDao(): UsageDao

    companion object {
        const val NAME = "speedbump.db"
    }
}
