package com.example.guardianangel.data

import com.example.guardianangel.domain.model.ListeningRequirement
import com.example.guardianangel.domain.model.ListeningSensitivity
import com.example.guardianangel.domain.model.ListeningState
import com.example.guardianangel.domain.model.ListeningStatus
import com.example.guardianangel.domain.model.WakeWord
import com.example.guardianangel.domain.repository.ListeningRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * In-memory listening state.
 *
 * The blocked-by list is *derived* rather than stored, so it can never drift out of step
 * with the permissions and enrolment that produced it — the most likely bug in this area
 * would be an interface cheerfully saying "listening" with the microphone revoked.
 */
class FakeListeningRepository(
    startEnrolled: Boolean = true,
) : ListeningRepository {

    private val wakeWord = MutableStateFlow(
        if (startEnrolled) {
            WakeWord(phrase = "hey angel", voiceSamples = WakeWord.RECOMMENDED_SAMPLES)
        } else {
            WakeWord(phrase = "")
        }
    )

    private val permissions = MutableStateFlow(
        PermissionSnapshot(
            microphone = startEnrolled,
            notifications = startEnrolled,
            batteryExempt = false,
        )
    )
    private val hasVoiceProfile = MutableStateFlow(startEnrolled)
    private val detectorReady = MutableStateFlow(false)
    private val state = MutableStateFlow(ListeningState.Off)

    override fun observeWakeWord(): Flow<WakeWord> = wakeWord.asStateFlow()

    override fun observeStatus(): Flow<ListeningStatus> =
        combine(
            wakeWord,
            permissions,
            hasVoiceProfile,
            detectorReady,
            state,
        ) { word, perms, _, ready, current ->
            val blocked = buildList {
                if (!perms.microphone) add(ListeningRequirement.MicrophonePermission)
                if (!perms.notifications) add(ListeningRequirement.NotificationPermission)
                if (!word.isEnrolled) add(ListeningRequirement.WakeWordEnrolled)
                if (!perms.batteryExempt) add(ListeningRequirement.BatteryExemption)
            }
            ListeningStatus(
                // Never claim to be listening while something required is missing.
                state = if (blocked.any { it != ListeningRequirement.BatteryExemption }) {
                    ListeningState.Blocked
                } else {
                    current
                },
                blockedBy = blocked,
                detectorReady = ready,
            )
        }

    override suspend fun setWakeWordPhrase(phrase: String) {
        // The spotter works from the phrase alone, so changing it does not invalidate
        // the voiceprint — that belongs to her voice, not to this phrase.
        wakeWord.update { it.copy(phrase = phrase.trim()) }
        state.value = ListeningState.Off
    }

    override suspend fun addEnrolmentTake() =
        wakeWord.update { it.copy(voiceSamples = it.voiceSamples + 1) }

    override suspend fun clearEnrolment() =
        wakeWord.update { it.copy(voiceSamples = 0) }

    override suspend fun setRequireVoiceMatch(enabled: Boolean) =
        wakeWord.update { it.copy(requireVoiceMatch = enabled) }

    override suspend fun setSensitivity(sensitivity: ListeningSensitivity) =
        wakeWord.update { it.copy(sensitivity = sensitivity) }

    override suspend fun updatePermissions(
        microphone: Boolean,
        notifications: Boolean,
        batteryExempt: Boolean,
    ) {
        permissions.value = PermissionSnapshot(microphone, notifications, batteryExempt)
    }

    override suspend fun setDetectorReady(ready: Boolean) {
        detectorReady.value = ready
    }

    override suspend fun arm() {
        state.value = ListeningState.Listening
    }

    override suspend fun disarm() {
        state.value = ListeningState.Off
    }

    override suspend fun onWakeWordDetected() {
        state.value = ListeningState.Triggered
    }

    private data class PermissionSnapshot(
        val microphone: Boolean,
        val notifications: Boolean,
        val batteryExempt: Boolean,
    )
}
