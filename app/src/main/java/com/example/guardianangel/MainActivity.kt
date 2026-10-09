package com.example.guardianangel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.guardianangel.ui.screens.HomeScreen
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the system bars; GuardianAngelTheme matches the bar icon polarity to
        // the active scheme, and each screen applies its own window insets.
        enableEdgeToEdge()
        setContent {
            GuardianAngelApp()
        }
    }
}

/**
 * App root. [GuardianAngelTheme] is installed exactly once here — nothing below should
 * wrap itself in the theme again, or the brand tokens get re-provided needlessly.
 */
@Composable
fun GuardianAngelApp() {
    GuardianAngelTheme {
        HomeScreen(
            modifier = Modifier
                .fillMaxSize()
                .background(GuardianTheme.colors.canvas),
        )
    }
}
