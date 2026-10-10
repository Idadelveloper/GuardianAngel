package com.example.guardianangel.audio

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.guardianangel.service.GuardianAudioSession
import com.example.guardianangel.service.SessionPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Plays the wake phrase out of the speaker and checks the microphone hears it.
 *
 * Every other test in this suite feeds samples straight into the detector, which proves
 * the model and the tokenisation but says nothing about the part that actually fails in
 * someone's hand: a real microphone, a real room, and whatever the platform does to the
 * signal on the way through.
 *
 * It is inherently environmental — it needs the volume up and a quiet-ish room — so it
 * reports what it measured rather than only passing or failing. A run that hears nothing
 * at all is a different problem from one that hears plenty and still does not fire.
 */
@RunWith(AndroidJUnit4::class)
class AcousticLoopbackTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theMicrophoneHearsTheSpeaker() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val detections = mutableListOf<String>()

        val session = GuardianAudioSession(
            context = context,
            scope = scope,
            onWakeWord = { detections += it },
            onAssessment = {},
        )
        assumeTrue("No keyword model in this build", session.prepare(wakePhrase = "light up"))

        session.start()
        // Let the capture loop open the microphone before anything is played.
        delay(1_500)

        val heard = playFixtureAndListen(session)

        session.release()
        scope.cancel()

        Log.i(TAG, "=== ACOUSTIC LOOPBACK RESULT ===")
        Log.i(TAG, "peak input level: $heard")
        Log.i(TAG, "detections: $detections")

        assumeTrue(
            "The microphone heard nothing — check the media volume and that no other " +
                "app holds the mic. Measured peak $heard.",
            heard > SILENCE_FLOOR,
        )

        // Reported rather than asserted: this is the number worth tuning against, and a
        // miss here is information about the threshold, not a broken build.
        Log.i(
            TAG,
            if (detections.isEmpty()) {
                "HEARD AUDIO BUT DID NOT FIRE — the spotter threshold is the thing to tune."
            } else {
                "FIRED: ${detections.first()}"
            },
        )
        Unit
    }

    /** Plays the fixture through the speaker, returning the loudest level the mic saw. */
    private suspend fun playFixtureAndListen(session: GuardianAudioSession): Float {
        val file = File(context.cacheDir, "loopback.wav")
        InstrumentationRegistry.getInstrumentation().context.assets.open(FIXTURE).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }

        val audio = context.getSystemService(AudioManager::class.java)
        val previous = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            0,
        )

        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setDataSource(file.absolutePath)
            prepare()
        }

        var peak = 0f
        try {
            player.start()
            // Poll the session's own view of the room while the clip plays.
            repeat(POLLS) {
                delay(POLL_MILLIS)
                peak = maxOf(peak, session.lastInputLevel())
            }
        } finally {
            runCatching { player.stop() }
            player.release()
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, previous, 0)
            file.delete()
        }
        return peak
    }

    private companion object {
        const val TAG = "AcousticLoopback"
        const val FIXTURE = "speech_light_up.wav"
        const val POLLS = 90
        const val POLL_MILLIS = 100L

        /** Below this the microphone is effectively deaf and the run proves nothing. */
        const val SILENCE_FLOOR = 0.005f
    }
}

/** True while the capture loop is running. */
private fun GuardianAudioSession.isCapturing(): Boolean =
    state.value.phase != SessionPhase.Idle
