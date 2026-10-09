package com.example.guardianangel.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.guardianangel.audio.SAMPLE_RATE
import com.example.guardianangel.audio.SherpaSpeakerIdentifier
import com.example.guardianangel.audio.speechWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hands-free activation, end to end, minus the microphone.
 *
 * This is the behaviour the whole feature exists for: **say the phrase, and recording
 * starts, without touching the phone.** Every layer under it was tested in isolation and
 * the integration still had two bugs that only showed up here — a tokeniser that made
 * detection impossible, and an embedding-length mismatch that would have made the voice
 * gate reject its owner.
 *
 * Recorded speech is pushed through `route()` in the same 100 ms buffers the capture loop
 * produces, so what is under test is the real phase machine: pre-roll, detection, speaker
 * gate, and the flip from waiting to recording.
 */
@RunWith(AndroidJUnit4::class)
class HandsFreeActivationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun spokenPhraseStartsRecording() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val triggers = mutableListOf<String>()
        val session = GuardianAudioSession(
            context = context,
            scope = scope,
            onWakeWord = { triggers += it },
            onAssessment = {},
        )

        assumeTrue(
            "No keyword model installed in this build",
            session.prepare(wakePhrase = "light up"),
        )
        session.armForTest()

        feed(session, readFixture())

        assertEquals("The wake word did not fire", 1, triggers.size)
        assertEquals(
            "Detection did not move the session into recording",
            SessionPhase.Recording,
            session.state.value.phase,
        )
        session.release()
        scope.cancel()
    }

    /**
     * With voice matching on and the right speaker enrolled, the wake word still fires.
     *
     * The failure this guards against is the quiet one: a gate that rejects the person it
     * belongs to, leaving her with a phone that ignores her when she needs it.
     */
    @Test
    fun enrolledSpeakerStillWakesWithVoiceMatchOn() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val samples = readFixture()

        val speakers = SherpaSpeakerIdentifier(context)
        assumeTrue("No speaker model installed in this build", speakers.load())
        val voiceprint = speakers.embedSpeech(samples)
        speakers.close()
        assertNotNull("Could not build a voiceprint from the fixture", voiceprint)

        val triggers = mutableListOf<String>()
        val session = GuardianAudioSession(
            context = context,
            scope = scope,
            onWakeWord = { triggers += it },
            onAssessment = {},
        )
        assumeTrue(
            "No keyword model installed in this build",
            session.prepare(
                wakePhrase = "light up",
                voiceprint = voiceprint,
                requireVoiceMatch = true,
            ),
        )
        session.armForTest()

        feed(session, samples)

        assertTrue(
            "The enrolled speaker was gated out of her own wake word",
            triggers.isNotEmpty(),
        )
        session.release()
        scope.cancel()
    }

    /**
     * With voice matching on and a *different* voiceprint enrolled, nothing starts.
     *
     * The enrolled voiceprint here is deliberately not a voice at all — noise embeds to
     * something far from any speaker, which is exactly the "not her" case the gate is
     * meant to reject. Asserting on a real second speaker would need a second recording;
     * what matters is that a non-matching reference blocks the wake rather than being
     * ignored.
     */
    @Test
    fun nonMatchingVoiceIsGatedOut() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val samples = readFixture()

        val speakers = SherpaSpeakerIdentifier(context)
        assumeTrue("No speaker model installed in this build", speakers.load())
        val notHer = speakers.embed(
            FloatArray(SherpaSpeakerIdentifier.EMBED_WINDOW_SAMPLES) {
                ((it * 7919) % 2000 - 1000) / 1000f * 0.5f
            }
        )
        speakers.close()
        assertNotNull(notHer)

        val triggers = mutableListOf<String>()
        val session = GuardianAudioSession(
            context = context,
            scope = scope,
            onWakeWord = { triggers += it },
            onAssessment = {},
        )
        assumeTrue(
            "No keyword model installed in this build",
            session.prepare(
                wakePhrase = "light up",
                voiceprint = notHer,
                requireVoiceMatch = true,
            ),
        )
        session.armForTest()

        feed(session, samples)

        assertEquals(
            "A voice that does not match the voiceprint started a recording",
            emptyList<String>(),
            triggers,
        )
        assertEquals(SessionPhase.Waiting, session.state.value.phase)
        session.release()
        scope.cancel()
    }

    /** The manual button must work whether or not anything else is set up. */
    @Test
    fun manualRecordingStartsWithoutAWakeWord() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val session = GuardianAudioSession(
            context = context,
            scope = scope,
            onWakeWord = {},
            onAssessment = {},
        )
        // No wake phrase at all — the fallback must not depend on one.
        session.prepare(wakePhrase = "")
        session.armForTest()

        session.forceRecording(GuardianListeningService.MANUAL_TRIGGER)

        assertEquals(SessionPhase.Recording, session.state.value.phase)
        assertEquals(
            GuardianListeningService.MANUAL_TRIGGER,
            session.state.value.triggeredBy,
        )
        session.release()
        scope.cancel()
    }

    @Test
    fun prerollMatchesTheEmbeddingWindow() {
        // The two have to be equal or the gate compares incomparable embeddings.
        assertEquals(SherpaSpeakerIdentifier.EMBED_WINDOW_SAMPLES, PREROLL_SAMPLES)
        // And a window that long must be findable in the fixture.
        assertNotNull(speechWindow(readFixture()))
    }

    /** Pushes [samples] through the session in the same buffers the capture loop uses. */
    private fun feed(session: GuardianAudioSession, samples: FloatArray) {
        val chunk = SAMPLE_RATE / 10
        val taggerWindow = FloatArray(SAMPLE_RATE) // stand-in; tagging is not under test
        var offset = 0
        while (offset < samples.size) {
            val end = minOf(offset + chunk, samples.size)
            session.route(samples.copyOfRange(offset, end), taggerWindow)
            offset = end
        }
    }

    /** Reads the 16 kHz mono fixture into -1f..1f samples. */
    private fun readFixture(): FloatArray =
        InstrumentationRegistry.getInstrumentation().context.assets
            .open(FIXTURE)
            .use { stream ->
                val bytes = stream.readBytes()
                val sampleCount = (bytes.size - HEADER_BYTES) / 2
                FloatArray(sampleCount) { i ->
                    val lo = bytes[HEADER_BYTES + i * 2].toInt() and 0xFF
                    val hi = bytes[HEADER_BYTES + i * 2 + 1].toInt()
                    ((hi shl 8) or lo).toShort() / Short.MAX_VALUE.toFloat()
                }
            }

    private companion object {
        const val FIXTURE = "speech_light_up.wav"
        const val HEADER_BYTES = 44
    }
}
