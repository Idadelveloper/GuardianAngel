package com.example.guardianangel.data

import com.example.guardianangel.domain.model.AnalyticsRange
import com.example.guardianangel.domain.model.Breakdown
import com.example.guardianangel.domain.model.DiarizedEntry
import com.example.guardianangel.domain.model.MonitoredSession
import com.example.guardianangel.domain.model.MovementAnalytics
import com.example.guardianangel.domain.model.Sanctuary
import com.example.guardianangel.domain.model.SessionKind
import com.example.guardianangel.domain.model.SpeakerKind

/**
 * Sample activity content.
 *
 * Invented throughout — no real people, places or recordings. Deliberately includes one
 * incident with a full diarized stream so the transcript screen can be built and judged
 * against realistic density before any audio pipeline exists.
 */
object ActivitySamples {

    private const val MINUTE = 60_000L
    private val now = System.currentTimeMillis()

    val incidentId = "session-incident"

    private fun entry(
        index: Int,
        offsetSeconds: Long,
        kind: SpeakerKind,
        label: String,
        text: String,
        qualifier: String? = null,
        decibels: Int? = null,
        flagged: Boolean = false,
    ) = DiarizedEntry(
        id = "entry-$index",
        sessionId = incidentId,
        atEpochMillis = now - 20 * MINUTE + offsetSeconds * 1000,
        speakerKind = kind,
        speakerLabel = label,
        speakerQualifier = qualifier,
        text = text,
        decibels = decibels,
        isFlagged = flagged,
    )

    private val incidentEntries = listOf(
        entry(1, 0, SpeakerKind.You, "You", "Excuse me, I'm just trying to get through to the cab.", "Voiceprint verified"),
        entry(2, 12, SpeakerKind.Unknown, "Unknown voice", "Hey, what are you doing around here?", "Far-field"),
        entry(3, 19, SpeakerKind.KnownPattern, "Person A", "Leave her alone, come over here.", "Repeated pattern"),
        entry(4, 33, SpeakerKind.SoundEvent, "Sound detected", "Aggressive shout and glass clatter in the background.", "Acoustic AI", decibels = 84, flagged = true),
        entry(5, 50, SpeakerKind.You, "You", "I'm heading to 16th Street now.", "Whispered codeword tone", flagged = true),
        entry(6, 63, SpeakerKind.SystemAction, "Angel", "GPS ping sent to Sarah Miller. Safety score dropped to 48%.", null),
        entry(7, 108, SpeakerKind.SoundEvent, "Sound detected", "Car door closing. Ambient noise settled to 42 dB.", "Acoustic AI", decibels = 42),
        entry(8, 178, SpeakerKind.You, "You", "Everything is fine now, getting in the car.", "Disarm tone logged"),
    )

    val sessions = listOf(
        MonitoredSession(
            id = incidentId,
            title = "Elevated ambience · Club district",
            kind = SessionKind.Incident,
            startedAtEpochMillis = now - 20 * MINUTE,
            endedAtEpochMillis = now - 3 * MINUTE,
            locationLabel = "Mission St & 16th",
            summary = "Crowd tension and raised voices while you were leaving the venue. " +
                "You used your danger codeword; Sarah was alerted and the alert stood " +
                "down once you were safely in a car.",
            peakDecibels = 84,
            lowestSafetyScore = 48,
            entries = incidentEntries,
            guardiansNotified = listOf("c1"),
        ),
        MonitoredSession(
            id = "session-study",
            title = "Midterm study session",
            kind = SessionKind.Conversation,
            startedAtEpochMillis = now - 26 * 60 * MINUTE,
            endedAtEpochMillis = now - 25 * 60 * MINUTE,
            locationLabel = "Campus library",
            summary = "Lively study discussion between you and two peers. Background noise " +
                "moderate. No threat markers detected.",
            peakDecibels = 58,
            lowestSafetyScore = 96,
        ),
        MonitoredSession(
            id = "session-market",
            title = "Evening grocery walk",
            kind = SessionKind.SafeTransit,
            startedAtEpochMillis = now - 30 * 60 * MINUTE,
            endedAtEpochMillis = now - 29 * 60 * MINUTE,
            locationLabel = "Shattuck Ave",
            summary = "Street lighting held at 100% for the whole route. Handed back to " +
                "your safe haven on arrival.",
            peakDecibels = 51,
            lowestSafetyScore = 92,
        ),
    )

    fun analytics(range: AnalyticsRange) = MovementAnalytics(
        range = range,
        overallRating = when (range) {
            AnalyticsRange.Week -> 94
            AnalyticsRange.Month -> 91
            AnalyticsRange.AllTime -> 93
        },
        guardedWalks = when (range) {
            AnalyticsRange.Week -> 18
            AnalyticsRange.Month -> 74
            AnalyticsRange.AllTime -> 312
        },
        safeArrivalsPercent = 100,
        duressTriggers = if (range == AnalyticsRange.Week) 0 else 1,
        diurnalBreakdown = listOf(
            Breakdown("Morning", 98),
            Breakdown("Afternoon", 96),
            Breakdown("Evening", 91),
            Breakdown("Late night", 78, isFlagged = true),
        ),
        acousticBreakdown = listOf(
            Breakdown("Quiet", 72),
            Breakdown("Moderate", 21),
            Breakdown("Crowded", 7),
        ),
        voiceBreakdown = listOf(
            Breakdown("You", 68),
            Breakdown("Your guardians", 18),
            Breakdown("Unknown voices", 14),
        ),
        sanctuaries = listOf(
            Sanctuary("Home", 64),
            Sanctuary("Campus library", 22),
            Sanctuary("Studio office", 18),
        ),
        insight = "Late-night routes score lowest. Angel can reroute you through " +
            "better-lit streets after 10pm.",
    )
}
