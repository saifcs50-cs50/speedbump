package com.speedbump.core.data.di

import android.content.Context
import androidx.room.Room
import com.speedbump.core.data.db.SPEEDBUMP_MIGRATIONS
import com.speedbump.core.data.db.SpeedbumpDatabase
import com.speedbump.core.data.db.dao.AppRuleDao
import com.speedbump.core.data.db.dao.InterceptionEventDao
import com.speedbump.core.data.db.dao.UsageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SpeedbumpDatabase =
        Room.databaseBuilder(context, SpeedbumpDatabase::class.java, SpeedbumpDatabase.NAME)
            .addMigrations(*SPEEDBUMP_MIGRATIONS)
            .build()

    @Provides
    fun provideInterceptionEventDao(database: SpeedbumpDatabase): InterceptionEventDao =
        database.interceptionEventDao()

    @Provides
    fun provideAppRuleDao(database: SpeedbumpDatabase): AppRuleDao =
        database.appRuleDao()

    @Provides
    fun provideUsageDao(database: SpeedbumpDatabase): UsageDao =
        database.usageDao()
}
