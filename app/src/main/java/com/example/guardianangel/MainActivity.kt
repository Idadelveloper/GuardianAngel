package com.example.guardianangel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.guardianangel.di.AppContainer
import com.example.guardianangel.di.InMemoryAppContainer
import com.example.guardianangel.ui.navigation.GuardianNavHost
import com.example.guardianangel.ui.theme.GuardianAngelTheme

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
 * The one place the object graph is built and the theme installed. Swapping
 * [InMemoryAppContainer] for a persistence-backed container is the only change needed
 * when the database lands — no screen or view model knows which it is talking to.
 *
 * @param startAtOnboarding set true to exercise the setup wizard; the in-memory container
 *   otherwise starts signed in so the main shell is reachable immediately.
 */
@Composable
fun GuardianAngelApp(
    startAtOnboarding: Boolean = false,
    container: AppContainer = remember { InMemoryAppContainer(startSignedIn = !startAtOnboarding) },
) {
    GuardianAngelTheme {
        GuardianNavHost(
            container = container,
            startAtOnboarding = startAtOnboarding,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
