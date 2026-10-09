package com.example.guardianangel.ui.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.WakeWord
import com.example.guardianangel.domain.repository.VoiceProfile
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.focalHalo

/**
 * The record-your-voice control, shared by onboarding and settings.
 *
 * One component rather than two because the two screens were drifting: the same feature
 * described differently in each is how a user ends up believing the takes teach Angel the
 * phrase, which they do not.
 *
 * The copy here carries one idea that has to survive: these takes are about *who*, never
 * *what*. The phrase works the moment it is typed. This only decides whether a stranger
 * who knows it can use it.
 */
@Composable
fun VoiceEnrolmentCard(
    state: VoiceEnrolmentUiState,
    phrase: String,
    onRecordTake: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enough = state.takes >= WakeWord.RECOMMENDED_SAMPLES

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TakeDots(taken = state.takes, total = WakeWord.RECOMMENDED_SAMPLES)
        Spacer(Modifier.height(GuardianTheme.spacing.md))
        RecordTakeButton(
            isRecording = state.isRecording,
            // Still enabled once there are enough: a user whose voice has changed, or
            // who enrolled in a quiet room and now gets ignored outdoors, needs a way to
            // add a better take without wiping the ones that work.
            enabled = true,
            onClick = onRecordTake,
        )
        Spacer(Modifier.height(GuardianTheme.spacing.sm))
        Text(
            text = when {
                state.error != null -> state.error
                state.isRecording -> "Listening — say it now."
                enough -> clarityMessage(state.clarityPercent)
                state.takes == 1 -> "Once more, in your normal voice."
                state.takes > 0 -> "One more after this."
                phrase.isBlank() -> "Set your wake word first, then record it here."
                else -> "Tap, then say \"$phrase\"."
            },
            style = GuardianTheme.type.bodySm,
            color = if (state.error != null) {
                GuardianTheme.materialColors.error
            } else {
                GuardianTheme.materialColors.onSurfaceVariant
            },
        )

        if (state.takes > 0) {
            Spacer(Modifier.height(GuardianTheme.spacing.xs))
            Text(
                text = "Start over",
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.primary,
                modifier = Modifier
                    .clip(GuardianTheme.shapes.sm)
                    .clickable(onClick = onReset)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * What to say once there are enough takes.
 *
 * Reports the truth rather than congratulating: takes that disagree make a voiceprint
 * that rejects *her*, and she needs to know that before she relies on it.
 */
private fun clarityMessage(clarityPercent: Int): String = when {
    clarityPercent >= GOOD_CLARITY -> "Got it — I'll know your voice."
    clarityPercent >= VoiceProfile.MIN_CLARITY ->
        "I've got your voice, though the takes varied a little. Somewhere quiet would " +
            "sharpen it."
    else ->
        "Those takes sounded quite different from each other, so I can't tell your " +
            "voice apart reliably yet. Try again somewhere quieter."
}

private const val GOOD_CLARITY = 75

/** Progress through the enrolment takes. */
@Composable
private fun TakeDots(taken: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        repeat(maxOf(total, taken)) { index ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < taken) {
                            GuardianTheme.colors.accentSoft
                        } else {
                            GuardianTheme.materialColors.surfaceContainerHigh
                        }
                    ),
            )
        }
    }
}

@Composable
private fun RecordTakeButton(
    isRecording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "take")
    val pulse by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "takePulse",
    )
    Box(
        modifier = Modifier
            .size(72.dp)
            .focalHalo(
                color = GuardianTheme.colors.focalGlow,
                spread = 1.5f,
                intensity = if (isRecording) pulse else 0.3f,
            )
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> GuardianTheme.materialColors.surfaceContainerHigh
                    isRecording -> GuardianTheme.colors.accentSoft
                    else -> GuardianTheme.materialColors.onSurface
                }
            )
            .clickable(enabled = enabled && !isRecording, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = GuardianIcons.Mic,
            contentDescription = if (isRecording) "Listening" else "Record a take",
            tint = when {
                !enabled -> GuardianTheme.materialColors.onSurfaceVariant
                isRecording -> GuardianTheme.colors.onAccentSoft
                else -> GuardianTheme.colors.canvas
            },
            modifier = Modifier.size(28.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VoiceEnrolmentCardPreview() {
    GuardianAngelTheme {
        VoiceEnrolmentCard(
            state = VoiceEnrolmentUiState(takes = 2, clarityPercent = 70),
            phrase = "hey angel",
            onRecordTake = {},
            onReset = {},
            modifier = Modifier.padding(GuardianTheme.spacing.lg),
        )
    }
}
