package com.example.guardianangel.ui.theme

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elevation here is "ambient warm glow, tonal layering and gentle peach-tinted outline"
 * rather than a hard drop shadow, per `design.md › Elevation & Depth`.
 *
 * Shadow tint (`ambientColor`/`spotColor`) is honoured from API 28; on 24–27 the platform
 * falls back to its own neutral shadow at the same elevation, which is a quiet degradation
 * rather than a visual break — the tonal surface and tinted border carry the layering.
 */
object GuardianElevation {
    /** Level 0 — flat canvas. Dividers do the structural work. */
    val Level0: Dp = 0.dp

    /** Level 1 — cards & content blocks: `0 4px 16px rgba(45, 36, 38, 0.04)`. */
    val Level1: Dp = 4.dp

    /** Level 2 — floating navigation, active sheets: `0 8px 24px rgba(45, 36, 38, 0.08)`. */
    val Level2: Dp = 8.dp

    /** Level 3 — SOS / priority focal elements, drawn as a diffused halo. */
    val Level3: Dp = 12.dp
}

/**
 * Level 1 — the standard card treatment: ambient warm shadow plus the 1px decorative
 * border, clipped to [shape].
 */
fun Modifier.guardianCardElevation(
    shape: Shape,
    shadowColor: Color,
    borderColor: Color,
): Modifier = this
    .shadow(
        elevation = GuardianElevation.Level1,
        shape = shape,
        ambientColor = shadowColor,
        spotColor = shadowColor,
    )
    .border(width = 1.dp, color = borderColor, shape = shape)

/**
 * Level 2 — floating navigation bars and active sheets: a deeper ambient warm shadow with
 * the emphasized peach outline.
 */
fun Modifier.guardianFloatingElevation(
    shape: Shape,
    shadowColor: Color,
    borderColor: Color,
): Modifier = this
    .shadow(
        elevation = GuardianElevation.Level2,
        shape = shape,
        ambientColor = shadowColor,
        spotColor = shadowColor,
    )
    .border(width = 1.dp, color = borderColor, shape = shape)

/**
 * Level 3 — the peach-coral diffused halo behind an emergency trigger or a live status
 * indicator (`0 0 24px rgba(255, 177, 177, 0.45)`).
 *
 * Drawn as a radial gradient rather than a blurred shadow so the glow is a true soft
 * falloff on every supported API level, and so it can bleed outside the element's bounds
 * without being clipped.
 *
 * @param spread how far past the element's own radius the halo reaches, as a multiplier.
 * @param intensity scales the halo's opacity — animate this for a breathing pulse.
 */
fun Modifier.focalHalo(
    color: Color,
    spread: Float = 1.6f,
    intensity: Float = 1f,
): Modifier = this.drawBehind {
    if (intensity <= 0f) return@drawBehind
    val haloRadius = size.minDimension / 2f * spread
    val tint = color.copy(alpha = color.alpha * intensity.coerceIn(0f, 1f))
    drawCircle(
        brush = Brush.radialGradient(
            // Hold the colour through the element's own footprint, then fall away softly.
            colorStops = arrayOf(
                0f to tint,
                (1f / spread).coerceIn(0.05f, 0.95f) to tint,
                1f to Color.Transparent,
            ),
            center = center,
            radius = haloRadius,
        ),
        radius = haloRadius,
        center = center,
    )
}

/**
 * A soft ambient glow around an arbitrary shape — used for the focused-input ring
 * (`rgba(255, 177, 177, 0.25)`) and for highlighted cards.
 */
fun Modifier.ambientGlow(
    color: Color,
    shape: Shape = RectangleShape,
    elevation: Dp = GuardianElevation.Level2,
): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    ambientColor = color,
    spotColor = color,
)
