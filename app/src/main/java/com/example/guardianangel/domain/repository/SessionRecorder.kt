package com.example.guardianangel.domain.repository

import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.DiarizedEntry
import com.example.guardianangel.domain.model.SessionKind

/**
 * Captures one live recording into storage as it happens.
 *
 * Separate from [ActivityRepository], which *reads* finished sessions. The split matters
 * because the two have opposite priorities: reading can be lazy and batched, while
 * writing is collecting evidence and must survive the app being killed mid-incident.
 *
 * ## Why it writes incrementally
 *
 * Buffering a session in memory and saving it on `finish()` is simpler and wrong for this
 * app. The moments worth recording are exactly the moments something might end the
 * process — a phone knocked out of a hand, a battery pulled, an app force-stopped. Each
 * line is therefore written as it is decoded, so whatever was captured before the end
 * survives even when there is no clean end.
 *
 * Implementations must be safe to call from the audio thread and must never throw into
 * it: losing a transcript line is survivable, crashing the listener mid-walk is not.
 */
interface SessionRecorder {

    /** The session being written, or null when nothing is recording. */
    val activeSessionId: String?

    /**
     * Opens a session and returns its id.
     *
     * @param trigger the codeword tier that opened it, or null for a wake word or a tap.
     * @param triggerLabel what actually started it, for the session title — a wake
     *   phrase, "manual", or a codeword.
     */
    suspend fun begin(trigger: CodewordTier?, triggerLabel: String): String

    /** Appends one decoded line. Ignored when no session is open. */
    suspend fun addTranscript(entry: DiarizedEntry)

    /**
     * Appends a tagged non-speech sound.
     *
     * @param decibels the level when it happened. Not stored per event — it is folded
     *   into the session's peak, because a per-event level implies a calibrated
     *   measurement this app cannot make.
     * @param confidence the classifier's certainty, 0f..1f.
     */
    suspend fun addAudioEvent(
        label: String,
        decibels: Int?,
        confidence: Float,
        atMillis: Long,
        isDanger: Boolean,
    )

    /** Appends a breadcrumb. Location is stored per point, correlated to lines by time. */
    suspend fun addLocation(
        latitude: Double,
        longitude: Double,
        atMillis: Long,
        label: String?,
        safetyScore: Int?,
    )

    /** Records the worst safety score seen, so the session can be ranked later. */
    suspend fun noteSafetyScore(score: Int)

    /** Records that a codeword fired, which is what makes a session an incident. */
    suspend fun noteCodeword(tier: CodewordTier, phrase: String, wasUserVoice: Boolean)

    /** Records which guardians were alerted. */
    suspend fun noteGuardiansNotified(names: List<String>)

    /**
     * Closes the session.
     *
     * @param kind defaults to [SessionKind.Incident] when a codeword fired during the
     *   session, so the Activity list and the analytics agree without the caller having
     *   to remember.
     */
    suspend fun finish(summary: String? = null, kind: SessionKind? = null)
}
