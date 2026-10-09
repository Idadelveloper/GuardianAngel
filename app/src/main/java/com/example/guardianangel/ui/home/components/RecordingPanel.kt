package com.example.guardianangel.ui.home.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.example.guardianangel.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.RecordingSession
import com.example.guardianangel.ui.components.GuardianPrimaryButton
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

/**
 * The panel shown while Guardian Angel is actively recording and transcribing.
 *
 * This is the screen's most important state, and it is built around three questions the
 * user needs answered instantly: *is it really recording*, *who already knows*, and *how
 * do I stop this*.
 *
 * The product brief calls for an accidental trigger to be cancellable, so "Stop
 * recording" is a full-width control sitting directly under the transcript rather than an
 * icon tucked in a corner. It is intentionally **not** a hold gesture: arming an alert
 * should be deliberate, but standing one down should be immediate.
 */
@Composable
fun ActiveRecordingPanel(
    session: RecordingSession,
    contactsNotifiedLabel: String?,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GuardianTheme.colors
    val tier = session.triggeredBy

    // Recording is urgent, so the panel uses the error container at the two top tiers and
    // the warm accent otherwise — loud enough to be unmistakable, never a strobe.
    val isCritical = tier == CodewordTier.Emergency || tier == CodewordTier.Danger
    val container = if (isCritical) {
        GuardianTheme.materialColors.errorContainer
    } else {
        colors.accentWarm
    }
    val onContainer = if (isCritical) {
        GuardianTheme.materialColors.onErrorContainer
    } else {
        colors.onAccentWarm
    }

    var elapsedSeconds by remember(session.id) { mutableLongStateOf(0L) }
    LaunchedEffect(session.id) {
        while (true) {
            elapsedSeconds = (System.currentTimeMillis() - session.startedAtEpochMillis) / 1000
            delay(1_000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(GuardianTheme.shapes.xl)
            .background(container)
            .border(1.dp, colors.borderEmphasis, GuardianTheme.shapes.xl)
            .padding(GuardianTheme.spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(color = GuardianTheme.materialColors.error, size = 12.dp)
            Spacer(Modifier.size(GuardianTheme.spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // Announced once when recording starts, so a screen-reader user is
                    // told without having to go hunting for the change.
                    text = stringResource(R.string.recording_title),
                    style = GuardianTheme.type.labelLg,
                    color = onContainer,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
                Text(
                    text = tier
                        ?.let { stringResource(R.string.recording_triggered_by, it.name.lowercase()) }
                        ?: stringResource(R.string.recording_started_manually),
                    style = GuardianTheme.type.bodySm,
                    color = onContainer.copy(alpha = 0.8f),
                )
            }
            Text(
                text = formatElapsed(elapsedSeconds),
                style = GuardianTheme.type.headlineMd,
                color = onContainer,
            )
        }

        Spacer(Modifier.height(GuardianTheme.spacing.md))

        LiveWaveform(color = onContainer)

        Spacer(Modifier.height(GuardianTheme.spacing.md))

        // What has already happened on the user's behalf, stated plainly.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GuardianTheme.shapes.lg)
                .background(GuardianTheme.materialColors.surfaceContainerLowest.copy(alpha = 0.75f))
                .padding(GuardianTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
        ) {
            DispatchRow(
                icon = GuardianIcons.Users,
                text = contactsNotifiedLabel ?: stringResource(R.string.recording_none_alerted),
                done = session.contactsNotified.isNotEmpty(),
            )
            if (session.isPoliceDispatched) {
                DispatchRow(
                    icon = GuardianIcons.Phone,
                    text = stringResource(R.string.recording_police_called),
                    done = true,
                )
            }
            DispatchRow(
                icon = GuardianIcons.MapPin,
                text = stringResource(R.string.recording_location_shared),
                done = session.contactsNotified.isNotEmpty(),
            )
        }

        Spacer(Modifier.height(GuardianTheme.spacing.md))
        Text(
            text = stringResource(R.string.recording_transcript),
            style = GuardianTheme.type.labelMd,
            color = onContainer,
        )
        Spacer(Modifier.height(GuardianTheme.spacing.xs))
        if (session.transcriptPreview.isNotEmpty()) {
            session.transcriptPreview.takeLast(4).forEach { line ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
                ) {
                    Text(
                        text = line.speakerLabel,
                        style = GuardianTheme.type.labelSm,
                        color = onContainer.copy(alpha = 0.85f),
                    )
                    Text(
                        text = line.text,
                        style = GuardianTheme.type.bodySm,
                        color = if (line.isFlagged) {
                            GuardianTheme.materialColors.error
                        } else {
                            onContainer
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Text(
                text = "Angel is actively listening and logging surroundings in real-time…",
                style = GuardianTheme.type.bodySm,
                color = onContainer.copy(alpha = 0.75f),
            )
        }

        Spacer(Modifier.height(GuardianTheme.spacing.md))

        // The accidental-trigger escape hatch. One tap, no confirmation dialog.
        //
        // Deliberately the primary dark treatment rather than an outline: an outlined
        // button's border measured only 2.65:1 against this panel's error container,
        // below the 3:1 floor for an element whose boundary identifies the control —
        // and this is the one control a frightened user must find instantly.
        GuardianPrimaryButton(
            text = stringResource(R.string.action_stop_recording),
            onClick = onStop,
            leadingIcon = GuardianIcons.StopSquare,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(GuardianTheme.spacing.xs))
        Text(
            text = stringResource(R.string.recording_mistake_hint),
            style = GuardianTheme.type.bodySm,
            color = onContainer.copy(alpha = 0.78f),
        )
    }
}

@Composable
private fun DispatchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    done: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        Icon(
            imageVector = if (done) GuardianIcons.Check else icon,
            contentDescription = null,
            tint = if (done) {
                GuardianTheme.materialColors.primary
            } else {
                GuardianTheme.materialColors.onSurfaceVariant
            },
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = GuardianTheme.type.bodySm,
            color = GuardianTheme.materialColors.onSurface,
        )
    }
}

/**
 * A moving waveform standing in for live mic input.
 *
 * Decorative confirmation that audio is flowing. Once the real pipeline lands this should
 * be driven by actual amplitude samples rather than a sine wave.
 */
@Composable
private fun LiveWaveform(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1800, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
        label = "wavePhase",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
    ) {
        val bars = 36
        val gap = size.width / bars
        repeat(bars) { i ->
            val amplitude = abs(sin(phase + i * 0.45f)) * 0.8f + 0.2f
            val barHeight = size.height * amplitude
            val x = gap * i + gap / 2f
            drawLine(
                color = color.copy(alpha = 0.55f + amplitude * 0.45f),
                start = Offset(x, size.height / 2f - barHeight / 2f),
                end = Offset(x, size.height / 2f + barHeight / 2f),
                strokeWidth = gap * 0.35f,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
