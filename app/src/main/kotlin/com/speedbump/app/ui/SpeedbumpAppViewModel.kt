package com.speedbump.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedbump.app.permissions.SpeedbumpPermissions
import com.speedbump.core.data.model.PermissionSnapshot
import com.speedbump.core.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Decides which screen the app opens on, and owns the permission snapshot that
 * onboarding is entirely built around.
 *
 * [startRoute] is null until settings have been read from disk. The UI shows
 * nothing in that window rather than guessing, because guessing "home" and then
 * correcting to onboarding would flash the wrong screen at a first-run user.
 */
@HiltViewModel
class SpeedbumpAppViewModel @Inject constructor(
    private val permissionsReader: SpeedbumpPermissions,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val startRoute: StateFlow<String?> = settingsRepository.settings
        .map { if (it.onboardingCompleted) SpeedbumpRoutes.HOME else SpeedbumpRoutes.ONBOARDING }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _permissions = MutableStateFlow(permissionsReader.snapshot())
    val permissions: StateFlow<PermissionSnapshot> = _permissions

    /** The grants only change in Settings, so returning to the app re-reads them. */
    fun refreshPermissions() {
        _permissions.value = permissionsReader.snapshot()
    }
}
