package com.example.guardianangel.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The vertical rhythm and grid tokens from `design.md › Layout & Spacing`. */
@Immutable
data class GuardianSpacing(
    /** Micro-spacing: icon/label pairings, inline pill content, segmented button gaps. */
    val xs: Dp = 4.dp,
    /** Tight spacing between closely related elements. */
    val sm: Dp = 8.dp,
    /** Standard padding within cards, lists, input fields, form groupings. */
    val md: Dp = 16.dp,
    /** Division between card components, sheet headers, major content blocks. */
    val lg: Dp = 24.dp,
    /** Section divisions, screen transitions, spacing around the safety dial. */
    val xl: Dp = 40.dp,

    /** Screen margin at the current width — see [WindowSizeClass]. */
    val screenMargin: Dp = 20.dp,
    /** Column gutter at the current width. */
    val gutter: Dp = 16.dp,
)

val GuardianSpacingDefaults = GuardianSpacing()

/**
 * Breakpoints from `design.md › Grid Architecture`.
 *
 * Deliberately a small local type rather than `androidx.compose.material3.windowsizeclass`:
 * the design document defines its own thresholds (768 / 1024dp) and column counts, and
 * those are what the layout code needs to honour.
 */
enum class WindowSizeClass(
    val columns: Int,
    val screenMargin: Dp,
    val gutter: Dp,
    val maxContentWidth: Dp,
) {
    /** < 768dp — 4-column fluid, thumb-reach first. */
    Compact(columns = 4, screenMargin = 20.dp, gutter = 16.dp, maxContentWidth = Dp.Infinity),

    /** 768–1024dp — 8-column. */
    Medium(columns = 8, screenMargin = 32.dp, gutter = 20.dp, maxContentWidth = Dp.Infinity),

    /** > 1024dp — 12-column, centred in a 1200dp container. */
    Expanded(columns = 12, screenMargin = 48.dp, gutter = 24.dp, maxContentWidth = 1200.dp);

    companion object {
        fun fromWidth(width: Dp): WindowSizeClass = when {
            width < 768.dp -> Compact
            width < 1024.dp -> Medium
            else -> Expanded
        }
    }
}

internal val LocalGuardianSpacing = staticCompositionLocalOf { GuardianSpacingDefaults }
internal val LocalWindowSizeClass = staticCompositionLocalOf { WindowSizeClass.Compact }
