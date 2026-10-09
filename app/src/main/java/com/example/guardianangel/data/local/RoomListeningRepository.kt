package com.example.guardianangel.data.local

import com.example.guardianangel.domain.model.ListeningRequirement
import com.example.guardianangel.domain.model.ListeningSensitivity
import com.example.guardianangel.domain.model.ListeningState
import com.example.guardianangel.domain.model.ListeningStatus
import com.example.guardianangel.domain.model.WakeWord
import com.example.guardianangel.domain.repository.ListeningRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Listening state, split by lifetime.
 *
 * The wake word is persisted — it has to survive a reboot or the app being killed
 * mid-walk. Permissions, whether a model is loaded, and whether capture is running are
 * deliberately **not**: they describe this process right now, and a stored copy would be
 * a lie the moment the user revoked a permission from system settings. Reading them
 * fresh is the only way the UI can be trusted when it says Angel is listening.
 */
class RoomListeningRepository(
    private val wakeWordStore: RoomWakeWordStore,
) : ListeningRepository {

    private val permissions = MutableStateFlow(PermissionSnapshot())
    private val detectorReady = MutableStateFlow(false)
    private val state = MutableStateFlow(ListeningState.Off)

    override fun observeWakeWord(): Flow<WakeWord> = wakeWordStore.observe()

    override fun observeStatus(): Flow<ListeningStatus> =
        combine(
            wakeWordStore.observe(),
            permissions,
            detectorReady,
            state,
        ) { word, perms, ready, current ->
            val blocked = buildList {
                if (!perms.microphone) add(ListeningRequirement.MicrophonePermission)
                if (!perms.notifications) add(ListeningRequirement.NotificationPermission)
                if (!word.isEnrolled) add(ListeningRequirement.WakeWordEnrolled)
                if (!perms.batteryExempt) add(ListeningRequirement.BatteryExemption)
            }
            ListeningStatus(
                // Derived, never stored: the UI must not be able to claim it is
                // listening while something required is missing.
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
        wakeWordStore.setPhrase(phrase)
        state.value = ListeningState.Off
    }

    override suspend fun addEnrolmentTake() = wakeWordStore.addVoiceSample()

    override suspend fun clearEnrolment() = wakeWordStore.clearVoiceSamples()

    override suspend fun setRequireVoiceMatch(enabled: Boolean) =
        wakeWordStore.setRequireVoiceMatch(enabled)

    override suspend fun setSensitivity(sensitivity: ListeningSensitivity) =
        wakeWordStore.setSensitivity(sensitivity)

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
        val microphone: Boolean = false,
        val notifications: Boolean = false,
        val batteryExempt: Boolean = false,
    )
}
