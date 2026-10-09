package com.example.guardianangel.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * The parts of voice enrolment that do not need the embedding model.
 *
 * Covered here because each has a quiet failure mode that would never show up as a crash:
 * a speech window that returns the wrong length, normalisation that leaves vectors
 * un-normalised, a clarity scale that reads 90% for two different people. All of them
 * produce a voiceprint that looks fine and does not work.
 *
 * The length assertions are the important ones. Embeddings are only comparable between
 * inputs of the same duration, so a window that is one frame short of the rest silently
 * makes the voice gate reject its own user.
 */
class VoiceEnrolmentTest {

    @Test
    fun `speech window is always exactly the embedding length`() {
        // Four seconds, with speech only in the middle second and a half.
        val samples = FloatArray(SAMPLE_RATE * 4)
        val speechFrom = SAMPLE_RATE * 2
        val speechTo = speechFrom + SherpaSpeakerIdentifier.EMBED_WINDOW_SAMPLES
        for (i in speechFrom until speechTo) {
            samples[i] = 0.4f * sin(2 * Math.PI * 220 * i / SAMPLE_RATE).toFloat()
        }

        val window = speechWindow(samples)

        assertNotNull("Speech in the middle should produce a window", window)
        assertEquals(
            "Every embedding must come from the same length of audio",
            SherpaSpeakerIdentifier.EMBED_WINDOW_SAMPLES,
            window!!.size,
        )
    }

    @Test
    fun `speech window lands on the speech, not the silence`() {
        val samples = FloatArray(SAMPLE_RATE * 4)
        val speechFrom = SAMPLE_RATE * 2
        val speechTo = speechFrom + SherpaSpeakerIdentifier.EMBED_WINDOW_SAMPLES
        for (i in speechFrom until speechTo) {
            samples[i] = 0.4f * sin(2 * Math.PI * 220 * i / SAMPLE_RATE).toFloat()
        }

        val window = speechWindow(samples)!!

        // A window of silence would embed the room, not the voice. Most of what comes
        // back has to actually be the signal.
        val loud = window.count { abs(it) > 0.05f }
        assertTrue(
            "Window is mostly silence: only $loud of ${window.size} samples carry signal",
            loud > window.size / 2,
        )
    }

    @Test
    fun `rejects audio with no speech in it`() {
        // Room tone: audible on a meter, far below the speech threshold.
        val quiet = FloatArray(SAMPLE_RATE * 4) { Random(7).nextFloat() * 0.002f }
        assertNull("Near-silence must not be treated as speech", speechWindow(quiet))
    }

    @Test
    fun `rejects audio shorter than one window`() {
        val brief = FloatArray(SherpaSpeakerIdentifier.EMBED_WINDOW_SAMPLES - 1) { 0.5f }
        assertNull(
            "Padding a short clip would change its duration and break comparability",
            speechWindow(brief),
        )
    }

    @Test
    fun `normalise produces a unit vector`() {
        val vector = floatArrayOf(3f, 4f, 12f)
        val unit = normalise(vector)

        var sum = 0.0
        for (value in unit) sum += (value * value).toDouble()
        assertEquals("Normalised vectors must have length 1", 1.0, sum, 1e-5)
    }

    @Test
    fun `normalise leaves a zero vector alone`() {
        val zero = FloatArray(4)
        // Dividing by a zero norm would produce NaNs, which would then poison every
        // similarity comparison silently rather than failing.
        assertTrue(normalise(zero).all { it == 0f })
    }

    @Test
    fun `clarity scale separates the same speaker from a different one`() {
        // The operating points that matter: CAM++ puts the same speaker around 0.6-0.9
        // and different speakers below 0.4.
        val sameSpeaker = VoiceEnroller.agreementPercent(0.75f)
        val differentSpeaker = VoiceEnroller.agreementPercent(0.3f)

        assertTrue(
            "A same-speaker pair should read as usable, got $sameSpeaker",
            sameSpeaker >= 60,
        )
        assertEquals(
            "A different-speaker pair should read as unusable",
            0,
            differentSpeaker,
        )
    }

    @Test
    fun `clarity scale is clamped to a percentage`() {
        assertEquals(100, VoiceEnroller.agreementPercent(1f))
        assertEquals(0, VoiceEnroller.agreementPercent(-1f))
    }

    @Test
    fun `cosine similarity is one for identical vectors`() {
        val vector = FloatArray(32) { it.toFloat() }
        assertTrue(abs(cosineSimilarity(normalise(vector), normalise(vector)) - 1f) < 1e-4)
    }
}
