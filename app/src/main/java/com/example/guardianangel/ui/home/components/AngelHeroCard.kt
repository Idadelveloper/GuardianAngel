package com.example.guardianangel.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.guardianangel.ui.mascot.AngelMascot
import com.example.guardianangel.ui.mascot.AngelMood
import com.example.guardianangel.ui.theme.GuardianTheme

/**
 * Angel's hero card — the emotional anchor of the home screen.
 *
 * Angel sits on a soft gradient tinted by mood, with what she has to say in a bubble
 * underneath. This is the first thing the user sees, and in most sessions it is the only
 * thing she reads, so the message is written as a sentence Angel says rather than a
 * status line the app reports.
 */
@Composable
fun AngelHeroCard(
    mood: AngelMood,
    message: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    badgeIcon: ImageVector? = null,
    mascotSize: Dp = 132.dp,
) {
    val tint = moodTint(mood)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(GuardianTheme.shapes.xl)
            .background(
                Brush.verticalGradient(
                    listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.16f)),
                )
            )
            .border(1.dp, GuardianTheme.colors.borderEmphasis, GuardianTheme.shapes.xl)
            .padding(GuardianTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AngelMascot(mood = mood, size = mascotSize)

        if (badge != null) {
            // Sits under Angel rather than overlapping her. Anchored to her bottom-right
            // it collided with the wing and heart, and the collision point moved with
            // every mood because each tier has a different silhouette.
            Spacer(Modifier.height(GuardianTheme.spacing.sm))
            Row(
                modifier = Modifier
                    .clip(GuardianTheme.shapes.pill)
                    .background(GuardianTheme.materialColors.surfaceContainerLowest)
                    .border(1.dp, GuardianTheme.colors.borderDefault, GuardianTheme.shapes.pill)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (badgeIcon != null) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = GuardianTheme.materialColors.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = badge,
                    style = GuardianTheme.type.labelSm,
                    color = GuardianTheme.materialColors.onSurface,
                )
            }
        }

        Spacer(Modifier.height(GuardianTheme.spacing.md))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GuardianTheme.shapes.lg)
                .background(GuardianTheme.materialColors.surfaceContainerLowest)
                .padding(GuardianTheme.spacing.md),
        ) {
            Text(
                text = message,
                style = GuardianTheme.type.bodyMd,
                color = GuardianTheme.materialColors.onSurface,
            )
        }
    }
}

/** The gradient wash behind Angel, matched to her mood. */
@Composable
private fun moodTint(mood: AngelMood): Color = when (mood) {
    AngelMood.Resting, AngelMood.Sanctuary -> GuardianTheme.colors.accentSoft
    AngelMood.Cautious -> GuardianTheme.colors.peachCream
    AngelMood.Warning -> GuardianTheme.colors.accentWarm
    AngelMood.Critical -> GuardianTheme.colors.duressGlow
}
