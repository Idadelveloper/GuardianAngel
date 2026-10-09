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

    /** Sets the phrase. Changing it clears enrolment — the old template no longer applies. */
    suspend fun setWakeWordPhrase(phrase: String)

    /** Records one enrolment take. [WakeWord.MIN_TAKES] are needed before it will fire. */
    suspend fun addEnrolmentTake()

    /** Discards enrolment so the user can start the takes again. */
    suspend fun clearEnrolment()

    suspend fun setRequireVoiceMatch(enabled: Boolean)

    suspend fun setSensitivity(sensitivity: ListeningSensitivity)

    /**
     * Tells the repository what the system currently grants.
     *
     * Permission state is owned by the platform, not by us, so it is pushed in from the
     * UI layer after a permission check rather than guessed at here.
     */
    suspend fun updatePermissions(
        microphone: Boolean,
        notifications: Boolean,
        batteryExempt: Boolean,
    )

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
