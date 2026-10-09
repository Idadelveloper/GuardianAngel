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
    /** Null on the first wizard step — there is nothing behind it but the auth gate. */
    onBack: (() -> Unit)?,
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
                // The listening repository reads the system itself; this only tells it
                // that now is a good moment to look again.
                listeningRepository.refreshPermissions()
                onContinue()
            }
        },
    )
}

@Composable
fun PermissionsScreen(
    onBack: (() -> Unit)?,
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

    // Everything recording depends on. Notifications are included because Android
    // requires a visible notification for a background microphone service, so without it
    // hands-free cannot run at all.
    val allGranted = microphone && notifications && location

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
        // Location joins the required set: recording is gated on it, because an alert
        // that cannot say where she is leaves her guardians with an emergency and no
        // address.
        ctaLabel = if (allGranted) "Continue" else "Grant permissions",
        onCta = {
            if (allGranted) onGrant(location, microphone, notifications) else request()
        },
        // Never disabled: the button asks for whatever is still missing, and a dead
        // button with no explanation is worse than one that opens a dialog.
        ctaEnabled = true,
        // Skipping is allowed, but named for what it costs rather than as a neutral
        // "later". Everything spoken depends on the microphone, and recording depends on
        // location, so a user who skips should learn that here and not on a dark street.
        skipLabel = if (allGranted) null else "Skip — recording won't work yet",
        onSkip = { onGrant(location, microphone, notifications) }.takeIf { !allGranted },
    ) {
        AngelSays(
            message = if (allGranted) {
                "That's everything I need. You can turn any of these off later."
            } else {
                "To hear you and to tell your guardians where you are, I need these " +
                    "three. You can turn any of them off at any time."
            },
            mood = if (allGranted) AngelMood.Sanctuary else AngelMood.Resting,
        )

        ToggleCard(
            icon = GuardianIcons.MapPin,
            title = "Location",
            description = "So an alert can say where you are. Without it I can tell your " +
                "guardians something is wrong but not where to go, so recording stays off.",
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
