package com.example.guardianangel.domain.trail

import com.example.guardianangel.domain.model.DiarizedEntry
import com.example.guardianangel.domain.model.SpeakerKind
import com.example.guardianangel.domain.model.TrailIncidentKind
import com.example.guardianangel.domain.model.TrailPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which moments earn a pin on the map, and where they land.
 *
 * These are judgement calls, and both failure directions are bad: a map covered in
 * markers reads as noise and gets scrolled past, while a map with none implies a walk
 * where nothing happened.
 */
class TrailBuilderTest {

    private val base = 1_700_000_000_000L

    private fun point(offsetSeconds: Long, score: Int? = null) = TrailPoint(
        latitude = 37.77 + offsetSeconds / 10_000.0,
        longitude = -122.41,
        atEpochMillis = base + offsetSeconds * 1000,
        safetyScore = score,
        placeLabel = "Market St",
    )

    private fun entry(
        offsetSeconds: Long,
        text: String,
        flagged: Boolean = true,
        kind: SpeakerKind = SpeakerKind.You,
        label: String = "You",
    ) = DiarizedEntry(
        id = "e$offsetSeconds",
        sessionId = "s1",
        atEpochMillis = base + offsetSeconds * 1000,
        speakerKind = kind,
        speakerLabel = label,
        text = text,
        isFlagged = flagged,
    )

    @Test
    fun `a session with no breadcrumbs has no path and no pins`() {
        val trail = TrailBuilder.build("s1", emptyList(), listOf(entry(5, "help")))
        assertFalse(trail.hasPath)
        assertTrue(trail.incidents.isEmpty())
    }

    @Test
    fun `one breadcrumb is a position, not a path`() {
        // A recording made standing still should not draw a line to nowhere.
        val trail = TrailBuilder.build("s1", listOf(point(0)), emptyList())
        assertFalse(trail.hasPath)
        assertTrue(trail.isStationary)
    }

    @Test
    fun `points are ordered by time whatever order they arrive in`() {
        val trail = TrailBuilder.build("s1", listOf(point(30), point(0), point(15)), emptyList())
        assertEquals(
            listOf(base, base + 15_000, base + 30_000),
            trail.points.map { it.atEpochMillis },
        )
    }

    @Test
    fun `unflagged chatter earns no pin`() {
        // Most of a session is ordinary conversation. Pinning it would bury the rest.
        val trail = TrailBuilder.build(
            "s1",
            listOf(point(0), point(30)),
            listOf(entry(10, "lovely evening", flagged = false)),
        )
        assertTrue(trail.incidents.isEmpty())
    }

    @Test
    fun `a flagged line is pinned to the nearest breadcrumb`() {
        val trail = TrailBuilder.build(
            "s1",
            listOf(point(0), point(30), point(60)),
            listOf(entry(28, "please leave me alone")),
        )
        val incident = trail.incidents.single()
        assertEquals(TrailIncidentKind.FlaggedSpeech, incident.kind)
        // Nearest in time is the 30s breadcrumb, not the first one.
        assertEquals(point(30).latitude, incident.latitude, 1e-9)
        assertTrue("The popup should quote what was said", incident.detail.contains("leave me alone"))
    }

    @Test
    fun `an event far from any breadcrumb is dropped rather than guessed`() {
        // Placing it anyway would put a marker an unknown distance from where it
        // happened, and it would be read as precise.
        val trail = TrailBuilder.build(
            "s1",
            listOf(point(0)),
            listOf(entry(600, "something happened")),
        )
        assertTrue(trail.incidents.isEmpty())
    }

    @Test
    fun `a danger sound is classified as its own kind`() {
        val trail = TrailBuilder.build(
            "s1",
            listOf(point(0)),
            listOf(entry(2, "Glass", kind = SpeakerKind.SoundEvent, label = "Glass")),
        )
        assertEquals(TrailIncidentKind.DangerSound, trail.incidents.single().kind)
    }

    @Test
    fun `an unfamiliar voice is distinguished from the user's own speech`() {
        val trail = TrailBuilder.build(
            "s1",
            listOf(point(0)),
            listOf(entry(2, "get in the car", kind = SpeakerKind.Unknown, label = "Unfamiliar voice")),
        )
        assertEquals(TrailIncidentKind.UnknownVoice, trail.incidents.single().kind)
    }

    @Test
    fun `a codeword is pinned and keeps Angel's own wording`() {
        val trail = TrailBuilder.build(
            "s1",
            listOf(point(0)),
            listOf(
                entry(
                    2,
                    "“lighthouse” recognised — Danger codeword acted on.",
                    kind = SpeakerKind.SoundEvent,
                    label = "Angel",
                )
            ),
        )
        val incident = trail.incidents.single()
        assertEquals(TrailIncidentKind.Codeword, incident.kind)
        assertTrue(incident.detail.contains("lighthouse"))
    }

    @Test
    fun `a sharp score drop is pinned where it happened`() {
        val trail = TrailBuilder.build(
            "s1",
            listOf(point(0, score = 90), point(30, score = 55)),
            emptyList(),
        )
        val incident = trail.incidents.single()
        assertEquals(TrailIncidentKind.ScoreDrop, incident.kind)
        assertTrue(incident.detail.contains("35"))
        assertEquals(55, incident.safetyScore)
    }

    @Test
    fun `a gentle decline is the score working normally and is not pinned`() {
        val trail = TrailBuilder.build(
            "s1",
            listOf(point(0, score = 90), point(30, score = 84), point(60, score = 78)),
            emptyList(),
        )
        assertTrue(trail.incidents.isEmpty())
    }

    @Test
    fun `things happening at the same moment collapse to the most serious one`() {
        // A raised voice, a flagged line and a score drop are usually one moment. Three
        // overlapping pins make all three unreadable.
        val trail = TrailBuilder.build(
            "s1",
            listOf(point(0, score = 90), point(10, score = 50)),
            listOf(
                entry(10, "stop it"),
                entry(11, "Shouting", kind = SpeakerKind.SoundEvent, label = "Shouting"),
            ),
        )
        assertEquals(1, trail.incidents.size)
        assertEquals(TrailIncidentKind.DangerSound, trail.incidents.single().kind)
    }

    @Test
    fun `separate moments each keep their own pin`() {
        val trail = TrailBuilder.build(
            "s1",
            listOf(point(0), point(60), point(120)),
            listOf(entry(5, "first"), entry(62, "second")),
        )
        assertEquals(2, trail.incidents.size)
        // And they stay in the order they happened, so the map reads as a sequence.
        assertTrue(trail.incidents[0].atEpochMillis < trail.incidents[1].atEpochMillis)
    }

    @Test
    fun `a long quote is truncated so the popup stays readable`() {
        val trail = TrailBuilder.build(
            "s1",
            listOf(point(0)),
            listOf(entry(1, "word ".repeat(80))),
        )
        assertTrue(trail.incidents.single().detail.length < 160)
        assertTrue(trail.incidents.single().detail.endsWith("…” — You"))
    }

    @Test
    fun `the lowest score on the path is reported for colouring`() {
        val trail = TrailBuilder.build(
            "s1",
            listOf(point(0, score = 90), point(30, score = 41), point(60, score = 70)),
            emptyList(),
        )
        assertEquals(41, trail.lowestScore)
    }
}
