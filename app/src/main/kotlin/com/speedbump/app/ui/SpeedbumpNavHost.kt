package com.speedbump.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.speedbump.feature.rules.RuleEditorScreen
import com.speedbump.feature.rules.RuleEditorViewModel
import com.speedbump.core.data.model.PermissionSnapshot
import com.speedbump.feature.onboarding.OnboardingScreen
import com.speedbump.feature.onboarding.OnboardingViewModel
import com.speedbump.feature.rules.RulesScreen
import com.speedbump.feature.rules.RulesViewModel
import com.speedbump.feature.stats.StatsScreen
import com.speedbump.feature.stats.StatsViewModel

object SpeedbumpRoutes {
    const val HOME = "home"
    const val RULES = "rules"
    const val RULE_EDITOR = "rules/{packageName}"
    const val STATS = "stats"
    const val ONBOARDING = "onboarding"

    fun ruleEditor(packageName: String) = "rules/$packageName"
}

@Composable
fun SpeedbumpNavHost(
    startDestination: String,
    permissions: PermissionSnapshot,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    navController: NavHostController = rememberNavController(),
    onHomeViewModelReady: (HomeViewModel) -> Unit = {},
    onStatsViewModelReady: (StatsViewModel?) -> Unit = {},
    onOnboardingViewModelReady: (OnboardingViewModel?) -> Unit = {},
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(SpeedbumpRoutes.ONBOARDING) {
            val viewModel: OnboardingViewModel = hiltViewModel()
            val baseline by viewModel.uiState.collectAsStateWithLifecycle()

            // Every step of onboarding sends the user to a Settings screen and
            // back, so the activity has to re-read grants on resume.
            DisposableEffect(viewModel) {
                onOnboardingViewModelReady(viewModel)
                onDispose { onOnboardingViewModelReady(null) }
            }

            OnboardingScreen(
                permissions = permissions,
                baseline = baseline,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onOpenUsageSettings = onOpenUsageSettings,
                onToggleProposal = viewModel::onToggleProposal,
                onAcceptProposals = viewModel::onAcceptProposals,
                onSkipBaseline = viewModel::onSkipBaseline,
                onFinish = {
                    viewModel.onFinishOnboarding()
                    navController.navigate(SpeedbumpRoutes.HOME) {
                        popUpTo(SpeedbumpRoutes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(SpeedbumpRoutes.HOME) {
            val viewModel: HomeViewModel = hiltViewModel()
            onHomeViewModelReady(viewModel)
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            HomeScreen(
                state = state,
                onOpenRules = { navController.navigate(SpeedbumpRoutes.RULES) },
                onOpenStats = { navController.navigate(SpeedbumpRoutes.STATS) },
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onOpenUsageSettings = onOpenUsageSettings,
            )
        }

        composable(SpeedbumpRoutes.RULES) {
            val viewModel: RulesViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            RulesScreen(
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onToggleArmed = viewModel::onToggleArmed,
                onToggleSystemApps = viewModel::onToggleSystemApps,
                onOpenRule = { navController.navigate(SpeedbumpRoutes.ruleEditor(it)) },
            )
        }

        composable(
            route = SpeedbumpRoutes.RULE_EDITOR,
            arguments = listOf(
                navArgument(RuleEditorViewModel.ARG_PACKAGE) { type = NavType.StringType },
            ),
        ) {
            val viewModel: RuleEditorViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            RuleEditorScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onBaseDelayChange = viewModel::onBaseDelayChange,
                onChallengeChange = viewModel::onChallengeChange,
                onAskIntentionChange = viewModel::onAskIntentionChange,
                onBudgetChange = viewModel::onBudgetChange,
                onQuotaChange = viewModel::onQuotaChange,
            )
        }

        composable(SpeedbumpRoutes.STATS) {
            val viewModel: StatsViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            // Usage access is granted in Settings, so returning from there has
            // to re-read it and re-ingest. The activity owns that moment.
            DisposableEffect(viewModel) {
                onStatsViewModelReady(viewModel)
                onDispose { onStatsViewModelReady(null) }
            }

            StatsScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onOpenUsageSettings = onOpenUsageSettings,
            )
        }
    }
}
