package com.example.guardianangel.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.guardianangel.domain.model.ListeningRequirement
import com.example.guardianangel.domain.repository.ListeningRepository
import com.example.guardianangel.audio.SherpaWakeWordDetector
import com.example.guardianangel.audio.YamnetAudioTagger
import com.example.guardianangel.service.GuardianListeningService
import kotlinx.coroutines.launch

/**
 * Bridges the listening UI to the Android permission and service APIs.
 *
 * Kept out of the screen composables because it is the only part of hands-free
 * activation that must touch the platform directly, and because the rules it encodes are
 * easy to get wrong:
 *
 *  - Permission state is owned by the system and can change while the app is backgrounded
 *    (the user revoking microphone access from Settings is a realistic thing to do), so
 *    it is re-read on every `ON_RESUME` rather than cached at first composition.
 *  - The microphone service is started from here, in response to a tap, while the
 *    activity is visible. Starting it anywhere else would throw.
 */
class HandsFreeController(
    private val context: Context,
    private val repository: ListeningRepository,
    private val requestMicrophone: () -> Unit,
    private val requestNotifications: () -> Unit,
    private val requestLocationPermission: () -> Unit,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    /**
     * Loads the wake-word model once and reports whether it is usable.
     *
     * Done here rather than lazily at arm time so the home screen can tell the user up
     * front that hands-free is unavailable, instead of offering an Arm button that
     * silently never fires.
     */
    fun checkDetector() {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val detector = SherpaWakeWordDetector(context)
            val ready = detector.load()
            repository.setDetectorReady(ready)
            // Nothing is listening yet; the service builds its own instance when armed.
            detector.close()

            // The tagger is not required for hands-free activation, so its absence does
            // not block arming — but knowing early whether it loaded is worth a probe.
            val tagger = YamnetAudioTagger(context)
            tagger.load()
            tagger.close()
        }
    }

    /**
     * Asks the repository to re-read the system.
     *
     * Needed because granting a permission is invisible to app state — nothing
     * observable changes when the user taps Allow — so something has to say "look again"
     * after a dialog or a trip to settings. The repository does the reading itself.
     */
    fun syncPermissions() {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) { repository.refreshPermissions() }
    }

    /** Arms listening. Only valid while an activity is visible. */
    fun arm() {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            GuardianListeningService.start(context)
            repository.arm()
        }
    }

    fun disarm() {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            GuardianListeningService.stop(context)
            repository.disarm()
        }
    }

    /**
     * Starts recording from a tap, arming first if Angel is not already listening.
     *
     * Kept separate from [arm] because its preconditions are deliberately narrower: it
     * needs the microphone and location, and nothing else. No wake word, no keyword
     * model, no notification permission. This is the path that still works when
     * everything clever has failed.
     *
     * Location is required rather than optional because a recording nobody can be sent
     * to is half a feature — the guardians get an alert and no idea where to go. Asking
     * at the moment of use is also the only honest time to ask: this is when it matters.
     *
     * @return what happened, so the caller only shows recording UI if recording started.
     */
    fun recordNow(): RecordAttempt {
        if (!context.hasPermission(Manifest.permission.RECORD_AUDIO)) {
            requestMicrophone()
            return RecordAttempt.NeedsMicrophone
        }
        // Returns here rather than falling through. An intermediate version asked for
        // location and then started recording anyway, so a tap both raised a permission
        // dialog and began capturing behind it — which is the one combination that makes
        // the dialog a lie.
        if (!context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            requestLocationPermission()
            return RecordAttempt.NeedsLocation
        }
        GuardianListeningService.record(context)
        return RecordAttempt.Started
    }

    /** Stops a recording that is under way, standing the service down. */
    fun stopRecording() {
        scope.launch {
            GuardianListeningService.stop(context)
            repository.disarm()
        }
    }

    /** Asks for location, which the setup card offers when it is missing. */
    fun requestLocation() = requestLocationPermission()

    /** Routes a blocker to the thing that actually resolves it. */
    fun resolve(requirement: ListeningRequirement) {
        when (requirement) {
            ListeningRequirement.MicrophonePermission -> requestMicrophone()
            ListeningRequirement.NotificationPermission -> requestNotifications()
            ListeningRequirement.LocationPermission -> requestLocationPermission()
            ListeningRequirement.BatteryExemption -> context.openBatterySettings()
            // App state, not system state — the caller navigates instead.
            ListeningRequirement.WakeWordEnrolled -> Unit
        }
    }
}

/** The outcome of asking to record. */
enum class RecordAttempt {
    Started,

    /** Permission dialog shown. The UI must not claim to be recording. */
    NeedsMicrophone,
    NeedsLocation;

    val isStarted: Boolean get() = this == Started
}

/**
 * Builds a controller wired to real permission launchers, and keeps permission state in
 * step with the system across resumes.
 */
@Composable
fun rememberHandsFreeController(repository: ListeningRepository): HandsFreeController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val microphoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Result is picked up by the resume sync below. */ }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val controller = remember(repository) {
        HandsFreeController(
            context = context,
            repository = repository,
            requestMicrophone = {
                microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            requestNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            requestLocationPermission = {
                locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            scope = scope,
        )
    }

    // Re-read on resume: the user may have changed permissions in system settings, and
    // showing "listening" over a revoked microphone would be the worst possible lie for
    // this app to tell.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, controller) {
        // Model availability cannot change while the app runs, so it is checked once.
        controller.checkDetector()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) controller.syncPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    return controller
}

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

/** Notifications are only a runtime permission from Android 13. */
private fun Context.hasNotificationPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        true
    }

private fun Context.isIgnoringBatteryOptimisations(): Boolean =
    getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(packageName) == true

/**
 * Opens the battery optimisation screen.
 *
 * Deliberately the settings screen rather than `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`:
 * Play policy only permits the direct prompt for a narrow set of app types, and a
 * settings deep link carries no policy risk.
 */
private fun Context.openBatterySettings() {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }.onFailure {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
