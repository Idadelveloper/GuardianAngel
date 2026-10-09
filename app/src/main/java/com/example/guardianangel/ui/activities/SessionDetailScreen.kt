package com.example.guardianangel.ui.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.guardianangel.data.ActivitySamples
import com.example.guardianangel.domain.model.DiarizedEntry
import com.example.guardianangel.domain.model.MonitoredSession
import com.example.guardianangel.domain.model.SpeakerKind
import com.example.guardianangel.domain.repository.ActivityRepository
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.components.GuardianPrimaryButton
import com.example.guardianangel.ui.components.GuardianStackScaffold
import com.example.guardianangel.ui.home.components.MetricTile
import com.example.guardianangel.ui.home.components.SectionHeader
import com.example.guardianangel.ui.home.components.TonalPill
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.map.RouteCanvas
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.SafetyLevel
import com.example.guardianangel.ui.theme.safetyColorsFor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One session, in full: Angel's summary, the key numbers, and the diarized stream.
 *
 * The reference packed an audio scrubber, a geofence snippet, a hash footer and a "past
 * sessions" list onto the same page. That is a lot to hold while reading a transcript of
 * something frightening, so this keeps the summary and the stream and drops the rest —
 * the past-sessions list already exists one screen back.
 */
@Composable
fun SessionDetailRoute(
    sessionId: String,
    repository: ActivityRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val session by repository.observeSession(sessionId)
        .collectAsStateWithLifecycle(initialValue = null)

    SessionDetailScreen(
        session = session,
        onBack = onBack,
        onExport = { scope.launch { repository.exportSession(sessionId) } },
        modifier = modifier,
    )
}

@Composable
fun SessionDetailScreen(
    session: MonitoredSession?,
    onBack: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GuardianStackScaffold(
        title = "Session",
        onBack = onBack,
        modifier = modifier,
        bottomBar = session?.let {
            {
                GuardianPrimaryButton(
                    text = "Export transcript",
                    onClick = onExport,
                    leadingIcon = GuardianIcons.Lock,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        if (session == null) {
            EmptyState(
                title = "Session not found",
                body = "It may have been deleted from this device.",
            )
            return@GuardianStackScaffold
        }

        val palette = safetyColorsFor(
            SafetyLevel.fromScore(session.lowestSafetyScore ?: 100)
        )

        // --- Header ----------------------------------------------------------------
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TonalPill(
                    text = if (session.isEncrypted) "Encrypted" else "Unencrypted",
                    icon = GuardianIcons.Lock,
                    container = GuardianTheme.materialColors.surfaceContainerLow,
                    content = GuardianTheme.materialColors.onSurfaceVariant,
                )
                Spacer(Modifier.width(GuardianTheme.spacing.sm))
                Text(
                    text = formatRange(session),
                    style = GuardianTheme.type.labelSm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(GuardianTheme.spacing.sm))
            Text(
                text = session.title,
                style = GuardianTheme.type.headlineLgMobile,
                color = GuardianTheme.materialColors.onSurface,
            )
            Text(
                text = session.locationLabel,
                style = GuardianTheme.type.bodySm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }

        // --- Angel's summary --------------------------------------------------------
        GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
            SectionHeader(title = "What Angel heard", icon = GuardianIcons.Waveform)
            Spacer(Modifier.height(GuardianTheme.spacing.sm))
            Text(
                text = session.summary,
                style = GuardianTheme.type.bodyMd,
                color = GuardianTheme.materialColors.onSurface,
            )
            Spacer(Modifier.height(GuardianTheme.spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm)) {
                session.peakDecibels?.let {
                    MetricTile(
                        label = "Peak volume",
                        value = "$it dB",
                        icon = GuardianIcons.Waveform,
                        modifier = Modifier.weight(1f),
                    )
                }
                session.lowestSafetyScore?.let {
                    MetricTile(
                        label = "Lowest score",
                        value = "$it%",
                        icon = GuardianIcons.TrendingDown,
                        emphasis = palette.accent,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // --- Where it happened ------------------------------------------------------
        GuardianCard(contentPadding = GuardianTheme.spacing.md) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(GuardianTheme.shapes.md),
            ) {
                RouteCanvas(
                    routes = emptyList(),
                    showGeofence = true,
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                )
            }
            Spacer(Modifier.height(GuardianTheme.spacing.sm))
            Text(
                text = "Breadcrumb trail stored encrypted on this device.",
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }

        // --- Diarized stream --------------------------------------------------------
        if (session.entries.isNotEmpty()) {
            Column {
                SectionHeader(title = "Timeline", icon = GuardianIcons.Clock)
                Spacer(Modifier.height(GuardianTheme.spacing.sm))
                session.entries.forEachIndexed { index, entry ->
                    TimelineRow(
                        entry = entry,
                        isLast = index == session.entries.lastIndex,
                    )
                }
            }
        }
    }
}

/**
 * One transcript entry on a connected timeline rail.
 *
 * Speaker class is carried by a glyph and a label as well as colour, so a flagged sound
 * event is still distinguishable without colour vision.
 */
@Composable
private fun TimelineRow(
    entry: DiarizedEntry,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = when (entry.speakerKind) {
        SpeakerKind.You -> GuardianTheme.materialColors.primary
        SpeakerKind.KnownPattern -> GuardianTheme.materialColors.secondary
        SpeakerKind.Unknown -> GuardianTheme.colors.iconMuted
        SpeakerKind.SoundEvent -> safetyColorsFor(SafetyLevel.Elevated).accent
        SpeakerKind.SystemAction -> GuardianTheme.materialColors.tertiary
    }
    val icon = when (entry.speakerKind) {
        SpeakerKind.You -> GuardianIcons.Mic
        SpeakerKind.KnownPattern -> GuardianIcons.Users
        SpeakerKind.Unknown -> GuardianIcons.Users
        SpeakerKind.SoundEvent -> GuardianIcons.Waveform
        SpeakerKind.SystemAction -> GuardianIcons.Shield
    }

    Row(modifier = modifier.fillMaxWidth()) {
        // Rail: dot plus connector, so entries read as one sequence.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp),
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(44.dp)
                        .background(GuardianTheme.colors.borderDefault),
                )
            }
        }

        Spacer(Modifier.width(GuardianTheme.spacing.sm))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = GuardianTheme.spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.xs),
            ) {
                Text(
                    text = entry.speakerLabel,
                    style = GuardianTheme.type.labelMd,
                    color = GuardianTheme.materialColors.onSurface,
                )
                entry.speakerQualifier?.let {
                    TonalPill(
                        text = it,
                        container = GuardianTheme.materialColors.surfaceContainerLow,
                        content = GuardianTheme.materialColors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatTime(entry.atEpochMillis),
                    style = GuardianTheme.type.labelSm,
                    color = GuardianTheme.colors.iconMuted,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.text,
                style = GuardianTheme.type.bodySm,
                color = if (entry.isFlagged) accent else GuardianTheme.materialColors.onSurfaceVariant,
            )
            entry.decibels?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$it dB",
                    style = GuardianTheme.type.labelSm,
                    color = GuardianTheme.colors.iconMuted,
                )
            }
        }
    }
}

private fun formatTime(epochMillis: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))

private fun formatRange(session: MonitoredSession): String {
    val start = formatTime(session.startedAtEpochMillis)
    val duration = session.durationMinutes?.let { " · $it min" }.orEmpty()
    return "$start$duration"
}

@Preview(showBackground = true, device = "id:pixel_8", heightDp = 1800)
@Composable
private fun SessionDetailPreview() {
    GuardianAngelTheme {
        SessionDetailScreen(
            session = ActivitySamples.sessions.first(),
            onBack = {}, onExport = {},
        )
    }
}
