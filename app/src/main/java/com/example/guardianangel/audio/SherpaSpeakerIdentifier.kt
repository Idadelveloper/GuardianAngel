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
 * Does two jobs from one 192-dimensional embedding:
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

    /** Embeds one speech segment. Needs roughly a second of speech to be meaningful. */
    fun embed(samples: FloatArray): FloatArray? {
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
        val embedding = embed(samples) ?: return false
        return cosineSimilarity(embedding, reference) >= SAME_SPEAKER
    }

    /**
     * Assigns a segment to a speaker, creating one if it matches nobody.
     *
     * @return a stable label like `spk0`, or null when the segment is too short to judge.
     */
    fun labelSpeaker(samples: FloatArray): String? {
        val embedding = embed(samples) ?: return null

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

    /** True when a voice other than the enrolled user has been heard this session. */
    fun hasUnknownVoice(): Boolean {
        val reference = enrolledVoiceprint ?: return sessionSpeakers.size > 1
        return sessionSpeakers.any { cosineSimilarity(it, reference) < SAME_SPEAKER }
    }

    val speakerCount: Int get() = sessionSpeakers.size

    /** Clears per-session speakers. The enrolled voiceprint is kept. */
    fun resetSession() = sessionSpeakers.clear()

    fun close() {
        extractor?.release()
        extractor = null
        sessionSpeakers.clear()
    }

    private companion object {
        const val MODEL_ASSET = "3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx"

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
    }
}
