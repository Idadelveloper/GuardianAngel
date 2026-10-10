package com.example.guardianangel.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.example.guardianangel.MainActivity
import com.example.guardianangel.R
import com.example.guardianangel.appContainer
import com.example.guardianangel.di.AppContainer
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.repository.SessionRecorder
import com.example.guardianangel.domain.codeword.CodewordGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps Angel listening for the wake word while the phone is pocketed and locked.
 *
 * ## Why a foreground service, and what Android actually allows
 *
 * This is the part of hands-free activation that platform rules dictate rather than
 * product preference, so it is worth stating plainly:
 *
 *  - `RECORD_AUDIO` is a *while-in-use* permission. A service that wants the microphone
 *    in the background must be a foreground service typed `microphone`, declaring
 *    `FOREGROUND_SERVICE_MICROPHONE` (required from Android 14).
 *  - **It cannot be started from the background.** Android throws
 *    `ForegroundServiceStartNotAllowedException` if you try, and there is no exemption a
 *    normal app can rely on — not on boot, not from a broadcast. So Angel cannot arm
 *    herself; the user must arm her from inside the app.
 *  - Once started legally from a visible activity, it *does* keep capturing with the app
 *    backgrounded and the screen off. That is precisely what the `microphone` service
 *    type exists for, and it is what makes the real scenario work: arm before setting
 *    off, then never touch the phone again.
 *  - True always-on hotword with the app closed needs `AlwaysOnHotwordDetector` and the
 *    SoundTrigger HAL, which are reserved for the device's default assistant. Not
 *    available to us, and the UI says so rather than implying otherwise.
 *
 * The persistent notification is not a formality either — Android requires it, and for an
 * app that is listening to a user's surroundings it is the right thing to show anyway.
 */
class GuardianListeningService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Teardown runs here, not in [scope].
     *
     * Releasing the session has to finish flushing the transcript before it frees the
     * recogniser, and it cannot do that on a scope that is itself being cancelled. Kept
     * separate so shutdown can complete even as the session's work is stopped.
     */
    private val teardownScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var session: GuardianAudioSession? = null

    /**
     * True when this service was started to record straight away.
     *
     * The manual button cannot simply call into a running session, because usually there
     * isn't one — the point of the button is to work when Angel is not armed. So the
     * intent carries the intent, and recording begins as soon as the models are up.
     */
    private var startRecordingWhenReady = false

    /**
     * Decides whether a spoken codeword acts. See [CodewordGate] for the rules.
     *
     * Lives on the service rather than inside the audio session because its state has to
     * outlive a single phase change: a Danger word held back at the end of one recording
     * must still be cancellable.
     */
    private val codewordGate = CodewordGate()

    /** Writes the live session to storage. Resolved lazily: the container outlives us. */
    private val recorder: SessionRecorder get() = appContainer.sessionRecorder

    /** The single path an alert takes, shared with the SOS button. */
    private val alerts get() = appContainer.alertDispatcher

    /** Writes the title and summary once the recording is closed. */
    private val summaryAgent get() = appContainer.summaryAgent

    /** Wall-clock of the last thing worth noticing, for the inactivity timeout. */
    @Volatile
    private var lastSignificantEvent = 0L

    private var watchdog: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListening()
                return START_NOT_STICKY
            }

            ACTION_RECORD -> {
                // Already listening: flip the live session over rather than rebuilding it.
                val live = session
                if (live != null) {
                    live.forceRecording(MANUAL_TRIGGER)
                    updateNotification(recording = true)
                } else {
                    startRecordingWhenReady = true
                    startListening()
                }
                scope.launch {
                    appContainer.guardianRepository.startRecording(trigger = null)
                }
            }

            else -> startListening()
        }
        // START_STICKY would have Android restart us after a kill — but the restart
        // happens with the app in the background, where a microphone service is not
        // allowed to start. Better to stay down and let the user re-arm knowingly than
        // to show a listening notification over a service with no microphone.
        return START_NOT_STICKY
    }

    private fun startListening() {
        if (session != null) return
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            },
        )
        _state.value = true
        beginSession()
    }

    /**
     * Builds the audio session and starts listening.
     *
     * Everything is read from the process-wide container rather than passed in, because
     * a service started from a notification action has no activity to hand it anything.
     */
    private fun beginSession() {
        val container = appContainer
        val listening = container.listeningRepository
        val guardian = container.guardianRepository
        val voiceProfiles = container.voiceProfileRepository

        scope.launch {
            val wakeWord = listening.observeWakeWord().first()

            // Loaded here, not in the session, so the audio layer never has to know how
            // the voiceprint is stored or decrypted.
            val profile = runCatching { voiceProfiles.observe().first() }.getOrNull()
            val voiceprint = runCatching { voiceProfiles.load() }.getOrNull()

            // The switch is a request, not the decision. Gating on a voiceprint built
            // from takes that disagreed would not keep a stranger out — it would stop
            // Angel waking for the person she belongs to, which is the one failure this
            // feature must never have. So the gate engages only when the voiceprint is
            // good enough to trust, and the UI says when it is not.
            val gateOnVoice = wakeWord.requireVoiceMatch &&
                voiceprint != null &&
                profile?.isUsable == true
            if (wakeWord.requireVoiceMatch && !gateOnVoice) {
                Log.i(
                    TAG,
                    "Voice matching is on but the voiceprint is not usable " +
                        "(clarity=${profile?.clarityPercent}, samples=${profile?.sampleCount}); " +
                        "waking for any voice",
                )
            }

            val orchestrator = container.angelOrchestrator
            orchestrator.setHyperAware(true)
            val codewordsRepo = container.codewordRepository
            val locationTracker = container.locationTracker

            // Stream live device location to orchestrator if permission granted
            locationTracker?.let { tracker ->
                scope.launch {
                    tracker.observeLocation().collect { point ->
                        val address = tracker.reverseGeocode(point)
                        orchestrator.onLocationUpdate(point, address)
                        // Breadcrumbed into the session so the exported record can say
                        // where each part of the conversation happened.
                        recordBreadcrumb(point, address, orchestrator)
                    }
                }

                // A heartbeat, because the location stream only fires after five metres
                // of movement. Someone standing still — stopped by a stranger, held
                // somewhere, sitting in a car that is not moving yet — produced no
                // breadcrumbs at all, so the trail ended at the last place she walked
                // and the record could not show that she stayed there for eleven
                // minutes. That gap is the shape of the incident.
                scope.launch {
                    while (true) {
                        delay(BREADCRUMB_HEARTBEAT_MILLIS)
                        if (System.currentTimeMillis() - lastBreadcrumbAt < BREADCRUMB_HEARTBEAT_MILLIS) {
                            continue
                        }
                        val point = runCatching { tracker.getCurrentLocation() }.getOrNull()
                            ?: continue
                        recordBreadcrumb(
                            point = point,
                            address = runCatching { tracker.reverseGeocode(point) }.getOrNull(),
                            orchestrator = orchestrator,
                        )
                    }
                }
            }

            // Continuously reflect multi-agent factor scores back to GuardianRepository
            scope.launch {
                orchestrator.state.collect { angelState ->
                    val factors = angelState.factors
                    val safetyScoreModel = com.example.guardianangel.domain.model.SafetyScore(
                        score = factors.compositeSafetyScore,
                        deltaPercent = 0,
                        rationale = angelState.lastAssessmentSummary,
                        factors = listOf(
                            com.example.guardianangel.domain.model.SafetyFactor("f-crime", "US Crime Index", "${factors.crimeScore}%", if (factors.crimeScore >= 80) com.example.guardianangel.domain.model.FactorDirection.Raises else com.example.guardianangel.domain.model.FactorDirection.Lowers),
                            com.example.guardianangel.domain.model.SafetyFactor("f-weather", "Time & Lighting", "${factors.weatherTimeScore}%", if (factors.weatherTimeScore >= 80) com.example.guardianangel.domain.model.FactorDirection.Raises else com.example.guardianangel.domain.model.FactorDirection.Lowers),
                            com.example.guardianangel.domain.model.SafetyFactor("f-crowd", "Pedestrian / Open Venues", "${factors.crowdScore}%", if (factors.crowdScore >= 80) com.example.guardianangel.domain.model.FactorDirection.Raises else com.example.guardianangel.domain.model.FactorDirection.Lowers),
                            com.example.guardianangel.domain.model.SafetyFactor("f-voice", "Voice & Tone Match", "${factors.voiceToneScore}%", if (factors.voiceToneScore >= 80) com.example.guardianangel.domain.model.FactorDirection.Raises else com.example.guardianangel.domain.model.FactorDirection.Lowers),
                            com.example.guardianangel.domain.model.SafetyFactor("f-acoustic", "Acoustic Threat Tag", "${factors.acousticScore}%", if (factors.acousticScore >= 80) com.example.guardianangel.domain.model.FactorDirection.Raises else com.example.guardianangel.domain.model.FactorDirection.Lowers),
                            com.example.guardianangel.domain.model.SafetyFactor("f-verbal", "Verbal / Codeword Risk", "${factors.verbalScore}%", if (factors.verbalScore >= 80) com.example.guardianangel.domain.model.FactorDirection.Raises else com.example.guardianangel.domain.model.FactorDirection.Lowers),
                        ),
                        isLiveScanning = angelState.isHyperAware,
                    )
                    guardian.updateSafetyScore(safetyScoreModel)
                    // Kept on the session so a finished recording can be ranked by how
                    // bad it got, which is what the Activity list and insights sort on.
                    recorder.noteSafetyScore(factors.compositeSafetyScore)
                }
            }

            val audio = GuardianAudioSession(
                context = applicationContext,
                scope = scope,
                onWakeWord = { keyword ->
                    scope.launch {
                        // A wake word only ever *starts* a recording. What happens next
                        // is decided by the codewords said during it, or by the
                        // reasoning tier — never by the wake word itself.
                        //
                        // Identical to what the record button does, which is the point:
                        // saying the phrase and tapping the button have to produce the
                        // same session, written the same way.
                        listening.onWakeWordDetected()
                        recorder.begin(trigger = null, triggerLabel = keyword)
                        guardian.startRecording(trigger = null)
                        noteActivity()
                        updateNotification(recording = true)
                    }
                },
                onAssessment = { assessment ->
                    if (!assessment.recommendEscalation) return@GuardianAudioSession
                    scope.launch {
                        // The cheap tier only ever escalates to Danger. Emergency stays
                        // reserved for the user's own emergency codeword: calling the
                        // police is not a call a heuristic should make unprompted.
                        alerts.dispatch(CodewordTier.Danger)
                    }
                },
                onTranscriptDecoded = { chunk, _, peakDb ->
                    scope.launch {
                        noteActivity()
                        val codewords = runCatching {
                            codewordsRepo.observeCodewords().first()
                        }.getOrDefault(emptyList())

                        // The orchestrator still scores the situation; it no longer fires
                        // codeword actions. Those run through the gate below, which knows
                        // about repeats, speakers and accidents.
                        orchestrator.onTranscriptReceived(chunk, guardian, codewords)

                        val flagged = orchestrator.state.value.factors.verbalScore < FLAG_SCORE
                        persistLine(chunk, peakDb, flagged)

                        guardian.updateTranscript(
                            com.example.guardianangel.domain.model.TranscriptLine(
                                speakerLabel = speakerLabel(chunk.isEnrolledUser),
                                text = chunk.text,
                                atEpochMillis = System.currentTimeMillis(),
                                isFlagged = flagged,
                            )
                        )

                        handleCodeword(
                            decision = codewordGate.evaluate(
                                text = chunk.text,
                                isEnrolledUser = chunk.isEnrolledUser,
                                codewords = codewords,
                            ),
                            guardian = guardian,
                        )
                    }
                },
                onEventsDetected = { events, peakDb ->
                    orchestrator.onAudioEvents(events, peakDb)
                    scope.launch {
                        // A scream or breaking glass is activity, so the inactivity
                        // timeout must not fire during an incident that has no speech.
                        if (events.any { it.isDangerSignal }) noteActivity()
                        events.forEach { event ->
                            recorder.addAudioEvent(
                                label = event.label,
                                decibels = peakDb,
                                confidence = event.confidence,
                                atMillis = System.currentTimeMillis(),
                                isDanger = event.isDangerSignal,
                            )
                        }
                    }
                },
            )
            session = audio

            val effectivePhrase = wakeWord.phrase.ifBlank { "hey angel" }
            val ready = audio.prepare(
                wakePhrase = effectivePhrase,
                voiceprint = voiceprint,
                requireVoiceMatch = gateOnVoice,
                // Her Settings choice, which until now reached nothing.
                sensitivity = wakeWord.sensitivity,
            )
            listening.setDetectorReady(ready)

            // A manual recording does not need the keyword model — it needs the
            // microphone. Refusing to record because hands-free is unavailable would
            // take away the fallback exactly when it is the only thing left.
            if (!ready && !startRecordingWhenReady) {
                Log.w(TAG, "No wake-word model; listening cannot start")
                stopListening()
                return@launch
            }

            audio.start()
            startWatchdog(guardian)
            if (startRecordingWhenReady) {
                startRecordingWhenReady = false
                audio.forceRecording(MANUAL_TRIGGER)
                recorder.begin(trigger = null, triggerLabel = MANUAL_TRIGGER)
                guardian.startRecording(trigger = null)
                updateNotification(recording = true)
            }
        }
    }

    /** When a point was last written, so the heartbeat only fills real gaps. */
    private var lastBreadcrumbAt = 0L

    private suspend fun recordBreadcrumb(
        point: com.example.guardianangel.domain.model.GeoPoint,
        address: String?,
        orchestrator: com.example.guardianangel.agent.AngelAgentOrchestrator,
    ) {
        lastBreadcrumbAt = System.currentTimeMillis()
        recorder.addLocation(
            latitude = point.latitude,
            longitude = point.longitude,
            atMillis = lastBreadcrumbAt,
            label = address,
            safetyScore = orchestrator.state.value.factors.compositeSafetyScore,
        )
    }

    /** Marks activity, which keeps the inactivity timeout from firing. */
    private fun noteActivity() {
        lastSignificantEvent = System.currentTimeMillis()
    }

    private fun speakerLabel(isEnrolledUser: Boolean?): String = when (isEnrolledUser) {
        true -> "You"
        false -> "Unfamiliar voice"
        // Honest about not knowing. Calling an unverifiable voice "unfamiliar" would put
        // a stranger's label on the user's own words whenever enrolment was skipped.
        null -> "Speaker"
    }

    private suspend fun persistLine(
        chunk: com.example.guardianangel.audio.TranscriptChunk,
        peakDb: Int?,
        flagged: Boolean,
    ) {
        recorder.addTranscript(
            com.example.guardianangel.domain.model.DiarizedEntry(
                id = chunk.speakerTag ?: "line",
                sessionId = recorder.activeSessionId.orEmpty(),
                atEpochMillis = System.currentTimeMillis(),
                speakerKind = when (chunk.isEnrolledUser) {
                    true -> com.example.guardianangel.domain.model.SpeakerKind.You
                    false -> com.example.guardianangel.domain.model.SpeakerKind.Unknown
                    null -> com.example.guardianangel.domain.model.SpeakerKind.KnownPattern
                },
                speakerLabel = speakerLabel(chunk.isEnrolledUser),
                speakerQualifier = when (chunk.isEnrolledUser) {
                    true -> "Voiceprint verified"
                    false -> "Not your voiceprint"
                    null -> null
                },
                text = chunk.text,
                decibels = peakDb,
                isFlagged = flagged,
            )
        )
    }

    /**
     * Acts on the gate's verdict.
     *
     * Note what is *not* here: no branch dispatches an alert straight from a transcript
     * match. Danger and Emergency are held by the gate and dispatched by [watchdog] once
     * their grace period passes, so an accident can be taken back.
     */
    private suspend fun handleCodeword(
        decision: CodewordGate.Decision,
        guardian: com.example.guardianangel.domain.repository.GuardianRepository,
    ) {
        when (decision) {
            CodewordGate.Decision.None -> Unit

            is CodewordGate.Decision.Act -> {
                recorder.noteCodeword(decision.tier, decision.phrase, wasUserVoice = true)
                when (decision.tier) {
                    CodewordTier.Safe -> {
                        // Said with nothing pending: she is telling her circle she is
                        // fine, which ends the session rather than starting anything.
                        alerts.dispatch(CodewordTier.Safe)
                        stopListening()
                    }
                    CodewordTier.Caution -> {
                        Log.i(TAG, "Caution codeword: recording continues")
                        alerts.dispatch(CodewordTier.Caution)
                    }
                    else -> alerts.dispatch(decision.tier)
                }
            }

            is CodewordGate.Decision.Holding -> {
                recorder.noteCodeword(
                    decision.pending.tier,
                    decision.pending.phrase,
                    wasUserVoice = decision.pending.saidByEnrolledUser,
                )
                Log.i(
                    TAG,
                    "${decision.pending.tier} held for " +
                        "${decision.pending.dispatchAtMillis - System.currentTimeMillis()}ms",
                )
                updateNotification(recording = true)
            }

            is CodewordGate.Decision.Cancelled -> {
                Log.i(TAG, "${decision.tier} cancelled by the safe word")
                // The all-clear goes to whoever was alerted. People who were woken are
                // owed the follow-up more than they were owed the original.
                alerts.dispatch(CodewordTier.Safe)
            }

            is CodewordGate.Decision.Withheld -> {
                // Logged into the session, not acted on. Silence here would read as a
                // bug to the user and as missing evidence to anyone reading it later.
                Log.i(TAG, "${decision.tier} withheld: ${decision.reason}")
                recorder.noteCodeword(
                    decision.tier,
                    decision.phrase,
                    wasUserVoice = false,
                )
            }
        }
    }

    /**
     * Dispatches held-back codewords, and stops a session that has gone quiet.
     *
     * One loop rather than two timers: both are "check the clock every second", and a
     * single job is one thing to cancel on teardown.
     */
    private fun startWatchdog(
        guardian: com.example.guardianangel.domain.repository.GuardianRepository,
    ) {
        watchdog?.cancel()
        noteActivity()
        watchdog = scope.launch {
            while (isActive) {
                delay(WATCHDOG_TICK_MILLIS)

                codewordGate.dueForDispatch()?.let { pending ->
                    codewordGate.consumePending()
                    Log.i(TAG, "Dispatching held ${pending.tier}")
                    // The dispatcher records who was actually reached; the placeholder
                    // "circle" that used to be written here was not a guardian.
                    alerts.dispatch(pending.tier)
                    noteActivity()
                }

                val idleFor = System.currentTimeMillis() - lastSignificantEvent
                val recording = session?.state?.value?.phase == SessionPhase.Recording
                if (recording && idleFor >= IDLE_TIMEOUT_MILLIS) {
                    // A recording left running all night is a battery drain and a
                    // privacy problem, and the user who forgot it is the one least
                    // likely to notice. Nothing worth keeping has happened for half an
                    // hour, so stand down rather than keep the microphone open.
                    Log.i(TAG, "No activity for ${idleFor / 60_000} min; standing down")
                    stopListening()
                    return@launch
                }
            }
        }
    }

    /**
     * Swaps the notification text once recording begins, so the state is never hidden.
     *
     * Guarded because notifications can be revoked while the service is already running
     * — the permission is checked when arming, not held forever. Losing the update is
     * survivable; crashing the listener mid-walk is not, and the system notification
     * that the microphone is in use stays regardless.
     */
    private fun updateNotification(recording: Boolean) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(NOTIFICATION_ID, buildNotification(recording))
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission revoked while listening", e)
        }
    }

    private fun stopListening() {
        val closing = session
        session = null
        _state.value = false
        watchdog?.cancel()
        watchdog = null
        runCatching { appContainer.angelOrchestrator.setHyperAware(false) }

        // Close the session before the models go, so the summary reflects everything
        // captured. Runs on the teardown scope because `scope` is about to be cancelled.
        teardownScope.launch {
            // Captured before `finish()`, which clears it.
            val finishedId = recorder.activeSessionId
            runCatching { recorder.finish() }
            codewordGate.reset()
            // So the next session can alert again at a tier this one already used.
            runCatching { alerts.resetForNewSession() }
            // Only now, with every line written, can anything say what this was. The
            // recorder could only title it by how long it ran.
            finishedId?.let { summaryAgent.summarise(it) }
        }
        // The notification and the service go immediately — the user asked to stand down
        // and should see that happen. Freeing the models trails behind, in order.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (closing != null) {
            teardownScope.launch {
                closing.release()
                stopSelf()
            }
        } else {
            stopSelf()
        }
    }

    override fun onDestroy() {
        val closing = session
        session = null
        scope.cancel()
        _state.value = false
        // Not awaited: onDestroy must return promptly. The models are freed on the
        // teardown scope, which outlives this call.
        teardownScope.launch {
            closing?.release()
            teardownScope.cancel()
        }
        super.onDestroy()
    }

    private fun buildNotification(recording: Boolean = false): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, GuardianListeningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(
                getString(
                    if (recording) R.string.recording_notification_title
                    else R.string.listening_notification_title
                )
            )
            .setContentText(
                getString(
                    if (recording) R.string.recording_notification_body
                    else R.string.listening_notification_body
                )
            )
            .setContentIntent(openApp)
            // Standing down must be reachable without unlocking and hunting for the app.
            .addAction(0, getString(R.string.listening_notification_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Creates the channel through the compat API.
     *
     * `NotificationChannel` itself is API 26; `minSdk` here is 24, so the platform type
     * must never be touched directly — the compat builder no-ops below 26 instead of
     * throwing on launch.
     */
    private fun createChannel() {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            // Low: present and hard to dismiss, but silent. A safety app that chimes
            // every time it arms would defeat the point of being discreet.
            NotificationManagerCompat.IMPORTANCE_LOW,
        )
            .setName(getString(R.string.listening_channel_name))
            .setDescription(getString(R.string.listening_channel_description))
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ListeningService"
        private const val CHANNEL_ID = "guardian_listening"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.example.guardianangel.STOP_LISTENING"

        /** Verbal-risk score below which a line is marked as a danger signal. */
        private const val FLAG_SCORE = 70

        private const val WATCHDOG_TICK_MILLIS = 1_000L

        /**
         * How long a recording runs with nothing happening before standing down.
         *
         * Thirty minutes: long enough that a quiet walk is not cut short, short enough
         * that a recording forgotten in a bag does not run until the battery dies. Speech
         * and danger sounds both reset it, so an incident cannot time out mid-way.
         */
        private const val IDLE_TIMEOUT_MILLIS = 30 * 60 * 1000L

        /**
         * How often a point is written when the user is not moving.
         *
         * A minute: frequent enough that a stop is visible on the trail, sparse enough
         * that a half-hour recording adds thirty rows rather than nine hundred.
         */
        private const val BREADCRUMB_HEARTBEAT_MILLIS = 60_000L
        private const val ACTION_RECORD = "com.example.guardianangel.START_RECORDING"

        /** Marks a session the user started by hand rather than by speaking. */
        const val MANUAL_TRIGGER = "manual"

        private val _state = MutableStateFlow(false)

        /** Whether the listening service is currently running. */
        val isListening: StateFlow<Boolean> = _state.asStateFlow()

        /**
         * Arms listening.
         *
         * **Must be called while an activity is visible.** See the class documentation:
         * Android rejects a background start of a microphone service.
         */
        fun start(context: Context) {
            val intent = Intent(context, GuardianListeningService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Starts recording now, arming first if necessary.
         *
         * The manual path, and the answer to "what if the wake word doesn't hear me".
         * Needs only `RECORD_AUDIO`: without notification permission the required
         * notification is suppressed by the system, but the service still runs and the
         * platform still shows its own microphone indicator.
         *
         * **Must be called while an activity is visible**, like [start].
         */
        fun record(context: Context) {
            val intent = Intent(context, GuardianListeningService::class.java)
                .setAction(ACTION_RECORD)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, GuardianListeningService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
