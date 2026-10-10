package com.example.guardianangel.audio

import android.content.Context
import android.util.Log
import com.example.guardianangel.domain.model.ListeningSensitivity
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SherpaWakeWord"

/**
 * Wake-word detection backed by sherpa-onnx's keyword spotter.
 *
 * ## Why this replaced the embedding approach
 *
 * The original design used a frozen speech-embedding model with few-shot enrolment. It
 * worked, but the only readily available pre-trained embedding — openWakeWord's — is
 * CC BY-NC-SA, which quietly rules out ever shipping the app commercially.
 *
 * sherpa-onnx's keyword spotter reaches the same place with none of that: it is
 * **Apache-2.0 throughout**, the English model is **3.3 M parameters (~5 MB int8)**, and
 * it is *open vocabulary* — any phrase can be registered at runtime by passing its BPE
 * tokens to [KeywordSpotter.createStream], with no retraining and no enrolment takes.
 *
 * ## What is lost, and how it is recovered
 *
 * The embedding approach matched *this user saying this phrase*. A keyword spotter
 * matches *anyone* saying it. That difference matters for a safety app — someone else
 * should not be able to start a recording on her phone.
 *
 * It is recovered one layer up: the enrolled voiceprint (CAM++ speaker embedding) gates
 * the detection when `WakeWord.requireVoiceMatch` is on. Phrase detection and speaker
 * identity become two cheap, independent checks rather than one model trying to do both,
 * which is also easier to tune — a missed wake and a wrong-speaker wake have very
 * different costs.
 */
class SherpaWakeWordDetector(
    private val context: Context,
    /**
     * How hard the spotter tries.
     *
     * Previously fixed, which meant the "How closely I listen" control in Settings —
     * Low, Balanced, Whisper — was wired to nothing at all. A user who could not be
     * heard would reasonably turn sensitivity up and get no change whatsoever.
     */
    private val sensitivity: ListeningSensitivity = ListeningSensitivity.Balanced,
) {
    /**
     * Bias toward reporting the keyword.
     *
     * Deliberately eager across the board. The costs are not symmetric: a spurious
     * recording is cancelled with one tap, while a missed wake word is the feature not
     * existing at the only moment it mattered.
     */
    private val keywordsScore: Float
        get() = when (sensitivity) {
            ListeningSensitivity.Low -> 1.5f
            ListeningSensitivity.Balanced -> 2.5f
            ListeningSensitivity.Whisper -> 4.0f
        }

    /** Minimum acoustic probability before a detection is reported. */
    private val keywordsThreshold: Float
        get() = when (sensitivity) {
            ListeningSensitivity.Low -> 0.30f
            ListeningSensitivity.Balanced -> 0.20f
            // Low enough to catch a phrase said under the breath, at the cost of more
            // false wakes. That is the trade the user asked for by choosing it.
            ListeningSensitivity.Whisper -> 0.10f
        }

    private var spotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private var tokenizer: SentencePieceTokenizer? = null

    val isReady: Boolean get() = spotter != null

    /** Loads the model. Returns false when the assets are not present in this build. */
    suspend fun load(): Boolean = withContext(Dispatchers.IO) {
        if (spotter != null) return@withContext true
        try {
            val config = KeywordSpotterConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80, dither = 0f),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        // fp32, not int8. Both int8 encoder conversions — the standard
                        // one and the "mobile" one — abort inside onnxruntime a second
                        // into streaming: a Zipformer downsample reshape receives 17
                        // frames where it wants an even 16. It is a native abort, so no
                        // Kotlin try/catch can contain it. Costs ~8 MB over int8.
                        encoder = "$MODEL_DIR/encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
                        decoder = "$MODEL_DIR/decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
                        joiner = "$MODEL_DIR/joiner-epoch-12-avg-2-chunk-16-left-64.onnx",
                    ),
                    tokens = "$MODEL_DIR/tokens.txt",
                    // Two threads: enough to keep up with real time, few enough that an
                    // always-on listener does not dominate the CPU for a whole walk.
                    numThreads = 2,
                    provider = "cpu",
                    // Required. Without it sherpa cannot identify the architecture and
                    // aborts inside the native decoder — a process kill, not a Kotlin
                    // exception, so nothing upstream can catch it.
                    modelType = "zipformer2",
                ),
                keywordsFile = "$MODEL_DIR/keywords.txt",
                keywordsScore = keywordsScore,
                keywordsThreshold = keywordsThreshold,
            )
            spotter = KeywordSpotter(context.assets, config)
            tokenizer = SentencePieceTokenizer.fromAssets(context.assets, "$MODEL_DIR/bpe.model")
            Log.i(
                TAG,
                "Keyword spotter loaded (sensitivity=$sensitivity, " +
                    "score=$keywordsScore, threshold=$keywordsThreshold)",
            )
            true
        } catch (e: Exception) {
            // Missing assets are the expected case in a build without models.
            Log.i(TAG, "Keyword spotter unavailable: ${e.message}")
            false
        }
    }

    /**
     * Registers the phrase to listen for.
     *
     * @return false when the phrase cannot be expressed in the model's vocabulary, which
     *   the setup screen should surface rather than silently never waking.
     */
    fun setWakePhrase(phrase: String): Boolean {
        val current = spotter ?: return false
        val tokens = tokenizer?.tokenize(phrase) ?: return false
        return try {
            stream?.release()
            stream = current.createStream(tokens)
            Log.i(TAG, "Listening for \"$phrase\" as [$tokens]")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not register wake phrase", e)
            false
        }
    }

    /** Checks whether [phrase] can be represented, without registering it. */
    fun canRepresent(phrase: String): Boolean = tokenizer?.tokenize(phrase) != null

    /**
     * Feeds mono PCM in -1f..1f.
     *
     * @return the matched keyword, or null. Returning the keyword rather than a boolean
     *   leaves room for several registered phrases later without changing callers.
     */
    fun accept(samples: FloatArray): String? {
        val current = spotter ?: return null
        val active = stream ?: return null

        active.acceptWaveform(samples, SAMPLE_RATE)
        while (current.isReady(active)) {
            current.decode(active)
        }
        val keyword = current.getResult(active).keyword
        return if (keyword.isNotBlank()) {
            // Without a reset the same detection keeps being reported on every call.
            current.reset(active)
            keyword
        } else {
            null
        }
    }

    fun close() {
        stream?.release()
        stream = null
        spotter?.release()
        spotter = null
    }

    private companion object {
        const val MODEL_DIR = "sherpa-onnx-kws-zipformer-gigaspeech"
    }
}
