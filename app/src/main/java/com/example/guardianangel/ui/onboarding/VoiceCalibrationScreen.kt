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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.guardianangel.domain.repository.AccountRepository
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.components.GuardianWizardScaffold
import com.example.guardianangel.ui.home.components.TonalPill
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.mascot.AngelMood
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.focalHalo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

private const val TARGET_SECONDS = 60

/**
 * Step 2 — enrol the user's voiceprint.
 *
 * Simplified from the reference, which carried a countdown, a progress bar, three
 * telemetry chips and two transport controls at once. During setup that reads as a studio
 * console; here it is one prompt, one button, one progress ring. Clarity chips appear
 * only once there is something real to report.
 */
@Composable
fun VoiceCalibrationRoute(
    repository: AccountRepository,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    VoiceCalibrationScreen(
        modifier = modifier,
        onBack = onBack,
        onSave = { clarity ->
            scope.launch {
                repository.saveVoiceProfile(clarity)
                onContinue()
            }
        },
    )
}

@Composable
fun VoiceCalibrationScreen(
    onBack: () -> Unit,
    onSave: (clarityPercent: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isRecording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }

    // Ticks only while recording, so pausing genuinely freezes the capture.
    LaunchedEffect(isRecording) {
        while (isRecording && elapsed < TARGET_SECONDS) {
            delay(1000)
            elapsed++
        }
        if (elapsed >= TARGET_SECONDS) isRecording = false
    }

    val progress = elapsed.toFloat() / TARGET_SECONDS
    val isComplete = elapsed >= TARGET_SECONDS
    // Clarity is a stand-in until the real acoustic model reports one.
    val clarity = (70 + progress * 24).toInt()

    GuardianWizardScaffold(
        modifier = modifier,
        stepLabel = "Step 2 of 5 · Your voice",
        progress = 0.4f,
        onBack = onBack,
        ctaLabel = if (isComplete) "Save voiceprint & continue" else "Record a minute to continue",
        onCta = { onSave(clarity) },
        ctaEnabled = isComplete,
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
                    onToggle = { isRecording = !isRecording },
                )
                Spacer(Modifier.height(GuardianTheme.spacing.sm))
                Text(
                    text = when {
                        isComplete -> "Got it — that's plenty."
                        isRecording -> "Listening… tap to pause"
                        elapsed > 0 -> "Paused · tap to keep going"
                        else -> "Tap to start"
                    },
                    style = GuardianTheme.type.bodySm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )

                if (elapsed > 0) {
                    Spacer(Modifier.height(GuardianTheme.spacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm)) {
                        TonalPill(text = "Clarity $clarity%", icon = GuardianIcons.Check)
                        TonalPill(
                            text = "Noise filtered",
                            container = GuardianTheme.materialColors.surfaceContainerLowest,
                            content = GuardianTheme.materialColors.onSurfaceVariant,
                        )
                        Text(
                            text = "Start over",
                            style = GuardianTheme.type.labelSm,
                            color = GuardianTheme.materialColors.primary,
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .clip(GuardianTheme.shapes.sm)
                                .clickable {
                                    isRecording = false
                                    elapsed = 0
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
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
    GuardianAngelTheme { VoiceCalibrationScreen(onBack = {}, onSave = {}) }
}
