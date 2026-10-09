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
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    /** Pushes current system permission state into the repository. */
    fun syncPermissions() {
        scope.launch {
            repository.updatePermissions(
                microphone = context.hasPermission(Manifest.permission.RECORD_AUDIO),
                notifications = context.hasNotificationPermission(),
                batteryExempt = context.isIgnoringBatteryOptimisations(),
            )
        }
    }

    /** Arms listening. Only valid while an activity is visible. */
    fun arm() {
        scope.launch {
            GuardianListeningService.start(context)
            repository.arm()
        }
    }

    fun disarm() {
        scope.launch {
            GuardianListeningService.stop(context)
            repository.disarm()
        }
    }

    /** Routes a blocker to the thing that actually resolves it. */
    fun resolve(requirement: ListeningRequirement) {
        when (requirement) {
            ListeningRequirement.MicrophonePermission -> requestMicrophone()
            ListeningRequirement.NotificationPermission -> requestNotifications()
            ListeningRequirement.BatteryExemption -> context.openBatterySettings()
            // These are app state, not system state — the caller navigates instead.
            ListeningRequirement.WakeWordEnrolled,
            ListeningRequirement.VoiceProfile -> Unit
        }
    }
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
            scope = scope,
        )
    }

    // Re-read on resume: the user may have changed permissions in system settings, and
    // showing "listening" over a revoked microphone would be the worst possible lie for
    // this app to tell.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, controller) {
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
