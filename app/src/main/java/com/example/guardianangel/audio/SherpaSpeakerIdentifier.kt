package com.example.guardianangel.audio

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SpeakerId"

/**
 * Tier 2c — whose voice is this, via the CAM++ speaker embedding.
 *
 * Does two jobs from one 512-dimensional embedding:
 *
 *  - **Verification.** Is this the enrolled user? Gates the wake word when
 *    `WakeWord.requireVoiceMatch` is on, so someone else saying her phrase cannot start
 *    a recording on her phone.
 *  - **Diarization.** During a session, cluster segments by similarity so the transcript
 *    can say "you" versus "unknown voice" — which is exactly what the reasoning tier
 *    needs to tell an argument on television from someone actually present.
 *
 * Clustering here is online and greedy rather than a proper offline diarizer: a segment
 * joins the nearest existing speaker above [SAME_SPEAKER], otherwise it starts a new
 * one. Offline clustering is more accurate but needs the whole recording first, and a
 * label that arrives after the incident is no use to anyone.
 */
class SherpaSpeakerIdentifier(
    private val context: Context,
) {
    private var extractor: SpeakerEmbeddingExtractor? = null

    /**
     * Guards the native extractor.
     *
     * Enrolment runs [embed] on a background coroutine, and leaving the screen mid-take
     * calls [close] from the composition — which would free the extractor while a
     * computation holds a pointer to it. The transcriber had the same shape and it
     * crashed the process natively; walking away from a recording is at least as likely
     * as standing down from a walk.
     */
    private val nativeLock = Any()

    /** Enrolled user's voiceprint, set once calibration has run. */
    @Volatile
    var enrolledVoiceprint: FloatArray? = null

    /** Speakers seen in the current session, index 0 being the first heard. */
    private val sessionSpeakers = mutableListOf<FloatArray>()

    val isReady: Boolean get() = extractor != null

    suspend fun load(): Boolean = withContext(Dispatchers.IO) {
        if (extractor != null) return@withContext true
        try {
            extractor = SpeakerEmbeddingExtractor(
                assetManager = context.assets,
                config = SpeakerEmbeddingExtractorConfig(
                    model = MODEL_ASSET,
                    numThreads = 1,
                    debug = false,
                    provider = "cpu",
                ),
            )
            Log.i(TAG, "Speaker extractor loaded: ${extractor?.dim()}-d embeddings")
            true
        } catch (e: Exception) {
            Log.i(TAG, "Speaker extractor unavailable: ${e.message}")
            false
        }
    }

    /**
     * Finds the speech in [samples] and embeds exactly one [EMBED_WINDOW_SAMPLES] window.
     *
     * **The way to produce an embedding that will be compared to another one.** Raw
     * [embed] takes whatever length it is given, and embeddings of different lengths are
     * not comparable — see [speechWindow]. Everything that compares voices goes through
     * here so the lengths always match.
     */
    fun embedSpeech(samples: FloatArray): FloatArray? =
        speechWindow(samples)?.let(::embed)

    /**
     * Embeds [samples] as given.
     *
     * Prefer [embedSpeech]: this does no length normalisation, so two calls with
     * different-length input produce embeddings that cannot be meaningfully compared.
     */
    fun embed(samples: FloatArray): FloatArray? = synchronized(nativeLock) {
        val current = extractor ?: return null
        if (samples.size < MIN_SAMPLES) return null
        return try {
            val stream = current.createStream()
            stream.acceptWaveform(samples, SAMPLE_RATE)
            stream.inputFinished()
            if (!current.isReady(stream)) {
                stream.release()
                return null
            }
            val embedding = current.compute(stream)
            stream.release()
            embedding
        } catch (e: Exception) {
            Log.e(TAG, "Embedding failed", e)
            null
        }
    }

    /** True when [samples] match the enrolled voiceprint. */
    fun matchesEnrolledUser(samples: FloatArray): Boolean {
        val reference = enrolledVoiceprint ?: return false
        val embedding = embedSpeech(samples) ?: return false
        return cosineSimilarity(embedding, reference) >= SAME_SPEAKER
    }

    /**
     * Assigns a segment to a speaker, creating one if it matches nobody.
     *
     * @return a stable label like `spk0`, or null when the segment is too short to judge.
     */
    fun labelSpeaker(samples: FloatArray): String? {
        val embedding = embedSpeech(samples) ?: return null

        var bestIndex = -1
        var bestScore = SAME_SPEAKER
        sessionSpeakers.forEachIndexed { index, known ->
            val score = cosineSimilarity(embedding, known)
            if (score >= bestScore) {
                bestScore = score
                bestIndex = index
            }
        }

        if (bestIndex >= 0) return "spk$bestIndex"
        sessionSpeakers += embedding
        return "spk${sessionSpeakers.lastIndex}"
    }

    /**
     * Who spoke this segment: a session-stable cluster label, and whether it was her.
     *
     * One embedding answers both questions, which is why they are returned together —
     * computing them separately embedded the same audio twice.
     *
     * `isEnrolledUser` is null when there is no voiceprint to compare against or the
     * segment was too short to embed. Null is not "someone else": a codeword from an
     * unverifiable voice is handled differently from one that is positively a stranger.
     */
    fun attribute(samples: FloatArray): SpeakerAttribution? {
        val embedding = embedSpeech(samples) ?: return null

        var bestIndex = -1
        var bestScore = SAME_SPEAKER
        sessionSpeakers.forEachIndexed { index, known ->
            val score = cosineSimilarity(embedding, known)
            if (score >= bestScore) {
                bestScore = score
                bestIndex = index
            }
        }
        val tag = if (bestIndex >= 0) {
            "spk$bestIndex"
        } else {
            sessionSpeakers += embedding
            "spk${sessionSpeakers.lastIndex}"
        }

        val reference = enrolledVoiceprint
        return SpeakerAttribution(
            tag = tag,
            isEnrolledUser = reference?.let {
                cosineSimilarity(embedding, it) >= SAME_SPEAKER
            },
        )
    }

    /** True when a voice other than the enrolled user has been heard this session. */
    fun hasUnknownVoice(): Boolean {
        val reference = enrolledVoiceprint ?: return sessionSpeakers.size > 1
        return sessionSpeakers.any { cosineSimilarity(it, reference) < SAME_SPEAKER }
    }

    val speakerCount: Int get() = sessionSpeakers.size

    /** Clears per-session speakers. The enrolled voiceprint is kept. */
    fun resetSession() = sessionSpeakers.clear()

    fun close() = synchronized(nativeLock) {
        extractor?.release()
        extractor = null
        sessionSpeakers.clear()
    }

    companion object {
        private const val MODEL_ASSET = "3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx"

        /**
         * Cosine similarity above which two segments are the same person.
         *
         * 0.5 is the conventional operating point for CAM++ on VoxCeleb. Deliberately
         * not raised: splitting one person into two speakers would make the transcript
         * claim a stranger is present, and a false "unknown voice" feeds straight into
         * the escalation score.
         */
        const val SAME_SPEAKER = 0.5f

        /** Half a second. Shorter segments give unstable embeddings. */
        const val MIN_SAMPLES = SAMPLE_RATE / 2

        /**
         * The one window length every comparable embedding is made from: 1.5 s.
         *
         * Not a tuning knob. CAM++ embeddings are only comparable between inputs of
         * similar duration — measured on one speaker, fixed 1.5 s windows agree at 0.79
         * mean / 0.61 worst, while the same speech at 1 s versus 2 s scores -0.03. So
         * enrolment, wake-word verification and diarization must all use this value or
         * they are comparing incomparable vectors.
         *
         * 1.5 s specifically because the wake-word gate has the tightest constraint: it
         * verifies against a pre-roll that has to contain a two or three syllable phrase
         * plus the decoder's own lag, and cannot be longer without delaying the wake.
         */
        const val EMBED_WINDOW_SAMPLES = SAMPLE_RATE * 3 / 2
    }
}
