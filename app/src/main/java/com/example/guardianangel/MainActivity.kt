package com.example.guardianangel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.guardianangel.di.AppContainer
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
 * The container is created by [GuardianAngelApplication] rather than here, so the
 * listening service shares it — a wake word detected with the app closed has to update
 * the same state this screen will read when it reopens.
 *
 * @param startAtOnboarding set true to exercise the setup wizard.
 */
@Composable
fun GuardianAngelApp(
    startAtOnboarding: Boolean = false,
    container: AppContainer = LocalContext.current.appContainer,
) {
    GuardianAngelTheme {
        GuardianNavHost(
            container = container,
            startAtOnboarding = startAtOnboarding,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
