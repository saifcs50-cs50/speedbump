package com.speedbump.feature.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.speedbump.core.data.baseline.ProposalReason
import com.speedbump.core.data.model.PermissionSnapshot
import com.speedbump.core.ui.component.AppIcon

private enum class OnboardingStep { WELCOME, ACCESSIBILITY, OVERLAY, USAGE, BASELINE }

@Composable
fun OnboardingScreen(
    permissions: PermissionSnapshot,
    baseline: BaselineUiState,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onToggleProposal: (String) -> Unit,
    onAcceptProposals: () -> Unit,
    onSkipBaseline: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(48.dp))
                when (step) {
                    OnboardingStep.WELCOME -> WelcomeStep()
                    OnboardingStep.ACCESSIBILITY -> AccessibilityStep(permissions)
                    OnboardingStep.OVERLAY -> OverlayStep(permissions)
                    OnboardingStep.USAGE -> UsageStep(permissions)
                    OnboardingStep.BASELINE -> BaselineStep(baseline, onToggleProposal)
                }
                Spacer(Modifier.height(24.dp))
            }

            StepActions(
                step = step,
                permissions = permissions,
                baseline = baseline,
                onAdvance = { step = step.next() },
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onOpenUsageSettings = onOpenUsageSettings,
                onAcceptProposals = onAcceptProposals,
                onSkipBaseline = onSkipBaseline,
                onFinish = onFinish,
            )
        }
    }
}

private fun OnboardingStep.next(): OnboardingStep =
    OnboardingStep.entries.getOrElse(ordinal + 1) { OnboardingStep.BASELINE }

@Composable
private fun StepActions(
    step: OnboardingStep,
    permissions: PermissionSnapshot,
    baseline: BaselineUiState,
    onAdvance: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onAcceptProposals: () -> Unit,
    onSkipBaseline: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (step) {
            OnboardingStep.WELCOME -> PrimaryAction("Set it up", onAdvance)

            OnboardingStep.ACCESSIBILITY -> if (permissions.accessibilityEnabled) {
                PrimaryAction("Next", onAdvance)
            } else {
                PrimaryAction("Open accessibility settings", onOpenAccessibilitySettings)
            }

            OnboardingStep.OVERLAY -> if (permissions.overlayGranted) {
                PrimaryAction("Next", onAdvance)
            } else {
                PrimaryAction("Open overlay settings", onOpenOverlaySettings)
            }

            OnboardingStep.USAGE -> {
                if (permissions.usageStatsGranted) {
                    PrimaryAction("Next", onAdvance)
                } else {
                    PrimaryAction("Open usage access", onOpenUsageSettings)
                    // Interception works without it, so this is genuinely
                    // optional and saying so is more honest than a dead end.
                    SecondaryAction("Skip — I don't want stats", onAdvance)
                }
            }

            OnboardingStep.BASELINE -> when {
                baseline.isReviewed -> PrimaryAction("Done", onFinish)

                baseline.proposals.isNotEmpty() -> {
                    PrimaryAction(
                        text = if (baseline.selectedCount == 0) {
                            "Continue without blocking anything"
                        } else {
                            "Block ${baseline.selectedCount} " +
                                if (baseline.selectedCount == 1) "app" else "apps"
                        },
                        onClick = onAcceptProposals,
                    )
                    SecondaryAction("I'll pick them myself", onSkipBaseline)
                }

                else -> {
                    PrimaryAction("Pick apps myself", onSkipBaseline)
                    if (!permissions.usageStatsGranted) {
                        SecondaryAction("Turn on usage access", onOpenUsageSettings)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

@Composable
private fun SecondaryAction(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

@Composable
private fun StepBody(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineLarge)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WelcomeStep() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Speedbump",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "It doesn't block your apps. It stands in front of them for a " +
                "few seconds and asks whether you meant it.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "The number it keeps is how many times you opened something, " +
                "hit the pause, and walked away. That is the whole product.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Speedbump has no internet permission. It cannot send your data " +
                "anywhere, because it cannot reach the network at all.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

/**
 * Play requires a prominent disclosure before an accessibility service is
 * requested, and it has to say what is read and where it goes. This is that
 * screen; keep it specific if the copy is ever revised.
 */
@Composable
private fun AccessibilityStep(permissions: PermissionSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepBody(
            title = "The accessibility service",
            body = "Speedbump uses Android's accessibility service to notice the " +
                "moment a blocked app comes to the front. That is the whole " +
                "blocking mechanism — without it the app cannot work.",
        )
        DisclosureBullet("It is told to watch only the apps you choose to block. Every other app on your phone is invisible to it.")
        DisclosureBullet("It reads which app just opened. It does not read the contents of your screen, your messages, or anything you type.")
        DisclosureBullet("Nothing is collected, stored off your device, or shared. Speedbump has no internet permission, so it could not send it even if it tried.")
        GrantedNote(permissions.accessibilityEnabled)
    }
}

@Composable
private fun OverlayStep(permissions: PermissionSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepBody(
            title = "Drawing over other apps",
            body = "The pause appears as a window on top of the app you opened, " +
                "rather than as a new screen. That is what lets \"continue\" put " +
                "you back exactly where you were instead of restarting the app.",
        )
        GrantedNote(permissions.overlayGranted)
    }
}

@Composable
private fun UsageStep(permissions: PermissionSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepBody(
            title = "Usage access",
            body = "This is how Speedbump reads screen time, pickups and the hourly " +
                "breakdown — and how it suggests which apps are worth blocking, " +
                "from what you actually do rather than what you'd guess.",
        )
        DisclosureBullet("Optional. Blocking works without it; you would just be picking apps by hand and losing the stats screen.")
        DisclosureBullet("Read on your phone, stored on your phone.")
        GrantedNote(permissions.usageStatsGranted)
    }
}

@Composable
private fun BaselineStep(state: BaselineUiState, onToggleProposal: (String) -> Unit) {
    when {
        state.isLoading -> StepBody(
            title = "Reading your week",
            body = "One moment.",
        )

        state.isReviewed -> StepBody(
            title = "You're set",
            body = "Speedbump is watching. Open a blocked app and it will be there.",
        )

        state.needsUsageAccess -> StepBody(
            title = "Pick apps yourself",
            body = "Without usage access Speedbump can't suggest anything, so you'll " +
                "choose the apps on the next screen. You can turn stats on later " +
                "and it will start suggesting.",
        )

        state.isMeasuring -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StepBody(
                title = "Still measuring",
                body = "Speedbump has ${state.daysMeasured} of " +
                    "${state.daysRequired} days of your usage. It needs a few more " +
                    "before a suggestion would mean anything. You can pick apps " +
                    "yourself in the meantime.",
            )
            LinearProgressIndicator(
                progress = {
                    state.daysMeasured.toFloat() / state.daysRequired.coerceAtLeast(1)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        state.proposals.isNotEmpty() -> Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepBody(
                title = "From your last ${state.daysMeasured} days",
                body = "These are where the time actually went. Uncheck anything " +
                    "you'd rather leave alone.",
            )
            state.proposals.forEach { proposal ->
                ProposalCard(proposal, onToggle = { onToggleProposal(proposal.packageName) })
            }
        }

        else -> StepBody(
            title = "Nothing stood out",
            body = "Speedbump didn't find an app worth interrupting. Pick the ones " +
                "you want yourself.",
        )
    }
}

@Composable
private fun ProposalCard(proposal: ProposalRow, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(packageName = proposal.packageName)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = proposal.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = proposal.evidence(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(checked = proposal.selected, onCheckedChange = { onToggle() })
        }
    }
}

/** The reason, in the user's own numbers. A suggestion without one is a guess. */
private fun ProposalRow.evidence(): String {
    val minutes = averageDailyMs / 60_000
    val time = when {
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m a day"
        else -> "${minutes}m a day"
    }
    return when (reason) {
        ProposalReason.TIME -> "$time  ·  $averageDailyOpens opens"
        ProposalReason.FREQUENCY -> "$averageDailyOpens times a day  ·  $time"
    }
}

@Composable
private fun DisclosureBullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = "·", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GrantedNote(granted: Boolean) {
    if (!granted) return
    Text(
        text = "Granted.",
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
    )
}
