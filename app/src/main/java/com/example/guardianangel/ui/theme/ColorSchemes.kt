package com.example.guardianangel.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The Material 3 scheme for Guardian Angel, light.
 *
 * `design.md` carries two overlapping palettes: a generated M3 token block in the
 * frontmatter (rose-tinted surfaces) and a hand-written brand narrative (cream-warm
 * surfaces) whose exact hex values every component spec then quotes. They disagree on the
 * surface family, so shipping both would read as two themes stapled together.
 *
 * Resolution: the surface ramp, foreground and borders follow the narrative — because the
 * Components section specifies components in terms of those literals — while the
 * accent/error ramps and the `*Fixed` roles come from the frontmatter, which is the only
 * source that supplies accessible `on*` pairings. The result is one coherent cream-warm
 * system in which every value the document names is honoured.
 */
internal val GuardianLightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = Color.White,
    primaryContainer = AccentSoft,
    onPrimaryContainer = Espresso,
    inversePrimary = PrimaryFixedDim,

    secondary = SecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = AccentWarm,
    onSecondaryContainer = Espresso,

    // Tertiary carries the "safe / all-clear" voice: soft buttery cream.
    tertiary = TertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = ButterCream,
    onTertiaryContainer = Espresso,

    background = Canvas,
    onBackground = Espresso,
    surface = Canvas,
    onSurface = Espresso,
    surfaceVariant = SurfaceContainerHighestLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceTint = PrimaryLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,

    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,

    // `outline` is the accessible control border (3.37:1); `outlineVariant` keeps the
    // design document's decorative peach divider.
    outline = BorderControl,
    outlineVariant = OutlineVariantLight,
    scrim = Color.Black,

    surfaceBright = Canvas,
    surfaceDim = SurfaceDimLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,

    primaryFixed = PrimaryFixed,
    primaryFixedDim = PrimaryFixedDim,
    onPrimaryFixed = OnPrimaryFixed,
    onPrimaryFixedVariant = OnPrimaryFixedVariant,
    secondaryFixed = SecondaryFixed,
    secondaryFixedDim = SecondaryFixedDim,
    onSecondaryFixed = OnSecondaryFixed,
    onSecondaryFixedVariant = OnSecondaryFixedVariant,
    tertiaryFixed = TertiaryFixed,
    tertiaryFixedDim = TertiaryFixedDim,
    onTertiaryFixed = OnTertiaryFixed,
    onTertiaryFixedVariant = OnTertiaryFixedVariant,
)

/** The warm espresso night scheme. See the note in `Color.kt`. */
internal val GuardianDarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    inversePrimary = PrimaryLight,

    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,

    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,

    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = BackgroundDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerHighestDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceTint = PrimaryDark,
    inverseSurface = OnSurfaceDark,
    inverseOnSurface = InverseSurfaceLight,

    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,

    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    scrim = Color.Black,

    surfaceBright = SurfaceBrightDark,
    surfaceDim = BackgroundDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,

    primaryFixed = PrimaryFixed,
    primaryFixedDim = PrimaryFixedDim,
    onPrimaryFixed = OnPrimaryFixed,
    onPrimaryFixedVariant = OnPrimaryFixedVariant,
    secondaryFixed = SecondaryFixed,
    secondaryFixedDim = SecondaryFixedDim,
    onSecondaryFixed = OnSecondaryFixed,
    onSecondaryFixedVariant = OnSecondaryFixedVariant,
    tertiaryFixed = TertiaryFixed,
    tertiaryFixedDim = TertiaryFixedDim,
    onTertiaryFixed = OnTertiaryFixed,
    onTertiaryFixedVariant = OnTertiaryFixedVariant,
)

/**
 * Brand tokens that Material 3 has no slot for.
 *
 * Exposed through [LocalGuardianColors] and read as `GuardianTheme.colors`, so components
 * never hard-code a hex value and dark mode stays correct for free.
 */
@Immutable
data class GuardianColorTokens(
    /** Flat page canvas (elevation level 0). */
    val canvas: Color,
    /** Card & content-block surface (elevation level 1). */
    val layer1: Color,
    /** Decorative card/divider border. Not a control affordance. */
    val borderDefault: Color,
    /** Decorative emphasized border for nested containers. */
    val borderEmphasis: Color,
    /** Accessible border for controls whose outline identifies them (>=3:1). */
    val borderControl: Color,
    /** Muted icon/label tint for inactive navigation and metadata (>=4:1). */
    val iconMuted: Color,

    /** Soft rose coral: live beacon, emergency trigger base, checkbox fill. */
    val accentSoft: Color,
    /** Content drawn on top of [accentSoft]. Always pair the two — the accent inverts
     *  between schemes (dark espresso-on-rose in light, deep maroon-on-rose in dark). */
    val onAccentSoft: Color,
    /** Warm apricot coral: active pills, badges, radio halo. */
    val accentWarm: Color,
    /** Content drawn on top of [accentWarm]. */
    val onAccentWarm: Color,
    /** Warm peach cream: secondary toggles, highlight panels. */
    val peachCream: Color,
    /** Buttery pale cream: safe status, gentle banners, card header strips. */
    val butterCream: Color,

    /** Container/content pair for the "guardian active" state. */
    val activeContainer: Color,
    val onActiveContainer: Color,
    /** Container/content pair for the "safe / all clear" state. */
    val safeContainer: Color,
    val onSafeContainer: Color,
    /** Container/content pair for the caution state. */
    val cautionContainer: Color,
    val onCautionContainer: Color,

    /** Solid duress fill — the SOS button. Carries white text at 5.48:1. */
    val duress: Color,
    /** Content on [duress]. */
    val onDuress: Color,
    /** High-visibility crimson for duress auras and halo strobes. Decorative only. */
    val duressGlow: Color,
    /** Diffused halo behind SOS / priority focal elements (elevation level 3). */
    val focalGlow: Color,
    /** Ambient warm shadow for cards (level 1). */
    val ambientShadow: Color,
    /** Ambient warm shadow for floating sheets & navigation (level 2). */
    val floatingShadow: Color,
    /** Ring colour for a focused field — accessible, paired with a [focalGlow] halo. */
    val focusRing: Color,
    /** True when the scheme in force is the dark one. */
    val isDark: Boolean,
)

internal val GuardianLightColorTokens = GuardianColorTokens(
    canvas = Canvas,
    layer1 = Layer1,
    borderDefault = BorderDefault,
    borderEmphasis = BorderEmphasis,
    borderControl = BorderControl,
    iconMuted = IconMuted,
    accentSoft = AccentSoft,
    onAccentSoft = Espresso,
    accentWarm = AccentWarm,
    onAccentWarm = Espresso,
    peachCream = PeachCream,
    butterCream = ButterCream,
    activeContainer = AccentWarm,
    onActiveContainer = Espresso,
    safeContainer = ButterCream,
    onSafeContainer = Espresso,
    cautionContainer = PeachCream,
    onCautionContainer = Espresso,
    duress = DuressRed,
    onDuress = Color.White,
    duressGlow = DuressBright,
    focalGlow = AccentSoft.copy(alpha = 0.45f),
    ambientShadow = Espresso.copy(alpha = 0.04f),
    floatingShadow = Espresso.copy(alpha = 0.08f),
    focusRing = PrimaryLight,
    isDark = false,
)

internal val GuardianDarkColorTokens = GuardianColorTokens(
    canvas = CanvasDark,
    layer1 = Layer1Dark,
    borderDefault = BorderDefaultDark,
    borderEmphasis = BorderEmphasisDark,
    borderControl = BorderControlDark,
    iconMuted = IconMutedDark,
    accentSoft = PrimaryDark,
    onAccentSoft = OnPrimaryDark,
    accentWarm = SecondaryFixedDim,
    onAccentWarm = OnSecondaryDark,
    peachCream = TertiaryFixedDim,
    butterCream = OnTertiaryContainerDark,
    activeContainer = SecondaryContainerDark,
    onActiveContainer = OnSecondaryContainerDark,
    safeContainer = TertiaryContainerDark,
    onSafeContainer = OnTertiaryContainerDark,
    cautionContainer = SecondaryContainerDark,
    onCautionContainer = OnSecondaryContainerDark,
    duress = DuressRed,
    onDuress = Color.White,
    duressGlow = DuressBright,
    focalGlow = PrimaryDark.copy(alpha = 0.35f),
    ambientShadow = Color.Black.copy(alpha = 0.40f),
    floatingShadow = Color.Black.copy(alpha = 0.55f),
    focusRing = PrimaryDark,
    isDark = true,
)

internal val LocalGuardianColors = staticCompositionLocalOf { GuardianLightColorTokens }
