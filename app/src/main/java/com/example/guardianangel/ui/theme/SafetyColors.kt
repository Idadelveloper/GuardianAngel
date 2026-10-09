package com.example.guardianangel.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.example.guardianangel.R

/**
 * How safe the user's current situation is judged to be.
 *
 * The bands are deliberately coarse. A safety score is a probability estimate built from
 * public crime data, time of day and distance from a safe base — presenting it as a
 * precise number would imply far more certainty than the inputs support, so the band and
 * its plain-language label are what the UI leads with and the percentage is secondary.
 */
enum class SafetyLevel(@StringRes val labelRes: Int, val minScore: Int) {
    /** At or near a safe haven; nothing elevated nearby. */
    Secure(R.string.safety_band_secure, 85),

    /** Normal street-level awareness. */
    Guarded(R.string.safety_band_guarded, 70),

    /** Several risk factors stacking — worth paying attention. */
    Elevated(R.string.safety_band_elevated, 50),

    /** Act now: move toward a safe node or start a guarded journey. */
    High(R.string.safety_band_high, 0);

    companion object {
        fun fromScore(score: Int): SafetyLevel =
            entries.firstOrNull { score >= it.minScore } ?: High
    }
}

/** The three roles each safety band needs: a ring/accent, a fill, and content on that fill. */
@Immutable
data class SafetyLevelColors(
    /** Progress ring, trend arrows, and accent text. Verified >=4.5:1 on both surfaces. */
    val accent: Color,
    /** Chip and tile fill. */
    val container: Color,
    /** Content drawn on [container]. */
    val onContainer: Color,
)

/**
 * The green-to-red safety ramp, warmed to sit inside the Guardian Angel palette.
 *
 * A literal traffic-light ramp would fight the cream/rose system, so each band is pulled
 * toward the brand's warmth: a sage rather than a pure green, the existing ochre
 * `tertiary` for the middle band, and the system `error` red only at the top of the
 * scale. Every pairing clears WCAG AA as both a 3:1 graphical element and 4.5:1 text.
 *
 * Colour is never the sole signal — [SafetyLevel.labelRes] and a distinct icon accompany it,
 * so the band survives both colour-blindness and a greyscale screenshot.
 */
@Composable
@ReadOnlyComposable
fun safetyColorsFor(level: SafetyLevel): SafetyLevelColors {
    val dark = LocalGuardianColors.current.isDark
    return if (dark) darkSafetyColors(level) else lightSafetyColors(level)
}

private fun lightSafetyColors(level: SafetyLevel) = when (level) {
    SafetyLevel.Secure -> SafetyLevelColors(
        accent = Color(0xFF3F6B4A),
        container = Color(0xFFD9E7D4),
        onContainer = Color(0xFF1E3A26),
    )
    SafetyLevel.Guarded -> SafetyLevelColors(
        accent = Color(0xFF745A38),
        container = Color(0xFFE2C097),
        onContainer = Color(0xFF4A3517),
    )
    SafetyLevel.Elevated -> SafetyLevelColors(
        accent = Color(0xFF9A4F22),
        container = Color(0xFFFFD5BC),
        onContainer = Color(0xFF5C2B0C),
    )
    SafetyLevel.High -> SafetyLevelColors(
        accent = Color(0xFFBA1A1A),
        container = Color(0xFFFFDAD6),
        onContainer = Color(0xFF93000A),
    )
}

private fun darkSafetyColors(level: SafetyLevel) = when (level) {
    SafetyLevel.Secure -> SafetyLevelColors(
        accent = Color(0xFFA7D0A4),
        container = Color(0xFF2A4631),
        onContainer = Color(0xFFC3E8BE),
    )
    SafetyLevel.Guarded -> SafetyLevelColors(
        accent = Color(0xFFE3C197),
        container = Color(0xFF5A4223),
        onContainer = Color(0xFFFFDDB5),
    )
    SafetyLevel.Elevated -> SafetyLevelColors(
        accent = Color(0xFFFFB68C),
        container = Color(0xFF6B3613),
        onContainer = Color(0xFFFFDBC8),
    )
    SafetyLevel.High -> SafetyLevelColors(
        accent = Color(0xFFFFB4AB),
        container = Color(0xFF93000A),
        onContainer = Color(0xFFFFDAD6),
    )
}
