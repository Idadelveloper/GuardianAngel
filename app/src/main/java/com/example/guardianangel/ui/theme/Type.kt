package com.example.guardianangel.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.guardianangel.R

/**
 * Plus Jakarta Sans — the sole typographic voice of the design system.
 *
 * Shipped as five static instances cut from the upstream variable font
 * (`PlusJakartaSans[wght].ttf`, SIL OFL 1.1) at the exact weights the design system uses.
 * Static cuts rather than the variable file because `minSdk` is 24 while font variation
 * settings only take effect from API 26 — on 24/25 a variable font would render every
 * weight at its default instance and let the platform fake the rest.
 */
val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_400, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_500, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_600, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_700, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_800, FontWeight.ExtraBold),
)

/**
 * Trim the extra leading Android adds above the first line and below the last, so a
 * declared line height produces the spacing the design system actually specifies.
 */
private val GuardianLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun guardianStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: Double = 0.0,
): TextStyle = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.em,
    lineHeightStyle = GuardianLineHeightStyle,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/**
 * The design system's named scale, verbatim from `design.md › typography`.
 *
 * Read via `GuardianTheme.type`. Prefer `MaterialTheme.typography` for anything a Material
 * component styles itself; reach for these when a spec names the token directly.
 */
@Immutable
data class GuardianTypeScale(
    val headlineXl: TextStyle,
    val headlineXlMobile: TextStyle,
    val headlineLg: TextStyle,
    val headlineLgMobile: TextStyle,
    val headlineMd: TextStyle,
    val bodyLg: TextStyle,
    val bodyMd: TextStyle,
    val bodySm: TextStyle,
    val labelLg: TextStyle,
    val labelMd: TextStyle,
    val labelSm: TextStyle,
)

val GuardianTypeScaleDefaults = GuardianTypeScale(
    headlineXl = guardianStyle(36, 44, FontWeight.Bold, -0.02),
    headlineXlMobile = guardianStyle(28, 36, FontWeight.Bold, -0.01),
    headlineLg = guardianStyle(30, 38, FontWeight.Bold, -0.01),
    headlineLgMobile = guardianStyle(24, 32, FontWeight.SemiBold, -0.01),
    headlineMd = guardianStyle(20, 28, FontWeight.SemiBold),
    bodyLg = guardianStyle(17, 26, FontWeight.Normal),
    bodyMd = guardianStyle(15, 22, FontWeight.Normal),
    bodySm = guardianStyle(13, 18, FontWeight.Normal),
    labelLg = guardianStyle(15, 20, FontWeight.SemiBold, 0.01),
    labelMd = guardianStyle(13, 18, FontWeight.SemiBold, 0.01),
    labelSm = guardianStyle(11, 14, FontWeight.Bold, 0.03),
)

/**
 * The design scale projected onto Material 3's fifteen slots, so stock Material
 * components inherit the brand voice without being individually restyled.
 *
 * `display*` extends the scale upward from `headline-xl` at the same 700 weight and
 * negative tracking; `title*` maps onto the `headline-md`/`label-lg` register, which is
 * what Material uses titles for.
 */
val GuardianTypography: Typography = with(GuardianTypeScaleDefaults) {
    Typography(
        displayLarge = guardianStyle(57, 64, FontWeight.Bold, -0.02),
        displayMedium = guardianStyle(45, 52, FontWeight.Bold, -0.02),
        displaySmall = guardianStyle(36, 44, FontWeight.Bold, -0.02),

        headlineLarge = headlineXl,
        headlineMedium = headlineLg,
        headlineSmall = guardianStyle(24, 32, FontWeight.SemiBold, -0.01),

        titleLarge = guardianStyle(22, 30, FontWeight.SemiBold, -0.01),
        titleMedium = headlineMd,
        titleSmall = labelLg,

        bodyLarge = bodyLg,
        bodyMedium = bodyMd,
        bodySmall = bodySm,

        labelLarge = labelLg,
        labelMedium = labelMd,
        labelSmall = labelSm,
    )
}

internal val LocalGuardianTypeScale = staticCompositionLocalOf { GuardianTypeScaleDefaults }
