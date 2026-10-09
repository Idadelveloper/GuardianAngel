package com.example.guardianangel.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.guardianangel.di.AppContainer
import com.example.guardianangel.domain.repository.AuthResult
import com.example.guardianangel.ui.auth.AuthFormState
import com.example.guardianangel.ui.auth.MIN_PASSWORD_LENGTH
import com.example.guardianangel.ui.auth.looksLikeEmail
import com.example.guardianangel.ui.components.GuardianTextField
import com.example.guardianangel.ui.components.GuardianTextLink
import com.example.guardianangel.ui.components.GuardianWizardScaffold
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.mascot.AngelMood
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme
import kotlinx.coroutines.launch

/**
 * Step 0 — create an account and meet Angel.
 *
 * Deliberately the shortest form the product can get away with: a name, an email and a
 * passphrase. The design brief asks for Partiful-level friction, and every extra field
 * here is one more reason to abandon setup for a safety app you have not needed yet.
 *
 * Email rather than a phone number because that is what both back ends can actually
 * authenticate: Firebase email/password, and a Keystore-encrypted hash on a device with
 * no Firebase project. The old form collected a mobile number, implied an SMS code, and
 * sent neither — it wrote the number to the profile and let anyone straight in.
 */
@Composable
fun SignUpRoute(
    container: AppContainer,
    onContinue: () -> Unit,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(AuthFormState()) }

    SignUpScreen(
        modifier = modifier,
        form = form,
        onSubmit = { name, email, password ->
            form = form.copy(isSubmitting = true, error = null)
            scope.launch {
                val result = container.authRepository.signUpWithEmail(name, email, password)
                when (result) {
                    is AuthResult.Success -> {
                        container.onAuthenticated(result.user)
                        // Mirrors the name onto the profile the settings screens read and
                        // moves the wizard past sign-up, so a reopened app resumes here.
                        container.accountRepository.createAccount(name, "", password)
                        form = form.copy(isSubmitting = false)
                        onContinue()
                    }

                    is AuthResult.Failure ->
                        form = form.copy(isSubmitting = false, error = result.message)
                }
            }
        },
        onLogIn = onLogIn,
        onSkip = {
            // Keeps the original safety property alive behind the gate: a woman
            // downloading this at 11pm should be able to arm a panic button before she
            // is asked for an email address. She can add credentials later from
            // Settings, and the anonymous account is upgraded in place rather than
            // replaced, so nothing set up now is lost.
            form = form.copy(isSubmitting = true, error = null)
            scope.launch {
                when (val result = container.authRepository.signInAnonymously()) {
                    is AuthResult.Success -> {
                        container.onAuthenticated(result.user)
                        form = form.copy(isSubmitting = false)
                        onContinue()
                    }

                    is AuthResult.Failure ->
                        form = form.copy(isSubmitting = false, error = result.message)
                }
            }
        },
    )
}

@Composable
fun SignUpScreen(
    onSubmit: (name: String, email: String, password: String) -> Unit,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier,
    form: AuthFormState = AuthFormState(),
    onSkip: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val strength = remember(password) { passwordStrength(password) }
    val canSubmit = name.isNotBlank() &&
        looksLikeEmail(email) &&
        password.length >= MIN_PASSWORD_LENGTH &&
        !form.isSubmitting

    GuardianWizardScaffold(
        modifier = modifier,
        stepLabel = "Create your account",
        progress = 0f,
        onBack = null,
        ctaLabel = if (form.isSubmitting) "Creating…" else "Create account",
        onCta = { onSubmit(name.trim(), email.trim(), password) },
        ctaEnabled = canSubmit,
        skipLabel = "Set up without an account".takeIf { onSkip != null },
        onSkip = onSkip,
        footer = {
            GuardianTextLink(
                prefix = "Already have an account?",
                linkLabel = "Log in",
                onClick = onLogIn,
            )
        },
    ) {
        AngelSays(
            message = "Meet Angel — I'll be right by your side. Let's set up your safe space.",
            mood = AngelMood.Sanctuary,
            mascotSize = 128.dp,
        )

        GuardianTextField(
            value = name,
            onValueChange = { name = it },
            label = "Full name",
            placeholder = "Ida Delphine",
            leadingIcon = GuardianIcons.Users,
            modifier = Modifier.fillMaxWidth(),
        )

        GuardianTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            placeholder = "you@example.com",
            leadingIcon = GuardianIcons.Users,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            isError = form.error != null,
            supportingText = form.error ?: "Used to get back in on a new phone.",
            modifier = Modifier.fillMaxWidth(),
        )

        Column {
            GuardianTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                placeholder = "At least $MIN_PASSWORD_LENGTH characters",
                leadingIcon = GuardianIcons.Lock,
                trailingIcon = if (passwordVisible) GuardianIcons.EyeOff else GuardianIcons.Eye,
                onTrailingIconClick = { passwordVisible = !passwordVisible },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(GuardianTheme.spacing.sm))
            PasswordStrengthMeter(strength)
        }

        AssuranceCard(
            icon = GuardianIcons.Shield,
            title = "Nothing leaves your phone",
            body = "Voice processing happens on-device. Your speech and coordinates are " +
                "never uploaded without you asking.",
        )
    }
}

/** 0–4. Deliberately simple and local: this is guidance, not a security control. */
private fun passwordStrength(password: String): Int {
    if (password.isEmpty()) return 0
    var score = 0
    if (password.length >= 8) score++
    if (password.length >= 12) score++
    if (password.any(Char::isDigit)) score++
    if (password.any { !it.isLetterOrDigit() } || password.count { it == ' ' } >= 2) score++
    return score.coerceIn(0, 4)
}

@Composable
private fun PasswordStrengthMeter(strength: Int) {
    val label = when (strength) {
        0 -> "Add a few more characters"
        1 -> "Getting there"
        2 -> "Decent"
        3 -> "Strong"
        else -> "Safe & sound"
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Protection strength",
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
            Text(
                text = label,
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.primary,
            )
        }
        Spacer(Modifier.height(GuardianTheme.spacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Four segments rather than a numeric score: a filled bar communicates
            // "more is better" without implying the app is grading the user.
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < strength) {
                                GuardianTheme.colors.accentSoft
                            } else {
                                GuardianTheme.materialColors.surfaceContainerHigh
                            }
                        ),
                )
            }
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_8", heightDp = 1200)
@Composable
private fun SignUpPreview() {
    GuardianAngelTheme {
        SignUpScreen(onSubmit = { _, _, _ -> }, onLogIn = {}, onSkip = {})
    }
}
