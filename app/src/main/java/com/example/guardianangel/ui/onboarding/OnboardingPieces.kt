package com.example.guardianangel.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.home.components.TonalPill
import com.example.guardianangel.ui.mascot.AngelMascot
import com.example.guardianangel.ui.mascot.AngelMood
import com.example.guardianangel.ui.theme.GuardianTheme

/**
 * Angel saying something, with a speech bubble.
 *
 * Used at the top of every onboarding step so the wizard reads as a conversation with the
 * mascot rather than a form to fill in. Angel is decorative here — the bubble carries the
 * same message in text — so it is not announced separately.
 */
@Composable
fun AngelSays(
    message: String,
    modifier: Modifier = Modifier,
    mood: AngelMood = AngelMood.Resting,
    mascotSize: androidx.compose.ui.unit.Dp = 104.dp,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AngelMascot(mood = mood, size = mascotSize)
        Spacer(Modifier.height(GuardianTheme.spacing.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GuardianTheme.shapes.lg)
                .background(GuardianTheme.materialColors.surfaceContainerLowest)
                .border(1.dp, GuardianTheme.colors.borderDefault, GuardianTheme.shapes.lg)
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

/**
 * A permission or feature card with an inline switch.
 *
 * The switch is the affordance *and* the state, so the whole card is clickable — a 48dp
 * target beats asking someone to hit a 32dp thumb.
 */
@Composable
fun ToggleCard(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    GuardianCard(
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        contentPadding = GuardianTheme.spacing.md,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(GuardianTheme.shapes.md)
                    .background(GuardianTheme.materialColors.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = GuardianTheme.materialColors.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(GuardianTheme.spacing.sm))
            Text(
                text = title,
                style = GuardianTheme.type.labelLg,
                color = GuardianTheme.materialColors.onSurface,
                modifier = Modifier.weight(1f),
            )
            GuardianSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
        Spacer(Modifier.height(GuardianTheme.spacing.sm))
        Text(
            text = description,
            style = GuardianTheme.type.bodySm,
            color = GuardianTheme.materialColors.onSurfaceVariant,
        )
    }
}

/** Brand switch — a pill track with a sliding thumb, sized to the Material touch target. */
@Composable
fun GuardianSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val track by animateColorAsState(
        targetValue = when {
            !enabled -> GuardianTheme.colors.borderControl.copy(alpha = 0.3f)
            checked -> GuardianTheme.colors.accentSoft
            else -> GuardianTheme.materialColors.surfaceContainerHigh
        },
        animationSpec = tween(180),
        label = "switchTrack",
    )
    val offset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = tween(180),
        label = "switchThumb",
    )
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, GuardianTheme.colors.borderControl.copy(alpha = 0.4f), CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = offset)
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (checked) {
                        GuardianTheme.materialColors.surfaceContainerLowest
                    } else {
                        GuardianTheme.colors.canvas
                    }
                ),
        )
    }
}

/** A row of single-select chips, e.g. the relationship picker. */
@Composable
fun ChipRow(
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            TonalPill(
                text = option,
                container = if (isSelected) {
                    GuardianTheme.colors.accentSoft
                } else {
                    GuardianTheme.materialColors.surfaceContainerLowest
                },
                content = if (isSelected) {
                    GuardianTheme.colors.onAccentSoft
                } else {
                    GuardianTheme.materialColors.onSurfaceVariant
                },
                modifier = Modifier
                    .clip(GuardianTheme.shapes.pill)
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Color.Transparent else GuardianTheme.colors.borderControl,
                        shape = GuardianTheme.shapes.pill,
                    )
                    .padding(vertical = 4.dp),
            )
        }
    }
}

/** A quiet reassurance footer — the privacy guarantees that run through onboarding. */
@Composable
fun AssuranceCard(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(GuardianTheme.shapes.lg)
            .background(GuardianTheme.materialColors.surfaceContainerLow)
            .padding(GuardianTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GuardianTheme.materialColors.primary,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(
                text = title,
                style = GuardianTheme.type.labelMd,
                color = GuardianTheme.materialColors.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                style = GuardianTheme.type.bodySm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}
