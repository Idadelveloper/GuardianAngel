package com.example.guardianangel.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "WakeWordEngine"

/** What the engine emits when it decides something happened. */
sealed interface WakeWordEvent {
    /** The enrolled phrase was heard. [confidence] is cosine similarity, 0f..1f. */
    data class Detected(val confidence: Float) : WakeWordEvent

    /** Capture could not start or died — the UI must stop claiming it is listening. */
    data class Failed(val reason: String) : WakeWordEvent
}

/**
 * Continuous wake-word listening.
 *
 * Captures 16 kHz mono, slides a window over the stream, embeds each window and compares
 * it to the enrolled template. Three details matter more than they look:
 *
 *  - **Overlapping windows.** A phrase that straddles two non-overlapping windows is
 *    split across both and matches neither. The hop is a quarter of the window, so any
 *    utterance lands cleanly inside at least one evaluation.
 *  - **A refractory period.** Without it a single spoken phrase fires on every
 *    overlapping window that contains it, and the user gets four recordings instead of
 *    one.
 *  - **Nothing is retained.** The ring buffer is the only audio that exists, it is
 *    overwritten continuously, and it never reaches disk. Until the wake word fires,
 *    there is no recording — which is the promise the onboarding screens make.
 */
class WakeWordEngine(
    private val spotter: KeywordSpotter,
    private val scope: CoroutineScope,
) {
    private val _events = MutableSharedFlow<WakeWordEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<WakeWordEvent> = _events.asSharedFlow()

    private var job: Job? = null

    /** Template to match against, set from the enrolled wake word. */
    @Volatile
    var template: KeywordTemplate? = null

    /**
     * Similarity above which a window counts as a match.
     *
     * Exposed rather than hard-coded because the right value depends on the model and on
     * how tightly the user's enrolment takes agreed — a loose enrolment needs a lower bar
     * to ever fire, and a lower bar is only safe because a false wake just starts a
     * recording the user can cancel with one tap.
     */
    @Volatile
    var threshold: Float = DEFAULT_THRESHOLD

    val isRunning: Boolean get() = job?.isActive == true

    /** Starts capture. Caller must already hold `RECORD_AUDIO`. */
    fun start() {
        if (isRunning) return
        job = scope.launch(Dispatchers.Default) { captureLoop() }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    @SuppressLint("MissingPermission") // The service checks before constructing us.
    private suspend fun captureLoop() = withContext(Dispatchers.Default) {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            _events.emit(WakeWordEvent.Failed("This device cannot capture 16 kHz mono audio"))
            return@withContext
        }

        val recorder = try {
            AudioRecord(
                // VOICE_RECOGNITION applies the platform's noise suppression and skips
                // AGC tuned for music, which is what a keyword spotter wants.
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, spotter.windowSamples * 2 * 2),
            )
        } catch (e: SecurityException) {
            _events.emit(WakeWordEvent.Failed("Microphone permission was revoked"))
            return@withContext
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            _events.emit(WakeWordEvent.Failed("Microphone is unavailable — another app may be using it"))
            return@withContext
        }

        val window = FloatArray(spotter.windowSamples)
        val ring = RingBuffer(spotter.windowSamples)
        val chunk = ShortArray(spotter.hopSamples)
        var samplesSinceEvaluation = 0
        var refractorySamples = 0

        try {
            recorder.startRecording()
            Log.i(TAG, "Listening at $SAMPLE_RATE Hz")

            while (isActive) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read <= 0) {
                    if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_DEAD_OBJECT) {
                        _events.emit(WakeWordEvent.Failed("Microphone stream ended unexpectedly"))
                        break
                    }
                    continue
                }

                for (i in 0 until read) {
                    ring.write(chunk[i] / Short.MAX_VALUE.toFloat())
                }

                if (refractorySamples > 0) {
                    refractorySamples -= read
                    continue
                }

                samplesSinceEvaluation += read
                if (samplesSinceEvaluation < spotter.hopSamples || !ring.isFull) continue
                samplesSinceEvaluation = 0

                val reference = template ?: continue
                ring.readInto(window)
                val embedding = spotter.embed(window) ?: continue
                val confidence = cosineSimilarity(embedding, reference.embedding)

                if (confidence >= threshold) {
                    Log.i(TAG, "Wake word matched at %.2f".format(confidence))
                    _events.emit(WakeWordEvent.Detected(confidence))
                    refractorySamples = SAMPLE_RATE * REFRACTORY_SECONDS
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Capture loop failed", e)
            _events.emit(WakeWordEvent.Failed(e.message ?: "Listening stopped unexpectedly"))
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            Log.i(TAG, "Stopped listening")
        }
    }

    private companion object {
        /**
         * Tuned for a safety app rather than a smart speaker: missing a real call for
         * help costs far more than starting a recording the user cancels, so the bar
         * sits lower than the ~0.9 a voice assistant would use.
         */
        const val DEFAULT_THRESHOLD = 0.72f

        /** Seconds to ignore further matches after one fires. */
        const val REFRACTORY_SECONDS = 2
    }
}

/**
 * Fixed-size circular buffer of the most recent samples.
 *
 * Deliberately the only place audio lives while Angel is on standby — it holds about a
 * second and a half and is overwritten continuously, so there is no growing buffer that
 * could be dumped if the process were compromised.
 */
internal class RingBuffer(private val capacity: Int) {
    private val data = FloatArray(capacity)
    private var writeIndex = 0
    private var filled = 0

    val isFull: Boolean get() = filled >= capacity

    fun write(sample: Float) {
        data[writeIndex] = sample
        writeIndex = (writeIndex + 1) % capacity
        if (filled < capacity) filled++
    }

    /** Copies the window into [out] oldest-first. */
    fun readInto(out: FloatArray) {
        require(out.size == capacity)
        val start = writeIndex
        for (i in 0 until capacity) {
            out[i] = data[(start + i) % capacity]
        }
    }
}
