package com.example.guardianangel.domain.trail

import com.example.guardianangel.domain.model.DiarizedEntry
import com.example.guardianangel.domain.model.SessionTrail
import com.example.guardianangel.domain.model.SpeakerKind
import com.example.guardianangel.domain.model.TrailIncident
import com.example.guardianangel.domain.model.TrailIncidentKind
import com.example.guardianangel.domain.model.TrailPoint
import kotlin.math.abs

/**
 * Joins a session's breadcrumbs to its timeline.
 *
 * Pure, so the rules below can be checked without a database or a map. They are
 * judgement calls about what is worth a pin, and getting them wrong in either direction
 * is bad: a map covered in markers is read as noise and scrolled past, while a map with
 * none implies a walk where nothing happened.
 *
 * ## How an event gets a position
 *
 * Breadcrumbs arrive every few seconds; speech and sounds arrive whenever they happen.
 * An event is placed at the breadcrumb nearest in time, and dropped if the nearest one is
 * further away than [MAX_MATCH_MILLIS] — a marker an unknown distance from where the
 * thing actually happened is worse than no marker, because it will be read as precise.
 */
object TrailBuilder {

    fun build(
        sessionId: String,
        points: List<TrailPoint>,
        entries: List<DiarizedEntry>,
    ): SessionTrail {
        val ordered = points.sortedBy { it.atEpochMillis }
        return SessionTrail(
            sessionId = sessionId,
            points = ordered,
            incidents = if (ordered.isEmpty()) {
                emptyList()
            } else {
                deriveIncidents(ordered, entries) + scoreDrops(ordered)
            }.dedupeByProximity(),
        )
    }

    private fun deriveIncidents(
        points: List<TrailPoint>,
        entries: List<DiarizedEntry>,
    ): List<TrailIncident> = entries.mapNotNull { entry ->
        val kind = entry.classify() ?: return@mapNotNull null
        val point = points.nearestTo(entry.atEpochMillis) ?: return@mapNotNull null
        if (abs(point.atEpochMillis - entry.atEpochMillis) > MAX_MATCH_MILLIS) {
            return@mapNotNull null
        }

        TrailIncident(
            id = "i-${entry.id}-${entry.atEpochMillis}",
            atEpochMillis = entry.atEpochMillis,
            latitude = point.latitude,
            longitude = point.longitude,
            kind = kind,
            title = kind.label(),
            detail = entry.detail(kind),
            safetyScore = point.safetyScore,
        )
    }

    /**
     * Pins the places where the score fell sharply.
     *
     * A gradual decline over a walk is the score working normally and is not worth a pin.
     * A [SCORE_DROP] fall between two consecutive breadcrumbs means something changed
     * where she was standing, which is exactly what a map is good at showing.
     */
    private fun scoreDrops(points: List<TrailPoint>): List<TrailIncident> =
        points.zipWithNext().mapNotNull { (previous, current) ->
            val before = previous.safetyScore ?: return@mapNotNull null
            val after = current.safetyScore ?: return@mapNotNull null
            val fall = before - after
            if (fall < SCORE_DROP) return@mapNotNull null

            TrailIncident(
                id = "i-score-${current.atEpochMillis}",
                atEpochMillis = current.atEpochMillis,
                latitude = current.latitude,
                longitude = current.longitude,
                kind = TrailIncidentKind.ScoreDrop,
                title = "Safety score fell",
                detail = "Dropped $fall points to $after here" +
                    (current.placeLabel?.let { ", near $it" } ?: "") + ".",
                safetyScore = after,
            )
        }

    private fun DiarizedEntry.classify(): TrailIncidentKind? = when {
        // Angel's own note that a codeword was recognised and acted on.
        speakerLabel == "Angel" && text.contains("codeword") -> TrailIncidentKind.Codeword
        speakerKind == SpeakerKind.SoundEvent && isFlagged -> TrailIncidentKind.DangerSound
        speakerKind == SpeakerKind.Unknown && isFlagged -> TrailIncidentKind.UnknownVoice
        isFlagged -> TrailIncidentKind.FlaggedSpeech
        else -> null
    }

    private fun TrailIncidentKind.label(): String = when (this) {
        TrailIncidentKind.Codeword -> "Codeword acted on"
        TrailIncidentKind.DangerSound -> "Sound flagged"
        TrailIncidentKind.FlaggedSpeech -> "Speech flagged"
        TrailIncidentKind.UnknownVoice -> "Unfamiliar voice"
        TrailIncidentKind.ScoreDrop -> "Safety score fell"
    }

    /**
     * What the popup says.
     *
     * Quotes what was captured rather than paraphrasing it. This is the part a person
     * might rely on later, and a summary written by the app is a layer of interpretation
     * between them and the recording.
     */
    private fun DiarizedEntry.detail(kind: TrailIncidentKind): String = when (kind) {
        TrailIncidentKind.DangerSound ->
            "Angel heard something she classified as “$speakerLabel”." +
                (speakerQualifier?.let { " ($it)" } ?: "")

        TrailIncidentKind.Codeword -> text

        else -> buildString {
            append("“")
            append(text.trim().take(MAX_QUOTE))
            if (text.trim().length > MAX_QUOTE) append("…")
            append("”")
            append(" — $speakerLabel")
            speakerQualifier?.let { append(", $it") }
        }
    }

    private fun List<TrailPoint>.nearestTo(millis: Long): TrailPoint? =
        minByOrNull { abs(it.atEpochMillis - millis) }

    /**
     * Collapses markers that would land on top of each other.
     *
     * Several things usually happen at once — a raised voice, a flagged line and a score
     * drop are often one moment — and three overlapping pins make all three unreadable.
     * The most severe survives, so a codeword is never hidden behind a flagged sentence.
     */
    private fun List<TrailIncident>.dedupeByProximity(): List<TrailIncident> =
        sortedWith(compareByDescending<TrailIncident> { it.severity.ordinal }
            .thenBy { it.atEpochMillis })
            .fold(mutableListOf<TrailIncident>()) { kept, candidate ->
                val clashes = kept.any {
                    abs(it.atEpochMillis - candidate.atEpochMillis) < CLUSTER_MILLIS
                }
                if (!clashes) kept += candidate
                kept
            }
            .sortedBy { it.atEpochMillis }

    /** Beyond this, a breadcrumb is too far from the event in time to locate it. */
    private const val MAX_MATCH_MILLIS = 45_000L

    /** Events closer together than this are treated as one moment. */
    private const val CLUSTER_MILLIS = 20_000L

    /** A fall this large between consecutive breadcrumbs is worth a pin. */
    private const val SCORE_DROP = 15

    private const val MAX_QUOTE = 110
}
