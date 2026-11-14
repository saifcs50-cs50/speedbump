package com.speedbump.feature.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.speedbump.core.data.model.ChallengeType
import com.speedbump.core.data.policy.EscalationPolicy
import com.speedbump.core.ui.component.AppIcon

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditorScreen(
    state: RuleEditorUiState,
    onBack: () -> Unit,
    onBaseDelayChange: (Int) -> Unit,
    onChallengeChange: (ChallengeType) -> Unit,
    onAskIntentionChange: (Boolean) -> Unit,
    onBudgetChange: (Int?) -> Unit,
    onQuotaChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(state.appLabel.ifEmpty { "Rule" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                AppIcon(packageName = state.packageName, size = 48.dp)
                Column {
                    Text(state.appLabel, style = MaterialTheme.typography.titleLarge)
                    Text(
                        state.packageName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            Section(
                title = "Wait ${state.rule.baseDelaySeconds} seconds",
                subtitle = "Reopening within half an hour escalates this to " +
                    "${EscalationPolicy().delaySecondsFor(state.rule.baseDelaySeconds, 3)}s.",
            ) {
                Slider(
                    value = state.rule.baseDelaySeconds.toFloat(),
                    onValueChange = { onBaseDelayChange(it.toInt()) },
                    valueRange = EscalationPolicy.MIN_BASE_SECONDS.toFloat()..
                        EscalationPolicy.MAX_BASE_SECONDS.toFloat(),
                    steps = EscalationPolicy.MAX_BASE_SECONDS - EscalationPolicy.MIN_BASE_SECONDS - 1,
                )
            }

            Section(
                title = "Unlock challenge",
                subtitle = "A tag or code you keep in another room turns a habit " +
                    "into a walk.",
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChallengeType.entries.forEach { challenge ->
                        FilterChip(
                            selected = state.rule.challenge == challenge,
                            onClick = { onChallengeChange(challenge) },
                            label = { Text(challenge.displayName()) },
                        )
                    }
                }
            }

            Section(
                title = "Ask what you're here for",
                subtitle = "Speedbump saves your answer and shows it back to you later.",
            ) {
                Switch(
                    checked = state.rule.askIntention,
                    onCheckedChange = onAskIntentionChange,
                )
            }

            Section(
                title = state.rule.dailyBudgetMinutes?.let { "$it minutes a day" }
                    ?: "No daily time budget",
                subtitle = "Speedbump sends you home when the budget runs out.",
            ) {
                BudgetChips(
                    options = listOf(null, 10, 20, 30, 45, 60, 90),
                    selected = state.rule.dailyBudgetMinutes,
                    onSelect = onBudgetChange,
                    format = { it?.let { m -> "$m min" } ?: "Off" },
                )
            }

            Section(
                title = state.rule.dailyLaunchQuota?.let { "$it opens a day" }
                    ?: "No daily open limit",
                subtitle = "\"You may open this six times today.\"",
            ) {
                BudgetChips(
                    options = listOf(null, 3, 5, 6, 8, 10, 15),
                    selected = state.rule.dailyLaunchQuota,
                    onSelect = onQuotaChange,
                    format = { it?.let { n -> "$n" } ?: "Off" },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetChips(
    options: List<Int?>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    format: (Int?) -> String,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(format(option)) },
            )
        }
    }
}

@Composable
private fun Section(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

private fun ChallengeType.displayName(): String = when (this) {
    ChallengeType.NONE -> "Just wait"
    ChallengeType.TYPING -> "Type a sentence"
    ChallengeType.MATH -> "Solve maths"
    ChallengeType.HOLD -> "Hold 30s"
    ChallengeType.NFC_TAG -> "Scan NFC tag"
    ChallengeType.QR_CODE -> "Scan QR code"
}
