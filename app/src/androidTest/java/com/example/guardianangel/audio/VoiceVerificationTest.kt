package com.example.guardianangel.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.guardianangel.domain.repository.VoiceProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * Proves the voice gate on the wake word does both of its jobs.
 *
 * A gate has two failure modes and only one of them is obvious. Letting a stranger
 * through is the one people think of; refusing the owner is the one that matters more,
 * because it turns a safety feature into a phone that ignores her. Both are asserted
 * here, against real recorded speech — CAM++ on a synthetic tone returns a vector that
 * proves nothing.
 *
 * ## The invariant these tests exist to protect
 *
 * Embeddings are only comparable between inputs of the same duration. Measured on this
 * fixture: fixed 1.5 s windows of one speaker agree at 0.79 mean / 0.61 worst, while the
 * *same speech* embedded at 1 s versus 2 s scores -0.03 — orthogonal, as if two
 * strangers. An earlier version of the enroller trimmed to variable lengths and used 3 s
 * segments against a 1.5 s pre-roll, which would have rejected the enrolled user every
 * single time while every model-level test still passed.
 *
 * So [enrolledSpeakerIsAccepted] is the test that matters most, and
 * [everyEmbeddingUsesTheSameWindow] is the one that stops the regression coming back.
 */
@RunWith(AndroidJUnit4::class)
class VoiceVerificationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyEmbeddingUsesTheSameWindow() {
        val samples = readFixture()
        val window = speechWindow(samples)
        assertNotNull("The fixture contains speech, so a window must be found", window)
        assertEquals(
            "Enrolment and verification must embed the same length of audio",
            SherpaSpeakerIdentifier.EMBED_WINDOW_SAMPLES,
            window!!.size,
        )
        // The session's pre-roll is what the gate verifies against, so it has to match.
        assertEquals(
            "The pre-roll length must equal the embedding window",
            SherpaSpeakerIdentifier.EMBED_WINDOW_SAMPLES,
            com.example.guardianangel.service.PREROLL_SAMPLES,
        )
    }

    @Test
    fun enrolledSpeakerIsAccepted() = runBlocking {
        val speakers = SherpaSpeakerIdentifier(context)
        assumeTrue("No speaker model installed in this build", speakers.load())

        val samples = readFixture()
        // Enrol on the first half, verify on the second: same person, different words.
        val half = samples.size / 2
        speakers.enrolledVoiceprint = speakers.embedSpeech(samples.copyOfRange(0, half))
        assertNotNull("Enrolment embedding failed", speakers.enrolledVoiceprint)

        assertTrue(
            "The enrolled speaker was rejected — her own wake word would not work",
            speakers.matchesEnrolledUser(samples.copyOfRange(half, samples.size)),
        )
        speakers.close()
    }

    @Test
    fun silenceIsRejected() = runBlocking {
        val speakers = SherpaSpeakerIdentifier(context)
        assumeTrue("No speaker model installed in this build", speakers.load())

        speakers.enrolledVoiceprint = speakers.embedSpeech(readFixture())
        assertNotNull(speakers.enrolledVoiceprint)

        // Room tone must never satisfy the gate; if it did, the check would be decorative.
        val quiet = FloatArray(SAMPLE_RATE * 3) { Random(11).nextFloat() * 0.002f }
        assertFalse("Silence satisfied the voice gate", speakers.matchesEnrolledUser(quiet))
        speakers.close()
    }

    @Test
    fun noiseIsRejected() = runBlocking {
        val speakers = SherpaSpeakerIdentifier(context)
        assumeTrue("No speaker model installed in this build", speakers.load())

        speakers.enrolledVoiceprint = speakers.embedSpeech(readFixture())
        assertNotNull(speakers.enrolledVoiceprint)

        // Loud enough to pass the speech threshold, but not a voice. A street, a bus, a
        // television hiss — the gate must not treat any of it as her.
        val noise = FloatArray(SAMPLE_RATE * 3) { (Random(23).nextFloat() - 0.5f) * 0.6f }
        assertFalse("Broadband noise satisfied the voice gate", speakers.matchesEnrolledUser(noise))
        speakers.close()
    }

    /**
     * The voiceprint [VoiceEnroller] builds must accept the speaker it was built from.
     *
     * This is the end of the chain the user actually walks through: segments of speech
     * folded into a running mean, with a clarity figure deciding whether the gate engages
     * at all. A voiceprint that enrols fine but rejects its owner, or whose clarity lands
     * below [VoiceProfile.MIN_CLARITY] so the gate silently never engages, would look
     * completely healthy from the outside.
     */
    @Test
    fun enrolmentProducesAUsableVoiceprint() = runBlocking {
        val speakers = SherpaSpeakerIdentifier(context)
        assumeTrue("No speaker model installed in this build", speakers.load())

        val samples = readFixture()
        val enroller = VoiceEnroller(context, speakers)

        // Fold segments the way a capture would, without needing the microphone.
        val segment = VoiceEnroller.SEGMENT_SAMPLES
        var offset = 0
        var folded = 0
        while (offset + segment <= samples.size) {
            if (enroller.foldForTest(samples.copyOfRange(offset, offset + segment))) folded++
            offset += segment
        }
        assumeTrue("Fixture too short to fold several segments", folded >= 2)

        val voiceprint = enroller.voiceprint()
        assertNotNull("Enrolment produced no voiceprint", voiceprint)
        assertTrue(
            "Clarity came out at ${enroller.clarityPercent}%, below the " +
                "${VoiceProfile.MIN_CLARITY}% needed to gate on — one speaker's own " +
                "segments must agree with each other",
            enroller.clarityPercent >= VoiceProfile.MIN_CLARITY,
        )

        speakers.enrolledVoiceprint = voiceprint
        assertTrue(
            "The enrolled voiceprint rejects the speaker it came from",
            speakers.matchesEnrolledUser(samples),
        )
        enroller.close()
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
