package com.example.guardianangel.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.guardianangel.audio.VoiceEnroller
import com.example.guardianangel.domain.repository.AccountRepository
import com.example.guardianangel.domain.repository.ListeningRepository
import com.example.guardianangel.domain.repository.VoiceProfile
import com.example.guardianangel.domain.repository.VoiceProfileRepository
import com.example.guardianangel.ui.voice.VoiceEnrolmentUiState
import com.example.guardianangel.ui.voice.rememberVoiceEnrolment
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.components.GuardianWizardScaffold
import com.example.guardianangel.ui.home.components.TonalPill
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.mascot.AngelMood
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.focalHalo
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

/**
 * Step 2 — enrol the user's voiceprint.
 *
 * Simplified from the reference, which carried a countdown, a progress bar, three
 * telemetry chips and two transport controls at once. During setup that reads as a studio
 * console; here it is one prompt, one button, one progress ring. Clarity appears only
 * once there is something real to report — and it *is* real: it comes from how well the
 * segments of speech agreed with each other, so a noisy room reports a low number rather
 * than a reassuring one.
 *
 * This step is skippable. Without a voiceprint the wake word still works; it just wakes
 * for anyone who says the phrase, which the screen says plainly rather than implying a
 * protection that is not there.
 */
@Composable
fun VoiceCalibrationRoute(
    repository: AccountRepository,
    listeningRepository: ListeningRepository,
    voiceProfiles: VoiceProfileRepository,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val enrolment = rememberVoiceEnrolment(listeningRepository, voiceProfiles)

    VoiceCalibrationScreen(
        modifier = modifier,
        enrolment = enrolment.state,
        onBack = onBack,
        onToggleRecording = enrolment.controller::recordSession,
        onStartOver = enrolment.controller::reset,
        onSave = { clarity ->
            scope.launch {
                // Mirrors the clarity onto the account row the settings screens read.
                // The voiceprint itself was already saved by the controller.
                repository.saveVoiceProfile(clarity)
                onContinue()
            }
        },
        onSkip = onContinue,
    )
}

@Composable
fun VoiceCalibrationScreen(
    onBack: () -> Unit,
    onSave: (clarityPercent: Int) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    enrolment: VoiceEnrolmentUiState = VoiceEnrolmentUiState(),
    onToggleRecording: () -> Unit = {},
    onStartOver: () -> Unit = {},
) {
    val isRecording = enrolment.isRecording
    val elapsed = enrolment.elapsedMillis / 1000
    val progress = (enrolment.elapsedMillis.toFloat() / TARGET_MILLIS).coerceIn(0f, 1f)

    // Having *a* voiceprint is what matters, not having recorded the full minute: the
    // embedding is useful from the first few seconds of speech and simply gets steadier.
    val hasVoiceprint = enrolment.takes > 0

    GuardianWizardScaffold(
        modifier = modifier,
        stepLabel = "Step 2 of 5 · Your voice",
        progress = 0.4f,
        onBack = onBack,
        ctaLabel = if (hasVoiceprint) "Save voiceprint & continue" else "Record to continue",
        onCta = { onSave(enrolment.clarityPercent) },
        ctaEnabled = hasVoiceprint && !isRecording,
        skipLabel = if (hasVoiceprint) null else "Skip — wake for any voice",
        onSkip = onSkip.takeIf { !hasVoiceprint },
    ) {
        AngelSays(
            message = "Talk to me for a minute so I learn your voice. Tell me about a " +
                "walk you like, or anything that feels calm to say.",
            mood = AngelMood.Resting,
            mascotSize = 92.dp,
        )

        GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = formatSeconds(elapsed),
                    style = GuardianTheme.typography.displaySmall,
                    color = GuardianTheme.materialColors.onSurface,
                )
                Text(
                    text = "of 1:00",
                    style = GuardianTheme.type.labelSm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )

                Spacer(Modifier.height(GuardianTheme.spacing.md))
                VoiceWaveform(active = isRecording)
                Spacer(Modifier.height(GuardianTheme.spacing.md))

                // Track doubles as the progress indicator, so there is one less widget.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(GuardianTheme.materialColors.surfaceContainerHigh),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(GuardianTheme.colors.accentSoft),
                    )
                }

                Spacer(Modifier.height(GuardianTheme.spacing.lg))
                MicButton(
                    isRecording = isRecording,
                    onToggle = onToggleRecording,
                )
                Spacer(Modifier.height(GuardianTheme.spacing.sm))
                Text(
                    text = when {
                        enrolment.error != null -> enrolment.error
                        !enrolment.isAvailable ->
                            "This build can't learn voices, so I'll wake for any voice " +
                                "saying your phrase."
                        isRecording -> "Listening… keep talking"
                        hasVoiceprint -> "Got it — I know your voice now."
                        else -> "Tap and talk to me for a minute"
                    },
                    style = GuardianTheme.type.bodySm,
                    color = if (enrolment.error != null) {
                        GuardianTheme.materialColors.error
                    } else {
                        GuardianTheme.materialColors.onSurfaceVariant
                    },
                )

                if (hasVoiceprint && !isRecording) {
                    Spacer(Modifier.height(GuardianTheme.spacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm)) {
                        TonalPill(
                            text = "Clarity ${enrolment.clarityPercent}%",
                            icon = if (enrolment.clarityPercent >= VoiceProfile.MIN_CLARITY) {
                                GuardianIcons.Check
                            } else {
                                GuardianIcons.Warning
                            },
                        )
                        Text(
                            text = "Start over",
                            style = GuardianTheme.type.labelSm,
                            color = GuardianTheme.materialColors.primary,
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .clip(GuardianTheme.shapes.sm)
                                .clickable(onClick = onStartOver)
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }

                    if (enrolment.clarityPercent < VoiceProfile.MIN_CLARITY) {
                        Spacer(Modifier.height(GuardianTheme.spacing.sm))
                        Text(
                            text = "That was clear enough to save, but not to tell your " +
                                "voice from someone else's. Until it improves I'll wake " +
                                "for any voice saying your phrase.",
                            style = GuardianTheme.type.bodySm,
                            color = GuardianTheme.materialColors.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        AssuranceCard(
            icon = GuardianIcons.Lock,
            title = "Stored in your phone's secure enclave",
            body = "Your voiceprint is a mathematical signature, not a recording, and it " +
                "never leaves this device.",
        )
    }
}

/** The minute of free speech the step asks for. */
private val TARGET_MILLIS = VoiceEnroller.SESSION_MILLIS

@Composable
private fun MicButton(isRecording: Boolean, onToggle: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "mic")
    val pulse by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "micPulse",
    )
    Box(
        modifier = Modifier
            .size(84.dp)
            .focalHalo(
                color = GuardianTheme.colors.focalGlow,
                spread = 1.5f,
                intensity = if (isRecording) pulse else 0.35f,
            )
            .clip(CircleShape)
            .background(
                if (isRecording) {
                    GuardianTheme.colors.accentSoft
                } else {
                    GuardianTheme.materialColors.onSurface
                }
            )
            .clickable(role = Role.Button, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isRecording) GuardianIcons.StopSquare else GuardianIcons.Mic,
            contentDescription = if (isRecording) "Pause recording" else "Start recording",
            tint = if (isRecording) {
                GuardianTheme.colors.onAccentSoft
            } else {
                GuardianTheme.colors.canvas
            },
            modifier = Modifier.size(32.dp),
        )
    }
}

/** Oscillating bars standing in for live input level. Flat and still when paused. */
@Composable
private fun VoiceWaveform(active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase",
    )
    val accent = GuardianTheme.colors.accentSoft
    val idle = GuardianTheme.materialColors.surfaceContainerHigh

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        val bars = 32
        val gap = size.width / bars
        repeat(bars) { i ->
            val amplitude = if (active) abs(sin(phase + i * 0.5f)) * 0.85f + 0.15f else 0.12f
            val h = size.height * amplitude
            val x = gap * i + gap / 2f
            drawLine(
                color = if (active) accent else idle,
                start = Offset(x, size.height / 2f - h / 2f),
                end = Offset(x, size.height / 2f + h / 2f),
                strokeWidth = gap * 0.4f,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun formatSeconds(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

@Preview(showBackground = true, device = "id:pixel_8", heightDp = 1200)
@Composable
private fun VoiceCalibrationPreview() {
    GuardianAngelTheme { VoiceCalibrationScreen(onBack = {}, onSave = {}, onSkip = {}) }
}
