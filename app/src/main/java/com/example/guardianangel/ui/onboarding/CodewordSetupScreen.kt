package com.example.guardianangel.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.Codeword
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.repository.AccountRepository
import com.example.guardianangel.domain.repository.CodewordRepository
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.components.GuardianTextField
import com.example.guardianangel.ui.components.GuardianWizardScaffold
import com.example.guardianangel.ui.home.components.TonalPill
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.mascot.AngelMood
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.SafetyLevel
import com.example.guardianangel.ui.theme.safetyColorsFor
import kotlinx.coroutines.launch

/** Copy for each tier, kept beside the screen that teaches them. */
internal data class TierCopy(
    val tier: CodewordTier,
    val name: String,
    val purpose: String,
    val suggestion: String,
    val exampleTemplate: String,
)

internal val TIER_COPY = listOf(
    TierCopy(
        CodewordTier.Safe, "Safe", "Tells your circle you got there fine.",
        "marshmallow", "Say \"%s\" and I'll let your circle know you're safe.",
    ),
    TierCopy(
        CodewordTier.Caution, "Caution", "I start listening closely. Nobody is told.",
        "pineapple", "Say \"%s\" and I'll start transcribing quietly, before anything escalates.",
    ),
    TierCopy(
        CodewordTier.Danger, "Danger", "Alerts your circle with your live location.",
        "yellow submarine", "Say \"%s\" and I'll tell your circle you need help and share where you are.",
    ),
    TierCopy(
        CodewordTier.Emergency, "Emergency", "Calls emergency services and alerts everyone.",
        "glitter", "Say \"%s\" and I'll call for help and send your location to your circle.",
    ),
)

/**
 * Step 4 — set the four codewords, one tier at a time.
 *
 * The reference put all four on one page alongside a PIN section. That is a lot of
 * consequence to absorb at once, and the tiers only make sense in order, so this paginates
 * them: one word, one explanation, one example built from what the user just typed.
 */
@Composable
fun CodewordSetupRoute(
    codewordRepository: CodewordRepository,
    accountRepository: AccountRepository,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    CodewordSetupScreen(
        modifier = modifier,
        onBack = onBack,
        onComplete = { phrases ->
            scope.launch {
                phrases.forEach { (tier, phrase) ->
                    codewordRepository.updateCodeword(
                        Codeword(id = "cw-${tier.name.lowercase()}", tier = tier, phrase = phrase)
                    )
                }
                accountRepository.completeOnboarding()
                onComplete()
            }
        },
    )
}

@Composable
fun CodewordSetupScreen(
    onBack: () -> Unit,
    onComplete: (Map<CodewordTier, String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var index by remember { mutableIntStateOf(0) }
    val phrases = remember { mutableStateMapOf<CodewordTier, String>() }

    val copy = TIER_COPY[index]
    val current = phrases[copy.tier].orEmpty()
    val isLast = index == TIER_COPY.lastIndex
    val canAdvance = current.isNotBlank()

    GuardianWizardScaffold(
        modifier = modifier,
        stepLabel = "Step 4 of 4 · Codeword ${index + 1} of ${TIER_COPY.size}",
        progress = 0.75f + 0.25f * ((index + 1f) / TIER_COPY.size),
        onBack = { if (index == 0) onBack() else index-- },
        ctaLabel = if (isLast) "Finish setup & meet Angel" else "Next codeword",
        onCta = {
            if (isLast) onComplete(phrases.toMap()) else index++
        },
        ctaEnabled = canAdvance,
    ) {
        AngelSays(
            message = "These are your quiet words. Say one naturally in conversation and " +
                "I'll act — and if you say it by accident, I'll notice and stand down.",
            mood = AngelMood.Resting,
            mascotSize = 88.dp,
        )

        TierDots(current = index, total = TIER_COPY.size)

        GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
            val palette = safetyColorsFor(levelFor(copy.tier))
            TonalPill(
                text = "Level ${index + 1} · ${copy.name}",
                container = palette.container,
                content = palette.onContainer,
            )
            Spacer(Modifier.height(GuardianTheme.spacing.sm))
            Text(
                text = copy.purpose,
                style = GuardianTheme.type.bodyMd,
                color = GuardianTheme.materialColors.onSurface,
            )
            Spacer(Modifier.height(GuardianTheme.spacing.md))
            GuardianTextField(
                value = current,
                onValueChange = { phrases[copy.tier] = it },
                label = "Your word or phrase",
                placeholder = "e.g. ${copy.suggestion}",
                leadingIcon = GuardianIcons.Mic,
                supportingText = "Pick something you'd never say by accident in a tense moment.",
                modifier = Modifier.fillMaxWidth(),
            )

            if (canAdvance) {
                Spacer(Modifier.height(GuardianTheme.spacing.md))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GuardianTheme.shapes.lg)
                        .background(GuardianTheme.materialColors.surfaceContainerLow)
                        .padding(GuardianTheme.spacing.md),
                ) {
                    Text(
                        text = copy.exampleTemplate.format(current.trim()),
                        style = GuardianTheme.type.bodySm,
                        color = GuardianTheme.materialColors.onSurfaceVariant,
                    )
                }
            }
        }

        AssuranceCard(
            icon = GuardianIcons.Shield,
            title = "Said it by mistake?",
            body = "You'll get a notification the moment a codeword fires, and one tap " +
                "stands everything down.",
        )
    }
}

internal fun levelFor(tier: CodewordTier) = when (tier) {
    CodewordTier.Safe -> SafetyLevel.Secure
    CodewordTier.Caution -> SafetyLevel.Guarded
    CodewordTier.Danger -> SafetyLevel.Elevated
    CodewordTier.Emergency -> SafetyLevel.High
}

/** Progress through the four codewords, so the user can see how much is left. */
@Composable
private fun TierDots(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .let { if (i == current) it.size(width = 28.dp, height = 6.dp) else it.size(6.dp) }
                    .clip(CircleShape)
                    .background(
                        when {
                            i == current -> GuardianTheme.colors.accentSoft
                            i < current -> GuardianTheme.materialColors.primary
                            else -> GuardianTheme.materialColors.surfaceContainerHigh
                        }
                    ),
            )
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_8", heightDp = 1200)
@Composable
private fun CodewordSetupPreview() {
    GuardianAngelTheme { CodewordSetupScreen(onBack = {}, onComplete = {}) }
}
