package com.speedbump.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.speedbump.core.data.usage.DayStat
import com.speedbump.core.ui.component.AppIcon
import com.speedbump.core.ui.theme.SpeedbumpCountdownStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    state: StatsUiState,
    onBack: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Your ${state.rangeDays} days") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { HeroCard(state) }

            if (!state.hasUsageAccess) {
                item { UsageAccessCard(onOpenUsageSettings) }
            } else if (!state.hasAnyUsage && !state.isLoading) {
                item { GatheringCard() }
            }

            if (state.hasUsageAccess && state.hasAnyUsage) {
                item { TodayTiles(state) }
                item { SectionHeading("Today, hour by hour") }
                item { HourlyHeatmap(state.hourlyToday) }
                item { SectionHeading("Screen time, last ${state.rangeDays} days") }
                item { WeekTrend(state.dailyTrend, state.rangeDays) }

                if (state.topApps.isNotEmpty()) {
                    item { SectionHeading("Where it went") }
                    items(state.topApps, key = { it.packageName }) { app ->
                        AppUsageCard(app)
                    }
                }
            }
        }
    }
}

/**
 * The one number the product is about. Abandoned launches lead; screen time is
 * deliberately further down the page, because every other app on the phone
 * already tells the user how much they used it and none of them tell them how
 * often they chose not to.
 */
@Composable
private fun HeroCard(state: StatsUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.abandonedInRange.toString(),
            style = SpeedbumpCountdownStyle,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "${plural(state.abandonedInRange, "launch", "launches")} abandoned",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (state.timeReclaimedRangeMs > 0) {
                "about ${formatDuration(state.timeReclaimedRangeMs)} you didn't spend"
            } else {
                "walk away from one and it lands here"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        if (state.abandonedToday > 0) {
            Text(
                text = "${state.abandonedToday} today",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TodayTiles(state: StatsUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatTile(
            modifier = Modifier.weight(1f),
            value = state.pickupsToday.toString(),
            label = "pickups today",
        )
        StatTile(
            modifier = Modifier.weight(1f),
            value = state.firstPickupMinute?.let(::formatMinuteOfDay) ?: "—",
            label = "first pickup",
        )
        StatTile(
            modifier = Modifier.weight(1f),
            value = formatDuration(state.screenTimeTodayMs),
            label = "screen time",
        )
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Twenty-four bars rather than a coloured grid: a grid asks the user to decode
 * a legend, where a bar chart is read at a glance, and the only question this
 * answers is "when do I lose the evening?".
 */
@Composable
private fun HourlyHeatmap(hourly: List<Long>, modifier: Modifier = Modifier) {
    val peak = hourly.maxOrNull() ?: 0L

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            hourly.forEach { ms ->
                Bar(
                    modifier = Modifier.weight(1f),
                    fraction = if (peak <= 0L) 0f else ms.toFloat() / peak,
                    filled = ms > 0L,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("12a", "6a", "12p", "6p").forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (peak > 0L) {
            Text(
                text = "busiest hour: ${formatDuration(peak)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekTrend(trend: List<DayStat>, rangeDays: Int, modifier: Modifier = Modifier) {
    val peak = trend.maxOfOrNull { it.foregroundMs } ?: 0L
    val today = trend.lastOrNull()?.epochDay

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            trend.forEach { day ->
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = formatDurationShort(day.foregroundMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Bar(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        fraction = if (peak <= 0L) 0f else day.foregroundMs.toFloat() / peak,
                        filled = day.foregroundMs > 0L,
                        emphasised = day.epochDay == today,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            trend.forEach { day ->
                Text(
                    text = weekdayInitial(day.epochDay),
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (day.epochDay == today) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        val totalPickups = trend.sumOf { it.pickups }
        if (totalPickups > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$totalPickups pickups over $rangeDays days  ·  " +
                    "${totalPickups / rangeDays} a day",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppUsageCard(app: AppUsageRow, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppIcon(packageName = app.packageName)

            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${formatDuration(app.foregroundMs)}  ·  " +
                        "${app.opens} ${plural(app.opens, "open")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Sparkline(app.perDayMs)
        }
    }
}

/** Per-app trend: seven days of this one app, scaled to its own worst day. */
@Composable
private fun Sparkline(perDayMs: List<Long>, modifier: Modifier = Modifier) {
    val peak = perDayMs.maxOrNull() ?: 0L

    Row(
        modifier = modifier.width(72.dp).height(32.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        perDayMs.forEach { ms ->
            Bar(
                modifier = Modifier.weight(1f),
                fraction = if (peak <= 0L) 0f else ms.toFloat() / peak,
                filled = ms > 0L,
            )
        }
    }
}

/**
 * A bar and the track it sits in. The track is always drawn so an empty hour
 * reads as "nothing here" rather than as a rendering gap.
 */
@Composable
private fun Bar(
    fraction: Float,
    filled: Boolean,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (filled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // A visible floor: a minute against a three-hour peak is a
                    // sliver too thin to see, and an invisible bar reads as
                    // missing data rather than as a small number.
                    .fillMaxHeight(fraction.coerceIn(MIN_BAR_FRACTION, 1f))
                    .clip(shape)
                    .background(
                        if (emphasised) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        }
                    ),
            )
        }
    }
}

private const val MIN_BAR_FRACTION = 0.05f

@Composable
private fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun UsageAccessCard(onOpenUsageSettings: () -> Unit, modifier: Modifier = Modifier) {
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
            Text(text = "Usage access is off", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Speedbump can count the launches you abandon without it, but " +
                    "screen time, pickups and the hourly breakdown all come from " +
                    "Android's usage data. It is read on your phone and never leaves it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenUsageSettings, modifier = Modifier.padding(top = 4.dp)) {
                Text("Turn it on")
            }
        }
    }
}

@Composable
private fun GatheringCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Nothing to show yet", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Android keeps about a week of usage history. Speedbump has " +
                    "started keeping its own, so this fills in from here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
