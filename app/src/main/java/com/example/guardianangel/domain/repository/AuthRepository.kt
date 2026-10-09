package com.example.guardianangel.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Who is signed in, and how.
 *
 * ## Why anonymous comes first
 *
 * A woman downloading a personal-safety app at 11pm should be protected before she is
 * asked for an email address. Every flow therefore starts with an anonymous account —
 * created silently, no screen — and the real credentials are *linked onto it later* if
 * and when she wants her setup to survive a new phone.
 *
 * That ordering is the reason [linkEmailPassword] and [linkPhone] exist separately from
 * the sign-in calls: upgrading must preserve the guardians and codewords already set up,
 * not start a fresh account beside them.
 */
interface AuthRepository {

    fun observeAuthState(): Flow<AuthState>

    /** True when a backend is configured. False means local-only accounts. */
    val isCloudEnabled: Boolean

    /**
     * Resumes an existing session. Safe to call on every launch.
     *
     * Fails when an account exists but was signed out, so a login screen is not bypassed
     * on the next launch. On a device with no account at all it creates an anonymous one,
     * which is what keeps the app usable before anyone has signed up.
     */
    suspend fun ensureSignedIn(): AuthResult

    /**
     * Opens an anonymous session deliberately — the "continue without an account" path.
     *
     * Kept separate from [ensureSignedIn] because this one is a choice the user made,
     * not a silent bootstrap, and it must reopen a signed-out account rather than
     * stranding its data behind a fresh id.
     */
    suspend fun signInAnonymously(): AuthResult

    suspend fun signUpWithEmail(name: String, email: String, password: String): AuthResult
    suspend fun signInWithEmail(email: String, password: String): AuthResult

    /**
     * Starts phone verification.
     *
     * @return a verification id to pass to [confirmPhoneCode], or a failure. On Android
     *   the SMS can also be auto-retrieved, in which case the state flow updates without
     *   [confirmPhoneCode] ever being called.
     */
    suspend fun startPhoneVerification(phoneNumber: String): PhoneVerificationResult
    suspend fun confirmPhoneCode(verificationId: String, code: String): AuthResult

    /** Upgrades the current anonymous account, keeping its id and data. */
    suspend fun linkEmailPassword(name: String, email: String, password: String): AuthResult
    suspend fun linkPhone(verificationId: String, code: String): AuthResult

    suspend fun signOut()
}

/** The signed-in identity, as the app sees it. */
data class AuthUser(
    val id: String,
    val displayName: String,
    val email: String?,
    val phoneNumber: String?,
    val isAnonymous: Boolean,
    /** "anonymous", "password" or "phone". */
    val provider: String,
)

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
}

sealed interface AuthResult {
    data class Success(val user: AuthUser) : AuthResult

    /**
     * @param message already written for a person to read. Firebase's own messages leak
     *   implementation detail and sometimes confirm whether an account exists, which is
     *   an account-enumeration hint this app should not hand out.
     */
    data class Failure(val message: String, val cause: Throwable? = null) : AuthResult
}

sealed interface PhoneVerificationResult {
    /** A code was sent; collect it and call `confirmPhoneCode`. */
    data class CodeSent(val verificationId: String) : PhoneVerificationResult

    /** Android auto-retrieved and verified the SMS; nothing more to collect. */
    data class AutoVerified(val user: AuthUser) : PhoneVerificationResult

    data class Failure(val message: String, val cause: Throwable? = null) :
        PhoneVerificationResult
}
