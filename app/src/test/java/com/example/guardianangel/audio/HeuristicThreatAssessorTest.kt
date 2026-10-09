package com.example.guardianangel.audio

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the escalation rules.
 *
 * This is the logic that decides whether to contact someone's emergency contacts, so the
 * two failure modes are tested directly: staying quiet when something is happening, and
 * crying wolf when nothing is. Both are costly — the second is how a safety app gets
 * uninstalled before the night it was needed.
 */
class HeuristicThreatAssessorTest {

    private val assessor = HeuristicThreatAssessor()

    private fun snapshot(
        text: String = "",
        events: List<AudioEvent> = emptyList(),
        speakerCount: Int = 1,
        unknownVoice: Boolean = false,
        peakDecibels: Int? = null,
        locationScore: Int = 90,
        hourOfDay: Int = 14,
    ) = SituationSnapshot(
        transcript = if (text.isBlank()) {
            emptyList()
        } else {
            listOf(TranscriptChunk(text, isFinal = true, startMillis = 0, endMillis = 1000))
        },
        events = events,
        speakerCount = speakerCount,
        unknownVoicePresent = unknownVoice,
        peakDecibels = peakDecibels,
        locationScore = locationScore,
        hourOfDay = hourOfDay,
    )

    private fun danger(label: String, confidence: Float = 0.9f) =
        AudioEvent(label, confidence, atMillis = 0, isDangerSignal = true)

    @Test
    fun `ordinary conversation does not escalate`() = runTest {
        val result = assessor.assess(
            snapshot(text = "I'll grab coffee on the way, see you in ten", speakerCount = 2)
        )
        assertFalse(result.recommendEscalation)
        assertEquals(0f, result.severity, 0.001f)
    }

    @Test
    fun `a quiet walk alone at night does not escalate on context alone`() = runTest {
        // Context nudges severity but must never be sufficient by itself — otherwise
        // every late walk home would alert the user's contacts.
        val result = assessor.assess(
            snapshot(hourOfDay = 23, locationScore = 45, peakDecibels = 50)
        )
        assertFalse(result.recommendEscalation)
        assertTrue("context alone scored ${result.severity}", result.severity < 0.3f)
    }

    @Test
    fun `repeated refusals with a stranger escalate`() = runTest {
        val result = assessor.assess(
            snapshot(
                text = "no no stop leave me alone",
                speakerCount = 2,
                unknownVoice = true,
                peakDecibels = 84,
                locationScore = 50,
                hourOfDay = 23,
            )
        )
        assertTrue("severity was ${result.severity}", result.recommendEscalation)
        assertTrue(result.contributingSignals.size >= 2)
    }

    @Test
    fun `a scream escalates when corroborated`() = runTest {
        val result = assessor.assess(
            snapshot(
                text = "help me",
                events = listOf(danger("Scream")),
                unknownVoice = true,
            )
        )
        assertTrue(result.recommendEscalation)
    }

    @Test
    fun `a single low-confidence event alone does not escalate`() = runTest {
        // One hedged detection is exactly the case where crying wolf is most likely.
        val result = assessor.assess(
            snapshot(events = listOf(danger("Glass", confidence = 0.35f)))
        )
        assertFalse(result.recommendEscalation)
    }

    @Test
    fun `severity stays within range`() = runTest {
        val result = assessor.assess(
            snapshot(
                text = "no no no stop stop help me call the police leave me alone",
                events = List(5) { danger("Scream") },
                speakerCount = 6,
                unknownVoice = true,
                peakDecibels = 110,
                locationScore = 5,
                hourOfDay = 3,
            )
        )
        assertTrue(result.severity <= 1f)
        assertTrue(result.recommendEscalation)
    }

    @Test
    fun `ambiguous cases are the ones handed to the expensive tier`() = runTest {
        val ambiguous = assessor.assess(
            snapshot(text = "no", unknownVoice = true, peakDecibels = 84)
        )
        // Mid-band: cheap signals are unsure, so a language model earns its latency.
        assertTrue(
            "severity ${ambiguous.severity} should be ambiguous",
            assessor.warrantsDeepAnalysis(ambiguous),
        )

        val calm = assessor.assess(snapshot(text = "lovely evening"))
        assertFalse(assessor.warrantsDeepAnalysis(calm))
    }
}
