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
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.example.guardianangel.MainActivity
import com.example.guardianangel.R
import com.example.guardianangel.appContainer
import com.example.guardianangel.domain.model.CodewordTier
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
    private var session: GuardianAudioSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListening()
                return START_NOT_STICKY
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

        scope.launch {
            val wakeWord = listening.observeWakeWord().first()

            val audio = GuardianAudioSession(
                context = applicationContext,
                scope = scope,
                onWakeWord = {
                    scope.launch {
                        // A wake word only ever *starts* a recording. What happens next
                        // is decided by the codewords said during it, or by the
                        // reasoning tier — never by the wake word itself.
                        listening.onWakeWordDetected()
                        guardian.startRecording(trigger = null)
                        updateNotification(recording = true)
                    }
                },
                onAssessment = { assessment ->
                    if (!assessment.recommendEscalation) return@GuardianAudioSession
                    scope.launch {
                        // The cheap tier only ever escalates to Danger. Emergency stays
                        // reserved for the user's own emergency codeword: calling the
                        // police is not a call a heuristic should make unprompted.
                        guardian.dispatchAlert(CodewordTier.Danger)
                    }
                },
            )
            session = audio

            val ready = audio.prepare(wakeWord.phrase)
            listening.setDetectorReady(ready)
            if (!ready) {
                Log.w(TAG, "No wake-word model; listening cannot start")
                stopListening()
                return@launch
            }
            audio.start()
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
        session?.release()
        session = null
        _state.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        scope.cancel()
        _state.value = false
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

        fun stop(context: Context) {
            context.startService(
                Intent(context, GuardianListeningService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
