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
import com.example.guardianangel.audio.SentencePieceTokenizer
import com.example.guardianangel.domain.model.WakeWord
import com.example.guardianangel.domain.repository.ListeningRepository
import com.example.guardianangel.domain.repository.VoiceProfileRepository
import com.example.guardianangel.ui.voice.VoiceEnrolmentCard
import com.example.guardianangel.ui.voice.VoiceEnrolmentUiState
import com.example.guardianangel.ui.voice.rememberVoiceEnrolment
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
    voiceProfiles: VoiceProfileRepository,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val enrolment = rememberVoiceEnrolment(repository, voiceProfiles)
    var phrase by remember { mutableStateOf("") }
    var isUsable by remember { mutableStateOf(true) }

    // Debounced so the warning does not flicker on every keystroke mid-word.
    LaunchedEffect(phrase) {
        if (phrase.isBlank()) {
            isUsable = true
            return@LaunchedEffect
        }
        delay(350)
        isUsable = repository.canUseWakePhrase(phrase.trim())
    }

    WakeWordScreen(
        modifier = modifier,
        enrolment = enrolment.state,
        phrase = phrase,
        onPhraseChange = { phrase = it },
        isPhraseUsable = isUsable,
        onBack = onBack,
        onRecordTake = enrolment.controller::recordTake,
        onResetTakes = enrolment.controller::reset,
        onSave = { phrase ->
            scope.launch {
                repository.setWakeWordPhrase(phrase)
                onContinue()
            }
        },
        // Skipping is allowed, and stated plainly rather than hidden: a wake word set
        // later works just as well. What does not work is hands-free activation without
        // one, which is why the home screen keeps offering it until it is set.
        onSkip = onContinue,
    )
}

@Composable
fun WakeWordScreen(
    onBack: () -> Unit,
    onSave: (phrase: String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    phrase: String = "",
    onPhraseChange: (String) -> Unit = {},
    /** False when the model cannot express the phrase, so saving it would be a lie. */
    isPhraseUsable: Boolean = true,
    enrolment: VoiceEnrolmentUiState = VoiceEnrolmentUiState(),
    onRecordTake: () -> Unit = {},
    onResetTakes: () -> Unit = {},
) {
    // Typing the phrase is enough. The spotter is open-vocabulary, so there is nothing
    // standing between setting a wake word and being protected by it — as long as the
    // model can actually express it.
    val canContinue = phrase.isNotBlank() && isPhraseUsable

    GuardianWizardScaffold(
        modifier = modifier,
        stepLabel = "Step 4 of 5 · Your wake word",
        progress = 0.8f,
        onBack = onBack,
        ctaLabel = "Save wake word",
        onCta = { onSave(phrase.trim()) },
        ctaEnabled = canContinue,
        skipLabel = "Set this up later",
        onSkip = onSkip,
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
                onValueChange = onPhraseChange,
                isError = phrase.isNotBlank() && !isPhraseUsable,
                label = "Your wake word",
                placeholder = "e.g. hey angel",
                leadingIcon = GuardianIcons.Waveform,
                supportingText = if (phrase.isNotBlank() && !isPhraseUsable) {
                    "I can't pronounce that one — try ordinary words, or one of " +
                        SentencePieceTokenizer.CURATED_PHRASES.take(3)
                            .joinToString(", ") { "\"$it\"" } + "."
                } else {
                    "Two or three syllables works best. Pick something you wouldn't say " +
                        "by accident mid-conversation — try " +
                        SentencePieceTokenizer.CURATED_PHRASES.take(3)
                            .joinToString(", ") { "\"$it\"" } + "."
                },
                modifier = Modifier.fillMaxWidth(),
            )

        }

        // Optional, and framed that way. Samples do not teach Angel the phrase — she
        // already knows it — they teach her *your voice*, so a stranger saying it cannot
        // start a recording. Making this a gate would add a minute of setup for a
        // protection that is worth having but not worth delaying everything else for.
        if (phrase.isNotBlank() && enrolment.isAvailable) {
            GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Only wake for your voice",
                        style = GuardianTheme.type.labelLg,
                        color = GuardianTheme.materialColors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    TonalPill(text = "Optional")
                }
                Spacer(Modifier.height(GuardianTheme.spacing.xs))
                Text(
                    text = "Say it a few times and I'll learn how you sound, so somebody " +
                        "else saying your phrase won't start a recording.",
                    style = GuardianTheme.type.bodySm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
                Spacer(Modifier.height(GuardianTheme.spacing.md))

                VoiceEnrolmentCard(
                    state = enrolment,
                    phrase = phrase.trim(),
                    onRecordTake = onRecordTake,
                    onReset = onResetTakes,
                )
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

@Preview(showBackground = true, device = "id:pixel_8", heightDp = 1500)
@Composable
private fun WakeWordPreview() {
    GuardianAngelTheme { WakeWordScreen(onBack = {}, onSave = {}, onSkip = {}) }
}
