package com.example.guardianangel.ui.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.Trip
import com.example.guardianangel.domain.model.TripOutcome
import com.example.guardianangel.domain.route.WalkDirections
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.home.components.TonalPill
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Every walk, and whether she got there.
 *
 * Kept separate from the recordings log because the two answer different questions. A
 * recording is "what was heard"; a journey is "did she arrive, and how long did it
 * take" — which over weeks is the thing that shows a route getting slower, or a walk she
 * keeps abandoning halfway.
 *
 * A journey she ended early is shown neutrally. Plans change, and a log that reads as a
 * reprimand for turning back is one she will switch off.
 */
@Composable
fun JourneysView(
    trips: List<Trip>,
    modifier: Modifier = Modifier,
) {
    if (trips.isEmpty()) {
        EmptyState(
            title = "No journeys yet",
            body = "Pick somewhere on the map and tap Walk with me. Every walk shows up " +
                "here with how long it took and whether you arrived.",
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        val arrived = trips.count { it.outcome == TripOutcome.Arrived }
        Text(
            text = "$arrived of ${trips.size} journeys completed",
            style = GuardianTheme.type.labelSm,
            color = GuardianTheme.materialColors.onSurfaceVariant,
        )

        trips.forEach { trip -> JourneyCard(trip = trip) }
    }
}

@Composable
private fun JourneyCard(trip: Trip, modifier: Modifier = Modifier) {
    GuardianCard(modifier = modifier, contentPadding = GuardianTheme.spacing.lg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = outcomeIcon(trip.outcome),
                contentDescription = null,
                tint = outcomeColor(trip.outcome),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(GuardianTheme.spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trip.destinationName,
                    style = GuardianTheme.type.labelLg,
                    color = GuardianTheme.materialColors.onSurface,
                )
                Text(
                    text = formatStart(trip.startedAtEpochMillis),
                    style = GuardianTheme.type.labelSm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
            }
            TonalPill(
                text = when (trip.outcome) {
                    TripOutcome.Arrived -> "Arrived"
                    TripOutcome.InProgress -> "Walking now"
                    TripOutcome.Ended -> "Ended early"
                },
                container = GuardianTheme.materialColors.surfaceContainerLow,
                content = outcomeColor(trip.outcome),
            )
        }

        Spacer(Modifier.height(GuardianTheme.spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm)) {
            trip.durationMillis?.let {
                JourneyStat(label = "Took", value = formatDuration(it))
            }
            trip.distanceMeters?.takeIf { it > 0 }?.let {
                JourneyStat(label = "Walked", value = WalkDirections.formatDistance(it))
            }
            trip.routeLabel?.let { JourneyStat(label = "Route", value = it) }
        }

        // Only shown when she asked for an arrival text, and it reports what actually
        // happened — a text that could not be sent must not look like one that was.
        trip.notifyContactName?.let { name ->
            Spacer(Modifier.height(GuardianTheme.spacing.sm))
            Text(
                text = when {
                    trip.arrivalNotifiedAtEpochMillis != null ->
                        "$name was told you arrived at " +
                            formatTime(trip.arrivalNotifiedAtEpochMillis)
                    trip.arrivalNotifyError != null ->
                        "$name was not told — ${trip.arrivalNotifyError}"
                    trip.outcome == TripOutcome.InProgress -> "$name will be told when you arrive"
                    else -> "$name was not told — the walk ended before you arrived"
                },
                style = GuardianTheme.type.labelSm,
                color = if (trip.arrivalNotifyError != null) {
                    GuardianTheme.materialColors.error
                } else {
                    GuardianTheme.materialColors.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun JourneyStat(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = GuardianTheme.type.labelSm,
            color = GuardianTheme.materialColors.onSurfaceVariant,
        )
        Text(
            text = value,
            style = GuardianTheme.type.labelMd,
            color = GuardianTheme.materialColors.onSurface,
        )
    }
}

@Composable
private fun outcomeColor(outcome: TripOutcome): Color = when (outcome) {
    TripOutcome.Arrived -> GuardianTheme.materialColors.primary
    TripOutcome.InProgress -> GuardianTheme.colors.accentWarm
    // Not an error colour. Turning back is a decision, not a failure.
    TripOutcome.Ended -> GuardianTheme.materialColors.onSurfaceVariant
}

private fun outcomeIcon(outcome: TripOutcome) = when (outcome) {
    TripOutcome.Arrived -> GuardianIcons.ShieldCheck
    TripOutcome.InProgress -> GuardianIcons.Walk
    TripOutcome.Ended -> GuardianIcons.MapPin
}

/** "7:42 PM" — the time a text would quote. */
private fun formatTime(millis: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))

private fun formatStart(millis: Long): String =
    SimpleDateFormat("EEE d MMM · h:mm a", Locale.getDefault()).format(Date(millis))

/** Minutes up to an hour, then hours and minutes. Seconds matter for a very short walk. */
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    return when {
        totalSeconds < 60 -> "${totalSeconds}s"
        totalSeconds < 3600 -> "${totalSeconds / 60} min"
        else -> "${totalSeconds / 3600} h ${(totalSeconds % 3600) / 60} min"
    }
}
