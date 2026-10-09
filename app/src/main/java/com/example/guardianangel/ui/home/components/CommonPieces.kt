package com.example.guardianangel.ui.home.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.guardianangel.ui.theme.GuardianTheme

/** Section heading with an optional trailing action, used between home screen blocks. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GuardianTheme.materialColors.secondary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = title,
            style = GuardianTheme.type.labelLg,
            color = GuardianTheme.materialColors.onSurface,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

/**
 * A compact "label over value" readout — battery, GPS accuracy, time away from base.
 *
 * @param emphasis tints the value, used to mark a factor that is dragging the score down.
 */
@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = GuardianTheme.materialColors.secondary,
    emphasis: Color? = null,
    container: Color = GuardianTheme.materialColors.surfaceContainerHigh,
) {
    Row(
        modifier = modifier
            .clip(GuardianTheme.shapes.lg)
            .background(container)
            .padding(GuardianTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = GuardianTheme.type.labelMd,
                color = emphasis ?: GuardianTheme.materialColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A slowly breathing dot marking a live state.
 *
 * Purely decorative — the adjacent text always states the same thing, so the animation is
 * hidden from accessibility services and nothing is lost if it is not perceived.
 */
@Composable
fun PulsingDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
    animate: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "pulseDot")
    val scale by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotScale",
    )
    Box(
        modifier = modifier
            .size(size)
            .scale(if (animate) scale else 1f)
            .clip(CircleShape)
            .background(color)
            .clearAndSetSemantics { },
    )
}

/** A circular monogram standing in for a contact photo. */
@Composable
fun InitialsAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    container: Color = GuardianTheme.materialColors.secondaryContainer,
    content: Color = GuardianTheme.materialColors.onSecondaryContainer,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .border(1.dp, GuardianTheme.colors.borderEmphasis, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = initials, style = GuardianTheme.type.labelMd, color = content)
    }
}

/** Small tonal pill used for inline state ("Encrypted", "Live Scan", "2 Online"). */
@Composable
fun TonalPill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = GuardianTheme.materialColors.secondaryContainer,
    content: Color = GuardianTheme.materialColors.onSecondaryContainer,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(GuardianTheme.shapes.pill)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(13.dp),
            )
        }
        Text(
            text = text,
            style = GuardianTheme.type.labelSm,
            color = content,
            maxLines = 1,
            softWrap = false,
        )
    }
}
