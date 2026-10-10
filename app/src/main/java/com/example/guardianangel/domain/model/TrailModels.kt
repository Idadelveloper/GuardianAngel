package com.example.guardianangel.domain.model

/**
 * Where a session happened, and what happened along the way.
 *
 * The breadcrumbs and the timeline are stored separately — points carry coordinates,
 * transcript lines and sounds carry text — and this is where they are brought back
 * together. That join is the whole feature: "a scream" is a detail, "a scream *here*, six
 * minutes in" is evidence.
 */
data class SessionTrail(
    val sessionId: String,
    val points: List<TrailPoint>,
    val incidents: List<TrailIncident>,
) {
    val hasPath: Boolean get() = points.size >= 2

    /** True when there is a position but no movement — a session recorded standing still. */
    val isStationary: Boolean get() = points.isNotEmpty() && !hasPath

    val start: TrailPoint? get() = points.firstOrNull()
    val end: TrailPoint? get() = points.lastOrNull()

    /** The worst score recorded anywhere on the path, for colouring the preview. */
    val lowestScore: Int? get() = points.mapNotNull { it.safetyScore }.minOrNull()
}

/** One breadcrumb. */
data class TrailPoint(
    val latitude: Double,
    val longitude: Double,
    val atEpochMillis: Long,
    /** The composite safety score when this point was recorded, if one was available. */
    val safetyScore: Int? = null,
    val placeLabel: String? = null,
)

/**
 * Something worth looking at, pinned to where it happened.
 *
 * Derived rather than stored. Storing them would mean deciding at record time what
 * counts as notable, and that decision changes as the heuristics improve — a session
 * recorded last month should benefit from today's understanding of it.
 */
data class TrailIncident(
    val id: String,
    val atEpochMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val kind: TrailIncidentKind,
    /** Short label for the marker, e.g. "Danger codeword". */
    val title: String,
    /** What actually happened, shown when the marker is opened. */
    val detail: String,
    val safetyScore: Int? = null,
) {
    val severity: TrailSeverity get() = kind.severity
}

/**
 * What kind of thing a marker represents.
 *
 * Ordered by how much it should draw the eye. When several land on the same spot the
 * most severe wins, so a scream is not hidden behind a flagged sentence.
 */
enum class TrailIncidentKind(val severity: TrailSeverity) {
    /** A codeword the user spoke, and Angel acted on. */
    Codeword(TrailSeverity.Critical),

    /** The acoustic tagger flagged a sound: a scream, breaking glass, shouting. */
    DangerSound(TrailSeverity.High),

    /** A line of speech the reasoning tier treated as a danger signal. */
    FlaggedSpeech(TrailSeverity.Elevated),

    /** A voice that was not the enrolled user's. */
    UnknownVoice(TrailSeverity.Elevated),

    /** The safety score fell sharply between two breadcrumbs. */
    ScoreDrop(TrailSeverity.Elevated),
}

enum class TrailSeverity { Elevated, High, Critical }
