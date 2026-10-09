package com.example.guardianangel.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Roundedness level 2 — "approachable, protective profiles".
 *
 * Mapped onto Material 3's five shape slots so stock components pick up the brand
 * silhouette: `small`/`medium` cover inner controls and inputs, `large` the standard card
 * and nav container, `extraLarge` the bottom sheets and feature widgets.
 */
val GuardianShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // rounded-sm  (0.25rem)
    small = RoundedCornerShape(8.dp),        // rounded     (0.5rem)
    medium = RoundedCornerShape(12.dp),      // rounded-md  (0.75rem)
    large = RoundedCornerShape(16.dp),       // rounded-lg  (1rem)
    extraLarge = RoundedCornerShape(24.dp),  // rounded-xl  (1.5rem)
)

/** The design system's named radii, including the pill the Material scale has no slot for. */
@Immutable
data class GuardianShapeTokens(
    /** Checkboxes, inner input icons, nested list items, miniature alert tags. */
    val sm: Shape = RoundedCornerShape(8.dp),
    /** Form field inputs — the 12dp radius the input spec calls for. */
    val md: Shape = RoundedCornerShape(12.dp),
    /** Standard cards, floating nav container, notification panels. */
    val lg: Shape = RoundedCornerShape(16.dp),
    /** Bottom sheet modals, feature widgets, check-in summary cards. */
    val xl: Shape = RoundedCornerShape(24.dp),
    /** Bottom sheets, which are only rounded at the top. */
    val bottomSheet: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    /** Chips, action buttons, quick-status indicators, safety dial triggers. */
    val pill: Shape = CircleShape,
)

val GuardianShapeTokenDefaults = GuardianShapeTokens()

internal val LocalGuardianShapes = staticCompositionLocalOf { GuardianShapeTokenDefaults }
