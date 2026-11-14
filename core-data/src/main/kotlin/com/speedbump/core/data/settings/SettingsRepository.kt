package com.speedbump.core.data.settings

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<SpeedbumpSettings>,
) {

    /**
     * An unreadable file surfaces as defaults rather than as a crash in whatever
     * collector happened to be first. [SpeedbumpSettingsSerializer] already
     * handles a corrupt document; this covers the disk itself failing.
     */
    val settings: Flow<SpeedbumpSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(SpeedbumpSettings()) else throw error
        }

    suspend fun skipBaseline() {
        dataStore.updateData { it.copy(baselineSkipped = true, baselineReviewed = true) }
    }

    suspend fun markBaselineReviewed() {
        dataStore.updateData { it.copy(baselineReviewed = true) }
    }

    suspend fun completeOnboarding() {
        dataStore.updateData { it.copy(onboardingCompleted = true) }
    }
}
