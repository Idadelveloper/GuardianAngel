package com.example.guardianangel.data.auth

import com.example.guardianangel.domain.repository.AuthRepository
import com.example.guardianangel.domain.repository.AuthResult
import com.example.guardianangel.domain.repository.AuthState
import com.example.guardianangel.domain.repository.AuthUser
import com.example.guardianangel.domain.repository.PhoneVerificationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A fixed signed-in user, for previews and tests. Touches no storage. */
class PreviewAuthRepository(signedIn: Boolean = true) : AuthRepository {

    private val sample = AuthUser(
        id = "preview-user",
        displayName = "Ida Delphine",
        email = null,
        phoneNumber = "+1 (555) 392-8174",
        isAnonymous = false,
        provider = "phone",
    )

    private val state = MutableStateFlow(
        if (signedIn) AuthState.SignedIn(sample) else AuthState.SignedOut
    )

    override val isCloudEnabled: Boolean = false

    override fun observeAuthState(): Flow<AuthState> = state.asStateFlow()

    override suspend fun ensureSignedIn(): AuthResult {
        state.value = AuthState.SignedIn(sample)
        return AuthResult.Success(sample)
    }

    override suspend fun signUpWithEmail(name: String, email: String, password: String) =
        ensureSignedIn()

    override suspend fun signInWithEmail(email: String, password: String) = ensureSignedIn()

    override suspend fun startPhoneVerification(phoneNumber: String) =
        PhoneVerificationResult.CodeSent("preview-verification")

    override suspend fun confirmPhoneCode(verificationId: String, code: String) = ensureSignedIn()

    override suspend fun linkEmailPassword(name: String, email: String, password: String) =
        ensureSignedIn()

    override suspend fun linkPhone(verificationId: String, code: String) = ensureSignedIn()

    override suspend fun signOut() {
        state.value = AuthState.SignedOut
    }
}
