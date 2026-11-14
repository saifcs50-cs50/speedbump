package com.speedbump.core.data.di

import com.speedbump.core.data.repository.ArmedAppsRepository
import com.speedbump.core.data.repository.RoomArmedAppsRepository
import com.speedbump.core.data.time.SpeedbumpClock
import com.speedbump.core.data.time.SystemSpeedbumpClock
import com.speedbump.core.data.usage.UsageStatsProvider
import com.speedbump.core.data.usage.UsageStatsSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {

    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemSpeedbumpClock): SpeedbumpClock

    @Binds
    @Singleton
    abstract fun bindArmedAppsRepository(impl: RoomArmedAppsRepository): ArmedAppsRepository

    @Binds
    @Singleton
    abstract fun bindUsageStatsSource(impl: UsageStatsProvider): UsageStatsSource
}
