package com.example.guardianangel.service

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.guardianangel.audio.AudioEvent
import com.example.guardianangel.audio.HeuristicThreatAssessor
import com.example.guardianangel.audio.SAMPLE_RATE
import com.example.guardianangel.audio.SherpaSpeakerIdentifier
import com.example.guardianangel.audio.SherpaTranscriber
import com.example.guardianangel.audio.SherpaWakeWordDetector
import com.example.guardianangel.audio.SituationSnapshot
import com.example.guardianangel.audio.ThreatAssessment
import com.example.guardianangel.audio.TranscriptChunk
import com.example.guardianangel.audio.YamnetAudioTagger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

private const val TAG = "GuardianAudio"

/** What the session is doing, so the service and UI agree. */
enum class SessionPhase { Idle, Waiting, Recording }

/** Everything the session surfaces while running. */
data class SessionState(
    val phase: SessionPhase = SessionPhase.Idle,
    val transcript: List<TranscriptChunk> = emptyList(),
    val events: List<AudioEvent> = emptyList(),
    val assessment: ThreatAssessment? = null,
    val triggeredBy: String? = null,
)

/**
 * The single audio loop behind hands-free activation.
 *
 * One `AudioRecord` serves every tier, because opening a second one would fail — Android
 * gives the microphone to one capture at a time — and because sharing the stream is what
 * makes the cascade cheap. Each buffer is routed by phase:
 *
 * ```
 *  Waiting    → wake-word spotter only            (one 5 MB model, nothing retained)
 *  Recording  → transcriber + tagger + speaker ID (the expensive tiers)
 * ```
 *
 * The transition between them is the whole feature: a detection flips the phase, and
 * from the next buffer onward the same audio starts being understood instead of merely
 * matched.
 */
class GuardianAudioSession(
    private val context: Context,
    private val scope: CoroutineScope,
    /** Called when the wake word fires, before recording starts. */
    private val onWakeWord: (String) -> Unit,
    /** Called whenever the reasoning tier raises its verdict. */
    private val onAssessment: (ThreatAssessment) -> Unit,
) {
    private val detector = SherpaWakeWordDetector(context)
    private val transcriber = SherpaTranscriber(context)
    private val tagger = YamnetAudioTagger(context)
    private val speakers = SherpaSpeakerIdentifier(context)
    private val assessor = HeuristicThreatAssessor()

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private var job: Job? = null
    private var recordingStartedAt = 0L

    /** Loads the models. Returns false when the wake-word model is missing. */
    suspend fun prepare(wakePhrase: String): Boolean {
        val detectorReady = detector.load()
        if (detectorReady && !detector.setWakePhrase(wakePhrase)) {
            Log.w(TAG, "Wake phrase \"$wakePhrase\" is not expressible in the model vocabulary")
        }
        // The understanding tiers are optional: missing them degrades the recording to
        // audio-only rather than preventing hands-free activation altogether.
        transcriber.load()
        tagger.load()
        speakers.load()
        return detectorReady
    }

    fun start() {
        if (job?.isActive == true) return
        _state.value = SessionState(phase = SessionPhase.Waiting)
        job = scope.launch(Dispatchers.Default) { captureLoop() }
        scope.launch {
            transcriber.transcript.collect { chunk -> onTranscript(chunk) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        scope.launch { transcriber.finish() }
        _state.value = SessionState(phase = SessionPhase.Idle)
    }

    /** Starts recording without a wake word — the manual SOS path. */
    fun forceRecording(reason: String) {
        beginRecording(reason)
    }

    fun release() {
        stop()
        detector.close()
        transcriber.close()
        tagger.close()
        speakers.close()
    }

    @SuppressLint("MissingPermission") // The service checks before constructing us.
    private suspend fun captureLoop() {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            Log.e(TAG, "Device cannot capture 16 kHz mono")
            return
        }

        val recorder = try {
            AudioRecord(
                // VOICE_RECOGNITION gets the platform's noise suppression without the
                // music-tuned AGC that would flatten a shout.
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, BUFFER_SAMPLES * 4),
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission revoked", e)
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            Log.e(TAG, "Microphone unavailable — another app may hold it")
            return
        }

        val shorts = ShortArray(BUFFER_SAMPLES)
        val floats = FloatArray(BUFFER_SAMPLES)
        // YAMNet wants a fixed 0.975 s frame, so buffers accumulate until one is full.
        val taggerWindow = FloatArray(tagger.windowSamples)
        var taggerFilled = 0

        try {
            recorder.startRecording()
            Log.i(TAG, "Capture started")

            while (coroutineContextActive()) {
                val read = recorder.read(shorts, 0, shorts.size)
                if (read <= 0) continue
                for (i in 0 until read) floats[i] = shorts[i] / Short.MAX_VALUE.toFloat()
                val buffer = if (read == floats.size) floats else floats.copyOf(read)

                when (_state.value.phase) {
                    SessionPhase.Waiting -> {
                        detector.accept(buffer)?.let { keyword ->
                            Log.i(TAG, "Wake word detected: $keyword")
                            onWakeWord(keyword)
                            beginRecording(keyword)
                        }
                    }

                    SessionPhase.Recording -> {
                        transcriber.accept(buffer)

                        taggerFilled = fillTaggerWindow(taggerWindow, taggerFilled, buffer)
                        if (taggerFilled >= taggerWindow.size) {
                            val events = tagger.tag(taggerWindow, elapsedMillis())
                            if (events.isNotEmpty()) onEvents(events, buffer)
                            taggerFilled = 0
                        }
                    }

                    SessionPhase.Idle -> Unit
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Capture loop failed", e)
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            Log.i(TAG, "Capture stopped")
        }
    }

    /** Slides [buffer] into the fixed tagger window, returning the new fill level. */
    private fun fillTaggerWindow(window: FloatArray, filled: Int, buffer: FloatArray): Int {
        val room = window.size - filled
        val take = minOf(room, buffer.size)
        buffer.copyInto(window, filled, 0, take)
        return filled + take
    }

    private fun beginRecording(trigger: String) {
        recordingStartedAt = System.currentTimeMillis()
        speakers.resetSession()
        _state.value = _state.value.copy(
            phase = SessionPhase.Recording,
            triggeredBy = trigger,
            transcript = emptyList(),
            events = emptyList(),
        )
    }

    private fun onTranscript(chunk: TranscriptChunk) {
        _state.value = _state.value.copy(
            transcript = (_state.value.transcript + chunk).takeLast(TRANSCRIPT_WINDOW),
        )
        reassess(peakDecibels = null)
    }

    private fun onEvents(events: List<AudioEvent>, buffer: FloatArray) {
        _state.value = _state.value.copy(
            events = (_state.value.events + events).takeLast(EVENT_WINDOW),
        )
        reassess(peakDecibels = estimateDecibels(buffer))
    }

    /**
     * Runs the cheap reasoning tier.
     *
     * Deliberately on every new signal: it costs microseconds, and the whole point of
     * the cascade is that something looks at *all* the evidence while the expensive
     * tiers look at a little of it.
     */
    private fun reassess(peakDecibels: Int?) {
        scope.launch {
            val current = _state.value
            val assessment = assessor.assess(
                SituationSnapshot(
                    transcript = current.transcript,
                    events = current.events,
                    speakerCount = speakers.speakerCount.coerceAtLeast(1),
                    unknownVoicePresent = speakers.hasUnknownVoice(),
                    peakDecibels = peakDecibels,
                    // Wired to the live score once the location tier exists; until then
                    // a neutral value keeps context from inventing danger.
                    locationScore = NEUTRAL_LOCATION_SCORE,
                    hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                )
            )
            _state.value = _state.value.copy(assessment = assessment)
            onAssessment(assessment)
        }
    }

    private fun elapsedMillis(): Long =
        if (recordingStartedAt == 0L) 0 else System.currentTimeMillis() - recordingStartedAt

    private fun coroutineContextActive(): Boolean = job?.isActive != false && scope.isActive

    private companion object {
        /** 100 ms at 16 kHz — small enough to keep wake-word latency low. */
        const val BUFFER_SAMPLES = SAMPLE_RATE / 10
        const val TRANSCRIPT_WINDOW = 20
        const val EVENT_WINDOW = 12
        const val NEUTRAL_LOCATION_SCORE = 85

        /** Rough dB SPL estimate from RMS. Relative, not calibrated. */
        fun estimateDecibels(buffer: FloatArray): Int {
            if (buffer.isEmpty()) return 0
            var sum = 0.0
            for (sample in buffer) sum += (sample * sample).toDouble()
            val rms = sqrt(sum / buffer.size)
            if (rms <= 1e-7) return 0
            // 94 dB is the conventional reference for full-scale on a phone mic.
            return (94 + 20 * log10(rms)).toInt().coerceIn(0, 130)
        }
    }
}
