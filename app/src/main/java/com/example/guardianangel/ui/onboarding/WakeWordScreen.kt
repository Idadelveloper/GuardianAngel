package com.example.guardianangel.ui.onboarding

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.WakeWord
import com.example.guardianangel.domain.repository.ListeningRepository
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.components.GuardianTextField
import com.example.guardianangel.ui.components.GuardianWizardScaffold
import com.example.guardianangel.ui.home.components.TonalPill
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.mascot.AngelMood
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.focalHalo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The wake-word step.
 *
 * This screen carries the single most important idea in the whole product, and the one
 * easiest to get muddled: **there are two kinds of spoken trigger.**
 *
 *  - The *wake word* wakes Angel and starts her recording. One phrase, said when nothing
 *    is happening yet.
 *  - The *codewords* come next, and are said while she is already recording, to tell her
 *    what to do.
 *
 * If a user conflates them she will say her danger codeword into a phone that is not
 * listening and assume help is coming. So the distinction is taught here, in order,
 * before any codeword is set — and the screen says in as many words what happens when
 * Angel is *not* armed.
 */
@Composable
fun WakeWordRoute(
    repository: ListeningRepository,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    WakeWordScreen(
        modifier = modifier,
        onBack = onBack,
        onSave = { phrase, takes ->
            scope.launch {
                repository.setWakeWordPhrase(phrase)
                repeat(takes) { repository.addEnrolmentTake() }
                onContinue()
            }
        },
    )
}

@Composable
fun WakeWordScreen(
    onBack: () -> Unit,
    onSave: (phrase: String, takes: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var phrase by remember { mutableStateOf("") }
    var takes by remember { mutableIntStateOf(0) }
    var isRecordingTake by remember { mutableStateOf(false) }

    // Each take is a short capture. The real implementation hands the window to the
    // keyword spotter and averages the embeddings; here it just advances the counter.
    LaunchedEffect(isRecordingTake) {
        if (!isRecordingTake) return@LaunchedEffect
        delay(1600)
        takes++
        isRecordingTake = false
    }

    val enoughTakes = takes >= WakeWord.MIN_TAKES
    val canContinue = phrase.isNotBlank() && enoughTakes

    GuardianWizardScaffold(
        modifier = modifier,
        stepLabel = "Step 4 of 5 · Your wake word",
        progress = 0.8f,
        onBack = onBack,
        ctaLabel = if (canContinue) "Save wake word" else "Say it ${WakeWord.MIN_TAKES - takes} more time${if (WakeWord.MIN_TAKES - takes == 1) "" else "s"}",
        onCta = { onSave(phrase.trim(), takes) },
        ctaEnabled = canContinue,
    ) {
        AngelSays(
            message = "Pick one phrase that wakes me up. Say it and I start recording — " +
                "you won't have to touch your phone.",
            mood = AngelMood.Resting,
            mascotSize = 92.dp,
        )

        // The distinction, stated once, plainly, before anything else on the screen.
        GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
            Text(
                text = "Two different kinds of word",
                style = GuardianTheme.type.labelLg,
                color = GuardianTheme.materialColors.onSurface,
            )
            Spacer(Modifier.height(GuardianTheme.spacing.md))
            ExplainerRow(
                icon = GuardianIcons.Mic,
                title = "Your wake word — this step",
                body = "Wakes me from standby and starts recording. Just the one.",
                accent = true,
            )
            Spacer(Modifier.height(GuardianTheme.spacing.md))
            ExplainerRow(
                icon = GuardianIcons.Shield,
                title = "Your codewords — next step",
                body = "Said while I'm already recording, to tell me what to do: " +
                    "check in, stay quiet, alert your circle, or call for help.",
                accent = false,
            )
        }

        GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
            GuardianTextField(
                value = phrase,
                onValueChange = {
                    phrase = it
                    // The recorded takes belong to the old phrase.
                    if (takes > 0) takes = 0
                },
                label = "Your wake word",
                placeholder = "e.g. hey angel",
                leadingIcon = GuardianIcons.Waveform,
                supportingText = "Two or three syllables works best. Pick something you " +
                    "wouldn't say by accident mid-conversation.",
                modifier = Modifier.fillMaxWidth(),
            )

            if (phrase.isNotBlank()) {
                Spacer(Modifier.height(GuardianTheme.spacing.lg))
                Text(
                    text = "Now say it ${WakeWord.MIN_TAKES} times so I learn how you say it",
                    style = GuardianTheme.type.labelMd,
                    color = GuardianTheme.materialColors.onSurface,
                )
                Spacer(Modifier.height(GuardianTheme.spacing.md))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TakeDots(taken = takes, total = WakeWord.MIN_TAKES)
                    Spacer(Modifier.height(GuardianTheme.spacing.md))
                    RecordTakeButton(
                        isRecording = isRecordingTake,
                        enabled = !enoughTakes,
                        onClick = { isRecordingTake = true },
                    )
                    Spacer(Modifier.height(GuardianTheme.spacing.sm))
                    Text(
                        text = when {
                            isRecordingTake -> "Listening…"
                            enoughTakes -> "Got it — I'll know your voice."
                            takes > 0 -> "Once more, in your normal voice."
                            else -> "Tap and say \"${phrase.trim()}\""
                        },
                        style = GuardianTheme.type.bodySm,
                        color = GuardianTheme.materialColors.onSurfaceVariant,
                    )
                    if (takes > 0 && !enoughTakes) {
                        Spacer(Modifier.height(GuardianTheme.spacing.xs))
                        Text(
                            text = "Start over",
                            style = GuardianTheme.type.labelSm,
                            color = GuardianTheme.materialColors.primary,
                            modifier = Modifier
                                .clip(GuardianTheme.shapes.sm)
                                .clickable { takes = 0 }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        AssuranceCard(
            icon = GuardianIcons.Lock,
            title = "I'm not recording until you wake me",
            body = "While I'm listening I keep about a second of sound in memory to match " +
                "your wake word against, and overwrite it continuously. Nothing is saved " +
                "until you wake me.",
        )
    }
}

@Composable
private fun ExplainerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    accent: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(GuardianTheme.shapes.sm)
                .background(
                    if (accent) {
                        GuardianTheme.colors.accentSoft
                    } else {
                        GuardianTheme.materialColors.surfaceContainerHigh
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (accent) {
                    GuardianTheme.colors.onAccentSoft
                } else {
                    GuardianTheme.materialColors.onSurfaceVariant
                },
                modifier = Modifier.size(17.dp),
            )
        }
        Column {
            Text(
                text = title,
                style = GuardianTheme.type.labelMd,
                color = GuardianTheme.materialColors.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                style = GuardianTheme.type.bodySm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

/** Progress through the enrolment takes. */
@Composable
private fun TakeDots(taken: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        repeat(total) { index ->
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
            imageVector = if (enabled) GuardianIcons.Mic else GuardianIcons.Check,
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

@Preview(showBackground = true, device = "id:pixel_8", heightDp = 1500)
@Composable
private fun WakeWordPreview() {
    GuardianAngelTheme { WakeWordScreen(onBack = {}, onSave = { _, _ -> }) }
}
