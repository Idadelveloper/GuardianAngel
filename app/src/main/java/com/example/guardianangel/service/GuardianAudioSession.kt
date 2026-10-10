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
import kotlinx.coroutines.cancelAndJoin
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

/**
 * The pre-roll the speaker check runs against.
 *
 * Tied to [SherpaSpeakerIdentifier.EMBED_WINDOW_SAMPLES] rather than chosen here: an
 * embedding of a different length than the enrolled voiceprint cannot be compared to it,
 * so a pre-roll of its own size would reject the enrolled user on every wake.
 *
 * Top-level so a test can assert the two agree, which is the regression that would
 * otherwise be invisible.
 */
const val PREROLL_SAMPLES = SherpaSpeakerIdentifier.EMBED_WINDOW_SAMPLES

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
    /** Called when a live segment transcript is emitted. */
    private val onTranscriptDecoded: ((TranscriptChunk, Boolean, Int?) -> Unit)? = null,
    /** Called when audio acoustic events are tagged. */
    private val onEventsDetected: ((List<AudioEvent>, Int?) -> Unit)? = null,
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

    /** True when a detection must also match the enrolled voiceprint. */
    private var verifySpeaker = false

    /**
     * The last [PREROLL_SAMPLES] of audio, kept while waiting.
     *
     * The speaker check needs the audio that *contained* the phrase, and by the time the
     * spotter reports a detection that audio is already behind us — a keyword decoder
     * only knows it heard something once the phrase has finished. Without a pre-roll the
     * only thing left to verify against is whatever the user said next, which is usually
     * silence.
     *
     * It is a fixed-size ring that overwrites itself continuously and is never written to
     * disk, which is exactly the promise the onboarding screen makes.
     */
    private val preroll = FloatArray(PREROLL_SAMPLES)
    private var prerollWrite = 0
    private var prerollFilled = 0

    private var taggerFilled = 0

    /**
     * Loads the models. Returns false when the wake-word model is missing.
     *
     * @param voiceprint the enrolled user's embedding, or null if she has not enrolled.
     * @param requireVoiceMatch whether a detection must also match [voiceprint]. Ignored
     *   when there is no voiceprint to match against — a gate with nothing behind it
     *   would silently stop the wake word working for everyone, including her.
     */
    suspend fun prepare(
        wakePhrase: String,
        voiceprint: FloatArray? = null,
        requireVoiceMatch: Boolean = false,
    ): Boolean {
        val detectorReady = detector.load()
        if (detectorReady && !detector.setWakePhrase(wakePhrase)) {
            Log.w(TAG, "Wake phrase \"$wakePhrase\" is not expressible in the model vocabulary")
        }
        // The understanding tiers are optional: missing them degrades the recording to
        // audio-only rather than preventing hands-free activation altogether.
        transcriber.load()
        // One speaker model serves the wake-word gate, diarization and per-line
        // attribution. Loading a second 28 MB copy per tier would be the obvious
        // alternative and the wrong one.
        transcriber.attributeSpeaker = { samples -> speakers.attribute(samples) }
        tagger.load()
        val speakersReady = speakers.load()
        speakers.enrolledVoiceprint = voiceprint

        verifySpeaker = requireVoiceMatch && voiceprint != null && speakersReady
        if (requireVoiceMatch && !verifySpeaker) {
            Log.w(
                TAG,
                "Voice matching requested but unavailable " +
                    "(voiceprint=${voiceprint != null}, model=$speakersReady) — " +
                    "the wake word will fire for any voice",
            )
        }
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

    /**
     * Stops capturing and flushes whatever speech was still buffered.
     *
     * Suspends until the flush completes, rather than launching it. Launching was a bug:
     * the flush decodes on another thread while [release] frees the recogniser it is
     * decoding with, which crashed the process natively on the disarm path. Teardown has
     * to be ordered, and the only way to order it is to wait.
     */
    suspend fun stop() {
        job?.cancelAndJoin()
        job = null
        // Trailing speech still belongs in the transcript — a session that ends
        // mid-sentence should keep what was said.
        runCatching { transcriber.finish() }
            .onFailure { Log.w(TAG, "Flushing the transcript failed", it) }
        _state.value = SessionState(phase = SessionPhase.Idle)
    }

    /** Starts recording without a wake word — the manual SOS path. */
    fun forceRecording(reason: String) {
        beginRecording(reason)
    }

    /**
     * Frees every model. The session cannot be reused afterwards.
     *
     * Suspends because [stop] does: the models must not be released until the capture
     * loop has stopped and the final flush has finished using them.
     */
    suspend fun release() {
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

        try {
            recorder.startRecording()
            Log.i(TAG, "Capture started")

            while (coroutineContextActive()) {
                val read = recorder.read(shorts, 0, shorts.size)
                if (read <= 0) continue
                for (i in 0 until read) floats[i] = shorts[i] / Short.MAX_VALUE.toFloat()
                val buffer = if (read == floats.size) floats else floats.copyOf(read)

                route(buffer, taggerWindow)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Capture loop failed", e)
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            Log.i(TAG, "Capture stopped")
        }
    }

    /**
     * Routes one buffer according to the current phase.
     *
     * Split out of the capture loop so the phase transition — the whole feature — can be
     * driven from a test with recorded audio instead of a microphone. The loop does
     * nothing but read bytes and hand them here.
     */
    internal fun route(buffer: FloatArray, taggerWindow: FloatArray) {
        when (_state.value.phase) {
            SessionPhase.Waiting -> {
                // Before matching, so the phrase is in the ring by the time a detection
                // comes back.
                appendPreroll(buffer)

                detector.accept(buffer)?.let { keyword ->
                    if (acceptDetection(keyword)) {
                        Log.i(TAG, "Wake word detected: $keyword")
                        onWakeWord(keyword)
                        beginRecording(keyword)
                    }
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

                // Speaker clustering happens per transcribed segment now, inside
                // `attribute()`, which is aligned to actual speech rather than to a
                // fixed grid. Feeding windows here as well clustered the same person
                // repeatedly and inflated the "how many voices" signal the reasoning
                // tier weighs.
            }

            SessionPhase.Idle -> Unit
        }
    }

    /** Puts the session into [SessionPhase.Waiting] without opening the microphone. */
    internal fun armForTest() {
        _state.value = SessionState(phase = SessionPhase.Waiting)
    }

    /**
     * Whether a detection should start a recording.
     *
     * When voice matching is on, the phrase being right is not enough — it also has to
     * have been *her*. The check runs against the pre-roll, and a failure is dropped
     * quietly: someone else saying her wake word should look to them like nothing
     * happened at all.
     *
     * If there is not yet enough pre-roll to judge — a detection within the first second
     * of arming — the detection is allowed through. Refusing it would mean the wake word
     * does not work for a second after arming, which is the moment it is most likely to
     * be needed.
     */
    private fun acceptDetection(keyword: String): Boolean {
        if (!verifySpeaker) return true

        val recent = prerollSnapshot()
        if (recent == null) {
            Log.i(TAG, "Not enough audio to verify the speaker; accepting \"$keyword\"")
            return true
        }
        if (speakers.matchesEnrolledUser(recent)) return true

        Log.i(TAG, "Ignored \"$keyword\" — the voice did not match the enrolled user")
        return false
    }

    /** Writes [buffer] into the pre-roll ring, overwriting the oldest audio. */
    private fun appendPreroll(buffer: FloatArray) {
        for (sample in buffer) {
            preroll[prerollWrite] = sample
            prerollWrite = (prerollWrite + 1) % preroll.size
        }
        prerollFilled = (prerollFilled + buffer.size).coerceAtMost(preroll.size)
    }

    /**
     * The pre-roll in chronological order, or null until the ring has filled.
     *
     * Deliberately all-or-nothing: a partial ring would embed a shorter window, and a
     * shorter window cannot be compared to the enrolled voiceprint. Better to let the
     * detection through unverified for the first second and a half after arming — see
     * [acceptDetection] — than to reject the user on a meaningless comparison.
     */
    private fun prerollSnapshot(): FloatArray? {
        if (prerollFilled < preroll.size) return null
        // Unwrap from the write cursor, which is the oldest sample.
        val out = FloatArray(preroll.size)
        val tail = preroll.size - prerollWrite
        preroll.copyInto(out, 0, prerollWrite, preroll.size)
        preroll.copyInto(out, tail, 0, prerollWrite)
        return out
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
        prerollFilled = 0
        prerollWrite = 0
        _state.value = _state.value.copy(
            phase = SessionPhase.Recording,
            triggeredBy = trigger,
            transcript = emptyList(),
            events = emptyList(),
        )
    }

    private var latestDecibels: Int? = null

    private fun onTranscript(chunk: TranscriptChunk) {
        _state.value = _state.value.copy(
            transcript = (_state.value.transcript + chunk).takeLast(TRANSCRIPT_WINDOW),
        )
        // Per chunk, from the audio that produced it. This used to pass
        // `!speakers.hasUnknownVoice()`, a session-wide flag: once any stranger had
        // spoken, every later line of *hers* was reported as not-her, and until then a
        // stranger's words were reported as hers. Codeword actions hang off this.
        onTranscriptDecoded?.invoke(chunk, chunk.isEnrolledUser == true, latestDecibels)
        reassess(peakDecibels = latestDecibels)
    }

    private fun onEvents(events: List<AudioEvent>, buffer: FloatArray) {
        val db = estimateDecibels(buffer)
        latestDecibels = db
        _state.value = _state.value.copy(
            events = (_state.value.events + events).takeLast(EVENT_WINDOW),
        )
        onEventsDetected?.invoke(events, db)
        reassess(peakDecibels = db)
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
        /** Speaker-labelling windows, the same length for the same reason. */
        const val SPEAKER_WINDOW_SAMPLES = SherpaSpeakerIdentifier.EMBED_WINDOW_SAMPLES

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
