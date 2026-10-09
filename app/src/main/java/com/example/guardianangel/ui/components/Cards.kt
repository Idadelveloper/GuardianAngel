package com.example.guardianangel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.guardianCardElevation

/**
 * The standard content block: layer-1 surface, 1px decorative border, `rounded-lg`
 * corners and an ambient warm shadow (elevation level 1).
 *
 * Deliberately built on `Box`/`Column` rather than Material's `Card`, because the design
 * system's elevation model is a tinted ambient glow plus a tonal border rather than
 * Material's tonal-elevation overlay — layering the two would double-tint the surface.
 */
@Composable
fun GuardianCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = GuardianTheme.spacing.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = GuardianTheme.shapes.lg
    Column(
        modifier = modifier
            .fillMaxWidth()
            .guardianCardElevation(
                shape = shape,
                shadowColor = GuardianTheme.colors.ambientShadow,
                borderColor = GuardianTheme.colors.borderDefault,
            )
            .clip(shape)
            .background(GuardianTheme.colors.layer1)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * A highlighted card — "Active Guardian", "Safe Zone Reached" — carrying the soft buttery
 * header strip the design system calls for, over the same layer-1 body.
 *
 * @param accent the strip colour; defaults to the safe/all-clear cream.
 */
@Composable
fun GuardianHighlightCard(
    modifier: Modifier = Modifier,
    accent: Color = GuardianTheme.colors.safeContainer,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = GuardianTheme.spacing.lg,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = GuardianTheme.shapes.xl
    Column(
        modifier = modifier
            .fillMaxWidth()
            .guardianCardElevation(
                shape = shape,
                shadowColor = GuardianTheme.colors.ambientShadow,
                borderColor = GuardianTheme.colors.borderEmphasis,
            )
            .clip(shape)
            .background(GuardianTheme.colors.layer1),
    ) {
        if (header != null) {
            // Header strip: the accent, fading into the card body so the seam stays soft.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(accent, accent.copy(alpha = 0.55f)),
                        )
                    )
                    .padding(
                        horizontal = contentPadding,
                        vertical = GuardianTheme.spacing.md,
                    ),
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides GuardianTheme.colors.onSafeContainer
                ) {
                    ProvideTextStyle(GuardianTheme.type.labelMd) { header() }
                }
            }
        } else {
            // Without a header, keep a thin accent cap so the card still reads as elevated.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(accent),
            )
        }
        Column(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(contentPadding),
            content = content,
        )
    }
}
