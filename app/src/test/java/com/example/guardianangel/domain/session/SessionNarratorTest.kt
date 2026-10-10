package com.example.guardianangel.domain.session

import com.example.guardianangel.domain.model.DiarizedEntry
import com.example.guardianangel.domain.model.SpeakerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app says a recording *was*.
 *
 * Worth testing carefully: this text is the title in her activity list, the summary she
 * reads months later, and part of an export someone may hand to a police officer. An
 * overstatement here is not a wording bug.
 */
class SessionNarratorTest {

    private val start = 1_700_000_000_000L

    private fun line(
        offsetSeconds: Long,
        text: String,
        kind: SpeakerKind = SpeakerKind.You,
        label: String = "You",
        flagged: Boolean = false,
    ) = DiarizedEntry(
        id = "e$offsetSeconds-$text",
        sessionId = "s1",
        atEpochMillis = start + offsetSeconds * 1000,
        speakerKind = kind,
        speakerLabel = label,
        speakerQualifier = null,
        text = text,
        decibels = null,
        isFlagged = flagged,
    )

    private fun sound(offsetSeconds: Long, label: String) =
        line(offsetSeconds, label, kind = SpeakerKind.SoundEvent, label = "Sound")

    @Test
    fun `a quiet walk is titled as one and does not invent events`() {
        val narrative = SessionNarrator.narrate(
            startedAtMillis = start,
            endedAtMillis = start + 18 * 60_000,
            entries = listOf(line(10, "Almost home.")),
        )

        assertTrue(narrative.title.startsWith("Quiet recording"))
        assertTrue(narrative.summary.contains("Everything spoken matched your voice"))
        assertFalse(narrative.summary.contains("struggle"))
    }

    @Test
    fun `an emergency codeword outranks everything else in the title`() {
        val narrative = SessionNarrator.narrate(
            startedAtMillis = start,
            endedAtMillis = start + 120_000,
            entries = listOf(sound(20, "Screaming"), line(30, "Get off me", flagged = true)),
            locationLabel = "Telegraph Ave",
            triggeredTierName = "Emergency",
        )

        assertEquals("Emergency codeword · Telegraph Ave", narrative.title)
    }

    @Test
    fun `an unknown voice is counted and said plainly`() {
        val narrative = SessionNarrator.narrate(
            startedAtMillis = start,
            endedAtMillis = start + 60_000,
            entries = listOf(
                line(5, "Hey, hold on a second", kind = SpeakerKind.Unknown, label = "Unfamiliar voice"),
                line(9, "No thanks"),
            ),
        )

        assertTrue(narrative.summary.contains("1 line came from a voice that isn't yours"))
    }

    @Test
    fun `raw classifier labels are never shown to the user`() {
        val narrative = SessionNarrator.narrate(
            startedAtMillis = start,
            endedAtMillis = start + 60_000,
            entries = listOf(sound(5, "Slap, smack")),
        )

        assertFalse(narrative.summary.contains("Slap, smack"))
        assertTrue(narrative.summary.contains("a sound like a slap or impact"))
    }

    @Test
    fun `it describes rather than diagnoses`() {
        val narrative = SessionNarrator.narrate(
            startedAtMillis = start,
            endedAtMillis = start + 60_000,
            entries = listOf(sound(5, "Slap, smack"), sound(7, "Screaming")),
        )

        val text = narrative.title + " " + narrative.summary
        listOf("assault", "attack", "was hit", "victim").forEach {
            assertFalse("must not claim '$it': $text", text.lowercase().contains(it))
        }
    }

    @Test
    fun `a flagged line is quoted verbatim, not softened`() {
        val narrative = SessionNarrator.narrate(
            startedAtMillis = start,
            endedAtMillis = start + 60_000,
            entries = listOf(line(5, "he grabbed my arm", flagged = true)),
        )

        assertTrue(narrative.summary.contains("“he grabbed my arm”"))
    }

    @Test
    fun `repeated identical sounds collapse into one key moment`() {
        val narrative = SessionNarrator.narrate(
            startedAtMillis = start,
            endedAtMillis = start + 60_000,
            entries = listOf(sound(5, "Screaming"), sound(8, "Screaming"), sound(11, "Screaming")),
        )

        assertEquals(1, narrative.keyMoments.count { it.label == "A scream" })
    }

    @Test
    fun `key moments are ordered by time and carry an offset`() {
        val narrative = SessionNarrator.narrate(
            startedAtMillis = start,
            endedAtMillis = start + 300_000,
            entries = listOf(
                line(130, "watch out", flagged = true),
                sound(20, "Screaming"),
            ),
        )

        assertEquals(listOf(20_000L, 130_000L), narrative.keyMoments.map { it.offsetMillis })
    }

    @Test
    fun `traffic is not promoted into a key moment`() {
        val narrative = SessionNarrator.narrate(
            startedAtMillis = start,
            endedAtMillis = start + 60_000,
            entries = listOf(sound(5, "Traffic noise, roadway noise")),
        )

        assertTrue(narrative.keyMoments.isEmpty())
    }

    @Test
    fun `a placeholder location is not used as a place name`() {
        val narrative = SessionNarrator.narrate(
            startedAtMillis = start,
            endedAtMillis = start + 60_000,
            entries = listOf(sound(5, "Screaming")),
            locationLabel = "Locating…",
        )

        assertFalse(narrative.title.contains("Locating"))
    }

    @Test
    fun `an empty recording says so instead of inventing a narrative`() {
        val narrative = SessionNarrator.narrate(
            startedAtMillis = start,
            endedAtMillis = start + 30_000,
            entries = emptyList(),
        )

        assertTrue(narrative.summary.contains("Nothing was picked up"))
        assertTrue(narrative.keyMoments.isEmpty())
    }

    @Test
    fun `guardians who were alerted are named`() {
        val narrative = SessionNarrator.narrate(
            startedAtMillis = start,
            endedAtMillis = start + 60_000,
            entries = listOf(line(5, "help", flagged = true)),
            guardiansNotified = listOf("Amara", "David"),
        )

        assertTrue(narrative.summary.contains("Amara and David were alerted"))
    }
}
