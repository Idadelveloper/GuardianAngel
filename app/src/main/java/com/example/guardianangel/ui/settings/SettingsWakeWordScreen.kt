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
import androidx.compose.runtime.LaunchedEffect
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
import com.example.guardianangel.domain.repository.VoiceProfile
import com.example.guardianangel.domain.repository.VoiceProfileRepository
import com.example.guardianangel.ui.voice.VoiceEnrolmentCard
import com.example.guardianangel.ui.voice.VoiceEnrolmentUiState
import com.example.guardianangel.ui.voice.rememberVoiceEnrolment
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
import kotlinx.coroutines.delay
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
    voiceProfiles: VoiceProfileRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val wakeWord by repository.observeWakeWord()
        .collectAsStateWithLifecycle(initialValue = WakeWord(phrase = ""))
    val enrolment = rememberVoiceEnrolment(repository, voiceProfiles)
    var draft by remember(wakeWord.phrase) { mutableStateOf(wakeWord.phrase) }
    var isUsable by remember { mutableStateOf(true) }

    // Debounced so the warning does not flicker on every keystroke mid-word.
    LaunchedEffect(draft) {
        if (draft.isBlank()) {
            isUsable = true
            return@LaunchedEffect
        }
        delay(350)
        isUsable = repository.canUseWakePhrase(draft.trim())
    }

    SettingsWakeWordScreen(
        wakeWord = wakeWord,
        enrolment = enrolment.state,
        draft = draft,
        onDraftChange = { draft = it },
        isDraftUsable = isUsable,
        onBack = onBack,
        onSavePhrase = { scope.launch { repository.setWakeWordPhrase(it) } },
        onAddTake = enrolment.controller::recordTake,
        onClearEnrolment = enrolment.controller::reset,
        onRequireVoiceMatch = { scope.launch { repository.setRequireVoiceMatch(it) } },
        onSensitivity = { scope.launch { repository.setSensitivity(it) } },
        modifier = modifier,
    )
}

@Composable
fun SettingsWakeWordScreen(
    wakeWord: WakeWord,
    onBack: () -> Unit,
    enrolment: VoiceEnrolmentUiState = VoiceEnrolmentUiState(),
    draft: String = wakeWord.phrase,
    onDraftChange: (String) -> Unit = {},
    /** False when the model cannot express the draft, so saving it would be a lie. */
    isDraftUsable: Boolean = true,
    onSavePhrase: (String) -> Unit,
    onAddTake: () -> Unit,
    onClearEnrolment: () -> Unit,
    onRequireVoiceMatch: (Boolean) -> Unit,
    onSensitivity: (ListeningSensitivity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val phraseChanged = draft.trim() != wakeWord.phrase

    GuardianStackScaffold(
        title = "Wake word",
        onBack = onBack,
        modifier = modifier,
        bottomBar = if (phraseChanged) {
            {
                GuardianPrimaryButton(
                    text = "Save wake word",
                    onClick = { onSavePhrase(draft.trim()) },
                    // Disabled rather than hidden: a greyed button with the reason beside
                    // the field explains itself; a vanished one looks like a bug.
                    enabled = isDraftUsable && draft.isNotBlank(),
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
                onValueChange = onDraftChange,
                label = "Wake word",
                placeholder = "e.g. hey angel",
                leadingIcon = GuardianIcons.Waveform,
                isError = draft.isNotBlank() && !isDraftUsable,
                supportingText = when {
                    draft.isNotBlank() && !isDraftUsable ->
                        "I can't pronounce that one — try ordinary words."
                    phraseChanged -> "Save and I'll start listening for the new phrase."
                    else -> "This is all I need to wake up."
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
            if (wakeWord.requireVoiceMatch && enrolment.isAvailable) {
                Spacer(Modifier.height(GuardianTheme.spacing.md))
                // States what the switch above is *actually doing* right now, not what
                // it is set to. A voiceprint that is missing or inconsistent means the
                // gate is off however the switch looks, and hiding that would promise a
                // protection the app is not providing.
                Text(
                    text = when {
                        enrolment.takes == 0 ->
                            "I don't know your voice yet, so I'll wake for anyone who " +
                                "says your phrase. Record below to change that."
                        enrolment.clarityPercent < VoiceProfile.MIN_CLARITY ->
                            "Your takes varied too much for me to tell your voice apart " +
                                "reliably, so for now I'll wake for any voice. Another " +
                                "recording somewhere quiet would fix it."
                        else ->
                            "I know your voice (${enrolment.clarityPercent}% clarity, " +
                                "${enrolment.takes} samples), so someone else saying your " +
                                "phrase won't wake me."
                    },
                    style = GuardianTheme.type.bodySm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
                Spacer(Modifier.height(GuardianTheme.spacing.md))
                VoiceEnrolmentCard(
                    state = enrolment,
                    phrase = wakeWord.phrase,
                    onRecordTake = onAddTake,
                    onReset = onClearEnrolment,
                )
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
