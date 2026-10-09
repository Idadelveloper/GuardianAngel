package com.example.guardianangel.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.guardianangel.domain.model.ListeningSensitivity
import com.example.guardianangel.domain.model.WakeWord
import com.example.guardianangel.domain.repository.ListeningRepository
import com.example.guardianangel.ui.activities.SegmentedToggle
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.components.GuardianOutlinedButton
import com.example.guardianangel.ui.components.GuardianPrimaryButton
import com.example.guardianangel.ui.components.GuardianStackScaffold
import com.example.guardianangel.ui.components.GuardianTextField
import com.example.guardianangel.ui.home.components.SectionHeader
import com.example.guardianangel.ui.home.components.TonalPill
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.onboarding.AssuranceCard
import com.example.guardianangel.ui.onboarding.GuardianSwitch
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme
import kotlinx.coroutines.launch

/**
 * Wake-word settings.
 *
 * Sits apart from the codeword screen because the two are different machinery with
 * different costs, and mixing them in one list would hide that. Everything here feeds
 * the always-on model: changing the phrase throws away the enrolment that trained it,
 * and sensitivity trades battery and false wakes against catching a whisper.
 */
@Composable
fun SettingsWakeWordRoute(
    repository: ListeningRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val wakeWord by repository.observeWakeWord()
        .collectAsStateWithLifecycle(initialValue = WakeWord(phrase = ""))

    SettingsWakeWordScreen(
        wakeWord = wakeWord,
        onBack = onBack,
        onSavePhrase = { scope.launch { repository.setWakeWordPhrase(it) } },
        onAddTake = { scope.launch { repository.addEnrolmentTake() } },
        onClearEnrolment = { scope.launch { repository.clearEnrolment() } },
        onRequireVoiceMatch = { scope.launch { repository.setRequireVoiceMatch(it) } },
        onSensitivity = { scope.launch { repository.setSensitivity(it) } },
        modifier = modifier,
    )
}

@Composable
fun SettingsWakeWordScreen(
    wakeWord: WakeWord,
    onBack: () -> Unit,
    onSavePhrase: (String) -> Unit,
    onAddTake: () -> Unit,
    onClearEnrolment: () -> Unit,
    onRequireVoiceMatch: (Boolean) -> Unit,
    onSensitivity: (ListeningSensitivity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(wakeWord.phrase) { mutableStateOf(wakeWord.phrase) }
    val phraseChanged = draft.trim() != wakeWord.phrase

    GuardianStackScaffold(
        title = "Wake word",
        onBack = onBack,
        modifier = modifier,
        bottomBar = if (phraseChanged) {
            {
                GuardianPrimaryButton(
                    text = "Save and re-record",
                    onClick = { onSavePhrase(draft) },
                    leadingIcon = GuardianIcons.Check,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            null
        },
    ) {
        AssuranceCard(
            icon = GuardianIcons.Mic,
            title = "This is the word that wakes me",
            body = "Say it and I start recording. The codewords that tell me what to do " +
                "come after — they live on their own screen.",
        )

        GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Your phrase",
                    style = GuardianTheme.type.labelLg,
                    color = GuardianTheme.materialColors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TonalPill(
                    text = if (wakeWord.isEnrolled) "Ready" else "Not set",
                    icon = if (wakeWord.isEnrolled) GuardianIcons.Check else GuardianIcons.Warning,
                )
            }
            Spacer(Modifier.height(GuardianTheme.spacing.md))
            GuardianTextField(
                value = draft,
                onValueChange = { draft = it },
                label = "Wake word",
                placeholder = "e.g. hey angel",
                leadingIcon = GuardianIcons.Waveform,
                supportingText = if (phraseChanged) {
                    "Save and I'll start listening for the new phrase."
                } else {
                    "This is all I need to wake up."
                },
                modifier = Modifier.fillMaxWidth(),
            )

        }

        GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
            SectionHeader(title = "How closely I listen", icon = GuardianIcons.Waveform)
            Spacer(Modifier.height(GuardianTheme.spacing.sm))
            Text(
                text = "Whisper catches you under your breath but wakes more often by " +
                    "mistake. A false wake only costs you one tap to cancel.",
                style = GuardianTheme.type.bodySm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
            Spacer(Modifier.height(GuardianTheme.spacing.md))
            SegmentedToggle(
                options = listOf("Low", "Balanced", "Whisper"),
                selectedIndex = wakeWord.sensitivity.ordinal,
                onSelect = { onSensitivity(ListeningSensitivity.entries[it]) },
            )
        }

        GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Only wake for my voice",
                        style = GuardianTheme.type.labelLg,
                        color = GuardianTheme.materialColors.onSurface,
                    )
                    Text(
                        text = "Checks the voice against your voiceprint, so someone else " +
                            "saying your wake word won't start a recording.",
                        style = GuardianTheme.type.bodySm,
                        color = GuardianTheme.materialColors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(GuardianTheme.spacing.sm))
                GuardianSwitch(
                    checked = wakeWord.requireVoiceMatch,
                    onCheckedChange = onRequireVoiceMatch,
                )
            }

            // Voice samples are optional and only matter for the check above, so they
            // live with it rather than beside the phrase — recording them is never a
            // condition of Angel waking up.
            if (wakeWord.requireVoiceMatch) {
                Spacer(Modifier.height(GuardianTheme.spacing.md))
                Text(
                    text = if (wakeWord.canMatchVoice) {
                        "I've heard your voice ${wakeWord.voiceSamples} times — enough to tell it apart."
                    } else {
                        "Record your voice ${WakeWord.RECOMMENDED_SAMPLES - wakeWord.voiceSamples} " +
                            "more times so I can tell it from someone else's. Until then I'll " +
                            "wake for anyone who says your phrase."
                    },
                    style = GuardianTheme.type.bodySm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
                Spacer(Modifier.height(GuardianTheme.spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm)) {
                    GuardianOutlinedButton(
                        text = if (wakeWord.canMatchVoice) "Record again" else "Record my voice",
                        onClick = onAddTake,
                        leadingIcon = GuardianIcons.Mic,
                        modifier = Modifier.weight(1f),
                    )
                    if (wakeWord.voiceSamples > 0) {
                        GuardianOutlinedButton(
                            text = "Reset",
                            onClick = onClearEnrolment,
                            leadingIcon = GuardianIcons.Refresh,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        AssuranceCard(
            icon = GuardianIcons.Power,
            title = "You arm me — I can't arm myself",
            body = "Android only lets an app start listening while it's open. Arm me from " +
                "the home screen before you set off; after that I keep listening with " +
                "your phone locked until you stand me down.",
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_8", heightDp = 1500)
@Composable
private fun SettingsWakeWordPreview() {
    GuardianAngelTheme {
        SettingsWakeWordScreen(
            wakeWord = WakeWord(phrase = "hey angel", voiceSamples = 3),
            onBack = {}, onSavePhrase = {}, onAddTake = {}, onClearEnrolment = {},
            onRequireVoiceMatch = {}, onSensitivity = {},
        )
    }
}
