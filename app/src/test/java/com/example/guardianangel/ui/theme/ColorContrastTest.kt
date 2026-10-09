package com.example.guardianangel.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Guards the contrast of every foreground/background pairing the design system actually
 * puts on screen, in both schemes.
 *
 * This exists because the pairings are easy to break by accident: a token such as
 * `accentSoft` inverts between light and dark, so a call site that borrows an unrelated
 * `on*` colour can look fine in light mode and render at 1.32:1 in dark — which is what
 * happened to the SOS label before [GuardianColorTokens.onAccentSoft] was introduced.
 *
 * Thresholds are WCAG 2.1: 4.5:1 for normal text, 3:1 for large text and for non-text
 * elements that carry meaning or identify a control (1.4.3, 1.4.11).
 */
class ColorContrastTest {

    private data class Pairing(
        val description: String,
        val foreground: Color,
        val background: Color,
        val minimumRatio: Double,
    )

    @Test
    fun lightSchemePairingsMeetWcagAa() = assertAllPass("light", lightPairings())

    @Test
    fun darkSchemePairingsMeetWcagAa() = assertAllPass("dark", darkPairings())

    private fun lightPairings(): List<Pairing> {
        val m = GuardianLightColorScheme
        val g = GuardianLightColorTokens
        return commonPairings(m, g) + listOf(
            // Body copy on both of the warm surfaces it is ever set on.
            Pairing("onSurface on canvas", m.onSurface, g.canvas, TEXT),
            Pairing("onSurface on card layer1", m.onSurface, g.layer1, TEXT),
            Pairing("onSurfaceVariant on card layer1", m.onSurfaceVariant, g.layer1, TEXT),
            // The espresso primary action.
            Pairing("primary button label", g.canvas, m.onSurface, TEXT),
        )
    }

    private fun darkPairings(): List<Pairing> {
        val m = GuardianDarkColorScheme
        val g = GuardianDarkColorTokens
        return commonPairings(m, g) + listOf(
            Pairing("onSurface on canvas", m.onSurface, g.canvas, TEXT),
            Pairing("onSurface on card layer1", m.onSurface, g.layer1, TEXT),
            Pairing("onSurfaceVariant on card layer1", m.onSurfaceVariant, g.layer1, TEXT),
            Pairing("primary button label", g.canvas, m.onSurface, TEXT),
        )
    }

    /** Pairings whose definition is identical in both schemes. */
    private fun commonPairings(
        m: androidx.compose.material3.ColorScheme,
        g: GuardianColorTokens,
    ) = listOf(
        // Accent surfaces must be read against their own paired content colour.
        Pairing("SOS label on accentSoft", g.onAccentSoft, g.accentSoft, TEXT),
        Pairing("checkmark on accentSoft fill", g.onAccentSoft, g.accentSoft, NON_TEXT),
        Pairing("radio dot on accentWarm halo", g.onAccentWarm, g.accentWarm, NON_TEXT),

        // Status chips.
        Pairing("active chip", g.onActiveContainer, g.activeContainer, TEXT),
        Pairing("safe chip", g.onSafeContainer, g.safeContainer, TEXT),
        Pairing("caution chip", g.onCautionContainer, g.cautionContainer, TEXT),
        Pairing("alert chip", m.onErrorContainer, m.errorContainer, TEXT),

        // Material containers used by the accent and outlined buttons.
        Pairing("accent button", m.onPrimaryContainer, m.primaryContainer, TEXT),
        Pairing("outlined button label on canvas", m.onSurface, g.canvas, TEXT),
        Pairing("primary on its own container", m.onPrimary, m.primary, TEXT),
        Pairing("secondary on its own container", m.onSecondary, m.secondary, TEXT),
        Pairing("tertiary on its own container", m.onTertiary, m.tertiary, TEXT),
        Pairing("error on its own container", m.onError, m.error, TEXT),

        // Controls whose outline is the only thing identifying them.
        Pairing("control border on canvas", g.borderControl, g.canvas, NON_TEXT),
        Pairing("control border on card", g.borderControl, g.layer1, NON_TEXT),
        Pairing("focus ring on canvas", g.focusRing, g.canvas, NON_TEXT),

        // Recording panel: drawn on the error container, with the stop control given
        // the primary dark treatment because an outline could not clear 3:1 here.
        Pairing("recording panel text", m.onErrorContainer, m.errorContainer, TEXT),
        Pairing("stop-recording label", g.canvas, m.onSurface, TEXT),
        Pairing("stop-recording fill on panel", m.onSurface, m.errorContainer, NON_TEXT),
        Pairing("flagged transcript line", m.error, m.errorContainer, TEXT),

        // Inactive navigation icons carry meaning, so they are held to 3:1.
        Pairing("inactive nav icon on canvas", g.iconMuted, g.canvas, NON_TEXT),
        Pairing("placeholder text in field", g.iconMuted, g.canvas, NON_TEXT),
    )

    private fun assertAllPass(scheme: String, pairings: List<Pairing>) {
        val failures = pairings.mapNotNull { p ->
            val ratio = contrastRatio(p.foreground, p.background)
            if (ratio < p.minimumRatio) {
                "  %-38s %.2f:1  (needs %.1f:1)".format(p.description, ratio, p.minimumRatio)
            } else {
                null
            }
        }
        assertTrue(
            "WCAG AA failures in the $scheme scheme:\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    private companion object {
        const val TEXT = 4.5
        const val NON_TEXT = 3.0

        /** WCAG 2.1 relative luminance. Assumes opaque colours. */
        fun relativeLuminance(color: Color): Double {
            fun channel(value: Float): Double {
                val c = value.toDouble()
                return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(color.red) +
                0.7152 * channel(color.green) +
                0.0722 * channel(color.blue)
        }

        fun contrastRatio(a: Color, b: Color): Double {
            val la = relativeLuminance(a)
            val lb = relativeLuminance(b)
            return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
        }
    }
}
