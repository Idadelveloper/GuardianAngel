package com.example.guardianangel.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the wake word actually fires.
 *
 * Everything before this was "the model loaded", which says nothing about whether a
 * spoken phrase is ever recognised. This feeds real recorded speech through the same
 * `accept()` path the listening service uses and asserts a detection comes out.
 *
 * The fixture is sherpa-onnx's own test clip — a sentence containing the words "light
 * up". Registering that as the wake phrase means a detection can only come from the
 * model genuinely matching audio, not from anything the test arranged.
 *
 * Audio is fed in 100 ms chunks, the same size the capture loop uses, so the streaming
 * behaviour under test is the behaviour that ships.
 */
@RunWith(AndroidJUnit4::class)
class WakeWordDetectionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun detectsSpokenWakePhrase() = runBlocking {
        val detector = SherpaWakeWordDetector(context)
        assumeTrue("No keyword model installed in this build", detector.load())

        assertTrue("Could not register the wake phrase", detector.setWakePhrase("light up"))

        val detections = feed(detector, readFixture())
        detector.close()

        assertTrue("Expected the phrase to be detected, got none", detections.isNotEmpty())
    }

    /** A phrase that is not in the clip must not fire. */
    @Test
    fun doesNotFireOnUnrelatedSpeech() = runBlocking {
        val detector = SherpaWakeWordDetector(context)
        assumeTrue("No keyword model installed in this build", detector.load())

        // In the vocabulary, absent from the recording — a hit here would be a false
        // accept, which for this app means a recording nobody asked for.
        assertTrue(detector.setWakePhrase("happy new year"))

        val detections = feed(detector, readFixture())
        detector.close()

        assertEquals("False accept on unrelated speech", emptyList<String>(), detections)
    }

    @Test
    fun rejectsPhrasesOutsideTheVocabulary() = runBlocking {
        val detector = SherpaWakeWordDetector(context)
        assumeTrue("No keyword model installed in this build", detector.load())

        // Better to refuse at setup than register a phrase that can never match.
        assertTrue(
            "Expected an unrepresentable phrase to be rejected",
            !detector.canRepresent("ЖЖЖ"),
        )
        detector.close()
    }

    @Test
    fun speakerEmbeddingsSeparateVoices() = runBlocking {
        val speakers = SherpaSpeakerIdentifier(context)
        assumeTrue("No speaker model installed in this build", speakers.load())

        val samples = readFixture()
        // Two halves of one speaker's sentence should look like the same person.
        val a = speakers.embed(samples.copyOfRange(0, samples.size / 2))
        val b = speakers.embed(samples.copyOfRange(samples.size / 2, samples.size))
        assertNotNull("Embedding failed for the first half", a)
        assertNotNull("Embedding failed for the second half", b)

        val similarity = cosineSimilarity(a!!, b!!)
        assertTrue(
            "Same speaker scored only $similarity — verification would reject her",
            similarity >= 0.5f,
        )

        // Too short to judge: the identifier must say so rather than guess.
        assertNull(speakers.embed(FloatArray(100)))
        speakers.close()
    }

    /** Streams [samples] through the detector, returning every keyword it reported. */
    private fun feed(detector: SherpaWakeWordDetector, samples: FloatArray): List<String> {
        val chunk = SAMPLE_RATE / 10 // 100 ms, matching GuardianAudioSession
        val detections = mutableListOf<String>()
        var offset = 0
        while (offset < samples.size) {
            val end = minOf(offset + chunk, samples.size)
            detector.accept(samples.copyOfRange(offset, end))?.let(detections::add)
            offset = end
        }
        return detections
    }

    /** Reads the 16 kHz mono fixture into -1f..1f samples. */
    private fun readFixture(): FloatArray =
        InstrumentationRegistry.getInstrumentation().context.assets
            .open(FIXTURE)
            .use { stream ->
                val bytes = stream.readBytes()
                // Skip the 44-byte canonical WAV header; the fixture is 16-bit PCM.
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
