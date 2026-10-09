package com.example.guardianangel.data.auth

import android.app.Activity
import android.util.Log
import com.example.guardianangel.domain.repository.AuthRepository
import com.example.guardianangel.domain.repository.AuthResult
import com.example.guardianangel.domain.repository.AuthState
import com.example.guardianangel.domain.repository.AuthUser
import com.example.guardianangel.domain.repository.PhoneVerificationResult
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private const val TAG = "FirebaseAuth"

/**
 * Firebase-backed accounts.
 *
 * ## The flow this implements
 *
 * 1. [ensureSignedIn] creates an **anonymous** account on first launch. No screen, no
 *    friction — the app is usable immediately.
 * 2. When the user later opts into cloud sync, [linkEmailPassword] or [linkPhone]
 *    attaches real credentials to *that same uid*, so her guardians and codewords carry
 *    over rather than being stranded on an account she can no longer reach.
 * 3. Only if linking finds the credential already belongs to someone else does it fall
 *    back to a plain sign-in, and that case is reported so the UI can warn that local
 *    setup will not merge.
 *
 * ## Why an Activity is needed for phone auth
 *
 * Firebase verifies the device with the Play Integrity API, falling back to a reCAPTCHA
 * challenge that has to be presented in a real window. [activityProvider] supplies the
 * current Activity; without one, phone sign-in cannot proceed and says so.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val activityProvider: () -> Activity?,
) : AuthRepository {

    override val isCloudEnabled: Boolean = true

    override fun observeAuthState(): Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { instance ->
            val user = instance.currentUser
            trySend(if (user == null) AuthState.SignedOut else AuthState.SignedIn(user.toAuthUser()))
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun ensureSignedIn(): AuthResult {
        auth.currentUser?.let { return AuthResult.Success(it.toAuthUser()) }
        return runCatching { auth.signInAnonymously().await() }
            .fold(
                onSuccess = { result ->
                    result.user
                        ?.let { AuthResult.Success(it.toAuthUser()) }
                        ?: AuthResult.Failure("Could not start a guest session.")
                },
                onFailure = { AuthResult.Failure(describe(it), it) },
            )
    }

    override suspend fun signUpWithEmail(
        name: String,
        email: String,
        password: String,
    ): AuthResult = runCatching {
        auth.createUserWithEmailAndPassword(email, password).await()
    }.fold(
        onSuccess = { result ->
            result.user?.let { user ->
                setDisplayName(user, name)
                AuthResult.Success(user.toAuthUser().copy(displayName = name))
            } ?: AuthResult.Failure("Could not create that account.")
        },
        onFailure = { AuthResult.Failure(describe(it), it) },
    )

    override suspend fun signInWithEmail(email: String, password: String): AuthResult =
        runCatching { auth.signInWithEmailAndPassword(email, password).await() }
            .fold(
                onSuccess = { result ->
                    result.user?.let { AuthResult.Success(it.toAuthUser()) }
                        ?: AuthResult.Failure("Could not sign in.")
                },
                onFailure = { AuthResult.Failure(describe(it), it) },
            )

    override suspend fun startPhoneVerification(
        phoneNumber: String,
    ): PhoneVerificationResult {
        val activity = activityProvider()
            ?: return PhoneVerificationResult.Failure(
                "Open the app to verify your number — this step needs a visible screen."
            )

        return suspendCancellableCoroutine { continuation ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Android read the SMS itself. Nothing to type.
                    if (!continuation.isActive) return
                    auth.signInWithCredential(credential)
                        .addOnSuccessListener { result ->
                            val user = result.user
                            continuation.resume(
                                if (user != null) {
                                    PhoneVerificationResult.AutoVerified(user.toAuthUser())
                                } else {
                                    PhoneVerificationResult.Failure("Could not sign in.")
                                }
                            )
                        }
                        .addOnFailureListener {
                            continuation.resume(PhoneVerificationResult.Failure(describe(it), it))
                        }
                }

                override fun onVerificationFailed(error: com.google.firebase.FirebaseException) {
                    Log.e(TAG, "Phone verification failed", error)
                    if (continuation.isActive) {
                        continuation.resume(PhoneVerificationResult.Failure(describe(error), error))
                    }
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken,
                ) {
                    if (continuation.isActive) {
                        continuation.resume(PhoneVerificationResult.CodeSent(verificationId))
                    }
                }
            }

            PhoneAuthProvider.verifyPhoneNumber(
                PhoneAuthOptions.newBuilder(auth)
                    .setPhoneNumber(phoneNumber)
                    .setTimeout(VERIFICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .setActivity(activity)
                    .setCallbacks(callbacks)
                    .build()
            )
        }
    }

    override suspend fun confirmPhoneCode(verificationId: String, code: String): AuthResult {
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        return runCatching { auth.signInWithCredential(credential).await() }
            .fold(
                onSuccess = { result ->
                    result.user?.let { AuthResult.Success(it.toAuthUser()) }
                        ?: AuthResult.Failure("Could not sign in.")
                },
                onFailure = { AuthResult.Failure(describe(it), it) },
            )
    }

    override suspend fun linkEmailPassword(
        name: String,
        email: String,
        password: String,
    ): AuthResult {
        val current = auth.currentUser ?: return signUpWithEmail(name, email, password)
        val credential = EmailAuthProvider.getCredential(email, password)
        return runCatching { current.linkWithCredential(credential).await() }
            .fold(
                onSuccess = { result ->
                    result.user?.let { user ->
                        setDisplayName(user, name)
                        AuthResult.Success(user.toAuthUser().copy(displayName = name))
                    } ?: AuthResult.Failure("Could not finish setting up your account.")
                },
                onFailure = { error ->
                    if (error is FirebaseAuthUserCollisionException) {
                        // The address already has an account. Signing in is the right
                        // move, but it abandons whatever this device set up anonymously,
                        // so the caller is told rather than it happening silently.
                        AuthResult.Failure(
                            "That email already has an account. Sign in instead — " +
                                "setup on this device won't carry over.",
                            error,
                        )
                    } else {
                        AuthResult.Failure(describe(error), error)
                    }
                },
            )
    }

    override suspend fun linkPhone(verificationId: String, code: String): AuthResult {
        val current = auth.currentUser
            ?: return confirmPhoneCode(verificationId, code)
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        return runCatching { current.linkWithCredential(credential).await() }
            .fold(
                onSuccess = { result ->
                    result.user?.let { AuthResult.Success(it.toAuthUser()) }
                        ?: AuthResult.Failure("Could not link that number.")
                },
                onFailure = { error ->
                    if (error is FirebaseAuthUserCollisionException) {
                        AuthResult.Failure(
                            "That number already has an account. Sign in instead — " +
                                "setup on this device won't carry over.",
                            error,
                        )
                    } else {
                        AuthResult.Failure(describe(error), error)
                    }
                },
            )
    }

    override suspend fun signOut() = auth.signOut()

    private suspend fun setDisplayName(user: FirebaseUser, name: String) {
        if (name.isBlank()) return
        runCatching {
            user.updateProfile(userProfileChangeRequest { displayName = name }).await()
        }.onFailure { Log.w(TAG, "Could not set display name", it) }
    }

    /**
     * Turns a Firebase exception into something worth showing a person.
     *
     * Firebase's own messages name internal error codes and, for sign-in, distinguish
     * "no such user" from "wrong password" — which tells an attacker whether an address
     * is registered. These replies deliberately do not.
     */
    private fun describe(error: Throwable): String = when (error) {
        is FirebaseAuthWeakPasswordException ->
            "That password is too easy to guess. Try at least eight characters."
        is FirebaseAuthUserCollisionException ->
            "There's already an account with those details."
        else -> when {
            error.message?.contains("network", ignoreCase = true) == true ->
                "No connection. Your setup is saved on this phone either way."
            error.message?.contains("blocked", ignoreCase = true) == true ->
                "Too many attempts. Wait a few minutes and try again."
            else -> "That didn't work. Check the details and try again."
        }
    }

    private companion object {
        const val VERIFICATION_TIMEOUT_SECONDS = 60L
    }
}

private fun FirebaseUser.toAuthUser() = AuthUser(
    id = uid,
    displayName = displayName.orEmpty(),
    email = email,
    phoneNumber = phoneNumber,
    isAnonymous = isAnonymous,
    provider = when {
        isAnonymous -> "anonymous"
        !phoneNumber.isNullOrBlank() -> "phone"
        else -> "password"
    },
)
