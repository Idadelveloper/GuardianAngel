package com.example.guardianangel.domain.alert

import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.DiarizedEntry
import com.example.guardianangel.domain.model.SpeakerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The text a guardian actually receives.
 *
 * Asserted as writing, not just as data. This message may be the only thing that reaches
 * another person — read on a lock screen, possibly by someone who was asleep — so the
 * tests here are about whether it answers "who, where, how urgent" before anything else.
 */
class AlertComposerTest {

    private val at = 1_700_000_000_000L

    private fun line(
        text: String,
        flagged: Boolean = false,
        kind: SpeakerKind = SpeakerKind.You,
        label: String = "You",
    ) = DiarizedEntry(
        id = "e",
        sessionId = "s",
        atEpochMillis = at,
        speakerKind = kind,
        speakerLabel = label,
        text = text,
        isFlagged = flagged,
    )

    private fun compose(
        tier: CodewordTier = CodewordTier.Danger,
        name: String = "Ida",
        lat: Double? = 37.8690,
        lon: Double? = -122.2680,
        place: String? = "Shattuck Ave",
        entries: List<DiarizedEntry> = emptyList(),
    ) = AlertComposer.compose(tier, name, lat, lon, place, entries, now = at)

    @Test
    fun `it names the person in the first line`() {
        // A guardian's first question is who this is about.
        assertTrue(compose().body.lineSequence().first().contains("Ida"))
    }

    @Test
    fun `an emergency says so unmistakably`() {
        val body = compose(tier = CodewordTier.Emergency).body
        assertTrue(body.startsWith("EMERGENCY"))
        assertTrue(compose(tier = CodewordTier.Emergency).promptEmergencyCall)
    }

    @Test
    fun `a danger alert does not prompt an emergency call`() {
        // Only the user's own emergency codeword does that.
        assertFalse(compose(tier = CodewordTier.Danger).promptEmergencyCall)
    }

    @Test
    fun `no alert claims emergency services have already been called`() {
        // The app prompts her to call; it never dials on her behalf. Telling a guardian
        // help is on the way when it is not could stop them calling it themselves.
        CodewordTier.entries.forEach { tier ->
            val body = compose(tier = tier).body.lowercase()
            assertFalse(tier.name, body.contains("are being called"))
            assertFalse(tier.name, body.contains("have been called"))
        }
    }

    @Test
    fun `the location is an https maps link, not a geo uri`() {
        // geo: links are not tappable in most SMS clients and do nothing on a desktop.
        val body = compose().body
        assertTrue(body, body.contains("https://www.google.com/maps/search/?api=1&query="))
        assertTrue(body.contains("37.869000,-122.268000"))
        assertFalse(body.contains("geo:"))
    }

    @Test
    fun `a place name appears alongside the link`() {
        assertTrue(compose().body.contains("Shattuck Ave"))
    }

    @Test
    fun `a missing location is stated, not silently omitted`() {
        // Omitting the line would leave the reader to assume it just was not included.
        val body = compose(lat = null, lon = null, place = null).body
        assertTrue(body, body.contains("location unavailable"))
    }

    @Test
    fun `an alarming line is quoted rather than paraphrased`() {
        val body = compose(
            entries = listOf(
                line("lovely evening"),
                line("get in the car", flagged = true, kind = SpeakerKind.Unknown, label = "Unfamiliar voice"),
            )
        ).body
        assertTrue(body, body.contains("get in the car"))
    }

    @Test
    fun `a stranger's words are quoted in preference to the user's`() {
        // The single most useful line a guardian can be handed, and the one a summary
        // would most likely smooth away.
        val body = compose(
            entries = listOf(
                line("please stop", flagged = true),
                line("get in the car", flagged = true, kind = SpeakerKind.Unknown, label = "Unfamiliar voice"),
            )
        ).body
        assertTrue(body, body.contains("get in the car"))
    }

    @Test
    fun `the safe tier reads as an all-clear`() {
        val body = compose(tier = CodewordTier.Safe).body
        assertTrue(body, body.contains("safe"))
        assertTrue(body.contains("stood down"))
    }

    @Test
    fun `a blank name still produces a sensible message`() {
        val body = compose(name = "  ").body
        assertTrue(body, body.contains("Someone you're a guardian for"))
    }

    @Test
    fun `every alert says it was sent automatically`() {
        // So the recipient knows it is not a message she typed, and does not reply
        // expecting her to be looking at her phone.
        CodewordTier.entries.forEach { tier ->
            assertTrue(tier.name, compose(tier = tier).body.contains("Guardian Angel"))
        }
    }

    @Test
    fun `a transcript with nothing in it is reported honestly`() {
        val body = compose(entries = emptyList()).body
        assertTrue(body, body.contains("Nothing was transcribed"))
    }
}

/** The derived title and one-line summary, which the alert and the session both use. */
class TranscriptSummariserTest {

    private fun line(
        text: String,
        flagged: Boolean = false,
        kind: SpeakerKind = SpeakerKind.You,
        label: String = "You",
    ) = DiarizedEntry("e", "s", 0, kind, label, text = text, isFlagged = flagged)

    @Test
    fun `an empty transcript says so`() {
        val summary = TranscriptSummariser.summarise(emptyList())
        assertEquals("Recording with no speech", summary.title)
        assertEquals(null, summary.quote)
    }

    @Test
    fun `a quiet recording is titled as one`() {
        val summary = TranscriptSummariser.summarise(listOf(line("nice evening")))
        assertEquals("A quiet recording", summary.title)
    }

    @Test
    fun `a stranger plus tension is titled for the situation`() {
        val summary = TranscriptSummariser.summarise(
            listOf(
                line("who are you", flagged = true),
                line("come with me", flagged = true, kind = SpeakerKind.Unknown, label = "Unfamiliar voice"),
            )
        )
        // Named after what happened, not after the wake word that started it.
        assertEquals("A tense exchange with someone else", summary.title)
        assertTrue(summary.detail.contains("escalating"))
    }

    @Test
    fun `a danger sound is named in the detail`() {
        val summary = TranscriptSummariser.summarise(
            listOf(line("Shouting", flagged = true, kind = SpeakerKind.SoundEvent, label = "Shouting"))
        )
        assertTrue(summary.detail, summary.detail.contains("shouting"))
    }

    @Test
    fun `a long quote is trimmed`() {
        val summary = TranscriptSummariser.summarise(listOf(line("word ".repeat(60), flagged = true)))
        assertTrue((summary.quote?.length ?: 0) <= 90)
    }
}
