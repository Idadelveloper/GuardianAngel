package com.example.guardianangel.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.ambientGlow

/**
 * Guardian Angel text field — 48dp tall, 12dp radius, canvas fill, 1.5dp border.
 *
 * On focus the border moves to the accessible focus ring and picks up the rose ambient
 * glow. The design document specifies the focus border as the soft rose `#FFB1B1`, which
 * measures 1.70:1 against the canvas — below the 3:1 WCAG 1.4.11 floor for a focus
 * indicator. The rose is kept as the surrounding glow for the intended warmth, while the
 * ring itself uses the brand primary at 6.35:1 so the focused field is unmistakable.
 */
@Composable
fun GuardianTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    trailingIconDescription: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = GuardianTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.borderControl.copy(alpha = 0.38f)
            isError -> GuardianTheme.materialColors.error
            focused -> colors.focusRing
            else -> colors.borderControl
        },
        animationSpec = tween(durationMillis = 180),
        label = "fieldBorder",
    )

    val shape = GuardianTheme.shapes.md
    val selectionColors = TextSelectionColors(
        handleColor = colors.focusRing,
        backgroundColor = colors.accentSoft.copy(alpha = 0.4f),
    )

    Column(modifier = modifier) {
        if (label != null) {
            // label-md floating above the field, per the input spec.
            Text(
                text = label,
                style = GuardianTheme.type.labelMd,
                color = if (enabled) {
                    GuardianTheme.materialColors.onSurface
                } else {
                    GuardianTheme.materialColors.onSurface.copy(alpha = 0.38f)
                },
                modifier = Modifier.padding(bottom = GuardianTheme.spacing.xs),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    // Ambient rose glow only while focused.
                    if (focused && !isError) {
                        Modifier.ambientGlow(
                            color = colors.accentSoft.copy(alpha = 0.25f),
                            shape = shape,
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(shape)
                .background(if (enabled) colors.canvas else colors.borderDefault.copy(alpha = 0.4f))
                .border(width = 1.5.dp, color = borderColor, shape = shape)
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = GuardianTheme.spacing.md),
            contentAlignment = Alignment.CenterStart,
        ) {
            CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    textStyle = GuardianTheme.type.bodyMd.copy(
                        color = if (enabled) {
                            GuardianTheme.materialColors.onSurface
                        } else {
                            GuardianTheme.materialColors.onSurface.copy(alpha = 0.38f)
                        },
                    ),
                    cursorBrush = SolidColor(colors.focusRing),
                    keyboardOptions = keyboardOptions,
                    visualTransformation = visualTransformation,
                    interactionSource = interactionSource,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
                        ) {
                            if (leadingIcon != null) {
                                Icon(
                                    imageVector = leadingIcon,
                                    contentDescription = null,
                                    tint = colors.iconMuted,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                if (value.isEmpty() && placeholder != null) {
                                    Text(
                                        text = placeholder,
                                        style = GuardianTheme.type.bodyMd,
                                        color = colors.iconMuted,
                                    )
                                }
                                innerTextField()
                            }
                            if (trailingIcon != null) {
                                Icon(
                                    imageVector = trailingIcon,
                                    contentDescription = trailingIconDescription,
                                    tint = colors.iconMuted,
                                    modifier = Modifier
                                        .then(
                                            if (onTrailingIconClick != null) {
                                                // 40dp keeps the tap target usable even
                                                // though the glyph itself is 20dp.
                                                Modifier
                                                    .size(40.dp)
                                                    .clip(GuardianTheme.shapes.pill)
                                                    .clickable(onClick = onTrailingIconClick)
                                                    .padding(10.dp)
                                            } else {
                                                Modifier.size(20.dp)
                                            }
                                        ),
                                )
                            }
                        }
                    },
                )
            }
        }

        if (supportingText != null) {
            Text(
                text = supportingText,
                style = GuardianTheme.type.bodySm,
                color = if (isError) {
                    GuardianTheme.materialColors.error
                } else {
                    GuardianTheme.materialColors.onSurfaceVariant
                },
                modifier = Modifier.padding(
                    start = GuardianTheme.spacing.xs,
                    top = GuardianTheme.spacing.xs,
                ),
            )
        }
    }
}
