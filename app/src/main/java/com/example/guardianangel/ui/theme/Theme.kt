package com.example.guardianangel.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * The Guardian Angel theme.
 *
 * Wrap the whole app in this exactly once, at the activity root. It installs the Material 3
 * scheme, the Plus Jakarta Sans type scale and the brand shapes, and additionally provides
 * the brand-only tokens ([GuardianColorTokens], [GuardianSpacing], [GuardianShapeTokens],
 * [GuardianTypeScale]) that Material has no slot for. Read those through [GuardianTheme].
 *
 * Dynamic colour is deliberately **not** offered. Guardian Angel's palette is a brand and
 * safety signal — "soft rose means your guardian is live", "buttery cream means safe" —
 * and letting the device wallpaper repaint those states would undermine both the identity
 * and the legibility the design document treats as life-critical.
 */
@Composable
fun GuardianAngelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) GuardianDarkColorScheme else GuardianLightColorScheme
    val guardianColors = if (darkTheme) GuardianDarkColorTokens else GuardianLightColorTokens

    // Grid architecture: margins and gutters widen with the window, per design.md.
    // Measured from the window container rather than Configuration.screenWidthDp, which
    // reports the whole display and so picks the wrong breakpoint in split-screen and
    // freeform windows.
    val containerWidthPx = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current
    val widthDp = remember(containerWidthPx, density) {
        with(density) { containerWidthPx.toDp() }
    }
    val sizeClass = remember(widthDp) { WindowSizeClass.fromWidth(widthDp) }
    val spacing = remember(sizeClass) {
        GuardianSpacingDefaults.copy(
            screenMargin = sizeClass.screenMargin,
            gutter = sizeClass.gutter,
        )
    }

    // Edge-to-edge: match system bar icon polarity to the scheme in force so the status
    // bar stays legible against the warm canvas.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalGuardianColors provides guardianColors,
        LocalGuardianSpacing provides spacing,
        LocalGuardianShapes provides GuardianShapeTokenDefaults,
        LocalGuardianTypeScale provides GuardianTypeScaleDefaults,
        LocalWindowSizeClass provides sizeClass,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GuardianTypography,
            shapes = GuardianShapes,
            content = content,
        )
    }
}

/**
 * Accessor for the brand tokens that sit alongside [MaterialTheme].
 *
 * `GuardianTheme.colors.accentSoft`, `GuardianTheme.spacing.lg`, `GuardianTheme.shapes.pill`,
 * `GuardianTheme.type.labelSm`. Material-native values stay on `MaterialTheme`.
 */
object GuardianTheme {
    val colors: GuardianColorTokens
        @Composable @ReadOnlyComposable get() = LocalGuardianColors.current

    val spacing: GuardianSpacing
        @Composable @ReadOnlyComposable get() = LocalGuardianSpacing.current

    val shapes: GuardianShapeTokens
        @Composable @ReadOnlyComposable get() = LocalGuardianShapes.current

    val type: GuardianTypeScale
        @Composable @ReadOnlyComposable get() = LocalGuardianTypeScale.current

    val windowSizeClass: WindowSizeClass
        @Composable @ReadOnlyComposable get() = LocalWindowSizeClass.current

    /** The Material scheme, re-exported so a screen can read everything off one object. */
    val materialColors
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme

    val materialShapes: Shapes
        @Composable @ReadOnlyComposable get() = MaterialTheme.shapes

    val typography: Typography
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography

    /** The responsive headline, which steps down on compact widths. */
    val headlineResponsive: TextStyle
        @Composable @ReadOnlyComposable get() =
            if (LocalWindowSizeClass.current == WindowSizeClass.Compact) {
                LocalGuardianTypeScale.current.headlineXlMobile
            } else {
                LocalGuardianTypeScale.current.headlineXl
            }
}

/** Convenience annotation for previewing a composable in both schemes at once. */
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = 0x20)
annotation class GuardianPreviews
