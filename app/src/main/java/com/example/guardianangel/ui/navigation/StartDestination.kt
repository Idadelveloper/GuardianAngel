package com.example.guardianangel.ui.navigation

import com.example.guardianangel.domain.model.OnboardingStep
import com.example.guardianangel.domain.repository.AccountRepository
import com.example.guardianangel.domain.repository.AuthRepository
import com.example.guardianangel.domain.repository.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Where the app opens.
 *
 * Resolved from persisted state rather than hard-coded, which is the whole point: the
 * root composable used to pass `startAtOnboarding = false` unconditionally, so a fresh
 * install landed on Home with no account, no wake word and no guardians — and the setup
 * wizard was only reachable by editing that default.
 */
sealed interface StartDestination {

    /**
     * Still reading the database.
     *
     * Modelled explicitly so the UI can hold a splash for a few milliseconds instead of
     * flashing the wrong screen. Guessing "signed out" and correcting would show the
     * login screen to someone already logged in; guessing the other way would flash Home
     * at someone who is not.
     */
    data object Loading : StartDestination

    /** No session. Sign up, or log in if this device already has an account. */
    data class Auth(val hasExistingAccount: Boolean) : StartDestination

    /** Signed in, but setup never finished. */
    data object Onboarding : StartDestination

    /** Signed in and set up. */
    data object Main : StartDestination

    /**
     * The concrete destination to open.
     *
     * Deliberately a leaf route for the auth case rather than a nested graph. A graph's
     * start destination is fixed when the graph is built, so routing "to the auth graph"
     * and letting it choose meant a sign-out landed on whichever screen had been correct
     * at launch.
     */
    val route: String
        get() = when (this) {
            Loading -> Routes.SIGN_UP
            is Auth -> if (hasExistingAccount) Routes.LOG_IN else Routes.SIGN_UP
            Onboarding -> Routes.ONBOARDING_GRAPH
            Main -> Routes.MAIN_GRAPH
        }
}

/**
 * Watches auth and onboarding state to decide the opening screen.
 *
 * A [Flow] rather than a one-shot read so signing out takes effect immediately — the
 * alternative is a stale gate that keeps someone inside the app after they asked to
 * leave it.
 *
 * @param hasExistingAccount whether this device has an account at all, signed in or not.
 *   Decides whether the gate opens on log-in or sign-up; a returning user sent to a
 *   sign-up form either makes a second account or concludes her data is gone.
 */
fun observeStartDestination(
    authRepository: AuthRepository,
    accountRepository: AccountRepository,
    hasExistingAccount: Flow<Boolean>,
): Flow<StartDestination> =
    combine(
        authRepository.observeAuthState(),
        accountRepository.observeAccount().map { it.onboardingStep },
        hasExistingAccount,
    ) { auth, step, existingAccount ->
        when (auth) {
            AuthState.Loading -> StartDestination.Loading
            AuthState.SignedOut -> StartDestination.Auth(hasExistingAccount = existingAccount)
            is AuthState.SignedIn ->
                if (step == OnboardingStep.Complete) {
                    StartDestination.Main
                } else {
                    StartDestination.Onboarding
                }
        }
    }
