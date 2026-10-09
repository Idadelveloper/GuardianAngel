package com.example.guardianangel.domain.model

/**
 * Recorded sessions, their transcripts and the analytics derived from them.
 *
 * Shapes a future `sessions`, `transcript_lines` and `session_locations` schema directly:
 * [MonitoredSession] is one row, [DiarizedEntry] is a child row keyed by session id, and
 * [MovementAnalytics] is a derived rollup that a query or a materialised view would
 * produce rather than something stored per-session.
 */

/** How a recorded session is classified once it has ended. */
enum class SessionKind {
    /** Ordinary monitored conversation, no threat markers. */
    Conversation,

    /** A journey that completed safely. */
    SafeTransit,

    /** A duress trigger fired during this session. */
    Incident,
}

/** Who or what produced a line of transcript. */
enum class SpeakerKind {
    /** Matched against the enrolled voiceprint. */
    You,

    /** A recurring cadence the model has seen before, but cannot identify. */
    KnownPattern,

    /** An unrecognised far-field voice. */
    Unknown,

    /** Non-speech audio the acoustic model flagged. */
    SoundEvent,

    /** Something Angel itself did — an alert dispatch, a score change. */
    SystemAction,
}

/** One entry in the diarized event stream. */
data class DiarizedEntry(
    val id: String,
    val sessionId: String,
    val atEpochMillis: Long,
    val speakerKind: SpeakerKind,
    /** Display name for the speaker, e.g. "You", "Unknown voice", "Person A". */
    val speakerLabel: String,
    /** Qualifier shown as a chip, e.g. "Voiceprint verified", "Far-field". */
    val speakerQualifier: String? = null,
    val text: String,
    /** Set for [SpeakerKind.SoundEvent]. */
    val decibels: Int? = null,
    /** True when the model treated this entry as a danger signal. */
    val isFlagged: Boolean = false,
)

/** A completed (or in-progress) monitored session. */
data class MonitoredSession(
    val id: String,
    val title: String,
    val kind: SessionKind,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val locationLabel: String,
    /** One-paragraph summary the acoustic model produced. */
    val summary: String,
    val peakDecibels: Int?,
    /** Duress score at its worst during the session, 0–100. */
    val lowestSafetyScore: Int?,
    val isEncrypted: Boolean = true,
    val entries: List<DiarizedEntry> = emptyList(),
    val guardiansNotified: List<String> = emptyList(),
) {
    val durationMinutes: Long?
        get() = endedAtEpochMillis?.let { (it - startedAtEpochMillis) / 60_000 }
}

/** Filter applied to the activity list. */
enum class ActivityFilter { All, Incidents, Conversations }

/** Window the analytics rollup covers. */
enum class AnalyticsRange { Week, Month, AllTime }

/** A labelled proportion, used for the illumination and acoustic breakdowns. */
data class Breakdown(
    val label: String,
    /** 0–100. */
    val percent: Int,
    /** True when this slice is worth the user's attention. */
    val isFlagged: Boolean = false,
)

/** A place the user spends protected time. */
data class Sanctuary(
    val name: String,
    val hoursLogged: Int,
)

/** The movement-intelligence rollup. */
data class MovementAnalytics(
    val range: AnalyticsRange,
    /** 0–100 overall safety rating for the window. */
    val overallRating: Int,
    val guardedWalks: Int,
    val safeArrivalsPercent: Int,
    val duressTriggers: Int,
    /** Illumination safety by time of day. */
    val diurnalBreakdown: List<Breakdown>,
    /** Ambient-noise distribution. */
    val acousticBreakdown: List<Breakdown>,
    /** Share of speech by speaker class. */
    val voiceBreakdown: List<Breakdown>,
    val sanctuaries: List<Sanctuary>,
    /** One actionable sentence, or null when there is nothing worth flagging. */
    val insight: String?,
)
