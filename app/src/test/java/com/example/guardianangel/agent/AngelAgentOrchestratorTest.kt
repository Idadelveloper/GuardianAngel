package com.example.guardianangel.agent

import com.example.guardianangel.audio.AudioEvent
import com.example.guardianangel.audio.TranscriptChunk
import com.example.guardianangel.data.FakeGuardianRepository
import com.example.guardianangel.data.crime.CrimeDataService
import com.example.guardianangel.domain.model.Codeword
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.GeoPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AngelAgentOrchestratorTest {

    private lateinit var crimeDataService: CrimeDataService
    private lateinit var testScope: TestScope
    private lateinit var orchestrator: AngelAgentOrchestrator
    private lateinit var fakeRepo: FakeGuardianRepository

    @Before
    fun setUp() {
        crimeDataService = CrimeDataService()
        testScope = TestScope()
        orchestrator = AngelAgentOrchestrator(crimeDataService, testScope)
        fakeRepo = FakeGuardianRepository()
    }

    @Test
    fun `factor scores average into composite score`() = runTest {
        orchestrator.onLocationUpdate(
            point = GeoPoint(37.7749, -122.4194),
            address = "San Francisco, CA"
        )
        orchestrator.onSpeakerUpdate(
            verifiedUser = true,
            unknownVoice = false,
            count = 1
        )
        orchestrator.onAudioEvents(emptyList(), peakDb = 45)

        val state = orchestrator.state.value
        val factors = state.factors

        // All 6 factors should be computed
        assertTrue(factors.crimeScore in 0..100)
        assertTrue(factors.weatherTimeScore in 0..100)
        assertTrue(factors.crowdScore in 0..100)
        assertTrue(factors.voiceToneScore in 0..100)
        assertTrue(factors.acousticScore in 0..100)
        assertTrue(factors.verbalScore in 0..100)

        // Composite score is the exact average of all 6 factors
        val expectedAverage = (
            factors.crimeScore +
            factors.weatherTimeScore +
            factors.crowdScore +
            factors.voiceToneScore +
            factors.acousticScore +
            factors.verbalScore
        ) / 6

        assertEquals(expectedAverage, factors.compositeSafetyScore)
    }

    @Test
    fun `danger codeword triggers agent action and lowers score`() = runTest {
        val codewords = listOf(
            Codeword(id = "code-danger", tier = CodewordTier.Danger, phrase = "silver moonlight")
        )

        orchestrator.onLocationUpdate(GeoPoint(37.7749, -122.4194), "Market St")
        orchestrator.onTranscriptReceived(
            chunk = TranscriptChunk("I see the silver moonlight tonight", isFinal = true, startMillis = 0, endMillis = 2000),
            guardianRepository = fakeRepo,
            codewords = codewords
        )

        val state = orchestrator.state.value
        assertEquals(CodewordTier.Danger, state.matchedCodeword)
        // Verbal factor should drop to Danger tier (25)
        assertEquals(25, state.factors.verbalScore)
    }

    @Test
    fun `severe scream corroborated with verbal distress triggers amber alert`() = runTest {
        orchestrator.onLocationUpdate(GeoPoint(37.7749, -122.4194), "Tenderloin, SF")
        orchestrator.onSpeakerUpdate(verifiedUser = true, unknownVoice = true, count = 2)
        orchestrator.onAudioEvents(
            events = listOf(
                AudioEvent("Screaming", confidence = 0.88f, atMillis = 1000, isDangerSignal = true)
            ),
            peakDb = 85
        )

        orchestrator.onTranscriptReceived(
            chunk = TranscriptChunk("No stop don't touch me let me go", isFinal = true, startMillis = 1000, endMillis = 3000),
            guardianRepository = fakeRepo,
            codewords = emptyList()
        )

        val state = orchestrator.state.value
        assertTrue(state.isAmberAlertActive)
        assertNotNull(state.activeDecisions)
        assertTrue(state.activeDecisions.isNotEmpty())

        // Dismiss amber alert works
        orchestrator.dismissAmberAlert()
        assertFalse(orchestrator.state.value.isAmberAlertActive)
    }

    @Test
    fun `forensic evidence log entries are captured with location and telemetry`() = runTest {
        val point = GeoPoint(37.7845, -122.4140)
        val address = "500 Howard St"
        orchestrator.onLocationUpdate(point, address)
        orchestrator.onSpeakerUpdate(verifiedUser = true, unknownVoice = false, count = 1)
        orchestrator.onAudioEvents(
            events = listOf(
                AudioEvent("Footsteps", confidence = 0.75f, atMillis = 500, isDangerSignal = false)
            ),
            peakDb = 52
        )

        orchestrator.onTranscriptReceived(
            chunk = TranscriptChunk("Just walking home from the station", isFinal = true, startMillis = 0, endMillis = 2000),
            guardianRepository = fakeRepo,
            codewords = emptyList()
        )

        val evidence = orchestrator.state.value.evidenceLog
        assertTrue("Evidence log should contain at least 1 entry", evidence.isNotEmpty())

        val firstEntry = evidence.first()
        assertEquals("Just walking home from the station", firstEntry.transcriptText)
        assertEquals(point, firstEntry.location)
        assertEquals(address, firstEntry.address)
        assertEquals("You", firstEntry.speaker)
        assertEquals(52, firstEntry.decibels)
    }
}
