package com.example.guardianangel.data.local

import com.example.guardianangel.domain.model.ListeningRequirement
import com.example.guardianangel.domain.model.ListeningSensitivity
import com.example.guardianangel.domain.model.ListeningState
import com.example.guardianangel.domain.model.ListeningStatus
import com.example.guardianangel.domain.model.WakeWord
import com.example.guardianangel.domain.repository.ListeningRepository
import com.example.guardianangel.domain.repository.PermissionProbe
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
    private val permissions: PermissionProbe,
) : ListeningRepository {

    /**
     * Bumped to re-emit the status after something that changes permissions invisibly.
     *
     * The probe is read inside the combine rather than cached, so the *first* value a
     * collector sees already reflects the system. This exists only because granting a
     * permission is not itself observable — nothing in app state changes when the user
     * taps Allow.
     */
    private val permissionGeneration = MutableStateFlow(0)
    private val detectorReady = MutableStateFlow(false)
    private val state = MutableStateFlow(ListeningState.Off)

    override fun observeWakeWord(): Flow<WakeWord> = wakeWordStore.observe()

    override fun observeStatus(): Flow<ListeningStatus> =
        combine(
            wakeWordStore.observe(),
            permissionGeneration,
            detectorReady,
            state,
        ) { word, _, ready, current ->
            val blocked = buildList {
                if (!permissions.hasMicrophone()) add(ListeningRequirement.MicrophonePermission)
                if (!permissions.hasNotifications()) {
                    add(ListeningRequirement.NotificationPermission)
                }
                if (!word.isEnrolled) add(ListeningRequirement.WakeWordEnrolled)
                if (!permissions.isBatteryExempt()) add(ListeningRequirement.BatteryExemption)
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

    override suspend fun canUseWakePhrase(phrase: String): Boolean =
        wakeWordStore.canRepresent(phrase)

    override suspend fun addEnrolmentTake() = wakeWordStore.addVoiceSample()

    override suspend fun clearEnrolment() = wakeWordStore.clearVoiceSamples()

    override suspend fun setRequireVoiceMatch(enabled: Boolean) =
        wakeWordStore.setRequireVoiceMatch(enabled)

    override suspend fun setSensitivity(sensitivity: ListeningSensitivity) =
        wakeWordStore.setSensitivity(sensitivity)

    override suspend fun refreshPermissions() {
        permissionGeneration.value += 1
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
}
