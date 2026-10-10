package com.example.guardianangel.data.local

import android.util.Log
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.DiarizedEntry
import com.example.guardianangel.domain.model.SessionKind
import com.example.guardianangel.domain.model.SpeakerKind
import com.example.guardianangel.domain.repository.SessionRecorder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

private const val TAG = "SessionRecorder"

/**
 * Writes a live session into Room as it happens.
 *
 * Every method swallows its own failures. These are called from the audio pipeline, and
 * an exception thrown back into the capture loop would stop the recording — losing the
 * rest of an incident to save one line is the wrong trade. Failures are logged instead.
 *
 * A [Mutex] serialises writes rather than `synchronized`, because the callers are
 * coroutines and blocking the audio dispatcher would stall capture.
 */
class RoomSessionRecorder(
    private val dao: SessionDao,
    private val currentUser: CurrentUser,
) : SessionRecorder {

    private val lock = Mutex()

    @Volatile
    private var current: String? = null
    override val activeSessionId: String? get() = current

    private var startedAt = 0L
    private var peakDecibels: Int? = null
    private var lowestScore: Int? = null
    private var firedTier: CodewordTier? = null
    private var guardians: List<String> = emptyList()
    private var lineCount = 0
    private var flaggedCount = 0
    private var lastLocationLabel: String? = null

    override suspend fun begin(trigger: CodewordTier?, triggerLabel: String): String =
        lock.withLock {
            current?.let { return@withLock it }

            val userId = runCatching { currentUser.requireId() }.getOrNull()
                ?: return@withLock "".also { Log.w(TAG, "No user; session not recorded") }

            val id = "s-${UUID.randomUUID()}"
            startedAt = System.currentTimeMillis()
            peakDecibels = null
            lowestScore = null
            firedTier = trigger
            guardians = emptyList()
            lineCount = 0
            flaggedCount = 0
            lastLocationLabel = null

            runCatching {
                dao.upsert(
                    SessionEntity(
                        id = id,
                        userId = userId,
                        kind = (if (trigger != null) SessionKind.Incident else SessionKind.Conversation).name,
                        title = titleFor(triggerLabel),
                        startedAt = startedAt,
                        // Null marks it live. The Activity list renders an open session
                        // differently, and a crash leaves it honestly unfinished rather
                        // than claiming an end time that never happened.
                        endedAt = null,
                        locationLabel = "Locating…",
                        summary = "Recording in progress",
                        peakDecibels = null,
                        lowestSafetyScore = null,
                        triggeredByTier = trigger?.name,
                        updatedAt = startedAt,
                    )
                )
            }.onFailure { Log.w(TAG, "Could not open session", it) }

            current = id
            Log.i(TAG, "Session $id opened by $triggerLabel")
            id
        }

    override suspend fun addTranscript(entry: DiarizedEntry) {
        val id = current ?: return
        lock.withLock {
            lineCount++
            if (entry.isFlagged) flaggedCount++
            entry.decibels?.let { peakDecibels = max(peakDecibels ?: it, it) }
            runCatching {
                dao.addTranscriptEntry(
                    TranscriptEntryEntity(
                        sessionId = id,
                        atMillis = entry.atEpochMillis,
                        speakerKind = entry.speakerKind.name,
                        speakerLabel = entry.speakerLabel,
                        speakerQualifier = entry.speakerQualifier,
                        text = entry.text,
                        decibels = entry.decibels,
                        isFlagged = entry.isFlagged,
                        speakerTag = entry.id.takeIf { it.startsWith("spk") },
                    )
                )
            }.onFailure { Log.w(TAG, "Dropped a transcript line", it) }
        }
    }

    override suspend fun addAudioEvent(
        label: String,
        decibels: Int?,
        confidence: Float,
        atMillis: Long,
        isDanger: Boolean,
    ) {
        val id = current ?: return
        lock.withLock {
            decibels?.let { peakDecibels = max(peakDecibels ?: it, it) }
            runCatching {
                dao.addEvent(
                    AudioEventEntity(
                        sessionId = id,
                        atMillis = atMillis,
                        label = label,
                        confidence = confidence,
                        isDangerSignal = isDanger,
                    )
                )
            }.onFailure { Log.w(TAG, "Dropped an audio event", it) }
        }
    }

    override suspend fun addLocation(
        latitude: Double,
        longitude: Double,
        atMillis: Long,
        label: String?,
        safetyScore: Int?,
    ) {
        val id = current ?: return
        lock.withLock {
            label?.let { lastLocationLabel = it }
            runCatching {
                dao.addLocation(
                    LocationPointEntity(
                        sessionId = id,
                        atMillis = atMillis,
                        latitude = latitude,
                        longitude = longitude,
                        accuracyMeters = null,
                        safetyScore = safetyScore,
                        placeLabel = label,
                    )
                )
            }.onFailure { Log.w(TAG, "Dropped a breadcrumb", it) }
        }
    }

    override suspend fun noteSafetyScore(score: Int) {
        if (current == null) return
        lock.withLock { lowestScore = min(lowestScore ?: score, score) }
    }

    override suspend fun noteCodeword(
        tier: CodewordTier,
        phrase: String,
        wasUserVoice: Boolean,
    ) {
        val id = current ?: return
        lock.withLock {
            // Only ever escalates. A Safe codeword after a Danger one cancels the alert
            // but must not rewrite history — the session still contains an incident, and
            // that is what the evidence has to say.
            if (firedTier == null || tier.ordinal > firedTier!!.ordinal) firedTier = tier

            // Written as a transcript line too, so the timeline shows the trigger in
            // place rather than only in the session's metadata.
            runCatching {
                dao.addTranscriptEntry(
                    TranscriptEntryEntity(
                        sessionId = id,
                        atMillis = System.currentTimeMillis(),
                        speakerKind = SpeakerKind.SoundEvent.name,
                        speakerLabel = "Angel",
                        speakerQualifier = if (wasUserVoice) {
                            "Voiceprint verified"
                        } else {
                            "Voice not verified"
                        },
                        text = "“$phrase” recognised — ${tier.name} codeword acted on.",
                        isFlagged = tier.ordinal >= CodewordTier.Danger.ordinal,
                    )
                )
            }.onFailure { Log.w(TAG, "Could not log the codeword", it) }
        }
    }

    override suspend fun noteGuardiansNotified(names: List<String>) {
        if (current == null) return
        lock.withLock { guardians = (guardians + names).distinct() }
    }

    override suspend fun finish(summary: String?, kind: SessionKind?) {
        val id = lock.withLock { current.also { current = null } } ?: return
        runCatching {
            val existing = dao.find(id) ?: return
            val endedAt = System.currentTimeMillis()
            dao.upsert(
                existing.copy(
                    kind = (kind ?: inferKind()).name,
                    endedAt = endedAt,
                    locationLabel = lastLocationLabel ?: existing.locationLabel
                        .takeIf { it != "Locating…" } ?: "Location not recorded",
                    summary = summary ?: describe(endedAt - startedAt),
                    peakDecibels = peakDecibels,
                    lowestSafetyScore = lowestScore,
                    triggeredByTier = firedTier?.name,
                    guardiansNotified = guardians.joinToString("|"),
                    updatedAt = endedAt,
                )
            )
            Log.i(TAG, "Session $id closed: $lineCount lines, $flaggedCount flagged")
        }.onFailure { Log.w(TAG, "Could not close session $id", it) }
    }

    /** Incident when a codeword fired or the score fell badly; otherwise a conversation. */
    private fun inferKind(): SessionKind = when {
        firedTier != null && firedTier!!.ordinal >= CodewordTier.Danger.ordinal ->
            SessionKind.Incident
        (lowestScore ?: 100) < INCIDENT_SCORE -> SessionKind.Incident
        else -> SessionKind.Conversation
    }

    private fun titleFor(triggerLabel: String): String = when {
        triggerLabel.equals("manual", ignoreCase = true) -> "Recording you started"
        triggerLabel.isBlank() -> "Recording"
        else -> "Woken by “$triggerLabel”"
    }

    /**
     * A plain description of what the session contains.
     *
     * Deliberately factual rather than interpretive. This string is read back after an
     * incident, possibly by someone other than the user, so it reports what was captured
     * and leaves the conclusions to whoever is reading.
     */
    private fun describe(durationMillis: Long): String {
        val minutes = (durationMillis / 60_000).toInt()
        val length = when {
            minutes < 1 -> "under a minute"
            minutes == 1 -> "1 minute"
            else -> "$minutes minutes"
        }
        val speech = when (lineCount) {
            0 -> "no speech was transcribed"
            1 -> "1 line of speech"
            else -> "$lineCount lines of speech"
        }
        val flagged = if (flaggedCount > 0) ", $flaggedCount flagged" else ""
        val trigger = firedTier?.let { " ${it.name} codeword acted on." } ?: ""
        return "Recorded for $length — $speech$flagged.$trigger"
    }

    private companion object {
        /** Below this, a session is treated as an incident even with no codeword. */
        const val INCIDENT_SCORE = 50
    }
}
