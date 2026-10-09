package com.example.guardianangel.ui.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.guardianangel.audio.EnrolmentOutcome
import com.example.guardianangel.audio.VoiceEnroller
import com.example.guardianangel.domain.repository.ListeningRepository
import com.example.guardianangel.domain.repository.VoiceProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What an enrolment control should be showing. */
data class VoiceEnrolmentUiState(
    /** Segments of speech folded into the voiceprint, across every session. */
    val takes: Int = 0,
    val clarityPercent: Int = 0,
    val isRecording: Boolean = false,
    /** Milliseconds captured in the current recording, for a progress ring. */
    val elapsedMillis: Int = 0,
    /** Set when the last attempt failed, phrased for the user. Cleared on the next one. */
    val error: String? = null,
    /** False when this build has no speaker-embedding model, so controls can hide. */
    val isAvailable: Boolean = true,
    /** False until the stored voiceprint has been read, so the UI avoids a flash of zero. */
    val isLoaded: Boolean = false,
)

/**
 * Runs voice enrolment for the screens that offer it.
 *
 * Exists because enrolment is the one part of setup that has to touch the microphone, the
 * embedding model and two repositories at once, and none of that belongs in a composable.
 * Screens render [VoiceEnrolmentUiState] and call [recordSession] or [recordTake];
 * everything about how a voiceprint is made stays here.
 *
 * It seeds the enroller from the stored voiceprint on first use, so the minute of free
 * speech during setup and a short take from the wake-word screen add up instead of
 * overwriting each other. Each capture is saved immediately — a user who records once and
 * closes the app keeps that progress, because a partial voiceprint is still a valid one.
 * It is simply gated on clarity before anything relies on it.
 */
class VoiceEnrolmentController(
    private val context: Context,
    private val listening: ListeningRepository,
    private val voiceProfiles: VoiceProfileRepository,
    private val scope: CoroutineScope,
    private val requestMicrophone: () -> Unit,
    private val onStateChanged: (VoiceEnrolmentUiState) -> Unit,
) {
    private val enroller = VoiceEnroller(context)
    private var current = VoiceEnrolmentUiState()
    private var started = false

    /** Loads the model and restores the stored voiceprint. Safe to call repeatedly. */
    fun prepare() {
        if (started) return
        started = true
        scope.launch {
            val ready = enroller.load()
            val stored = voiceProfiles.observe().first()
            val embedding = if (ready) runCatching { voiceProfiles.load() }.getOrNull() else null
            enroller.seed(
                embedding = embedding,
                sampleCount = stored?.sampleCount ?: 0,
                clarityPercent = stored?.clarityPercent ?: 0,
            )
            update {
                it.copy(
                    isAvailable = ready,
                    isLoaded = true,
                    takes = enroller.sampleCount,
                    clarityPercent = enroller.clarityPercent,
                )
            }
        }
    }

    /** Records one short take — the wake-word screen's "say it again" button. */
    fun recordTake() = record(VoiceEnroller.TAKE_MILLIS)

    /** Records the minute of free speech used during setup. */
    fun recordSession() = record(VoiceEnroller.SESSION_MILLIS)

    /**
     * Captures [millis] of audio and folds it into the voiceprint.
     *
     * Asks for the microphone first if it is missing rather than failing: enrolment is
     * often the first thing in the app that needs it, and a user who skipped the
     * permission step should not meet a dead button.
     */
    private fun record(millis: Int) {
        if (current.isRecording) return
        if (!hasMicrophone()) {
            requestMicrophone()
            return
        }

        update { it.copy(isRecording = true, error = null, elapsedMillis = 0) }
        scope.launch {
            val outcome = enroller.capture(millis) { elapsed ->
                update { it.copy(elapsedMillis = elapsed) }
            }
            when (outcome) {
                is EnrolmentOutcome.Captured -> {
                    voiceProfiles.save(
                        embedding = outcome.voiceprint,
                        clarityPercent = outcome.clarityPercent,
                        sampleCount = outcome.sampleCount,
                    )
                    listening.addEnrolmentTake()
                    update {
                        it.copy(
                            takes = outcome.sampleCount,
                            clarityPercent = outcome.clarityPercent,
                            isRecording = false,
                        )
                    }
                }

                EnrolmentOutcome.NoSpeech -> fail(
                    "I didn't hear anything — try again and speak up a little."
                )

                EnrolmentOutcome.MicrophoneUnavailable -> fail(
                    "I couldn't reach the microphone. Another app may be using it."
                )

                EnrolmentOutcome.ModelMissing ->
                    update { it.copy(isRecording = false, isAvailable = false) }
            }
        }
    }

    /** Throws away the voiceprint and the takes behind it, so the user can start again. */
    fun reset() {
        enroller.reset()
        scope.launch {
            voiceProfiles.clear()
            listening.clearEnrolment()
            update { it.copy(takes = 0, clarityPercent = 0, error = null, elapsedMillis = 0) }
        }
    }

    fun release() = enroller.close()

    private fun fail(message: String) =
        update { it.copy(isRecording = false, error = message) }

    private fun update(transform: (VoiceEnrolmentUiState) -> VoiceEnrolmentUiState) {
        current = transform(current)
        onStateChanged(current)
    }

    private fun hasMicrophone(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}

/** A controller and the state it drives, handed to a screen as one thing. */
data class VoiceEnrolment(
    val controller: VoiceEnrolmentController,
    val state: VoiceEnrolmentUiState,
)

/**
 * Builds a controller tied to the composition, and releases its model when it leaves.
 */
@Composable
fun rememberVoiceEnrolment(
    listening: ListeningRepository,
    voiceProfiles: VoiceProfileRepository,
): VoiceEnrolment {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(VoiceEnrolmentUiState()) }

    val microphoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Nothing to do on refusal: the control stays, and the next tap asks again —
        // which Android turns into a settings prompt after the second refusal.
        if (granted) scope.launch { listening.refreshPermissions() }
    }

    val controller = remember(listening, voiceProfiles) {
        VoiceEnrolmentController(
            context = context,
            listening = listening,
            voiceProfiles = voiceProfiles,
            scope = scope,
            requestMicrophone = {
                microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onStateChanged = { state = it },
        )
    }

    DisposableEffect(controller) {
        controller.prepare()
        // The embedding model holds a native session; leaving the screen must free it.
        onDispose { controller.release() }
    }

    return VoiceEnrolment(controller, state)
}
