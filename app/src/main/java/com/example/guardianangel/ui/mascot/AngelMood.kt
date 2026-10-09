package com.example.guardianangel.ui.mascot

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Angel's emotional state.
 *
 * The mascot is the app's primary ambient signal: a user who glances at her phone should
 * read her situation from Angel's posture before she reads a single number. Each mood
 * therefore maps to a safety band, and the mapping lives in [fromScore] so every screen
 * derives the same mood from the same inputs.
 */
enum class AngelMood {
    /** Mic dormant, nothing to watch. Soft golden breathing. */
    Resting,

    /** Verified safe haven, or a 100% score. Serene bobbing and sparkles. */
    Sanctuary,

    /** 75–95%. Evening hours or an unfamiliar route. Amber, watchful. */
    Cautious,

    /** 50–74%. Poor lighting or an acoustic anomaly. Orange, trembling, shielding. */
    Warning,

    /** Below 50%, or a duress trigger. Crimson strobe, full protective wing shield. */
    Critical;

    companion object {
        /**
         * Derives the mood from live state.
         *
         * @param score 0–100 location safety score.
         * @param atSafeHaven true inside a verified geofence — pins Angel to [Sanctuary]
         *   regardless of score, because a known-safe place should always read as safe.
         * @param isArmed false when the guardian is on standby, which shows [Resting].
         * @param inDuress true once a duress trigger has fired; overrides everything.
         */
        fun fromScore(
            score: Int,
            atSafeHaven: Boolean = false,
            isArmed: Boolean = true,
            inDuress: Boolean = false,
        ): AngelMood = when {
            inDuress -> Critical
            !isArmed -> Resting
            atSafeHaven || score >= 100 -> Sanctuary
            score >= 75 -> Cautious
            score >= 50 -> Warning
            else -> Critical
        }
    }
}

/**
 * Everything the renderer needs to draw one mood.
 *
 * Pulled out of the drawing code so the five tiers are declared side by side and can be
 * compared at a glance, and so adding a tier never means editing the draw pass.
 */
@Immutable
internal data class AngelStyle(
    val haloColor: Color,
    val haloGlow: Color,
    /** Degrees the halo tilts off level — vigilance reads as a cocked head. */
    val haloTilt: Float,
    /** Seconds for one halo breath / strobe cycle. */
    val haloPeriodMillis: Int,
    val auraColor: Color,
    /** Seconds for one aura ring to travel out and fade. */
    val auraPeriodMillis: Int,
    /** 0 = none, 1 = gentle acoustic rings, 2 = fast radar pings, 3 = shockwaves. */
    val auraIntensity: Int,
    /** Seconds for one body bob / tremble cycle. */
    val bodyPeriodMillis: Int,
    /** Vertical bob travel as a fraction of mascot size. */
    val bobAmount: Float,
    /** Horizontal tremble as a fraction of mascot size. */
    val trembleAmount: Float,
    /** Wing sweep outward (positive) or tucked inward (negative), in degrees. */
    val wingAngle: Float,
    /** Wings raised into a shielding posture over the shoulders. */
    val wingsShielding: Boolean,
    val wingColor: Color,
    val blushColor: Color,
    val talismanColor: Color,
    val talismanPeriodMillis: Int,
    val eyes: EyeStyle,
    val mouth: MouthStyle,
    val sweatDrops: Int,
    val hasBrows: Boolean,
    val sparkles: Boolean,
)

internal enum class EyeStyle {
    /** Calm, blinking every few seconds. */
    Sparkly,

    /** Scanning left-to-right, watching the street. */
    Scanning,

    /** Dilated, blinking fast. */
    Anxious,

    /** Wide open, alarmed. */
    Alarmed,
}

internal enum class MouthStyle { Smile, JoyfulSmile, Flat, Quiver, Open }
