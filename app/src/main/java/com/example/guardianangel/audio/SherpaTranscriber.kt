package com.example.guardianangel.audio

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

private const val TAG = "SherpaTranscriber"

/**
 * Tier 2a — real-time transcription, via Silero VAD segmenting into Moonshine.
 *
 * ## Why VAD + offline, not a streaming recogniser
 *
 * Moonshine is an encoder-decoder model, which sherpa exposes as an *offline* recogniser
 * — it wants a complete utterance, not a rolling stream. Rather than treat that as a
 * limitation, it is the right shape here: Silero VAD cuts the audio at natural speech
 * boundaries and each segment is decoded whole.
 *
 * That buys three things a chunked streaming decoder would not:
 *
 *  - **No compute on silence.** A walk home is mostly quiet; the expensive model only
 *    runs when somebody speaks, which is most of the battery argument for this design.
 *  - **Whole-utterance accuracy.** Moonshine sees the full phrase, not a window that
 *    might cut "leave me alone" in half — and half of that phrase means something very
 *    different to the reasoning tier.
 *  - **Natural transcript units.** Segments map onto turns, which is what diarization
 *    and the session timeline both want anyway.
 *
 * The cost is latency bounded by utterance length plus [MIN_SILENCE_SECONDS]: text
 * arrives when someone stops talking rather than while they still are. For deciding
 * whether a situation is escalating that is the right trade — a half-decoded phrase is
 * not evidence.
 */
class SherpaTranscriber(
    private val context: Context,
) : SpeechTranscriber {

    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null

    /**
     * Guards every use of the native handles.
     *
     * Not defensive clutter — the absence of this caused a native crash on the disarm
     * path. `finish()` reads `recognizer` into a local and then decodes, which can take
     * hundreds of milliseconds; if `close()` released the recognizer during that window,
     * the local pointed at freed memory and the process died inside `decodeSegment`.
     * A user ending a walk is not an edge case.
     *
     * Decoding holds the lock, so [close] can wait for a segment in flight. That is the
     * right trade: the alternative is the crash.
     */
    private val nativeLock = Any()

    /** Running sample offset, so chunks carry real timestamps rather than indices. */
    private var samplesConsumed: Long = 0

    private val _transcript = MutableSharedFlow<TranscriptChunk>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val transcript: Flow<TranscriptChunk> = _transcript.asSharedFlow()

    override val isReady: Boolean get() = recognizer != null && vad != null

    override suspend fun load(): Boolean = withContext(Dispatchers.IO) {
        if (isReady) return@withContext true
        try {
            vad = Vad(
                assetManager = context.assets,
                config = VadModelConfig(
                    sileroVadModelConfig = SileroVadModelConfig(
                        model = VAD_ASSET,
                        threshold = 0.5f,
                        minSilenceDuration = MIN_SILENCE_SECONDS,
                        minSpeechDuration = 0.25f,
                        windowSize = 512,
                        maxSpeechDuration = MAX_SEGMENT_SECONDS,
                    ),
                    sampleRate = SAMPLE_RATE,
                    numThreads = 1,
                    provider = "cpu",
                ),
            )

            recognizer = OfflineRecognizer(
                assetManager = context.assets,
                config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig = OfflineModelConfig(
                        moonshine = OfflineMoonshineModelConfig(
                            preprocessor = "$MODEL_DIR/preprocess.onnx",
                            encoder = "$MODEL_DIR/encode.int8.onnx",
                            uncachedDecoder = "$MODEL_DIR/uncached_decode.int8.onnx",
                            cachedDecoder = "$MODEL_DIR/cached_decode.int8.onnx",
                        ),
                        tokens = "$MODEL_DIR/tokens.txt",
                        // Two threads keeps a segment's decode comfortably under its own
                        // duration without starving the rest of the pipeline.
                        numThreads = 2,
                        modelType = "moonshine",
                    ),
                ),
            )
            Log.i(TAG, "Transcriber loaded")
            true
        } catch (e: Exception) {
            Log.i(TAG, "Transcriber unavailable: ${e.message}")
            close()
            false
        }
    }

    /**
     * Feeds mono PCM in -1f..1f.
     *
     * Decoding happens inline on the caller's thread, which must not be the main one —
     * the listening service calls this from its capture coroutine on [Dispatchers.Default].
     */
    override fun accept(samples: FloatArray) = synchronized(nativeLock) {
        val detector = vad ?: return
        val model = recognizer ?: return

        detector.acceptWaveform(samples)
        samplesConsumed += samples.size

        while (!detector.empty()) {
            val segment = detector.front()
            decodeSegment(model, segment.samples, segment.start.toLong())
            detector.pop()
        }
    }

    override suspend fun finish() = withContext(Dispatchers.Default) {
        synchronized(nativeLock) {
            val detector = vad ?: return@synchronized
            val model = recognizer ?: return@synchronized
            // Flush pushes trailing speech out of the VAD even without a closing pause,
            // so a session ending mid-sentence still transcribes what was said.
            detector.flush()
            while (!detector.empty()) {
                val segment = detector.front()
                decodeSegment(model, segment.samples, segment.start.toLong())
                detector.pop()
            }
        }
    }

    private fun decodeSegment(model: OfflineRecognizer, samples: FloatArray, startSample: Long) {
        if (samples.isEmpty()) return
        try {
            val stream = model.createStream()
            stream.acceptWaveform(samples, SAMPLE_RATE)
            model.decode(stream)
            val text = model.getResult(stream).text.trim()
            stream.release()

            if (text.isEmpty()) return
            val startMillis = startSample * 1000 / SAMPLE_RATE
            val endMillis = startMillis + samples.size * 1000L / SAMPLE_RATE
            _transcript.tryEmit(
                TranscriptChunk(
                    text = text,
                    // Offline decoding never revises: a segment is final when emitted.
                    isFinal = true,
                    startMillis = startMillis,
                    endMillis = endMillis,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Decode failed", e)
        }
    }

    override fun close() = synchronized(nativeLock) {
        recognizer?.release()
        recognizer = null
        vad?.release()
        vad = null
        samplesConsumed = 0
    }

    private companion object {
        const val MODEL_DIR = "sherpa-onnx-moonshine-tiny-en-int8"
        const val VAD_ASSET = "silero_vad.onnx"

        /** Pause that ends an utterance. Short enough to feel live, long enough to not
         *  split a sentence at a comma. */
        const val MIN_SILENCE_SECONDS = 0.5f

        /** Hard cap so a monologue still produces text instead of buffering forever. */
        const val MAX_SEGMENT_SECONDS = 12f
    }
}
