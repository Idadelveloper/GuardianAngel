package com.example.guardianangel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.guardianangel.di.AppContainer
import androidx.compose.ui.unit.dp
import com.example.guardianangel.ui.mascot.AngelMascot
import com.example.guardianangel.ui.mascot.AngelMood
import com.example.guardianangel.ui.navigation.GuardianNavHost
import com.example.guardianangel.ui.navigation.StartDestination
import com.example.guardianangel.ui.navigation.observeStartDestination
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the system bars; GuardianAngelTheme matches bar icon polarity to
        // the active scheme and each screen applies its own insets.
        enableEdgeToEdge()
        setContent { GuardianAngelApp() }
    }
}

/**
 * App root.
 *
 * The container is created by [GuardianAngelApplication] rather than here, so the
 * listening service shares it — a wake word detected with the app closed has to update
 * the same state this screen will read when it reopens.
 *
 * ## Why the destination is resolved before the graph is built
 *
 * Where the app opens depends on persisted state — is there a session, did setup finish —
 * and reading that takes a few milliseconds. Composing the graph with a guess and
 * correcting it afterwards would flash the wrong screen: Home at someone who is signed
 * out, or a login form at someone who is already in. So the root holds a splash until
 * the answer is known, and `NavHost` is created once, with the right start destination.
 */
@Composable
fun GuardianAngelApp(container: AppContainer = LocalContext.current.appContainer) {
    val start by produceState<StartDestination>(
        initialValue = StartDestination.Loading,
        container,
    ) {
        observeStartDestination(
            authRepository = container.authRepository,
            accountRepository = container.accountRepository,
            hasExistingAccount = container.observeHasAccount(),
        ).collect { value = it }
    }

    GuardianAngelTheme {
        if (start is StartDestination.Loading) {
            StartupSplash(modifier = Modifier.fillMaxSize())
        } else {
            GuardianNavHost(
                container = container,
                start = start,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Held while the opening destination is resolved.
 *
 * Angel rather than a spinner: this is the first thing seen on every launch, and it is
 * usually on screen for a single frame. A branded hold reads as the app opening; a
 * spinner reads as the app being slow.
 */
@Composable
private fun StartupSplash(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(GuardianTheme.colors.canvas),
        contentAlignment = Alignment.Center,
    ) {
        AngelMascot(mood = AngelMood.Resting, size = 132.dp)
    }
}
