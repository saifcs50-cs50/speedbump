package com.speedbump.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.speedbump.core.ui.theme.SpeedbumpCountdownStyle

@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenRules: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeroMetric(abandonedToday = state.abandonedToday)

            if (!state.permissions.accessibilityEnabled) {
                PermissionCard(
                    title = "Accessibility service",
                    granted = false,
                    rationale = "How Speedbump knows a blocked app just opened. " +
                        "It is told to ignore every other app on your phone.",
                    onFix = onOpenAccessibilitySettings,
                )
            }
            if (!state.permissions.overlayGranted) {
                PermissionCard(
                    title = "Display over other apps",
                    granted = false,
                    rationale = "How the speedbump appears without stealing your place in the app.",
                    onFix = onOpenOverlaySettings,
                )
            }
            if (!state.permissions.usageStatsGranted) {
                PermissionCard(
                    title = "Usage access",
                    granted = false,
                    rationale = "Powers the stats screen. Interception works without it.",
                    onFix = onOpenUsageSettings,
                )
            }

            if (state.permissions.canIntercept) {
                ReadyCard(armedCount = state.armedCount)
            }

            NavRow(
                title = "Blocked apps",
                value = state.armedCount.toString(),
                onClick = onOpenRules,
            )

            NavRow(
                title = "Stats",
                value = "7 days",
                onClick = onOpenStats,
            )
        }
    }
}

@Composable
private fun HeroMetric(abandonedToday: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = abandonedToday.toString(),
            style = SpeedbumpCountdownStyle,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = if (abandonedToday == 1) "launch abandoned today" else "launches abandoned today",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadyCard(armedCount: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (armedCount == 0) "Nothing armed yet" else "Speedbump is on",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = if (armedCount == 0) {
                    "Pick an app below and Speedbump will start standing in front of it."
                } else {
                    "Watching $armedCount app${if (armedCount == 1) "" else "s"}. " +
                        "Everything else on your phone is invisible to it."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    rationale: String,
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = if (granted) "on" else "needed",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (granted) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                )
            }
            Text(
                text = rationale,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!granted) {
                TextButton(onClick = onFix, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Turn it on")
                }
            }
        }
    }
}

@Composable
private fun NavRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
