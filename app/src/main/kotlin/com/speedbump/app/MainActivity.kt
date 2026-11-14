package com.speedbump.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speedbump.app.permissions.SpeedbumpPermissions
import com.speedbump.app.ui.HomeViewModel
import com.speedbump.app.ui.SpeedbumpAppViewModel
import com.speedbump.app.ui.SpeedbumpNavHost
import com.speedbump.feature.onboarding.OnboardingViewModel
import com.speedbump.feature.stats.StatsViewModel
import com.speedbump.core.ui.theme.SpeedbumpTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var permissionIntents: SpeedbumpPermissions

    private val appViewModel: SpeedbumpAppViewModel by viewModels()

    private var homeViewModel: HomeViewModel? = null
    private var statsViewModel: StatsViewModel? = null
    private var onboardingViewModel: OnboardingViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            SpeedbumpTheme {
                val startRoute by appViewModel.startRoute.collectAsStateWithLifecycle()
                val permissions by appViewModel.permissions.collectAsStateWithLifecycle()

                // Nothing is drawn until settings resolve; see SpeedbumpAppViewModel.
                startRoute?.let { route ->
                    SpeedbumpNavHost(
                        startDestination = route,
                        permissions = permissions,
                        onOpenAccessibilitySettings = {
                            startActivity(permissionIntents.accessibilitySettingsIntent())
                        },
                        onOpenOverlaySettings = {
                            startActivity(permissionIntents.overlaySettingsIntent())
                        },
                        onOpenUsageSettings = {
                            startActivity(permissionIntents.usageAccessSettingsIntent())
                        },
                        onHomeViewModelReady = { homeViewModel = it },
                        onStatsViewModelReady = { statsViewModel = it },
                        onOnboardingViewModelReady = { onboardingViewModel = it },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The three special-access grants can only be changed in Settings, so
        // returning to the app is the only moment they can have changed.
        appViewModel.refreshPermissions()
        homeViewModel?.refreshPermissions()
        statsViewModel?.refresh()
        onboardingViewModel?.refresh()
    }
}
