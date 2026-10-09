package com.example.guardianangel.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.guardianangel.di.AppContainer
import com.example.guardianangel.domain.repository.AuthResult
import com.example.guardianangel.ui.components.GuardianTextField
import com.example.guardianangel.ui.components.GuardianTextLink
import com.example.guardianangel.ui.components.GuardianWizardScaffold
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.mascot.AngelMood
import com.example.guardianangel.ui.onboarding.AngelSays
import com.example.guardianangel.ui.onboarding.AssuranceCard
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme
import kotlinx.coroutines.launch

/**
 * Log in to an account that already exists.
 *
 * The counterpart to [SignUpRoute]. Both exist because the app now opens on an auth gate
 * rather than signing in silently, and a returning user who is sent to a sign-up form
 * will either create a second account or conclude her data is gone.
 *
 * Without a Firebase project this authenticates against a Keystore-encrypted hash held on
 * the device, so the gate is real offline rather than decorative. See
 * `LocalAuthRepository`.
 */
@Composable
fun LoginRoute(
    container: AppContainer,
    onAuthenticated: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(AuthFormState()) }

    LoginScreen(
        modifier = modifier,
        form = form,
        onSubmit = { email, password ->
            form = form.copy(isSubmitting = true, error = null)
            scope.launch {
                when (val result = container.authRepository.signInWithEmail(email, password)) {
                    is AuthResult.Success -> {
                        // Reconciles the auth identity with the local tables before any
                        // screen reads them, so Home never renders against a half-built
                        // account.
                        container.onAuthenticated(result.user)
                        form = form.copy(isSubmitting = false)
                        onAuthenticated()
                    }

                    is AuthResult.Failure ->
                        form = form.copy(isSubmitting = false, error = result.message)
                }
            }
        },
        onCreateAccount = onCreateAccount,
    )
}

@Composable
fun LoginScreen(
    onSubmit: (email: String, password: String) -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
    form: AuthFormState = AuthFormState(),
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val canSubmit = looksLikeEmail(email) && password.isNotEmpty() && !form.isSubmitting

    GuardianWizardScaffold(
        modifier = modifier,
        stepLabel = "Welcome back",
        progress = 0f,
        onBack = null,
        ctaLabel = if (form.isSubmitting) "Signing in…" else "Log in",
        onCta = { onSubmit(email.trim(), password) },
        ctaEnabled = canSubmit,
        footer = {
            GuardianTextLink(
                prefix = "New here?",
                linkLabel = "Create an account",
                onClick = onCreateAccount,
            )
        },
    ) {
        AngelSays(
            message = "Good to see you again. Let me get your safe space back.",
            mood = AngelMood.Sanctuary,
            mascotSize = 120.dp,
        )

        GuardianTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            placeholder = "you@example.com",
            leadingIcon = GuardianIcons.Users,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )

        GuardianTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            placeholder = "Your password",
            leadingIcon = GuardianIcons.Lock,
            trailingIcon = if (passwordVisible) GuardianIcons.EyeOff else GuardianIcons.Eye,
            onTrailingIconClick = { passwordVisible = !passwordVisible },
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            // One message for a wrong password and an unknown address, so the screen
            // cannot be used to find out whether someone has an account here.
            isError = form.error != null,
            supportingText = form.error,
            modifier = Modifier.fillMaxWidth(),
        )

        AssuranceCard(
            icon = GuardianIcons.Lock,
            title = "Your recordings stay on your phone",
            body = "Logging in unlocks this device's data. Transcripts and your voiceprint " +
                "are never uploaded, so they cannot be read from anywhere else.",
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_8", heightDp = 1000)
@Composable
private fun LoginPreview() {
    GuardianAngelTheme { LoginScreen(onSubmit = { _, _ -> }, onCreateAccount = {}) }
}

@Preview(showBackground = true, device = "id:pixel_8", heightDp = 1000)
@Composable
private fun LoginErrorPreview() {
    GuardianAngelTheme {
        LoginScreen(
            onSubmit = { _, _ -> },
            onCreateAccount = {},
            form = AuthFormState(error = "That email and password don't match."),
        )
    }
}
