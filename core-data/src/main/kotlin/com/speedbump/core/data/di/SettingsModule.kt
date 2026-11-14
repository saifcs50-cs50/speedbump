package com.speedbump.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.speedbump.core.data.settings.SpeedbumpSettings
import com.speedbump.core.data.settings.SpeedbumpSettingsSerializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<SpeedbumpSettings> = DataStoreFactory.create(
        serializer = SpeedbumpSettingsSerializer,
        scope = scope,
        produceFile = { context.dataStoreFile(SpeedbumpSettingsSerializer.FILE_NAME) },
    )
}
