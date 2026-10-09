package com.example.guardianangel.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.guardianangel.R
import com.example.guardianangel.ui.icons.GuardianIcons

/**
 * Every place the user can be.
 *
 * Routes are plain string constants rather than typed destinations because the graph is
 * small and flat; a typed graph would add ceremony without removing a real risk here.
 * Keeping them in one file means a route can never be misspelled in two places.
 */
object Routes {

    /** The onboarding wizard — no bottom bar. */
    /**
     * Where a returning user lands.
     *
     * A sibling of [SIGN_UP] rather than a step inside onboarding, because the two have
     * different lifetimes: onboarding is a wizard that can be skipped step by step and
     * resumed, while authentication is a gate that is either passed or not — and once
     * passed, must never reappear.
     */
    const val LOG_IN = "auth/log-in"

    const val ONBOARDING_GRAPH = "onboarding"
    const val SIGN_UP = "onboarding/sign-up"
    const val PERMISSIONS = "onboarding/permissions"
    const val VOICE_CALIBRATION = "onboarding/voice"
    const val GUARDIAN_CONTACTS = "onboarding/guardians"
    const val WAKE_WORD = "onboarding/wake-word"
    const val CODEWORD_SETUP = "onboarding/codewords"

    /** The main tabbed shell. */
    const val MAIN_GRAPH = "main"
    const val HOME = "main/home"
    const val MAP = "main/map"
    const val ACTIVITIES = "main/activities"
    const val SETTINGS = "main/settings"

    /** Stacked sub-screens — top app bar with a back arrow, no bottom bar. */
    const val SESSION_DETAIL = "session/{sessionId}"
    const val SETTINGS_CODEWORDS = "settings/codewords"
    const val SETTINGS_GUARDIANS = "settings/guardians"
    const val SETTINGS_VOICE = "settings/voice"

    /**
     * Re-enrolling the voiceprint from settings.
     *
     * A separate destination from [VOICE_CALIBRATION] even though it shows the same
     * screen, because the two differ in where Continue goes. Reusing the onboarding
     * route sent a user who tapped "recalibrate" into the rest of the wizard — adding a
     * guardian, setting codewords — on the way back out.
     */
    const val SETTINGS_VOICE_RECALIBRATE = "settings/voice/recalibrate"
    const val SETTINGS_WAKE_WORD = "settings/wake-word"

    fun sessionDetail(sessionId: String) = "session/$sessionId"
}

/**
 * The four primary tabs.
 *
 * Ordered as declared; the bottom bar renders them in this order and nothing else decides
 * tab order, so Home is always first and Settings always last.
 */
enum class TopLevelTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Home(Routes.HOME, R.string.nav_home, GuardianIcons.Shield),
    Map(Routes.MAP, R.string.nav_map, GuardianIcons.MapPin),
    Activities(Routes.ACTIVITIES, R.string.nav_activity, GuardianIcons.Activity),
    Settings(Routes.SETTINGS, R.string.nav_settings, GuardianIcons.Gear);

    companion object {
        /** The tab owning [route], or null for a screen that sits outside the shell. */
        fun forRoute(route: String?): TopLevelTab? =
            entries.firstOrNull { route == it.route }
    }
}
