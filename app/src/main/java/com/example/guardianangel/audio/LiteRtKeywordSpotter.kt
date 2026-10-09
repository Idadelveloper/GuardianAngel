package com.example.guardianangel.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

private const val TAG = "LiteRtSpotter"

/**
 * Keyword spotter backed by LiteRT — the successor runtime to TensorFlow Lite.
 *
 * ## The model this expects
 *
 * A frozen **speech embedding** model: log-mel frames in, a fixed-length vector out. The
 * intended one is the `speech_embedding` backbone that openWakeWord and several
 * few-shot keyword-spotting papers build on — roughly 1 MB, a few milliseconds per
 * window on a mid-range phone, which is what makes always-on listening affordable.
 *
 * Put the converted model at `app/src/main/assets/[MODEL_ASSET]`. There is deliberately
 * no model committed to this repository: openWakeWord's *pre-trained* weights are
 * CC BY-NC-SA 4.0, which is fine for a prototype and not fine for a shipped app, so the
 * model is a decision to make at integration time rather than one baked in here. Until a
 * file is present [load] returns false and the app says plainly that hands-free
 * activation is unavailable.
 *
 * ## Why a classifier is not used
 *
 * A fixed-vocabulary classifier would be smaller still, but it can only recognise the
 * words it was trained on, and the whole point here is that the user picks her own
 * phrase. Embedding plus cosine similarity trades a little accuracy for the ability to
 * enrol any phrase in about fifteen seconds, on device, with no training step.
 */
class LiteRtKeywordSpotter(
    private val context: Context,
    override val windowSamples: Int = (SAMPLE_RATE * 1.5f).toInt(),
    override val hopSamples: Int = SAMPLE_RATE / 4,
) : KeywordSpotter {

    private val mel = MelSpectrogram()
    private var interpreter: Interpreter? = null
    private var embeddingSize: Int = 0

    override val isReady: Boolean get() = interpreter != null

    override suspend fun load(): Boolean = withContext(Dispatchers.IO) {
        if (interpreter != null) return@withContext true
        try {
            val options = Interpreter.Options().apply {
                // A model this small is CPU-cheap; two threads is plenty and keeps the
                // always-on cost low enough to run for a whole walk home.
                numThreads = 2
            }
            val created = Interpreter(loadModelFile(), options)
            // The output shape tells us the embedding width, so nothing is hard-coded to
            // one particular model.
            embeddingSize = created.getOutputTensor(0).shape().last()
            interpreter = created
            Log.i(TAG, "Keyword model loaded: ${'$'}embeddingSize-d embeddings")
            true
        } catch (e: FileNotFoundException) {
            // Expected in builds without a model; not an error worth shouting about.
            Log.i(TAG, "No keyword model installed — hands-free activation is unavailable")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load keyword model", e)
            false
        }
    }

    override fun embed(window: FloatArray): FloatArray? {
        val model = interpreter ?: return null
        require(window.size == windowSamples) {
            "window must be ${'$'}windowSamples samples, was ${'$'}{window.size}"
        }

        return try {
            val frames = mel.extract(window)
            if (frames.isEmpty()) return null

            // [1][frames][bins] — a leading batch dimension, as every speech embedding
            // model in this family expects.
            val input = arrayOf(frames)
            val output = Array(1) { FloatArray(embeddingSize) }
            model.run(input, output)
            output[0]
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            null
        }
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }

    /** Memory-maps the model so it is not copied onto the heap. */
    private fun loadModelFile(): ByteBuffer =
        context.assets.openFd(MODEL_ASSET).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { stream ->
                stream.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.declaredLength,
                ).order(ByteOrder.nativeOrder())
            }
        }

    private companion object {
        const val MODEL_ASSET = "speech_embedding.tflite"
    }
}

/**
 * Stand-in spotter for builds and tests with no model installed.
 *
 * It deliberately **never reports a match**: a fake detector that fired on a timer would
 * make the hands-free path look like it worked and would be a genuinely dangerous thing
 * to demo for a safety app. What it does do is let the service, the enrolment flow and
 * every piece of UI state be exercised end to end, with [isReady] false so the interface
 * can tell the user the truth.
 */
class StubKeywordSpotter(
    override val windowSamples: Int = (SAMPLE_RATE * 1.5f).toInt(),
    override val hopSamples: Int = SAMPLE_RATE / 4,
) : KeywordSpotter {
    override val isReady: Boolean = false
    override suspend fun load(): Boolean = false
    override fun embed(window: FloatArray): FloatArray? = null
    override fun close() = Unit
}
