package com.example.guardianangel.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme

/**
 * Checkbox — 20x20dp, 8dp radius, 2dp border. Checked state fills with soft rose and
 * shows an espresso checkmark.
 *
 * Hand-rolled rather than Material's `Checkbox` because the spec's box is 20dp with an
 * 8dp radius, where Material's is 18dp at 2dp — and because the brand fill/checkmark
 * pairing inverts Material's (tonal container, dark mark) convention.
 */
@Composable
fun GuardianCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
) {
    val colors = GuardianTheme.colors
    val boxShape = RoundedCornerShape(6.dp)

    val fill by animateColorAsState(
        targetValue = when {
            !enabled -> colors.borderControl.copy(alpha = 0.18f)
            checked -> colors.accentSoft
            else -> Color.Transparent
        },
        animationSpec = tween(160),
        label = "checkboxFill",
    )
    val border by animateColorAsState(
        targetValue = when {
            !enabled -> colors.borderControl.copy(alpha = 0.38f)
            checked -> colors.accentSoft
            else -> colors.borderControl
        },
        animationSpec = tween(160),
        label = "checkboxBorder",
    )

    val row = modifier.then(
        if (onCheckedChange != null) {
            Modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
        } else {
            Modifier
        }
    )

    Row(
        modifier = row,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(boxShape)
                .background(fill)
                .border(width = 2.dp, color = border, shape = boxShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = GuardianIcons.Check,
                    contentDescription = null,
                    tint = colors.onAccentSoft,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        if (label != null) {
            Text(
                text = label,
                style = GuardianTheme.type.bodyMd,
                color = if (enabled) {
                    GuardianTheme.materialColors.onSurface
                } else {
                    GuardianTheme.materialColors.onSurface.copy(alpha = 0.38f)
                },
            )
        }
    }
}

/**
 * Radio button — circular, 2dp border, espresso inner dot with an apricot background halo
 * when selected.
 */
@Composable
fun GuardianRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
) {
    val colors = GuardianTheme.colors

    val halo by animateColorAsState(
        targetValue = when {
            !enabled -> colors.borderControl.copy(alpha = 0.18f)
            selected -> colors.accentWarm
            else -> Color.Transparent
        },
        animationSpec = tween(160),
        label = "radioHalo",
    )
    val border by animateColorAsState(
        targetValue = when {
            !enabled -> colors.borderControl.copy(alpha = 0.38f)
            selected -> colors.accentWarm
            else -> colors.borderControl
        },
        animationSpec = tween(160),
        label = "radioBorder",
    )
    val dotSize by animateDpAsState(
        targetValue = if (selected) 10.dp else 0.dp,
        animationSpec = tween(160),
        label = "radioDot",
    )

    val row = modifier.then(
        if (onClick != null) {
            Modifier.selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
        } else {
            Modifier
        }
    )

    Row(
        modifier = row,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(halo)
                .border(width = 2.dp, color = border, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(
                        if (enabled) {
                            colors.onAccentWarm
                        } else {
                            GuardianTheme.materialColors.onSurface.copy(alpha = 0.38f)
                        }
                    ),
            )
        }
        if (label != null) {
            Text(
                text = label,
                style = GuardianTheme.type.bodyMd,
                color = if (enabled) {
                    GuardianTheme.materialColors.onSurface
                } else {
                    GuardianTheme.materialColors.onSurface.copy(alpha = 0.38f)
                },
            )
        }
    }
}
