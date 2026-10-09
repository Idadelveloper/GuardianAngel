package com.example.guardianangel.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.focalHalo

/** Height of every standard Guardian Angel action, per `design.md › Buttons`. */
private val ButtonHeight = 52.dp
private val ButtonContentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp)
private val ButtonIconSize = 20.dp

/**
 * Primary dark action — espresso fill, canvas text, full pill.
 * For high-priority standard actions and confirmations.
 */
@Composable
fun GuardianPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(ButtonHeight),
        enabled = enabled,
        shape = GuardianTheme.shapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = GuardianTheme.materialColors.onSurface,
            contentColor = GuardianTheme.colors.canvas,
            disabledContainerColor = GuardianTheme.materialColors.onSurface.copy(alpha = 0.12f),
            disabledContentColor = GuardianTheme.materialColors.onSurface.copy(alpha = 0.38f),
        ),
        contentPadding = ButtonContentPadding,
    ) {
        ButtonContent(text, leadingIcon)
    }
}

/**
 * Soft accent action — rose coral fill with espresso text. The everyday check-in control,
 * sized for immediate visibility.
 */
@Composable
fun GuardianAccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(ButtonHeight),
        enabled = enabled,
        shape = GuardianTheme.shapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = GuardianTheme.materialColors.primaryContainer,
            contentColor = GuardianTheme.materialColors.onPrimaryContainer,
            disabledContainerColor = GuardianTheme.materialColors.primaryContainer.copy(alpha = 0.38f),
            disabledContentColor = GuardianTheme.materialColors.onPrimaryContainer.copy(alpha = 0.38f),
        ),
        contentPadding = ButtonContentPadding,
    ) {
        ButtonContent(text, leadingIcon)
    }
}

/** Secondary outlined action — transparent, 1.5dp emphasized border. Unhurried. */
@Composable
fun GuardianOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(ButtonHeight),
        enabled = enabled,
        shape = GuardianTheme.shapes.pill,
        border = BorderStroke(
            width = 1.5.dp,
            // The accessible control border, not the decorative peach: this outline is the
            // only thing identifying the button.
            color = if (enabled) {
                GuardianTheme.colors.borderControl
            } else {
                GuardianTheme.colors.borderControl.copy(alpha = 0.38f)
            },
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = GuardianTheme.materialColors.onSurface,
            disabledContentColor = GuardianTheme.materialColors.onSurface.copy(alpha = 0.38f),
        ),
        contentPadding = ButtonContentPadding,
    ) {
        ButtonContent(text, leadingIcon)
    }
}

@Composable
private fun ButtonContent(text: String, leadingIcon: ImageVector?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(ButtonIconSize),
            )
        }
        Text(text = text, style = GuardianTheme.type.labelLg)
    }
}

/**
 * Emergency trigger — concentric pulsing circles over a rose-coral base with an apricot
 * ripple ring, sitting in the diffused level-3 halo.
 *
 * The pulse is slow and even on purpose: `design.md` asks micro-interactions to stay
 * "fluid, calm and deliberate … to keep stress low during critical moments", so this
 * breathes rather than flashing. It also stops entirely when [pulsing] is false, so the
 * control can be parked quietly when no alert is armed.
 */
@Composable
fun EmergencyTrigger(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "SOS",
    contentDescription: String = "Send an emergency alert",
    size: Dp = 168.dp,
    pulsing: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "emergencyPulse")
    val pulseSpec: InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
    )
    val ripple by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = pulseSpec,
        label = "rippleScale",
    )
    val haloIntensity by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = pulseSpec,
        label = "haloIntensity",
    )

    // Bound to a local: inside `semantics { }` a bare `contentDescription` would resolve
    // to the receiver's write-only property rather than this parameter.
    val accessibleName = contentDescription
    val rippleScale = if (pulsing) ripple else 1f
    val glow = if (pulsing) haloIntensity else 0.7f
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        // Outer apricot ripple ring, breathing outward.
        Box(
            modifier = Modifier
                .size(size)
                .scale(rippleScale)
                .focalHalo(
                    color = GuardianTheme.colors.accentWarm.copy(alpha = 0.40f),
                    spread = 1.0f,
                    intensity = glow,
                ),
        )
        // The level-3 peach-coral halo.
        Box(
            modifier = Modifier
                .size(size * 0.74f)
                .focalHalo(
                    color = GuardianTheme.colors.focalGlow,
                    spread = 1.45f,
                    intensity = glow,
                ),
        )
        Button(
            onClick = onClick,
            modifier = Modifier
                .size(size * 0.62f)
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { this.contentDescription = accessibleName },
            shape = GuardianTheme.shapes.pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = GuardianTheme.colors.accentSoft,
                contentColor = GuardianTheme.colors.onAccentSoft,
            ),
            interactionSource = interactionSource,
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(text = label, style = GuardianTheme.type.headlineMd)
        }
    }
}
