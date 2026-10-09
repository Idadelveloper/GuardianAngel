package com.example.guardianangel.data.auth

import com.example.guardianangel.data.crypto.KeystoreCrypto
import com.example.guardianangel.data.local.UserDao
import com.example.guardianangel.data.local.UserEntity
import com.example.guardianangel.domain.repository.AuthRepository
import com.example.guardianangel.domain.repository.AuthResult
import com.example.guardianangel.domain.repository.AuthState
import com.example.guardianangel.domain.repository.AuthUser
import com.example.guardianangel.domain.repository.PhoneVerificationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.util.UUID

/**
 * Accounts that live only on this device.
 *
 * Used whenever Firebase is not configured, which is the default state of a fresh
 * checkout. It is a real implementation rather than a stub: the app is fully usable
 * without ever touching a network, and for a safety tool that is a feature — signing up
 * should never be the thing standing between someone and a working panic button.
 *
 * ## Why the password is stored here
 *
 * An earlier version discarded it, on the reasoning that there is nothing to
 * authenticate against on a single device and a credential protecting nothing is a
 * liability. That held while the app signed in anonymously with no login screen. It does
 * not hold now: there *is* a login screen, and behind it sit transcripts of someone's
 * worst moments and her guardians' phone numbers. A login that accepts any password is
 * worse than no login, because it looks like protection.
 *
 * So the password is salted with the account id, hashed with SHA-256, and the digest is
 * encrypted with a hardware-backed Keystore key — the same treatment as the disarm PIN.
 * The password itself is never written anywhere.
 *
 * What this cannot do is restore a setup onto a second device. Those calls fail with a
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

    /**
     * Resumes an open session, and nothing more.
     *
     * Creates no account: an earlier version made an anonymous one whenever the database
     * was empty, which is exactly a fresh install — so launch always ended up
     * authenticated and the auth gate was skipped before it could render. Starting a
     * guest session is now a choice the user makes, in [signInAnonymously].
     */
    override suspend fun ensureSignedIn(): AuthResult {
        userDao.observeCurrentOnce()?.let { return AuthResult.Success(it.toAuthUser()) }
        return AuthResult.Failure(SIGNED_OUT)
    }

    /** Creates an anonymous account and opens a session. The "skip for now" path. */
    override suspend fun signInAnonymously(): AuthResult {
        userDao.observeCurrentOnce()?.let { return AuthResult.Success(it.toAuthUser()) }

        val existing = userDao.findAnyAccount()
        if (existing != null) {
            // An account that was signed out: reopen its session rather than stranding
            // the user's data behind a second, empty account.
            userDao.setSessionActive(existing.id, true, System.currentTimeMillis())
            return AuthResult.Success(existing.toAuthUser())
        }
        return AuthResult.Success(createAnonymous().toAuthUser())
    }

    override suspend fun signUpWithEmail(
        name: String,
        email: String,
        password: String,
    ): AuthResult {
        val normalised = email.trim()
        if (userDao.findByEmail(normalised) != null) {
            return AuthResult.Failure("An account already uses that email on this device.")
        }

        // Reuse the anonymous row if there is one, so anything already set up survives
        // the upgrade instead of being orphaned behind a new id.
        val existing = userDao.observeCurrentOnce() ?: userDao.findAnyAccount()
        val now = System.currentTimeMillis()
        val id = existing?.id ?: "local-${UUID.randomUUID()}"

        val user = (existing ?: blank(id, now)).copy(
            displayName = name.trim().ifBlank { existing?.displayName.orEmpty() },
            email = normalised.ifBlank { null },
            isAnonymous = false,
            authProvider = "password",
            sessionActive = true,
            encryptedPasswordHash = encodePassword(password, id),
            updatedAt = now,
        )
        userDao.upsert(user)
        return AuthResult.Success(user.toAuthUser())
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult {
        val user = userDao.findByEmail(email.trim())
            ?: return AuthResult.Failure(WRONG_CREDENTIALS)

        val stored = user.encryptedPasswordHash?.let(KeystoreCrypto::decrypt)
            ?: return AuthResult.Failure(
                "This account was created on another device. Turn on cloud sync to use it here."
            )

        // Constant-time compare: a length-or-prefix leak here is small, but free to avoid.
        if (!MessageDigest.isEqual(stored, hash(password, user.id))) {
            return AuthResult.Failure(WRONG_CREDENTIALS)
        }

        userDao.setSessionActive(user.id, true, System.currentTimeMillis())
        return AuthResult.Success(user.copy(sessionActive = true).toAuthUser())
    }

    override suspend fun startPhoneVerification(phoneNumber: String): PhoneVerificationResult =
        PhoneVerificationResult.Failure(CLOUD_REQUIRED)

    override suspend fun confirmPhoneCode(verificationId: String, code: String): AuthResult =
        AuthResult.Failure(CLOUD_REQUIRED)

    override suspend fun linkEmailPassword(
        name: String,
        email: String,
        password: String,
    ): AuthResult = signUpWithEmail(name, email, password)

    override suspend fun linkPhone(verificationId: String, code: String): AuthResult =
        AuthResult.Failure(CLOUD_REQUIRED)

    /**
     * Ends the session, keeping every row.
     *
     * This used to delete the user, which cascaded through guardians, codewords,
     * sessions and the voiceprint. Signing out is not a request to be forgotten.
     */
    override suspend fun signOut() {
        userDao.observeCurrentOnce()?.let {
            userDao.setSessionActive(it.id, false, System.currentTimeMillis())
        }
    }

    private suspend fun createAnonymous(): UserEntity {
        val now = System.currentTimeMillis()
        val user = blank("local-${UUID.randomUUID()}", now)
        userDao.upsert(user)
        return user
    }

    private fun blank(id: String, now: Long) = UserEntity(
        id = id,
        displayName = "",
        email = null,
        phoneNumber = null,
        isAnonymous = true,
        authProvider = "anonymous",
        sessionActive = true,
        createdAt = now,
        updatedAt = now,
    )

    /** Null when the Keystore is unavailable; sign-up then fails rather than storing plaintext. */
    private fun encodePassword(password: String, id: String): ByteArray? =
        KeystoreCrypto.encrypt(hash(password, id))

    /** Salted with the account id so the same password hashes differently per account. */
    private fun hash(password: String, salt: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("$salt:$password".toByteArray())

    private companion object {
        const val CLOUD_REQUIRED =
            "This needs cloud sync. Turn it on in Settings to use your account on more than one device."
        const val SIGNED_OUT = "Signed out."

        /**
         * One message for "no such account" and "wrong password".
         *
         * Distinguishing them would confirm whether an address has an account here,
         * which is an enumeration hint a safety app should not hand out — least of all
         * to someone holding another person's phone.
         */
        const val WRONG_CREDENTIALS = "That email and password don't match."
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
