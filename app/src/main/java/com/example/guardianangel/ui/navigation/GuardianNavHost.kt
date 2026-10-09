package com.example.guardianangel.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.example.guardianangel.di.AppContainer
import com.example.guardianangel.ui.activities.ActivitiesRoute
import com.example.guardianangel.ui.activities.SessionDetailRoute
import com.example.guardianangel.ui.components.GuardianBottomNav
import com.example.guardianangel.ui.components.GuardianNavItem
import com.example.guardianangel.ui.home.HomeRoute
import com.example.guardianangel.ui.map.MapRoute
import com.example.guardianangel.ui.onboarding.CodewordSetupRoute
import com.example.guardianangel.ui.onboarding.GuardianContactsRoute
import com.example.guardianangel.ui.onboarding.PermissionsRoute
import com.example.guardianangel.ui.auth.LoginRoute
import com.example.guardianangel.ui.onboarding.SignUpRoute
import com.example.guardianangel.ui.onboarding.VoiceCalibrationRoute
import com.example.guardianangel.ui.onboarding.WakeWordRoute
import com.example.guardianangel.ui.settings.SettingsCodewordsRoute
import com.example.guardianangel.ui.settings.SettingsGuardiansRoute
import com.example.guardianangel.ui.settings.SettingsHubRoute
import com.example.guardianangel.ui.settings.SettingsVoiceRoute
import com.example.guardianangel.ui.settings.SettingsWakeWordRoute
import com.example.guardianangel.ui.theme.GuardianTheme

private const val TRANSITION_MILLIS = 280

/**
 * The app's single navigation graph.
 *
 * Three shells, matching the design spec's archetypes:
 *
 *  - **Onboarding** slides horizontally and shows no bottom bar — it is a wizard, and a
 *    tab bar would invite the user to wander out of a flow that has to complete in order.
 *  - **Main tabs** cross-fade, because a slide implies hierarchy that peer tabs do not have.
 *  - **Stacked sub-screens** slide in from the right over the tabs they came from.
 *
 * The bottom bar lives here rather than inside each screen so it never re-enters
 * composition on tab change, and so exactly one place decides when it is visible.
 */
@Composable
fun GuardianNavHost(
    container: AppContainer,
    start: StartDestination,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTab = TopLevelTab.forRoute(currentRoute)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GuardianTheme.colors.canvas),
    ) {
        NavHost(
            navController = navController,
            startDestination = start.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { slideIn() },
            exitTransition = { slideOut() },
            popEnterTransition = { popIn() },
            popExitTransition = { popOut() },
        ) {
            authScreens(navController, container)
            onboardingGraph(navController, container)
            mainGraph(navController, container)
            stackedGraph(navController, container)
        }

        // Shown only on the four primary tabs; wizard steps and stacked sub-screens get
        // their own back affordance instead.
        if (currentTab != null) {
            GuardianBottomNav(
                items = TopLevelTab.entries.map {
                    GuardianNavItem(stringResource(it.labelRes), it.icon)
                },
                selectedIndex = currentTab.ordinal,
                onSelect = { index ->
                    navController.navigateToTab(TopLevelTab.entries[index].route)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 560.dp)
                    .navigationBarsPadding(),
            )
        }
    }
}

/**
 * Moves into the app, discarding the auth gate.
 *
 * `popUpTo(0)` clears the entire back stack rather than popping to a named destination,
 * because the gate may have been reached from either sign-up or log-in and either could
 * be the graph's start. Leaving them on the stack would let the back button walk a
 * signed-in user back to a login form.
 */
private fun NavHostController.enterApp(route: String) {
    navigate(route) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}

// --- Graphs --------------------------------------------------------------------------

/**
 * The gate: sign up or log in.
 *
 * Two top-level destinations rather than a nested graph, because which one opens depends
 * on live state — whether this device already has an account — and a nested graph fixes
 * its start destination when the graph is built.
 *
 * Entering the app replaces the whole back stack, so the hardware back button cannot
 * return to a login screen from inside. Once someone is in, the auth screens are gone.
 */
private fun NavGraphBuilder.authScreens(
    navController: NavHostController,
    container: AppContainer,
) {
    composable(Routes.SIGN_UP) {
        SignUpRoute(
            container = container,
            onContinue = { navController.enterApp(Routes.ONBOARDING_GRAPH) },
            onLogIn = { navController.navigate(Routes.LOG_IN) },
        )
    }
    composable(Routes.LOG_IN) {
        LoginRoute(
            container = container,
            // Straight to the app: a returning user has already been through setup, and
            // anything she skipped is surfaced on Home rather than re-run here.
            onAuthenticated = { navController.enterApp(Routes.MAIN_GRAPH) },
            onCreateAccount = { navController.navigate(Routes.SIGN_UP) },
        )
    }
}

private fun NavGraphBuilder.onboardingGraph(
    navController: NavHostController,
    container: AppContainer,
) {
    navigation(startDestination = Routes.PERMISSIONS, route = Routes.ONBOARDING_GRAPH) {
        composable(Routes.PERMISSIONS) {
            PermissionsRoute(
                repository = container.accountRepository,
                listeningRepository = container.listeningRepository,
                // No back: sign-up is behind the auth gate and must not be returned to.
                onBack = null,
                onContinue = { navController.navigate(Routes.VOICE_CALIBRATION) },
            )
        }
        composable(Routes.VOICE_CALIBRATION) {
            VoiceCalibrationRoute(
                repository = container.accountRepository,
                listeningRepository = container.listeningRepository,
                voiceProfiles = container.voiceProfileRepository,
                onBack = navController::popBackStack,
                onContinue = { navController.navigate(Routes.GUARDIAN_CONTACTS) },
            )
        }
        composable(Routes.GUARDIAN_CONTACTS) {
            GuardianContactsRoute(
                contactsRepository = container.contactsRepository,
                accountRepository = container.accountRepository,
                onBack = navController::popBackStack,
                onContinue = { navController.navigate(Routes.WAKE_WORD) },
            )
        }
        composable(Routes.WAKE_WORD) {
            WakeWordRoute(
                repository = container.listeningRepository,
                voiceProfiles = container.voiceProfileRepository,
                onBack = navController::popBackStack,
                onContinue = { navController.navigate(Routes.CODEWORD_SETUP) },
            )
        }
        composable(Routes.CODEWORD_SETUP) {
            CodewordSetupRoute(
                codewordRepository = container.codewordRepository,
                accountRepository = container.accountRepository,
                onBack = navController::popBackStack,
                onComplete = {
                    // The wizard is finished, so drop it entirely: pressing back from
                    // Home must not walk the user through setup again.
                    navController.navigate(Routes.MAIN_GRAPH) {
                        popUpTo(Routes.ONBOARDING_GRAPH) { inclusive = true }
                    }
                },
            )
        }
    }
}

private fun NavGraphBuilder.mainGraph(
    navController: NavHostController,
    container: AppContainer,
) {
    navigation(startDestination = Routes.HOME, route = Routes.MAIN_GRAPH) {
        composable(Routes.HOME, enterTransition = { fadeThrough() }, exitTransition = { fadeAway() }) {
            HomeRoute(
                repository = container.guardianRepository,
                listeningRepository = container.listeningRepository,
                codewordRepository = container.codewordRepository,
                contactsRepository = container.contactsRepository,
                voiceProfiles = container.voiceProfileRepository,
                permissions = container.permissionProbe,
                angelOrchestrator = container.angelOrchestrator,
                onOpenSession = { navController.navigate(Routes.sessionDetail(it)) },
                onPlanRoute = { navController.navigateToTab(Routes.MAP) },
                onSetUpWakeWord = { navController.navigate(Routes.SETTINGS_WAKE_WORD) },
                onSetUpVoice = { navController.navigate(Routes.SETTINGS_VOICE_RECALIBRATE) },
                onSetUpCodewords = { navController.navigate(Routes.SETTINGS_CODEWORDS) },
                onSetUpGuardians = { navController.navigate(Routes.SETTINGS_GUARDIANS) },
            )
        }
        composable(Routes.MAP, enterTransition = { fadeThrough() }, exitTransition = { fadeAway() }) {
            MapRoute(
                routeRepository = container.routeRepository,
                guardianRepository = container.guardianRepository,
                crimeDataService = container.crimeDataService,
                locationTracker = container.locationTracker,
                permissionProbe = container.permissionProbe,
                onJourneyStarted = { navController.navigateToTab(Routes.HOME) },
            )
        }
        composable(Routes.ACTIVITIES, enterTransition = { fadeThrough() }, exitTransition = { fadeAway() }) {
            ActivitiesRoute(
                repository = container.activityRepository,
                onOpenSession = { navController.navigate(Routes.sessionDetail(it)) },
            )
        }
        composable(Routes.SETTINGS, enterTransition = { fadeThrough() }, exitTransition = { fadeAway() }) {
            SettingsHubRoute(
                accountRepository = container.accountRepository,
                contactsRepository = container.contactsRepository,
                codewordRepository = container.codewordRepository,
                listeningRepository = container.listeningRepository,
                cloudSync = container.cloudSync,
                currentUserId = { container.currentUserId() },
                authRepository = container.authRepository,
                onOpenWakeWord = { navController.navigate(Routes.SETTINGS_WAKE_WORD) },
                onOpenCodewords = { navController.navigate(Routes.SETTINGS_CODEWORDS) },
                onOpenGuardians = { navController.navigate(Routes.SETTINGS_GUARDIANS) },
                onOpenVoice = { navController.navigate(Routes.SETTINGS_VOICE) },
                // Back to the gate, not to onboarding: the account still exists and its
                // setup is intact, so what is needed is a log-in, not a fresh wizard.
                // Log-in rather than sign-up: the account and its setup are still
                // there, so what is needed is a way back in.
                onSignOut = { navController.enterApp(Routes.LOG_IN) },
            )
        }
    }
}

private fun NavGraphBuilder.stackedGraph(
    navController: NavHostController,
    container: AppContainer,
) {
    composable(
        route = Routes.SESSION_DETAIL,
        arguments = listOf(navArgument("sessionId") { }),
    ) { entry ->
        SessionDetailRoute(
            sessionId = entry.arguments?.getString("sessionId").orEmpty(),
            repository = container.activityRepository,
            onBack = navController::popBackStack,
        )
    }
    composable(Routes.SETTINGS_CODEWORDS) {
        SettingsCodewordsRoute(
            codewordRepository = container.codewordRepository,
            accountRepository = container.accountRepository,
            onBack = navController::popBackStack,
        )
    }
    composable(Routes.SETTINGS_GUARDIANS) {
        SettingsGuardiansRoute(
            repository = container.contactsRepository,
            onBack = navController::popBackStack,
        )
    }
    composable(Routes.SETTINGS_WAKE_WORD) {
        SettingsWakeWordRoute(
            repository = container.listeningRepository,
            voiceProfiles = container.voiceProfileRepository,
            onBack = navController::popBackStack,
        )
    }
    composable(Routes.SETTINGS_VOICE_RECALIBRATE) {
        VoiceCalibrationRoute(
            repository = container.accountRepository,
            listeningRepository = container.listeningRepository,
            voiceProfiles = container.voiceProfileRepository,
            onBack = navController::popBackStack,
            // Back to settings, not onward through the wizard.
            onContinue = navController::popBackStack,
        )
    }
    composable(Routes.SETTINGS_VOICE) {
        SettingsVoiceRoute(
            repository = container.accountRepository,
            onBack = navController::popBackStack,
            onRecalibrate = { navController.navigate(Routes.SETTINGS_VOICE_RECALIBRATE) },
        )
    }
}

// --- Transitions ---------------------------------------------------------------------

/**
 * Switches tab without stacking history.
 *
 * `launchSingleTop` plus `popUpTo` the graph start means tapping Home four times leaves
 * one Home on the stack, and the system back button exits rather than replaying tabs.
 * `restoreState` keeps each tab's scroll position.
 */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun AnimatedContentTransitionScope<*>.slideIn() =
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(TRANSITION_MILLIS))

private fun AnimatedContentTransitionScope<*>.slideOut() =
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(TRANSITION_MILLIS))

private fun AnimatedContentTransitionScope<*>.popIn() =
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(TRANSITION_MILLIS))

private fun AnimatedContentTransitionScope<*>.popOut() =
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(TRANSITION_MILLIS))

private fun fadeThrough() = fadeIn(tween(TRANSITION_MILLIS))
private fun fadeAway() = fadeOut(tween(TRANSITION_MILLIS / 2))
