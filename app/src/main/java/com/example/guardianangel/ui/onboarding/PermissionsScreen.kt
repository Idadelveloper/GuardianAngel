package com.example.guardianangel.ui.onboarding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.RECORD_AUDIO
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.tooling.preview.Preview
import com.example.guardianangel.domain.repository.AccountRepository
import com.example.guardianangel.domain.repository.ListeningRepository
import com.example.guardianangel.ui.components.GuardianWizardScaffold
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.mascot.AngelMood
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import kotlinx.coroutines.launch

/**
 * Step 1 — the permissions Angel cannot work without.
 *
 * All default to off and must be turned on deliberately. Pre-ticking a microphone switch
 * on a safety app would undercut exactly the trust the screen is trying to build.
 *
 * The notification toggle is not bureaucratic box-ticking: Android requires a visible
 * notification for any service that holds the microphone in the background, so without
 * it hands-free listening cannot run at all. The copy says that rather than pretending
 * it is a preference.
 */
@Composable
fun PermissionsRoute(
    repository: AccountRepository,
    listeningRepository: ListeningRepository,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    PermissionsScreen(
        modifier = modifier,
        onBack = onBack,
        onGrant = { location, microphone, notifications ->
            scope.launch {
                repository.grantPermissions(location, microphone)
                listeningRepository.updatePermissions(
                    microphone = microphone,
                    notifications = notifications,
                    // Asked for separately, later — it needs a system settings trip and
                    // blocking setup on it would be hostile.
                    batteryExempt = false,
                )
                onContinue()
            }
        },
    )
}

@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    onGrant: (location: Boolean, microphone: Boolean, notifications: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Reflects what the system actually grants, re-read on resume. A local boolean
    // would keep claiming a permission the user revoked in Settings, and this screen
    // exists precisely to tell her the truth about what Angel can do.
    var location by remember { mutableStateOf(context.hasPermission(ACCESS_FINE_LOCATION)) }
    var microphone by remember { mutableStateOf(context.hasPermission(RECORD_AUDIO)) }
    var notifications by remember { mutableStateOf(context.hasNotificationPermission()) }

    fun refresh() {
        location = context.hasPermission(ACCESS_FINE_LOCATION)
        microphone = context.hasPermission(RECORD_AUDIO)
        notifications = context.hasNotificationPermission()
    }

    val requestPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        // Catches the case where the user granted from system settings after being
        // sent there by a permanent denial.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    /** Asks for anything still missing. Android ignores already-granted entries. */
    fun request() {
        val wanted = buildList {
            if (!microphone) add(RECORD_AUDIO)
            if (!location) add(ACCESS_FINE_LOCATION)
            if (!notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(POST_NOTIFICATIONS)
            }
        }
        if (wanted.isEmpty()) refresh() else requestPermissions.launch(wanted.toTypedArray())
    }

    GuardianWizardScaffold(
        modifier = modifier,
        stepLabel = "Step 1 of 5 · Permissions",
        progress = 0.2f,
        onBack = onBack,
        ctaLabel = if (microphone && notifications) "Continue" else "Grant permissions",
        onCta = {
            if (microphone && notifications) {
                onGrant(location, microphone, notifications)
            } else {
                request()
            }
        },
        // Location is genuinely optional — Angel can still listen and record without
        // it, she just cannot tell guardians where to go. Blocking setup on it would
        // turn a degraded feature into no app at all.
        ctaEnabled = true,
    ) {
        AngelSays(
            message = "To watch your route and listen for your codewords, I need two " +
                "permissions. You can turn either off at any time.",
            mood = AngelMood.Resting,
        )

        ToggleCard(
            icon = GuardianIcons.MapPin,
            title = "Location",
            description = "Lets me check street lighting on your route and know when " +
                "you've reached a safe haven.",
            checked = location,
            onCheckedChange = { request() },
            modifier = Modifier.fillMaxWidth(),
        )

        ToggleCard(
            icon = GuardianIcons.Mic,
            title = "Microphone",
            description = "Lets me listen for your wake word. Matching happens on your " +
                "phone and nothing is recorded until you wake me.",
            checked = microphone,
            onCheckedChange = { request() },
            modifier = Modifier.fillMaxWidth(),
        )

        ToggleCard(
            icon = GuardianIcons.Bell,
            title = "Notifications",
            description = "Android requires a visible notification whenever an app holds " +
                "the microphone in the background. Without it I can't listen hands-free.",
            checked = notifications,
            onCheckedChange = { request() },
            modifier = Modifier.fillMaxWidth(),
        )

        AssuranceCard(
            icon = GuardianIcons.Lock,
            title = "Processed on your device",
            body = "Wake-word matching runs inside the phone's encrypted sandbox. No " +
                "audio stream leaves it.",
        )

        AssuranceCard(
            icon = GuardianIcons.Power,
            title = "You arm me — I can't arm myself",
            body = "Android only lets an app start listening while you have it open, so " +
                "tap Arm before you set off. After that I keep listening with your phone " +
                "locked and in your pocket until you stand me down.",
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_8", heightDp = 1100)
@Composable
private fun PermissionsPreview() {
    GuardianAngelTheme { PermissionsScreen(onBack = {}, onGrant = { _, _, _ -> }) }
}

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Notifications only became a runtime permission in Android 13. */
private fun Context.hasNotificationPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        hasPermission(POST_NOTIFICATIONS)
    } else {
        true
    }
