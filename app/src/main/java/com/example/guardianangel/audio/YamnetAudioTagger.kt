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

private const val TAG = "YamnetTagger"

/**
 * Tier 2b — non-speech audio understanding, via YAMNet on LiteRT.
 *
 * YAMNet is a MobileNet-v1 classifier over AudioSet's 521 classes: ~4 MB, trivially fast
 * on a phone, and it already knows the categories that matter here — screaming,
 * shouting, breaking glass, gunshots, sirens. Words are often the *last* evidence to
 * arrive in a bad situation, and sometimes there are none at all, so this runs alongside
 * transcription rather than after it.
 *
 * Only [DANGER_CLASSES] are treated as threat signals. Everything else YAMNet reports is
 * still returned so the session log can show context ("Speech", "Vehicle", "Music"), but
 * it carries no weight in [HeuristicThreatAssessor].
 *
 * Tensor shapes are read from the model at load time rather than hard-coded: the YAMNet
 * TFLite variants differ in whether they take a fixed 15600-sample frame or a variable
 * waveform, and in how many heads they expose.
 */
class YamnetAudioTagger(
    private val context: Context,
    /** Below this, a class is treated as noise rather than an observation. */
    private val reportThreshold: Float = 0.30f,
    /** Danger classes need more confidence before they are allowed to raise severity. */
    private val dangerThreshold: Float = 0.45f,
) : AudioTagger {

    private var interpreter: Interpreter? = null
    private var classNames: List<String> = emptyList()
    private var inputSamples: Int = DEFAULT_FRAME_SAMPLES
    private var scoreOutputIndex: Int = 0
    private var classCount: Int = 0

    override val isReady: Boolean get() = interpreter != null

    /** Samples per inference window — read from the model, not assumed. */
    val windowSamples: Int get() = inputSamples

    override suspend fun load(): Boolean = withContext(Dispatchers.IO) {
        if (interpreter != null) return@withContext true
        try {
            val created = Interpreter(
                loadModelFile(),
                Interpreter.Options().apply { numThreads = 1 },
            )

            val inputShape = created.getInputTensor(0).shape()
            inputSamples = inputShape.last().takeIf { it > 1 } ?: DEFAULT_FRAME_SAMPLES

            // Variants expose scores / embeddings / spectrogram in different orders;
            // the scores head is the one whose last dimension matches the class map.
            classNames = loadClassMap()
            scoreOutputIndex = (0 until created.outputTensorCount).firstOrNull { index ->
                created.getOutputTensor(index).shape().last() == classNames.size
            } ?: 0
            classCount = created.getOutputTensor(scoreOutputIndex).shape().last()

            interpreter = created
            Log.i(TAG, "YAMNet loaded: $inputSamples samples in, $classCount classes out")
            true
        } catch (e: FileNotFoundException) {
            Log.i(TAG, "No audio tagging model installed")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load YAMNet", e)
            false
        }
    }

    override fun tag(window: FloatArray, atMillis: Long): List<AudioEvent> {
        val model = interpreter ?: return emptyList()

        // YAMNet wants exactly one frame; pad or trim rather than refusing, so the
        // caller's window size does not have to track the model's.
        val frame = when {
            window.size == inputSamples -> window
            window.size > inputSamples -> window.copyOfRange(window.size - inputSamples, window.size)
            else -> FloatArray(inputSamples).also { window.copyInto(it) }
        }

        return try {
            val scores = Array(1) { FloatArray(classCount) }
            model.runForMultipleInputsOutputs(
                arrayOf<Any>(frame),
                mapOf(scoreOutputIndex to scores as Any),
            )

            scores[0]
                .mapIndexed { index, confidence -> index to confidence }
                .filter { (_, confidence) -> confidence >= reportThreshold }
                .sortedByDescending { it.second }
                .take(MAX_EVENTS)
                .map { (index, confidence) ->
                    val label = classNames.getOrElse(index) { "Class $index" }
                    AudioEvent(
                        label = label,
                        confidence = confidence,
                        atMillis = atMillis,
                        isDangerSignal = label in DANGER_CLASSES && confidence >= dangerThreshold,
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Tagging failed", e)
            emptyList()
        }
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }

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

    /** Reads `index,mid,display_name` and returns display names in index order. */
    private fun loadClassMap(): List<String> =
        context.assets.open(CLASS_MAP_ASSET).bufferedReader().useLines { lines ->
            lines.drop(1)
                .mapNotNull { line -> parseClassLine(line) }
                .toList()
        }

    private companion object {
        const val MODEL_ASSET = "yamnet.tflite"
        const val CLASS_MAP_ASSET = "yamnet_class_map.csv"

        /** YAMNet's native frame: 0.975 s at 16 kHz. */
        const val DEFAULT_FRAME_SAMPLES = 15_600
        const val MAX_EVENTS = 5

        /**
         * AudioSet classes treated as threat signals.
         *
         * Kept deliberately tight. "Crying" and sirens are *context*, not danger — a
         * siren usually means help is already nearby — so they are reported but excluded
         * here. Widening this list is the fastest way to make the app cry wolf.
         */
        val DANGER_CLASSES = setOf(
            "Screaming",
            "Shout",
            "Yell",
            "Children shouting",
            "Glass",
            "Smash, crash",
            "Breaking",
            "Slap, smack",
            "Gunshot, gunfire",
            "Explosion",
        )

        /** Splits on commas outside quotes — several display names contain a comma. */
        fun parseClassLine(line: String): String? {
            if (line.isBlank()) return null
            val firstComma = line.indexOf(',')
            if (firstComma < 0) return null
            val secondComma = line.indexOf(',', firstComma + 1)
            if (secondComma < 0) return null
            return line.substring(secondComma + 1)
                .trim()
                .removeSurrounding("\"")
                .takeIf { it.isNotBlank() }
        }
    }
}
