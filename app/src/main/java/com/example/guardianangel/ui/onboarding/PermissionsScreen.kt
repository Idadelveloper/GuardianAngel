package com.example.guardianangel.ui.onboarding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.guardianangel.domain.repository.AccountRepository
import com.example.guardianangel.ui.components.GuardianWizardScaffold
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.mascot.AngelMood
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import kotlinx.coroutines.launch

/**
 * Step 1 — the two permissions Angel cannot work without.
 *
 * Both default to off and must be turned on deliberately. The spec's own wording is that
 * Angel "needs your permission", and pre-ticking a microphone switch on a safety app
 * would undercut exactly the trust the screen is trying to build.
 */
@Composable
fun PermissionsRoute(
    repository: AccountRepository,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    PermissionsScreen(
        modifier = modifier,
        onBack = onBack,
        onGrant = { location, microphone ->
            scope.launch {
                repository.grantPermissions(location, microphone)
                onContinue()
            }
        },
    )
}

@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    onGrant: (location: Boolean, microphone: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var location by remember { mutableStateOf(false) }
    var microphone by remember { mutableStateOf(false) }

    GuardianWizardScaffold(
        modifier = modifier,
        stepLabel = "Step 1 of 4 · Permissions",
        progress = 0.25f,
        onBack = onBack,
        ctaLabel = "Grant permissions & continue",
        onCta = { onGrant(location, microphone) },
        ctaEnabled = location && microphone,
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
            onCheckedChange = { location = it },
            modifier = Modifier.fillMaxWidth(),
        )

        ToggleCard(
            icon = GuardianIcons.Mic,
            title = "Microphone",
            description = "Lets me listen for your codewords. Audio is matched on your " +
                "phone and nothing is recorded until a codeword fires.",
            checked = microphone,
            onCheckedChange = { microphone = it },
            modifier = Modifier.fillMaxWidth(),
        )

        AssuranceCard(
            icon = GuardianIcons.Lock,
            title = "Processed on your device",
            body = "Keyword spotting runs inside the phone's encrypted sandbox. No audio " +
                "stream leaves it.",
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_8", heightDp = 1100)
@Composable
private fun PermissionsPreview() {
    GuardianAngelTheme { PermissionsScreen(onBack = {}, onGrant = { _, _ -> }) }
}
