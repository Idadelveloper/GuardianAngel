package com.example.guardianangel.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Guardian Angel raw palette.
 *
 * These are the literal brand values from `design.md`. Nothing in the app should reference
 * them directly — consume [GuardianColors] or `MaterialTheme.colorScheme` instead so that
 * light/dark switching keeps working. They live here only so the mapping from the design
 * document to the Material 3 scheme stays auditable.
 *
 * Contrast note: every text pairing used by the schemes in `ColorSchemes.kt` was verified
 * against WCAG 2.1 AA (4.5:1 for text, 3:1 for meaningful non-text). Where the design
 * document's decorative values fell short of 3:1 for elements that *identify* a control
 * (form borders, focus rings, inactive nav icons), an accessible sibling token was added
 * rather than silently shipping a non-conformant value. See [BorderControl], [IconMuted].
 */

// ---------------------------------------------------------------------------
// Warm surface system (design.md › Surface System)
// ---------------------------------------------------------------------------

/** Surface canvas — "pure, soft daylight foundation". */
val Canvas = Color(0xFFFFFDF7)

/** Surface layer 1 — cards & sheets. */
val Layer1 = Color(0xFFFFF7F0)

/** Steps interpolated between [Canvas] and [SurfaceDimLight] to fill the M3 container ramp. */
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFFFFBF5)
val SurfaceContainerLight = Color(0xFFFFF7F0)
val SurfaceContainerHighLight = Color(0xFFFBF0E7)
val SurfaceContainerHighestLight = Color(0xFFF6E9DE)
val SurfaceDimLight = Color(0xFFEDDFD4)

// ---------------------------------------------------------------------------
// Accents (design.md › Core Roles)
// ---------------------------------------------------------------------------

/** Soft rose coral — active status, safety beacon highlights, focal interactive elements. */
val AccentSoft = Color(0xFFFFB1B1)

/** Warm apricot coral — primary state card fills, active tab pills, badge backgrounds. */
val AccentWarm = Color(0xFFFFCCB8)

/** Warm peach cream — secondary toggles, contextual highlight panels. */
val PeachCream = Color(0xFFFFDBB0)

/** Soft buttery pale cream — safe status, gentle banners, card glow accents. */
val ButterCream = Color(0xFFFFFAD3)

/** Deep warm espresso charcoal — primary type and iconography on light surfaces. */
val Espresso = Color(0xFF2D2426)

// ---------------------------------------------------------------------------
// Borders & dividers (design.md › Borders & Dividers)
// ---------------------------------------------------------------------------

/** Default border — card boundaries, dividers. Decorative only (1.27:1): never the sole
 *  means of identifying an interactive control. */
val BorderDefault = Color(0xFFF2DFD5)

/** Emphasized border — nested containers, segmented controls. Decorative (1.47:1). */
val BorderEmphasis = Color(0xFFE8CEBF)

/** Accessible warm-taupe border for elements whose boundary *identifies* the control
 *  (text fields, checkboxes, radios, outlined buttons). 3.37:1 on [Canvas],
 *  3.23:1 on [Layer1] — satisfies WCAG 1.4.11. */
val BorderControl = Color(0xFF9E8678)

/** Muted warm icon/label tint, 4.40:1 on [Canvas]. Replaces the design document's
 *  "espresso at 45% opacity" for inactive navigation, which measured only 2.70:1. */
val IconMuted = Color(0xFF857372)

// ---------------------------------------------------------------------------
// Brand ramps (design.md frontmatter)
// ---------------------------------------------------------------------------

val PrimaryLight = Color(0xFF8A4D4E)
val OnPrimaryContainerLight = Color(0xFF7B4142)
val PrimaryFixed = Color(0xFFFFDAD9)
val PrimaryFixedDim = Color(0xFFFFB3B3)
val OnPrimaryFixed = Color(0xFF380C0F)
val OnPrimaryFixedVariant = Color(0xFF6E3638)

val SecondaryLight = Color(0xFF7B5546)
val OnSecondaryContainerLight = Color(0xFF7A5445)
val SecondaryFixed = Color(0xFFFFDBCD)
val SecondaryFixedDim = Color(0xFFEDBCA8)
val OnSecondaryFixed = Color(0xFF2F1409)
val OnSecondaryFixedVariant = Color(0xFF613E30)

val TertiaryLight = Color(0xFF745A38)
val OnTertiaryContainerLight = Color(0xFF664D2D)
val TertiaryFixed = Color(0xFFFFDDB5)
val TertiaryFixedDim = Color(0xFFE3C197)
val OnTertiaryFixed = Color(0xFF291800)
val OnTertiaryFixedVariant = Color(0xFF5A4223)

val OnSurfaceVariantLight = Color(0xFF524343)
val OutlineVariantLight = Color(0xFFE8CEBF)
val InverseSurfaceLight = Color(0xFF372E30)
val InverseOnSurfaceLight = Color(0xFFFEEDEF)

val ErrorLight = Color(0xFFBA1A1A)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF93000A)

// ---------------------------------------------------------------------------
// Warm dark scheme — derived from the frontmatter's inverse/dim/fixed tokens.
// design.md specifies a light theme only; these keep the brand coherent at night,
// which matters for an app used after dark. All pairings verified at AA.
// ---------------------------------------------------------------------------

val BackgroundDark = Color(0xFF191113)
val SurfaceContainerLowestDark = Color(0xFF130C0E)
val SurfaceContainerLowDark = Color(0xFF211A1B)
val SurfaceContainerDark = Color(0xFF251D1F)
val SurfaceContainerHighDark = Color(0xFF302829)
val SurfaceContainerHighestDark = Color(0xFF3B3234)
val SurfaceBrightDark = Color(0xFF403739)
val OnSurfaceDark = Color(0xFFEFDFE1)
val OnSurfaceVariantDark = Color(0xFFD7C2C1)
val OutlineDark = Color(0xFFA08C8B)
val OutlineVariantDark = Color(0xFF524343)

val PrimaryDark = Color(0xFFFFB3B3)
val OnPrimaryDark = Color(0xFF561D20)
val PrimaryContainerDark = Color(0xFF7B4142)
val OnPrimaryContainerDark = Color(0xFFFFDAD9)

val SecondaryDark = Color(0xFFEDBCA8)
val OnSecondaryDark = Color(0xFF482A1C)
val SecondaryContainerDark = Color(0xFF613E30)
val OnSecondaryContainerDark = Color(0xFFFFDBCD)

val TertiaryDark = Color(0xFFE3C197)
val OnTertiaryDark = Color(0xFF412C0E)
val TertiaryContainerDark = Color(0xFF5A4223)
val OnTertiaryContainerDark = Color(0xFFFFDDB5)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

/** Dark-mode counterparts of the warm brand surfaces/borders. */
val CanvasDark = BackgroundDark
val Layer1Dark = SurfaceContainerDark
val BorderDefaultDark = Color(0xFF3A3032)
val BorderEmphasisDark = Color(0xFF4A3E3E)
val BorderControlDark = OutlineDark
val IconMutedDark = Color(0xFFB4A0A0)
