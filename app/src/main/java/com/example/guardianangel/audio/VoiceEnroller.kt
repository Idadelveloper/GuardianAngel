package com.example.guardianangel.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

private const val TAG = "VoiceEnroller"

/** What a capture produced. */
sealed interface EnrolmentOutcome {

    /**
     * The voiceprint was updated.
     *
     * @param clarityPercent how well the new speech agreed with what was already known.
     * @param sampleCount total segments folded in, across every session.
     */
    data class Captured(
        val voiceprint: FloatArray,
        val clarityPercent: Int,
        val sampleCount: Int,
    ) : EnrolmentOutcome

    /** Nothing loud enough to be speech. Usually: did not speak in time. */
    data object NoSpeech : EnrolmentOutcome

    /** The microphone could not be opened — revoked permission, or another app has it. */
    data object MicrophoneUnavailable : EnrolmentOutcome

    /** The speaker-embedding model is absent from this build. */
    data object ModelMissing : EnrolmentOutcome
}

/**
 * Builds the user's voiceprint from spoken audio.
 *
 * ## What this is *not* for
 *
 * It does not teach Angel the wake phrase. The keyword spotter is open-vocabulary and
 * matches the phrase from its own tokens, so a typed phrase works immediately. This
 * answers a different question — *was that her saying it?* — so a stranger who learns
 * her wake word cannot start a recording on her phone.
 *
 * ## Why a running average, not a batch
 *
 * Enrolment happens in more than one place: a minute of free speech during setup, and
 * short takes afterwards from the wake-word screen. If each place built a voiceprint from
 * only the audio it captured, whichever ran last would overwrite the other — a single
 * two-second take would discard a minute of speech.
 *
 * So the voiceprint is a weighted mean kept across sessions: [seed] restores what is
 * already stored, every new segment is folded into it, and clarity tracks how well new
 * speech agrees with what was already known. Takes that disagree drag clarity down, and
 * low clarity leaves the wake-word gate off — because a bad voiceprint does not weaken
 * the protection, it stops the wake word working for the person it belongs to.
 *
 * Audio never leaves this object. Each segment becomes a 512-float embedding and the
 * samples are dropped; nothing is written to disk and nothing is uploaded.
 */
class VoiceEnroller(
    private val context: Context,
    private val speakers: SherpaSpeakerIdentifier = SherpaSpeakerIdentifier(context),
) {
    private var mean: FloatArray? = null
    private var count = 0
    private var clarity = 0

    val sampleCount: Int get() = count
    val clarityPercent: Int get() = clarity

    /** Loads the embedding model. False means this build cannot do voice matching. */
    suspend fun load(): Boolean = speakers.load()

    /**
     * Restores a voiceprint from storage so new audio adds to it instead of replacing it.
     *
     * Call before the first capture. Passing null starts fresh.
     */
    fun seed(embedding: FloatArray?, sampleCount: Int, clarityPercent: Int) {
        mean = embedding?.copyOf()
        count = if (embedding == null) 0 else sampleCount.coerceAtLeast(1)
        clarity = if (embedding == null) 0 else clarityPercent.coerceIn(0, 100)
    }

    /** Throws away everything learned, so the user can start again. */
    fun reset() {
        mean = null
        count = 0
        clarity = 0
    }

    /** The current voiceprint, or null before any speech has been folded in. */
    fun voiceprint(): FloatArray? = mean?.let(::normalise)

    /**
     * Captures [millis] of audio and folds every speech segment into the voiceprint.
     *
     * One continuous capture rather than repeated short ones: opening the microphone
     * costs a few hundred milliseconds and drops the start of whatever is being said, so
     * a minute of enrolment would lose a noticeable slice of it.
     *
     * @param onProgress called about every 100 ms with elapsed milliseconds, for a
     *   progress ring. Runs on the capture thread, so it must not block.
     */
    @SuppressLint("MissingPermission") // Caller checks; failure returns MicrophoneUnavailable.
    suspend fun capture(
        millis: Int,
        onProgress: (elapsedMillis: Int) -> Unit = {},
    ): EnrolmentOutcome = withContext(Dispatchers.IO) {
        if (!speakers.isReady) return@withContext EnrolmentOutcome.ModelMissing

        val recorder = openRecorder() ?: return@withContext EnrolmentOutcome.MicrophoneUnavailable

        val total = SAMPLE_RATE.toLong() * millis / 1000
        val segment = FloatArray(SEGMENT_SAMPLES)
        val shorts = ShortArray(SAMPLE_RATE / 10)
        var segmentFilled = 0
        var captured = 0L
        var folded = 0

        try {
            recorder.startRecording()
            while (captured < total && currentCoroutineContext().isActive) {
                val read = recorder.read(shorts, 0, shorts.size)
                if (read <= 0) break

                for (i in 0 until read) {
                    segment[segmentFilled] = shorts[i] / Short.MAX_VALUE.toFloat()
                    segmentFilled++
                    if (segmentFilled == segment.size) {
                        if (fold(segment)) folded++
                        segmentFilled = 0
                    }
                }

                captured += read
                onProgress((captured * 1000 / SAMPLE_RATE).toInt())
            }

            // A short take never fills a whole segment, so the tail is where its only
            // embedding comes from. It still has to contain a full window.
            if (segmentFilled >= SherpaSpeakerIdentifier.EMBED_WINDOW_SAMPLES) {
                if (fold(segment.copyOf(segmentFilled))) folded++
            }
        } catch (e: Exception) {
            Log.w(TAG, "Capture failed", e)
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }

        val voiceprint = voiceprint()
        if (folded == 0 || voiceprint == null) {
            EnrolmentOutcome.NoSpeech
        } else {
            EnrolmentOutcome.Captured(voiceprint, clarity, count)
        }
    }

    fun close() = speakers.close()

    /**
     * Folds one segment directly, bypassing the microphone.
     *
     * Exists so the accumulation and clarity logic can be tested against recorded speech
     * on a device, which is the only place the embedding model runs. Everything that
     * decides whether the voice gate works lives in [fold]; without a seam there it
     * could only be exercised by actually speaking at a phone.
     */
    internal fun foldForTest(samples: FloatArray): Boolean = fold(samples)

    /**
     * Folds one segment into the running mean. Returns false when it held no speech.
     *
     * Clarity is updated *before* the segment joins the mean — comparing a segment to a
     * mean it is already part of would flatter it, and with few samples it would read
     * near-perfect however inconsistent the takes actually were.
     */
    private fun fold(samples: FloatArray): Boolean {
        val embedding = speakers.embedSpeech(samples) ?: return false

        val current = mean
        if (current == null) {
            mean = embedding.copyOf()
            count = 1
            // One segment has nothing to agree with. Clarity stays 0, which keeps the
            // gate off until there is corroboration.
            clarity = 0
            return true
        }

        val agreement = agreementPercent(cosineSimilarity(normalise(current), embedding))
        clarity = if (count <= 1) {
            agreement
        } else {
            // A moving average, so one bad segment in a long session does not erase a
            // good voiceprint and one good segment cannot rescue a bad one.
            ((clarity * (1 - CLARITY_WEIGHT)) + (agreement * CLARITY_WEIGHT)).toInt()
        }

        for (i in current.indices) {
            current[i] = (current[i] * count + embedding[i]) / (count + 1)
        }
        count++
        return true
    }

    private fun openRecorder(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return null

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, SEGMENT_SAMPLES),
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Microphone permission not granted", e)
            return null
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            Log.w(TAG, "Microphone unavailable — another app may hold it")
            return null
        }
        return recorder
    }

    companion object {
        /**
         * How much audio is read before looking for a window to embed.
         *
         * Twice the embedding window, so [speechWindow] has room to pick the part that is
         * actually speech rather than being handed a window that happens to start in a
         * pause. The window it returns is always exactly
         * [SherpaSpeakerIdentifier.EMBED_WINDOW_SAMPLES] — see [speechWindow] for why
         * that length must never vary.
         */
        const val SEGMENT_SAMPLES = SherpaSpeakerIdentifier.EMBED_WINDOW_SAMPLES * 2

        /** A short take: one phrase at a natural pace. */
        const val TAKE_MILLIS = 2500

        /** The free-speech enrolment during setup. */
        const val SESSION_MILLIS = 60_000

        /** How much a new segment moves clarity. Low enough to need several bad ones. */
        private const val CLARITY_WEIGHT = 0.35f

        /**
         * Maps a cosine similarity onto 0-100.
         *
         * Same-speaker pairs land around 0.6-0.9 and different-speaker pairs below 0.4,
         * so the raw cosine would read as a scary-looking number for a perfectly good
         * voiceprint. 0.4 maps to 0 and 0.95 to 100.
         */
        fun agreementPercent(cosine: Float): Int {
            val scaled = (cosine - 0.4f) / (0.95f - 0.4f)
            return (scaled * 100).toInt().coerceIn(0, 100)
        }
    }
}

/**
 * Picks the [windowSamples]-long stretch of [samples] with the most speech in it.
 *
 * ## Why a fixed length, and not a trim
 *
 * This looks like it should just trim the silence off each end, and it used to. That was
 * wrong in a way that no test of the model alone would reveal: **CAM++ embeddings are
 * only comparable between inputs of similar duration.** Measured on one speaker saying
 * one sentence, fixed 1.5 s windows agree at 0.79 mean / 0.61 worst, while the *same
 * speech* embedded at 1 s versus 2 s scores -0.03 — orthogonal, as if two strangers.
 *
 * A variable-length trim therefore produced embeddings that could not be compared to each
 * other at all. Combined with enrolment using 3 s segments and the wake-word gate using a
 * 1.5 s pre-roll, the gate would have rejected the enrolled user every single time: the
 * exact failure that turns a safety feature into a phone that ignores her.
 *
 * So every embedding in the app comes from a window of exactly
 * [SherpaSpeakerIdentifier.EMBED_WINDOW_SAMPLES], and this is the one place that decides
 * which window. Returns null when nothing in [samples] is loud enough to be speech, or
 * when there is less audio than one window.
 */
internal fun speechWindow(
    samples: FloatArray,
    windowSamples: Int = SherpaSpeakerIdentifier.EMBED_WINDOW_SAMPLES,
    frameSize: Int = SAMPLE_RATE / 100,
    threshold: Float = 0.015f,
): FloatArray? {
    if (samples.size < windowSamples) return null

    val frames = samples.size / frameSize
    if (frames == 0) return null

    // Per-frame speech flags, then a sliding count to find the densest window. Energy
    // alone would be pulled towards a single loud bang; counting speech frames prefers
    // the stretch that is most consistently voice.
    val isSpeech = BooleanArray(frames)
    var anySpeech = false
    for (frame in 0 until frames) {
        var sum = 0.0
        val start = frame * frameSize
        for (i in start until start + frameSize) sum += (samples[i] * samples[i]).toDouble()
        if (sqrt(sum / frameSize) >= threshold) {
            isSpeech[frame] = true
            anySpeech = true
        }
    }
    if (!anySpeech) return null

    val windowFrames = (windowSamples / frameSize).coerceAtLeast(1)
    if (windowFrames > frames) return null

    var count = 0
    for (frame in 0 until windowFrames) if (isSpeech[frame]) count++
    var bestCount = count
    var bestFrame = 0
    for (frame in 1..frames - windowFrames) {
        if (isSpeech[frame - 1]) count--
        if (isSpeech[frame + windowFrames - 1]) count++
        if (count > bestCount) {
            bestCount = count
            bestFrame = frame
        }
    }
    if (bestCount == 0) return null

    val from = (bestFrame * frameSize).coerceAtMost(samples.size - windowSamples)
    return samples.copyOfRange(from, from + windowSamples)
}

/** Rescales to unit length so cosine similarity is meaningful. */
internal fun normalise(vector: FloatArray): FloatArray {
    var sum = 0.0
    for (value in vector) sum += (value * value).toDouble()
    val norm = sqrt(sum).toFloat()
    if (norm <= 1e-8f) return vector
    return FloatArray(vector.size) { vector[it] / norm }
}
