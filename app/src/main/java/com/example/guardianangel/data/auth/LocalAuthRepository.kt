package com.example.guardianangel.data.auth

import com.example.guardianangel.data.local.UserDao
import com.example.guardianangel.data.local.UserEntity
import com.example.guardianangel.domain.repository.AuthRepository
import com.example.guardianangel.domain.repository.AuthResult
import com.example.guardianangel.domain.repository.AuthState
import com.example.guardianangel.domain.repository.AuthUser
import com.example.guardianangel.domain.repository.PhoneVerificationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Accounts that live only on this device.
 *
 * Used whenever Firebase is not configured, which is the default state of a fresh
 * checkout. It is a real implementation rather than a stub: the app is fully usable
 * without ever touching a network, and for a safety tool that is a feature — signing up
 * should never be the thing standing between someone and a working panic button.
 *
 * What it cannot do is restore a setup onto a second device. Those calls fail with a
 * message saying so rather than pretending, so the UI can offer the cloud upgrade
 * honestly.
 */
class LocalAuthRepository(
    private val userDao: UserDao,
) : AuthRepository {

    override val isCloudEnabled: Boolean = false

    override fun observeAuthState(): Flow<AuthState> =
        userDao.observeCurrent().map { entity ->
            entity?.let { AuthState.SignedIn(it.toAuthUser()) } ?: AuthState.SignedOut
        }

    override suspend fun ensureSignedIn(): AuthResult {
        userDao.observeCurrentOnce()?.let { return AuthResult.Success(it.toAuthUser()) }

        val now = System.currentTimeMillis()
        val user = UserEntity(
            id = "local-${UUID.randomUUID()}",
            displayName = "",
            email = null,
            phoneNumber = null,
            isAnonymous = true,
            authProvider = "anonymous",
            createdAt = now,
            updatedAt = now,
        )
        userDao.upsert(user)
        return AuthResult.Success(user.toAuthUser())
    }

    override suspend fun signUpWithEmail(
        name: String,
        email: String,
        password: String,
    ): AuthResult = linkEmailPassword(name, email, password)

    override suspend fun signInWithEmail(email: String, password: String): AuthResult =
        AuthResult.Failure(CLOUD_REQUIRED)

    override suspend fun startPhoneVerification(phoneNumber: String): PhoneVerificationResult =
        PhoneVerificationResult.Failure(CLOUD_REQUIRED)

    override suspend fun confirmPhoneCode(verificationId: String, code: String): AuthResult =
        AuthResult.Failure(CLOUD_REQUIRED)

    /**
     * Records the details locally.
     *
     * The password is intentionally discarded. There is nothing to authenticate against
     * on a single device — the lock screen already does that — and storing a credential
     * that protects nothing would be a liability rather than a safeguard.
     */
    override suspend fun linkEmailPassword(
        name: String,
        email: String,
        password: String,
    ): AuthResult {
        val existing = userDao.observeCurrentOnce()
            ?: return AuthResult.Failure("No account to update.")
        val updated = existing.copy(
            displayName = name.ifBlank { existing.displayName },
            email = email.ifBlank { null },
            isAnonymous = false,
            authProvider = "password",
            updatedAt = System.currentTimeMillis(),
        )
        userDao.upsert(updated)
        return AuthResult.Success(updated.toAuthUser())
    }

    override suspend fun linkPhone(verificationId: String, code: String): AuthResult =
        AuthResult.Failure(CLOUD_REQUIRED)

    override suspend fun signOut() {
        userDao.observeCurrentOnce()?.let { userDao.delete(it.id) }
    }

    private companion object {
        const val CLOUD_REQUIRED =
            "This needs cloud sync. Turn it on in Settings to use your account on more than one device."
    }
}

internal fun UserEntity.toAuthUser() = AuthUser(
    id = id,
    displayName = displayName,
    email = email,
    phoneNumber = phoneNumber,
    isAnonymous = isAnonymous,
    provider = authProvider,
)
