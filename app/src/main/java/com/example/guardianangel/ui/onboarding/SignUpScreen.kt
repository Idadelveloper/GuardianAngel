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
import com.example.guardianangel.data.FakeAccountRepository
import com.example.guardianangel.domain.repository.AccountRepository
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
 * Deliberately the shortest form the product can get away with: a name, a number and a
 * passphrase. The design brief asks for Partiful-level friction, and every extra field
 * here is one more reason to abandon setup for a safety app you have not needed yet.
 */
@Composable
fun SignUpRoute(
    repository: AccountRepository,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    SignUpScreen(
        modifier = modifier,
        onSubmit = { name, phone, password ->
            scope.launch {
                repository.createAccount(name, phone, password)
                onContinue()
            }
        },
    )
}

@Composable
fun SignUpScreen(
    onSubmit: (name: String, phone: String, password: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val strength = remember(password) { passwordStrength(password) }
    val canSubmit = name.isNotBlank() && phone.length >= 7 && password.length >= 8

    GuardianWizardScaffold(
        modifier = modifier,
        stepLabel = "Create your account",
        progress = 0f,
        onBack = null,
        ctaLabel = "Create account",
        onCta = { onSubmit(name.trim(), phone.trim(), password) },
        ctaEnabled = canSubmit,
        footer = {
            GuardianTextLink(
                prefix = "Already have an account?",
                linkLabel = "Log in",
                onClick = { /* Login flow is not part of this build. */ },
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
            placeholder = "Maya Lin",
            leadingIcon = GuardianIcons.Users,
            modifier = Modifier.fillMaxWidth(),
        )

        GuardianTextField(
            value = phone,
            onValueChange = { phone = it.filter { c -> c.isDigit() || c in "+() -" } },
            label = "Mobile number",
            placeholder = "(555) 392-8174",
            leadingIcon = GuardianIcons.Phone,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            supportingText = "We text a code to verify it's you.",
            modifier = Modifier.fillMaxWidth(),
        )

        Column {
            GuardianTextField(
                value = password,
                onValueChange = { password = it },
                label = "Master password",
                placeholder = "Create a strong passphrase",
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
    GuardianAngelTheme { SignUpScreen(onSubmit = { _, _, _ -> }) }
}
