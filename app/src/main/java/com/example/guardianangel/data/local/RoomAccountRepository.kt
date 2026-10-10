package com.example.guardianangel.data.local

import com.example.guardianangel.data.crypto.KeystoreCrypto
import com.example.guardianangel.domain.model.AccountSnapshot
import com.example.guardianangel.domain.model.DisarmPin
import com.example.guardianangel.domain.model.ListeningSensitivity
import com.example.guardianangel.domain.model.OnboardingStep
import com.example.guardianangel.domain.model.PermissionState
import com.example.guardianangel.domain.model.UserProfile
import com.example.guardianangel.domain.model.VoiceProfile
import com.example.guardianangel.domain.repository.AccountRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.security.MessageDigest

/**
 * Profile, voice settings, onboarding progress and the disarm PIN, in Room.
 *
 * Runtime permissions are the one thing here that is *not* persisted — they belong to
 * the system and are pushed in from the UI after a check. A stored copy would go stale
 * the moment someone revoked microphone access from Android settings, and this app must
 * never claim a capability it no longer has.
 */
class RoomAccountRepository(
    private val userDao: UserDao,
    private val voiceDao: VoiceProfileDao,
    private val pinDao: DisarmPinDao,
    private val currentUser: CurrentUser,
) : AccountRepository {

    private val permissions = MutableStateFlow(PermissionState())

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAccount(): Flow<AccountSnapshot> =
        userDao.observeCurrent().flatMapLatest { user ->
            if (user == null) {
                flowOf(
                    AccountSnapshot(
                        profile = null,
                        permissions = permissions.value,
                        voiceProfile = VoiceProfile(),
                        disarmPin = DisarmPin(),
                        onboardingStep = OnboardingStep.SignUp,
                    )
                )
            } else {
                combine(
                    voiceDao.observe(user.id),
                    pinDao.observe(user.id),
                    permissions,
                ) { voice, pin, perms ->
                    AccountSnapshot(
                        profile = UserProfile(
                            id = user.id,
                            fullName = user.displayName,
                            phoneNumber = user.phoneNumber.orEmpty(),
                            isPhoneVerified = !user.phoneNumber.isNullOrBlank(),
                            shieldActive = user.shieldActive,
                            callGuardianOnEmergency = user.callGuardianOnEmergency,
                        ),
                        permissions = perms,
                        voiceProfile = voice.toModel(),
                        disarmPin = DisarmPin(
                            isSet = pin?.encryptedPinHash != null,
                            hasDecoyPin = pin?.encryptedDecoyHash != null,
                            length = pin?.length ?: 4,
                        ),
                        onboardingStep = runCatching {
                            OnboardingStep.valueOf(user.onboardingStep)
                        }.getOrDefault(OnboardingStep.SignUp),
                    )
                }
            }
        }

    override suspend fun createAccount(fullName: String, phoneNumber: String, password: String) {
        val id = currentUser.requireId()
        val existing = userDao.find(id) ?: return
        userDao.upsert(
            existing.copy(
                displayName = fullName,
                phoneNumber = phoneNumber.ifBlank { null },
                updatedAt = System.currentTimeMillis(),
            )
        )
        advanceOnboarding(OnboardingStep.Permissions)
    }

    override suspend fun updateProfile(fullName: String, phoneNumber: String) {
        val id = currentUser.requireId()
        val existing = userDao.find(id) ?: return
        userDao.upsert(
            existing.copy(
                displayName = fullName.trim().ifBlank { existing.displayName },
                phoneNumber = phoneNumber.trim().ifBlank { null },
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun setCallGuardianOnEmergency(enabled: Boolean) {
        val id = currentUser.requireId()
        val existing = userDao.find(id) ?: return
        userDao.upsert(
            existing.copy(
                callGuardianOnEmergency = enabled,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun grantPermissions(location: Boolean, microphone: Boolean) {
        permissions.value = permissions.value.copy(
            locationGranted = location,
            microphoneGranted = microphone,
        )
    }

    override suspend fun saveVoiceProfile(clarityPercent: Int) {
        val id = currentUser.requireId()
        val existing = voiceDao.find(id)
        voiceDao.upsert(
            (existing ?: VoiceProfileEntity(userId = id, encryptedEmbedding = null)).copy(
                clarityPercent = clarityPercent,
                calibratedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun setSensitivity(sensitivity: ListeningSensitivity) =
        updateVoice { it.copy(sensitivity = sensitivity.name) }

    override suspend fun setWhisperDetection(enabled: Boolean) =
        updateVoice { it.copy(whisperDetection = enabled) }

    override suspend fun setNoiseCancellation(enabled: Boolean) =
        updateVoice { it.copy(noiseCancellation = enabled) }

    /**
     * Stores a salted hash of the PIN, encrypted.
     *
     * Two layers because they defend different things: the hash means a database dump
     * does not reveal the PIN, and the Keystore encryption means an offline dictionary
     * attack against a four-digit space — trivial otherwise — needs the device's key too.
     */
    override suspend fun setDisarmPin(pin: String) {
        val id = currentUser.requireId()
        val digits = pin.filter(Char::isDigit)
        if (digits.isEmpty()) return
        pinDao.upsert(
            DisarmPinEntity(
                userId = id,
                encryptedPinHash = KeystoreCrypto.encrypt(hash(digits, id)),
                // The decoy is the PIN reversed: typing it looks like standing the alert
                // down while it quietly escalates instead.
                encryptedDecoyHash = KeystoreCrypto.encrypt(hash(digits.reversed(), id)),
                length = digits.length,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /** Which PIN was entered, if either. Drives the decoy behaviour. */
    suspend fun checkPin(entered: String): PinOutcome {
        val id = currentUser.requireId()
        val row = pinDao.find(id) ?: return PinOutcome.NotSet
        val candidate = hash(entered.filter(Char::isDigit), id)
        val real = row.encryptedPinHash?.let(KeystoreCrypto::decrypt)
        val decoy = row.encryptedDecoyHash?.let(KeystoreCrypto::decrypt)
        return when {
            real != null && real.contentEquals(candidate) -> PinOutcome.Disarm
            decoy != null && decoy.contentEquals(candidate) -> PinOutcome.DecoyEscalate
            else -> PinOutcome.Wrong
        }
    }

    override suspend fun advanceOnboarding(step: OnboardingStep) {
        userDao.setOnboardingStep(
            currentUser.requireId(),
            step.name,
            System.currentTimeMillis(),
        )
    }

    override suspend fun completeOnboarding() = advanceOnboarding(OnboardingStep.Complete)

    override suspend fun signOut() {
        // Only the auth session ends here. Local data is deliberately left in place:
        // wiping someone's guardians because they signed out would be destructive, and
        // this app is useful signed out.
    }

    private suspend fun updateVoice(transform: (VoiceProfileEntity) -> VoiceProfileEntity) {
        val id = currentUser.requireId()
        val existing = voiceDao.find(id) ?: VoiceProfileEntity(userId = id, encryptedEmbedding = null)
        voiceDao.upsert(transform(existing).copy(updatedAt = System.currentTimeMillis()))
    }

    /** Salted with the user id so the same PIN hashes differently per account. */
    private fun hash(value: String, salt: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("$salt:$value".toByteArray())
}

/** What happened when a PIN was entered. */
enum class PinOutcome {
    /** Correct PIN — stand the alert down. */
    Disarm,

    /** The reversed PIN — look disarmed, escalate silently. */
    DecoyEscalate,

    Wrong,
    NotSet,
}

private fun VoiceProfileEntity?.toModel() = VoiceProfile(
    isCalibrated = this?.calibratedAt != null,
    clarityPercent = this?.clarityPercent ?: 0,
    calibratedOnEpochMillis = this?.calibratedAt,
    whisperDetection = this?.whisperDetection ?: true,
    noiseCancellation = this?.noiseCancellation ?: true,
    sensitivity = this?.sensitivity
        ?.let { runCatching { ListeningSensitivity.valueOf(it) }.getOrNull() }
        ?: ListeningSensitivity.Balanced,
)
