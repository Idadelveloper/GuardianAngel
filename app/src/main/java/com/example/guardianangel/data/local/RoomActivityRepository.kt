package com.example.guardianangel.data.local

import com.example.guardianangel.domain.model.ActivityFilter
import com.example.guardianangel.domain.model.AnalyticsRange
import com.example.guardianangel.domain.model.Breakdown
import com.example.guardianangel.domain.model.DiarizedEntry
import com.example.guardianangel.domain.model.MonitoredSession
import com.example.guardianangel.domain.model.MovementAnalytics
import com.example.guardianangel.domain.model.Sanctuary
import com.example.guardianangel.domain.model.SessionTrail
import com.example.guardianangel.domain.model.TrailPoint
import com.example.guardianangel.domain.model.SessionKind
import com.example.guardianangel.domain.model.SpeakerKind
import com.example.guardianangel.domain.repository.ActivityRepository
import com.example.guardianangel.domain.trail.TrailBuilder
import com.example.guardianangel.domain.repository.LocationBreadcrumb
import com.example.guardianangel.domain.repository.TranscriptExporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Recorded sessions, their transcripts, and the analytics derived from them.
 *
 * Replaces a fake that served invented content. The difference is visible to the user in
 * a way that matters: an account that has recorded nothing now shows nothing, instead of
 * three fabricated incidents. A safety app that displays imaginary evidence teaches the
 * user that its records cannot be trusted.
 *
 * Nothing here is stored pre-computed. Analytics are derived from the rows on read, so a
 * deleted session immediately stops counting toward the insights — which is the whole
 * point of being able to delete one.
 */
class RoomActivityRepository(
    private val dao: SessionDao,
    private val currentUser: CurrentUser,
    private val exporter: TranscriptExporter,
) : ActivityRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSessions(filter: ActivityFilter): Flow<List<MonitoredSession>> =
        currentUser.observeId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                dao.observeAll(userId).map { rows ->
                    rows.map { it.toModel() }.filter {
                        when (filter) {
                            ActivityFilter.All -> true
                            ActivityFilter.Incidents -> it.kind == SessionKind.Incident
                            ActivityFilter.Conversations -> it.kind == SessionKind.Conversation
                        }
                    }
                }
            }
        }

    /**
     * One session with its full timeline.
     *
     * Transcript lines and tagged sounds are merged and sorted by time rather than shown
     * in separate lists: "glass breaking" between two sentences is evidence of what was
     * happening, and splitting them apart loses the sequence that gives it meaning.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSession(sessionId: String): Flow<MonitoredSession?> =
        combine(
            dao.observe(sessionId),
            dao.observeTranscript(sessionId),
            dao.observeEvents(sessionId),
        ) { session, transcript, events ->
            session?.toModel(
                entries = (transcript.map { it.toEntry() } + events.map { it.toEntry() })
                    .sortedBy { it.atEpochMillis }
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAnalytics(range: AnalyticsRange): Flow<MovementAnalytics> =
        currentUser.observeId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(empty(range))
            } else {
                // Recomputed whenever a session changes, so the rollup cannot drift from
                // the list it summarises.
                dao.observeAll(userId).map { rollUp(range, it.filter { s -> s.inRange(range) }) }
            }
        }

    /**
     * The path and the pins for one session.
     *
     * Combined rather than joined in SQL because the pins are *derived* from the
     * timeline, not stored. Deriving on read means a session recorded last month
     * benefits from today's understanding of what counts as notable.
     */
    override fun observeTrail(sessionId: String): Flow<SessionTrail> =
        combine(
            dao.observeTrail(sessionId),
            dao.observeTranscript(sessionId),
            dao.observeEvents(sessionId),
        ) { points, transcript, events ->
            TrailBuilder.build(
                sessionId = sessionId,
                points = points.map { it.toTrailPoint() },
                entries = (transcript.map { it.toEntry() } + events.map { it.toEntry() })
                    .sortedBy { it.atEpochMillis },
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeTrailPaths(): Flow<Map<String, List<TrailPoint>>> =
        currentUser.observeId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyMap())
            } else {
                dao.observeAllTrails(userId).map { rows ->
                    rows.groupBy { it.sessionId }
                        .mapValues { (_, points) -> points.map { it.toTrailPoint() } }
                }
            }
        }

    override suspend fun exportSession(sessionId: String): String {
        val session = dao.find(sessionId) ?: error("That session no longer exists.")
        return exporter.export(
            session = session.toModel(),
            entries = (dao.findTranscript(sessionId).map { it.toEntry() } +
                dao.findEvents(sessionId).map { it.toEntry() })
                .sortedBy { it.atEpochMillis },
            trail = dao.findTrail(sessionId).map { it.toBreadcrumb() },
        )
    }

    override suspend fun deleteSession(sessionId: String) = dao.delete(sessionId)

    /**
     * Builds the rollup from real rows.
     *
     * Every figure is either measured or omitted. The breakdowns return an empty list
     * when there is nothing to break down, so the UI can hide a chart rather than draw a
     * convincing-looking flat one.
     */
    private suspend fun rollUp(
        range: AnalyticsRange,
        sessions: List<SessionEntity>,
    ): MovementAnalytics {
        if (sessions.isEmpty()) return empty(range)

        val userId = currentUser.requireId()
        val since = range.since()
        val events = runCatching { dao.findEventsSince(userId, since) }.getOrDefault(emptyList())
        val lines = runCatching { dao.findTranscriptSince(userId, since) }
            .getOrDefault(emptyList())

        val incidents = sessions.count { it.kind == SessionKind.Incident.name }
        val completed = sessions.count { it.endedAt != null }
        val scores = sessions.mapNotNull { it.lowestSafetyScore }

        return MovementAnalytics(
            range = range,
            // The average of the worst points, not of the averages: a window containing
            // one bad night should not be smoothed into looking uneventful.
            overallRating = if (scores.isEmpty()) NO_SCORE else scores.average().toInt(),
            guardedWalks = sessions.size,
            safeArrivalsPercent = if (sessions.isEmpty()) {
                0
            } else {
                ((sessions.size - incidents) * 100) / sessions.size
            },
            duressTriggers = sessions.count { it.triggeredByTier != null },
            diurnalBreakdown = diurnal(sessions),
            acousticBreakdown = acoustic(events),
            voiceBreakdown = voices(lines),
            sanctuaries = sanctuaries(sessions),
            insight = insight(sessions, incidents, completed),
        )
    }

    /** Where recordings happened, by time of day. */
    private fun diurnal(sessions: List<SessionEntity>): List<Breakdown> {
        if (sessions.isEmpty()) return emptyList()
        val buckets = linkedMapOf("Morning" to 0, "Afternoon" to 0, "Evening" to 0, "Night" to 0)
        sessions.forEach { session ->
            val hour = Calendar.getInstance()
                .apply { timeInMillis = session.startedAt }
                .get(Calendar.HOUR_OF_DAY)
            val key = when (hour) {
                in 5..11 -> "Morning"
                in 12..16 -> "Afternoon"
                in 17..20 -> "Evening"
                else -> "Night"
            }
            buckets[key] = (buckets[key] ?: 0) + 1
        }
        return buckets.toPercentages()
    }

    private fun acoustic(events: List<AudioEventEntity>): List<Breakdown> {
        if (events.isEmpty()) return emptyList()
        return events.groupingBy { it.label }.eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(4)
            .toMap(LinkedHashMap())
            .toPercentages()
    }

    private fun voices(lines: List<TranscriptEntryEntity>): List<Breakdown> {
        val speech = lines.filter { it.speakerKind != SpeakerKind.SoundEvent.name }
        if (speech.isEmpty()) return emptyList()
        val buckets = linkedMapOf<String, Int>()
        speech.forEach { line ->
            val key = when (line.speakerKind) {
                SpeakerKind.You.name -> "You"
                SpeakerKind.Unknown.name -> "Unrecognised"
                else -> "Other"
            }
            buckets[key] = (buckets[key] ?: 0) + 1
        }
        return buckets.toPercentages()
    }

    /** Places that recur across sessions, by time spent there. */
    private fun sanctuaries(sessions: List<SessionEntity>): List<Sanctuary> =
        sessions
            .filter { it.locationLabel.isNotBlank() && it.endedAt != null }
            .groupBy { it.locationLabel }
            .map { (label, group) ->
                Sanctuary(
                    name = label,
                    hoursLogged = group.sumOf { (it.endedAt!! - it.startedAt) }
                        .let { TimeUnit.MILLISECONDS.toHours(it).toInt() },
                )
            }
            .filter { it.name != "Locating…" && it.name != "Location not recorded" }
            .sortedByDescending { it.hoursLogged }
            .take(3)

    /**
     * One sentence, or null.
     *
     * Null is the common case and the right one. An insight line that always says
     * something ends up saying nothing, and on this screen it competes with the record
     * of an actual incident.
     */
    private fun insight(
        sessions: List<SessionEntity>,
        incidents: Int,
        completed: Int,
    ): String? {
        val unfinished = sessions.size - completed
        return when {
            incidents > 0 -> {
                val nightIncidents = sessions.count {
                    it.kind == SessionKind.Incident.name && it.startedAt.hour() !in 6..19
                }
                if (nightIncidents > 0) {
                    "$incidents of your recordings became incidents, $nightIncidents after dark."
                } else {
                    "$incidents of your recordings became incidents."
                }
            }

            unfinished > 0 ->
                "$unfinished recording${if (unfinished == 1) "" else "s"} ended without " +
                    "being stopped — worth checking the app was not closed mid-walk."

            sessions.size >= FREQUENT_SESSIONS ->
                "${sessions.size} recordings in this window, none of which escalated."

            else -> null
        }
    }

    private fun empty(range: AnalyticsRange) = MovementAnalytics(
        range = range,
        overallRating = NO_SCORE,
        guardedWalks = 0,
        safeArrivalsPercent = 0,
        duressTriggers = 0,
        diurnalBreakdown = emptyList(),
        acousticBreakdown = emptyList(),
        voiceBreakdown = emptyList(),
        sanctuaries = emptyList(),
        insight = null,
    )

    private fun SessionEntity.inRange(range: AnalyticsRange) = startedAt >= range.since()

    private fun AnalyticsRange.since(): Long = when (this) {
        AnalyticsRange.Week -> System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        AnalyticsRange.Month -> System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        AnalyticsRange.AllTime -> 0
    }

    private fun Long.hour(): Int =
        Calendar.getInstance().apply { timeInMillis = this@hour }.get(Calendar.HOUR_OF_DAY)

    private fun Map<String, Int>.toPercentages(): List<Breakdown> {
        val total = values.sum().takeIf { it > 0 } ?: return emptyList()
        return filterValues { it > 0 }
            .map { (label, count) -> Breakdown(label, (count * 100) / total) }
    }

    private companion object {
        /**
         * Shown when no session has a score yet.
         *
         * 0 would read as "maximally unsafe" and 100 as "all clear"; both are claims the
         * data does not support. The UI treats this as "no rating yet".
         */
        const val NO_SCORE = -1
        const val FREQUENT_SESSIONS = 5
    }
}

private fun SessionEntity.toModel(entries: List<DiarizedEntry> = emptyList()) = MonitoredSession(
    id = id,
    title = title,
    kind = runCatching { SessionKind.valueOf(kind) }.getOrDefault(SessionKind.Conversation),
    startedAtEpochMillis = startedAt,
    endedAtEpochMillis = endedAt,
    locationLabel = locationLabel,
    summary = summary,
    peakDecibels = peakDecibels,
    lowestSafetyScore = lowestSafetyScore,
    isEncrypted = isEncrypted,
    entries = entries,
    guardiansNotified = guardiansNotified.split("|").filter { it.isNotBlank() },
)

private fun TranscriptEntryEntity.toEntry() = DiarizedEntry(
    id = speakerTag ?: "t$id",
    sessionId = sessionId,
    atEpochMillis = atMillis,
    speakerKind = runCatching { SpeakerKind.valueOf(speakerKind) }
        .getOrDefault(SpeakerKind.Unknown),
    speakerLabel = speakerLabel,
    speakerQualifier = speakerQualifier,
    text = text,
    decibels = decibels,
    isFlagged = isFlagged,
)

private fun AudioEventEntity.toEntry() = DiarizedEntry(
    id = "e$id",
    sessionId = sessionId,
    atEpochMillis = atMillis,
    speakerKind = SpeakerKind.SoundEvent,
    speakerLabel = label,
    // The tagger's confidence, not a sound level: `audio_events` stores how sure the
    // classifier was, while the loudest moment is kept once on the session.
    speakerQualifier = "${(confidence * 100).toInt()}% confident",
    text = label,
    decibels = null,
    isFlagged = isDangerSignal,
)

private fun LocationPointEntity.toBreadcrumb() = LocationBreadcrumb(
    atMillis = atMillis,
    latitude = latitude,
    longitude = longitude,
    placeLabel = placeLabel,
    safetyScore = safetyScore,
)

private fun LocationPointEntity.toTrailPoint() = TrailPoint(
    latitude = latitude,
    longitude = longitude,
    atEpochMillis = atMillis,
    safetyScore = safetyScore,
    placeLabel = placeLabel,
)
