package com.speedbump.service.accessibility.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.speedbump.core.ui.theme.SpeedbumpCountdownStyle
import com.speedbump.core.ui.theme.SpeedbumpTheme

/**
 * The screen the whole product is about.
 *
 * Two design rules drive every choice here:
 *
 * 1. Leaving must be the easy action. "Not now" is the large, primary, always
 *    available control; continuing is a small text button that does not even
 *    exist until the wait has been served. Most blockers do this backwards.
 * 2. Nothing may feel like punishment. No red, no shame copy, no counter of
 *    failures. The breathing circle is there to give the impulse somewhere to
 *    go for ten seconds, because that is usually all it needs.
 */
@Composable
fun InterceptionScreen(
    state: InterceptionUiState,
    onAbandon: () -> Unit,
    onProceed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpeedbumpTheme(darkTheme = true) {
        val colors = MaterialTheme.colorScheme

        Box(
            modifier = modifier
                .fillMaxSize()
                // Fully opaque on purpose. A nearly-opaque scrim leaves a video
                // perfectly watchable underneath it - which makes the speedbump
                // a frame around the dopamine rather than an interruption of it.
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.surfaceContainerLow,
                            Color.Black,
                        )
                    )
                )
                .systemBarsPadding()
                .padding(horizontal = 28.dp, vertical = 32.dp),
        ) {
            Column(
                modifier = Modifier.align(Alignment.TopStart),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = state.appLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onBackground,
                )
                Text(
                    text = escalationCopy(state.escalationIndex),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }

            BreathingCountdown(
                remainingSeconds = state.remainingSeconds,
                modifier = Modifier.align(Alignment.Center),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(
                    onClick = onAbandon,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                    ),
                    contentPadding = PaddingValues(vertical = 18.dp),
                ) {
                    Text(
                        text = "Not now",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                // The escape hatch is real, but it has to be earned and it is
                // deliberately the quietest thing on the screen.
                AnimatedVisibility(
                    visible = state.canProceed,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 },
                ) {
                    TextButton(onClick = onProceed) {
                        Text(
                            text = "Open ${state.appLabel} anyway",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }

                if (!state.canProceed) {
                    Text(
                        text = " ",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun BreathingCountdown(
    remainingSeconds: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "breath")

    // ~4s in, ~4s out. Slow enough to actually slow a person down; a faster
    // pulse reads as urgency, which is the opposite of the point.
    val scale by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.04f,
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathScale",
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .scale(scale)
                .background(
                    brush = Brush.radialGradient(
                        listOf(
                            colors.primary.copy(alpha = 0.16f),
                            colors.primary.copy(alpha = 0.02f),
                        )
                    ),
                    shape = CircleShape,
                )
        )
        Text(
            text = remainingSeconds.coerceAtLeast(0).toString(),
            style = SpeedbumpCountdownStyle,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
        )
    }
}

private fun escalationCopy(escalationIndex: Int): String = when (escalationIndex) {
    0 -> "Take a breath first."
    1 -> "Second time in the last half hour."
    2 -> "Third time in the last half hour."
    else -> "${escalationIndex + 1} times in the last half hour."
}
