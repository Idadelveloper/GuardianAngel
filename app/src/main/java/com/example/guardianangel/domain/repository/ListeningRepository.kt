package com.example.guardianangel.domain.repository

import com.example.guardianangel.domain.model.ListeningSensitivity
import com.example.guardianangel.domain.model.ListeningStatus
import com.example.guardianangel.domain.model.WakeWord
import kotlinx.coroutines.flow.Flow

/**
 * Hands-free activation: the wake word, and whether Angel is allowed to listen for it.
 *
 * Separate from [CodewordRepository] on purpose. The wake word runs through an always-on
 * audio model with a hard battery budget, while codewords are matched against a
 * transcript that only exists once recording has already started. They are configured in
 * different places, constrained by different things, and will be stored differently —
 * one row beside the device settings versus a table of phrases.
 */
interface ListeningRepository {

    fun observeWakeWord(): Flow<WakeWord>

    fun observeStatus(): Flow<ListeningStatus>

    /**
     * Sets the phrase.
     *
     * Does not clear the voiceprint: that describes her voice, not this phrase, so a
     * changed wake word keeps the voice matching that was already enrolled.
     */
    suspend fun setWakeWordPhrase(phrase: String)

    /**
     * Whether the keyword model can express [phrase].
     *
     * The spotter works from sub-word pieces, and a phrase containing pieces it does not
     * have cannot be registered. Screens check this before offering to save, because the
     * failure is otherwise invisible — the phrase saves, looks set up, and never fires.
     */
    suspend fun canUseWakePhrase(phrase: String): Boolean

    /**
     * Records that one enrolment take was captured.
     *
     * Only the count. The voiceprint itself is built by `VoiceEnroller`, which owns the
     * microphone and the embedding model; this keeps the repository to persistence.
     */
    suspend fun addEnrolmentTake()

    /** Discards enrolment so the user can start the takes again. */
    suspend fun clearEnrolment()

    suspend fun setRequireVoiceMatch(enabled: Boolean)

    suspend fun setSensitivity(sensitivity: ListeningSensitivity)

    /**
     * Re-reads the permissions from the system.
     *
     * A nudge, not a setter: the repository owns a [PermissionProbe] and asks the
     * platform itself. Callers used to push a snapshot in, which meant two screens could
     * disagree and a fresh process assumed nothing was granted until some UI corrected
     * it — so the home screen urged the user to grant what she had granted in onboarding.
     *
     * Call it after returning from a permission dialog or system settings, where the
     * answer changes without anything observable happening in app state.
     */
    suspend fun refreshPermissions()

    /** Reports whether a keyword model is installed and loaded. */
    suspend fun setDetectorReady(ready: Boolean)

    /**
     * Starts listening.
     *
     * Only legal to call while an activity is visible — Android rejects a background
     * start of a microphone foreground service.
     */
    suspend fun arm()

    suspend fun disarm()

    /** Called when the wake word fires, so the status can move to Triggered. */
    suspend fun onWakeWordDetected()
}
