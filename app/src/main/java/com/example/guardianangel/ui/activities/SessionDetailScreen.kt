package com.example.guardianangel.ui.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.guardianangel.data.ActivitySamples
import com.example.guardianangel.domain.model.DiarizedEntry
import com.example.guardianangel.domain.model.SessionTrail
import com.example.guardianangel.domain.model.MonitoredSession
import com.example.guardianangel.domain.model.SpeakerKind
import com.example.guardianangel.domain.session.SessionNarrator
import com.example.guardianangel.domain.repository.ActivityRepository
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.components.GuardianOutlinedButton
import com.example.guardianangel.ui.components.GuardianPrimaryButton
import com.example.guardianangel.ui.components.GuardianStackScaffold
import com.example.guardianangel.ui.home.components.MetricTile
import com.example.guardianangel.ui.home.components.SectionHeader
import com.example.guardianangel.ui.home.components.TonalPill
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.map.RouteCanvas
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.trail.SessionTrailSection
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.SafetyLevel
import com.example.guardianangel.ui.theme.safetyColorsFor
import java.io.File
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
    val context = LocalContext.current
    val session by repository.observeSession(sessionId)
        .collectAsStateWithLifecycle(initialValue = null)
    val trail by repository.observeTrail(sessionId)
        .collectAsStateWithLifecycle(initialValue = SessionTrail(sessionId, emptyList(), emptyList()))
    var exportState by remember { mutableStateOf<ExportState>(ExportState.Idle) }

    SessionDetailScreen(
        session = session,
        trail = trail,
        onBack = onBack,
        exportState = exportState,
        onExport = {
            exportState = ExportState.Running
            scope.launch {
                // The result was previously thrown away, so "Export transcript" wrote a
                // file nobody could find — and before that, wrote no file at all.
                exportState = runCatching { repository.exportSession(sessionId) }.fold(
                    onSuccess = { ExportState.Done(it) },
                    onFailure = {
                        ExportState.Failed(it.message ?: "The export could not be written.")
                    },
                )
            }
        },
        onShare = { path -> context.shareTranscript(path) },
        onDelete = {
            scope.launch {
                repository.deleteSession(sessionId)
                onBack()
            }
        },
        modifier = modifier,
    )
}

/** Where an export has got to, so the button can report rather than guess. */
sealed interface ExportState {
    data object Idle : ExportState
    data object Running : ExportState
    data class Done(val path: String) : ExportState
    data class Failed(val message: String) : ExportState
}

/**
 * Hands the file to whatever the user wants to send it with.
 *
 * Shared as plain text content rather than a file URI: a `content://` URI needs a
 * `FileProvider` and a grant per target app, and for a transcript the text *is* the
 * evidence. This way it pastes into a message, an email or a notes app with nothing to
 * configure and nothing to go wrong at the moment it is needed.
 */
private fun Context.shareTranscript(path: String) {
    val body = runCatching { File(path).readText() }.getOrNull() ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Guardian Angel session record")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    runCatching { startActivity(Intent.createChooser(intent, "Share session record")) }
}

@Composable
fun SessionDetailScreen(
    session: MonitoredSession?,
    trail: SessionTrail,
    onBack: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
    exportState: ExportState = ExportState.Idle,
    onShare: (String) -> Unit = {},
    onDelete: () -> Unit = {},
) {
    // Two taps, not a dialog. Deleting a recording destroys evidence, so it should not
    // happen on one stray touch — but a user who wants it gone, perhaps because someone
    // is standing over her, should not have to fight a modal to do it.
    var confirmingDelete by remember(session?.id) { mutableStateOf(false) }
    GuardianStackScaffold(
        title = "Session",
        onBack = onBack,
        modifier = modifier,
        bottomBar = session?.let {
            {
                Column {
                    when (exportState) {
                        is ExportState.Done -> Text(
                            text = "Saved to ${exportState.path.substringAfterLast('/')}",
                            style = GuardianTheme.type.bodySm,
                            color = GuardianTheme.materialColors.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = GuardianTheme.spacing.xs),
                        )

                        is ExportState.Failed -> Text(
                            text = exportState.message,
                            style = GuardianTheme.type.bodySm,
                            color = GuardianTheme.materialColors.error,
                            modifier = Modifier.padding(bottom = GuardianTheme.spacing.xs),
                        )

                        else -> Unit
                    }

                    GuardianPrimaryButton(
                        text = when (exportState) {
                            ExportState.Running -> "Exporting…"
                            is ExportState.Done -> "Share this record"
                            else -> "Export transcript"
                        },
                        onClick = {
                            val done = exportState as? ExportState.Done
                            if (done != null) onShare(done.path) else onExport()
                        },
                        enabled = exportState != ExportState.Running,
                        leadingIcon = GuardianIcons.Lock,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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

        // --- Key moments ------------------------------------------------------------
        //
        // Derived here rather than stored, like the trail's incidents: what counts as
        // notable changes as the heuristics improve, and a recording made last month
        // should benefit from today's reading of it.
        val narrative = remember(session.id, session.entries) {
            SessionNarrator.narrate(
                startedAtMillis = session.startedAtEpochMillis,
                endedAtMillis = session.endedAtEpochMillis,
                entries = session.entries,
                locationLabel = session.locationLabel,
                triggeredTierName = session.triggeredByTier?.name,
                guardiansNotified = session.guardiansNotified,
                lowestSafetyScore = session.lowestSafetyScore,
            )
        }
        if (narrative.keyMoments.isNotEmpty()) {
            GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
                SectionHeader(title = "Key moments", icon = GuardianIcons.Activity)
                Spacer(Modifier.height(GuardianTheme.spacing.sm))
                Text(
                    text = "The points worth jumping to, timed from the start of the " +
                        "recording.",
                    style = GuardianTheme.type.labelSm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
                Spacer(Modifier.height(GuardianTheme.spacing.md))
                narrative.keyMoments.forEach { moment ->
                    KeyMomentRow(moment = moment)
                    Spacer(Modifier.height(GuardianTheme.spacing.sm))
                }
            }
        }

        // --- Where it happened ------------------------------------------------------
        //
        // The real breadcrumb trail. This drew `RouteCanvas(routes = emptyList())` — an
        // empty decorative map with a caption claiming a trail was stored, under a
        // session whose actual breadcrumbs were sitting unread in the database.
        SessionTrailSection(trail = trail)

        // --- Delete -----------------------------------------------------------------
        Column(modifier = Modifier.fillMaxWidth()) {
            GuardianOutlinedButton(
                text = if (confirmingDelete) {
                    "Tap again to delete permanently"
                } else {
                    "Delete this recording"
                },
                onClick = { if (confirmingDelete) onDelete() else confirmingDelete = true },
                leadingIcon = GuardianIcons.Close,
                modifier = Modifier.fillMaxWidth(),
            )
            if (confirmingDelete) {
                Spacer(Modifier.height(GuardianTheme.spacing.xs))
                Text(
                    text = "The transcript, the sounds and the path all go with it. " +
                        "This cannot be undone.",
                    style = GuardianTheme.type.labelSm,
                    color = GuardianTheme.materialColors.error,
                )
            }
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
            trail = SessionTrail("preview", emptyList(), emptyList()),
            onBack = {}, onExport = {},
        )
    }
}

/**
 * One moment in a recording, with how far into it the moment was.
 *
 * The offset rather than a clock time: "04:12" is how you find a point in a recording,
 * and "11:47 PM" is not, even though the absolute time is what the database stores.
 */
@Composable
private fun KeyMomentRow(
    moment: SessionNarrator.KeyMoment,
    modifier: Modifier = Modifier,
) {
    val accent = when (moment.severity) {
        SessionNarrator.Severity.Alarming -> GuardianTheme.materialColors.error
        SessionNarrator.Severity.Notable -> GuardianTheme.colors.accentWarm
        SessionNarrator.Severity.Routine -> GuardianTheme.materialColors.onSurfaceVariant
    }
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = formatOffset(moment.offsetMillis),
            style = GuardianTheme.type.labelSm,
            color = accent,
            modifier = Modifier.width(52.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = moment.label,
                style = GuardianTheme.type.labelMd,
                color = GuardianTheme.materialColors.onSurface,
            )
            if (moment.detail.isNotBlank()) {
                Text(
                    text = moment.detail,
                    style = GuardianTheme.type.bodySm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
            }
        }
    }
}

/** `mm:ss`, or `h:mm:ss` once a recording runs past an hour. */
private fun formatOffset(offsetMillis: Long): String {
    val totalSeconds = offsetMillis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
