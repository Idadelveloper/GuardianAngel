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
import com.example.guardianangel.ui.onboarding.SignUpRoute
import com.example.guardianangel.ui.onboarding.VoiceCalibrationRoute
import com.example.guardianangel.ui.settings.SettingsCodewordsRoute
import com.example.guardianangel.ui.settings.SettingsGuardiansRoute
import com.example.guardianangel.ui.settings.SettingsHubRoute
import com.example.guardianangel.ui.settings.SettingsVoiceRoute
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
    startAtOnboarding: Boolean,
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
            startDestination = if (startAtOnboarding) Routes.ONBOARDING_GRAPH else Routes.MAIN_GRAPH,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { slideIn() },
            exitTransition = { slideOut() },
            popEnterTransition = { popIn() },
            popExitTransition = { popOut() },
        ) {
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

// --- Graphs --------------------------------------------------------------------------

private fun NavGraphBuilder.onboardingGraph(
    navController: NavHostController,
    container: AppContainer,
) {
    navigation(startDestination = Routes.SIGN_UP, route = Routes.ONBOARDING_GRAPH) {
        composable(Routes.SIGN_UP) {
            SignUpRoute(
                repository = container.accountRepository,
                onContinue = { navController.navigate(Routes.PERMISSIONS) },
            )
        }
        composable(Routes.PERMISSIONS) {
            PermissionsRoute(
                repository = container.accountRepository,
                onBack = navController::popBackStack,
                onContinue = { navController.navigate(Routes.VOICE_CALIBRATION) },
            )
        }
        composable(Routes.VOICE_CALIBRATION) {
            VoiceCalibrationRoute(
                repository = container.accountRepository,
                onBack = navController::popBackStack,
                onContinue = { navController.navigate(Routes.GUARDIAN_CONTACTS) },
            )
        }
        composable(Routes.GUARDIAN_CONTACTS) {
            GuardianContactsRoute(
                contactsRepository = container.contactsRepository,
                accountRepository = container.accountRepository,
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
                onOpenSession = { navController.navigate(Routes.sessionDetail(it)) },
                onPlanRoute = { navController.navigateToTab(Routes.MAP) },
            )
        }
        composable(Routes.MAP, enterTransition = { fadeThrough() }, exitTransition = { fadeAway() }) {
            MapRoute(
                routeRepository = container.routeRepository,
                guardianRepository = container.guardianRepository,
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
                onOpenCodewords = { navController.navigate(Routes.SETTINGS_CODEWORDS) },
                onOpenGuardians = { navController.navigate(Routes.SETTINGS_GUARDIANS) },
                onOpenVoice = { navController.navigate(Routes.SETTINGS_VOICE) },
                onSignOut = {
                    navController.navigate(Routes.ONBOARDING_GRAPH) {
                        popUpTo(Routes.MAIN_GRAPH) { inclusive = true }
                    }
                },
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
    composable(Routes.SETTINGS_VOICE) {
        SettingsVoiceRoute(
            repository = container.accountRepository,
            onBack = navController::popBackStack,
            onRecalibrate = { navController.navigate(Routes.VOICE_CALIBRATION) },
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
