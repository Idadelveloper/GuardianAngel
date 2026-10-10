package com.example.guardianangel.data

import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.DiarizedEntry
import com.example.guardianangel.domain.model.SessionKind
import com.example.guardianangel.domain.repository.SessionRecorder

/**
 * A recorder that writes nothing, for previews.
 *
 * Deliberately inert rather than collecting into memory: a preview has no session and
 * nothing should appear to have been recorded. Returning a plausible session id would be
 * the kind of fake that makes a screen look like it works.
 */
class NoOpSessionRecorder : SessionRecorder {
    override val activeSessionId: String? = null
    override suspend fun begin(trigger: CodewordTier?, triggerLabel: String) = ""
    override suspend fun addTranscript(entry: DiarizedEntry) = Unit
    override suspend fun addAudioEvent(
        label: String,
        decibels: Int?,
        confidence: Float,
        atMillis: Long,
        isDanger: Boolean,
    ) = Unit
    override suspend fun addLocation(
        latitude: Double,
        longitude: Double,
        atMillis: Long,
        label: String?,
        safetyScore: Int?,
    ) = Unit
    override suspend fun noteSafetyScore(score: Int) = Unit
    override suspend fun noteCodeword(tier: CodewordTier, phrase: String, wasUserVoice: Boolean) = Unit
    override suspend fun noteGuardiansNotified(names: List<String>) = Unit
    override suspend fun finish(summary: String?, kind: SessionKind?) = Unit
}
