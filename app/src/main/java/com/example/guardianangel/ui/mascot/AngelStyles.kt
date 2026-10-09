package com.example.guardianangel.ui.mascot

import androidx.compose.ui.graphics.Color

/**
 * The five tiers, declared together.
 *
 * Colours and timings come straight from the mascot animation spec, so this table is the
 * one place to look when a tier needs retuning.
 */
internal fun styleFor(mood: AngelMood): AngelStyle = when (mood) {

    // Baseline ambient listening: gentle golden breathing, acoustic rings.
    AngelMood.Resting -> AngelStyle(
        haloColor = Color(0xFFFFD166),
        haloGlow = Color(0xFFFFE8A3),
        haloTilt = 0f,
        haloPeriodMillis = 2500,
        auraColor = Color(0xFFFFE8A3),
        auraPeriodMillis = 2800,
        auraIntensity = 1,
        bodyPeriodMillis = 3400,
        bobAmount = 0.016f,
        trembleAmount = 0f,
        wingAngle = 6f,
        wingsShielding = false,
        wingColor = Color(0xFFFFCCD3),
        blushColor = Color(0xFFFFB1B1),
        talismanColor = Color(0xFFFF7B7B),
        talismanPeriodMillis = 2600,
        eyes = EyeStyle.Sparkly,
        mouth = MouthStyle.Smile,
        sweatDrops = 0,
        hasBrows = false,
        sparkles = false,
    )

    // 100%: serene float, golden sparkles, rosy cheeks.
    AngelMood.Sanctuary -> AngelStyle(
        haloColor = Color(0xFFFFD166),
        haloGlow = Color(0xFFFFF8B5),
        haloTilt = 0f,
        haloPeriodMillis = 4000,
        auraColor = Color(0xFFFFEFA6),
        auraPeriodMillis = 3600,
        auraIntensity = 1,
        bodyPeriodMillis = 4000,
        bobAmount = 0.028f,
        trembleAmount = 0f,
        wingAngle = 10f,
        wingsShielding = false,
        wingColor = Color(0xFFFFCCD3),
        blushColor = Color(0xFFFFB1B1),
        talismanColor = Color(0xFFFF7B7B),
        talismanPeriodMillis = 3000,
        eyes = EyeStyle.Sparkly,
        mouth = MouthStyle.JoyfulSmile,
        sweatDrops = 0,
        hasBrows = false,
        sparkles = true,
    )

    // 95–75%: amber, halo tilted, eyes scanning, wings tucked, one worry drop.
    AngelMood.Cautious -> AngelStyle(
        haloColor = Color(0xFFFFAA00),
        haloGlow = Color(0xFFFFE066),
        haloTilt = 3f,
        haloPeriodMillis = 3000,
        auraColor = Color(0xFFFFC870),
        auraPeriodMillis = 2600,
        auraIntensity = 1,
        bodyPeriodMillis = 3000,
        bobAmount = 0.012f,
        trembleAmount = 0f,
        wingAngle = -4f,
        wingsShielding = false,
        wingColor = Color(0xFFFFDFCC),
        blushColor = Color(0xFFFFB74D),
        talismanColor = Color(0xFFFFAA00),
        talismanPeriodMillis = 2200,
        eyes = EyeStyle.Scanning,
        mouth = MouthStyle.Flat,
        sweatDrops = 1,
        hasBrows = false,
        sparkles = false,
    )

    // 74–50%: trembling, flickering orange halo, defensive wings, two sweat drops.
    AngelMood.Warning -> AngelStyle(
        haloColor = Color(0xFFFF7043),
        haloGlow = Color(0xFFFFCC80),
        haloTilt = 6f,
        haloPeriodMillis = 900,
        auraColor = Color(0xFFFF8A65),
        auraPeriodMillis = 1400,
        auraIntensity = 2,
        bodyPeriodMillis = 2200,
        bobAmount = 0.006f,
        trembleAmount = 0.006f,
        wingAngle = -10f,
        wingsShielding = true,
        wingColor = Color(0xFFFFCCBC),
        blushColor = Color(0xFFFFAB91),
        talismanColor = Color(0xFFFF5722),
        talismanPeriodMillis = 1200,
        eyes = EyeStyle.Anxious,
        mouth = MouthStyle.Quiver,
        sweatDrops = 2,
        hasBrows = false,
        sparkles = false,
    )

    // Below 50% / duress: crimson strobe, shockwaves, full enclosing wing shield.
    AngelMood.Critical -> AngelStyle(
        haloColor = Color(0xFFFF1744),
        haloGlow = Color(0xFFFF5252),
        haloTilt = 0f,
        haloPeriodMillis = 800,
        auraColor = Color(0xFFFF1744),
        auraPeriodMillis = 900,
        auraIntensity = 3,
        bodyPeriodMillis = 1600,
        bobAmount = 0.004f,
        trembleAmount = 0.004f,
        wingAngle = -16f,
        wingsShielding = true,
        wingColor = Color(0xFFFFCDD2),
        blushColor = Color(0xFFEF9A9A),
        talismanColor = Color(0xFFD50000),
        talismanPeriodMillis = 850,
        eyes = EyeStyle.Alarmed,
        mouth = MouthStyle.Open,
        sweatDrops = 2,
        hasBrows = true,
        sparkles = false,
    )
}
