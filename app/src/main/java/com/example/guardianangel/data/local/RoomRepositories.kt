package com.example.guardianangel.data.local

import com.example.guardianangel.audio.SentencePieceTokenizer
import com.example.guardianangel.data.crypto.KeystoreCrypto
import com.example.guardianangel.domain.model.Codeword
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.ContactPresence
import com.example.guardianangel.domain.model.EmergencyContact
import com.example.guardianangel.domain.model.ListeningSensitivity
import com.example.guardianangel.domain.model.WakeWord
import com.example.guardianangel.domain.repository.CodewordRepository
import com.example.guardianangel.domain.repository.ContactsRepository
import com.example.guardianangel.domain.repository.VoiceProfile
import com.example.guardianangel.domain.repository.VoiceProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Resolves the signed-in user for repositories that are scoped to one.
 *
 * Every table hangs off a user id, and every repository needs it, so rather than
 * threading it through each call site it is read from the database here. Reads use
 * [flatMapLatest] so that signing in or linking an account re-points the whole UI at the
 * new user's rows without anything being re-created.
 */
class CurrentUser(private val userDao: UserDao) {

    fun observeId(): Flow<String?> = userDao.observeCurrent().map { it?.id }

    /** Suspends until a user exists. `ensureSignedIn` guarantees one on launch. */
    suspend fun requireId(): String =
        userDao.observeCurrentOnce()?.id
            ?: userDao.observeCurrent().first { it != null }!!.id
}

/**
 * The wake word, in Room.
 *
 * Note what is stored alongside the phrase: its **BPE tokens**. They are computed once
 * at save time and persisted, so the spotter matches exactly what the user confirmed. If
 * they were recomputed on load, a change to the tokeniser would silently alter what an
 * already-set wake word responds to — and the user would have no way of discovering that
 * until it failed to wake.
 */
class RoomWakeWordStore(
    private val dao: WakeWordDao,
    private val currentUser: CurrentUser,
    private val tokenizer: SentencePieceTokenizer?,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<WakeWord> = currentUser.observeId().flatMapLatest { userId ->
        if (userId == null) flowOf(WakeWord(phrase = "")) else dao.observe(userId).map { it.toModel() }
    }

    suspend fun setPhrase(phrase: String) {
        val userId = currentUser.requireId()
        val existing = dao.find(userId)
        val trimmed = phrase.trim()
        dao.upsert(
            (existing ?: blank(userId)).copy(
                phrase = trimmed,
                tokens = tokenizer?.tokenize(trimmed).orEmpty(),
                tokenizerVersion = if (tokenizer != null) SentencePieceTokenizer.VERSION else 0,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /**
     * Whether the keyword model can express [phrase] at all.
     *
     * The spotter matches a phrase by its sub-word pieces, and a phrase containing
     * pieces outside the model's vocabulary cannot be registered — it would be saved,
     * look set up, and never once fire. So this is checked before saving rather than
     * discovered on a dark street.
     *
     * True when no tokeniser is installed: a build without the model cannot do
     * hands-free activation at all, and refusing phrases on top of that would be
     * confusing rather than informative.
     */
    fun canRepresent(phrase: String): Boolean =
        phrase.isNotBlank() && (tokenizer == null || tokenizer.canRepresent(phrase))

    suspend fun addVoiceSample() = update { it.copy(voiceSamples = it.voiceSamples + 1) }

    suspend fun clearVoiceSamples() = update { it.copy(voiceSamples = 0) }

    suspend fun setRequireVoiceMatch(enabled: Boolean) =
        update { it.copy(requireVoiceMatch = enabled) }

    suspend fun setSensitivity(sensitivity: ListeningSensitivity) =
        update { it.copy(sensitivity = sensitivity.name) }

    private suspend fun update(transform: (WakeWordEntity) -> WakeWordEntity) {
        val userId = currentUser.requireId()
        val existing = dao.find(userId) ?: blank(userId)
        dao.upsert(transform(existing).copy(updatedAt = System.currentTimeMillis()))
    }

    private fun blank(userId: String) = WakeWordEntity(
        userId = userId,
        phrase = "",
        tokens = "",
    )
}

private fun WakeWordEntity?.toModel(): WakeWord = WakeWord(
    phrase = this?.phrase.orEmpty(),
    voiceSamples = this?.voiceSamples ?: 0,
    requireVoiceMatch = this?.requireVoiceMatch ?: true,
    sensitivity = this?.sensitivity
        ?.let { runCatching { ListeningSensitivity.valueOf(it) }.getOrNull() }
        ?: ListeningSensitivity.Balanced,
)

/** Codewords, in Room. Seeded with the four tiers the first time a user is created. */
class RoomCodewordRepository(
    private val dao: CodewordDao,
    private val currentUser: CurrentUser,
) : CodewordRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCodewords(): Flow<List<Codeword>> =
        currentUser.observeId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                dao.observeAll(userId).map { rows ->
                    // Ordered by escalation, not by whatever SQLite returns, so every
                    // screen shows safe → emergency.
                    CodewordTier.entries.mapNotNull { tier ->
                        rows.firstOrNull { it.tier == tier.name }?.toModel()
                    }
                }
            }
        }

    override suspend fun updateCodeword(codeword: Codeword) {
        val userId = currentUser.requireId()
        dao.upsert(
            CodewordEntity(
                id = codeword.id,
                userId = userId,
                tier = codeword.tier.name,
                phrase = codeword.phrase.trim(),
                notifyAllContacts = codeword.notifyContactIds.isEmpty(),
                isArmed = codeword.isArmed,
                updatedAt = System.currentTimeMillis(),
            )
        )
        dao.setNotifiedGuardians(codeword.id, codeword.notifyContactIds)
    }

    override suspend fun rehearse(codewordId: String): Boolean {
        // Stands in for running the phrase through the spotter in a sandbox.
        delay(1200)
        return true
    }

    /** Writes the default tiers for a new account. Existing rows are left alone. */
    suspend fun seedDefaults(userId: String) {
        val now = System.currentTimeMillis()
        dao.upsertAll(
            DEFAULT_PHRASES.map { (tier, phrase) ->
                CodewordEntity(
                    id = "cw-${userId}-${tier.name.lowercase()}",
                    userId = userId,
                    tier = tier.name,
                    phrase = phrase,
                    updatedAt = now,
                )
            }
        )
    }

    private companion object {
        /** Suggestions, not secrets — onboarding asks the user to replace them. */
        val DEFAULT_PHRASES = listOf(
            CodewordTier.Safe to "marshmallow",
            CodewordTier.Caution to "pineapple",
            CodewordTier.Danger to "yellow submarine",
            CodewordTier.Emergency to "glitter",
        )
    }
}

private fun CodewordEntity.toModel() = Codeword(
    id = id,
    tier = CodewordTier.valueOf(tier),
    phrase = phrase,
    notifyContactIds = emptyList(),
    isArmed = isArmed,
)

/** The trusted circle, in Room. */
class RoomContactsRepository(
    private val dao: GuardianDao,
    private val currentUser: CurrentUser,
) : ContactsRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeContacts(): Flow<List<EmergencyContact>> =
        currentUser.observeId().flatMapLatest { userId ->
            if (userId == null) flowOf(emptyList()) else dao.observeAll(userId).map { rows ->
                rows.map { it.toModel() }
            }
        }

    override suspend fun addContact(contact: EmergencyContact) {
        val userId = currentUser.requireId()
        // Priority is positional; appending rather than trusting the caller keeps the
        // ordering dense even when a middle guardian was removed earlier.
        val nextPriority = dao.count(userId) + 1
        dao.upsert(
            contact.toEntity(
                userId = userId,
                priority = if (contact.priority > 0) contact.priority else nextPriority,
            )
        )
    }

    override suspend fun updateContact(contact: EmergencyContact) {
        dao.upsert(contact.toEntity(currentUser.requireId(), contact.priority))
    }

    override suspend fun removeContact(contactId: String) = dao.deleteById(contactId)

    override suspend fun sendTestPing(contactId: String) {
        // Stands in for the real silent push.
        delay(900)
    }
}

private fun GuardianEntity.toModel() = EmergencyContact(
    id = id,
    name = name,
    relationship = relationship,
    phoneNumber = phoneNumber,
    priority = priority,
    presence = ContactPresence.Unknown,
    locationLabel = null,
)

private fun EmergencyContact.toEntity(userId: String, priority: Int) = GuardianEntity(
    id = id,
    userId = userId,
    name = name,
    relationship = relationship,
    phoneNumber = phoneNumber,
    priority = priority,
    updatedAt = System.currentTimeMillis(),
)

/** The voiceprint, encrypted at rest. */
class RoomVoiceProfileStore(
    private val dao: VoiceProfileDao,
    private val currentUser: CurrentUser,
) : VoiceProfileRepository {

    override fun observe(): Flow<VoiceProfile?> =
        kotlinx.coroutines.flow.flow { emit(currentUser.requireId()) }
            .let { ids ->
                @OptIn(ExperimentalCoroutinesApi::class)
                ids.flatMapLatest { dao.observe(it) }
            }
            .map { entity ->
                entity?.let {
                    VoiceProfile(
                        clarityPercent = it.clarityPercent,
                        sampleCount = it.sampleCount,
                        calibratedAt = it.calibratedAt,
                    )
                }
            }

    /** Stores the averaged embedding. The raw audio it came from is never written. */
    override suspend fun save(embedding: FloatArray, clarityPercent: Int, sampleCount: Int) {
        val userId = currentUser.requireId()
        dao.upsert(
            VoiceProfileEntity(
                userId = userId,
                encryptedEmbedding = KeystoreCrypto.encryptFloats(embedding),
                embeddingDim = embedding.size,
                clarityPercent = clarityPercent,
                sampleCount = sampleCount,
                calibratedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /** Null when absent, or when the Keystore key was invalidated and it must be re-enrolled. */
    override suspend fun load(): FloatArray? =
        dao.find(currentUser.requireId())
            ?.encryptedEmbedding
            ?.let(KeystoreCrypto::decryptFloats)

    override suspend fun clear() {
        val userId = currentUser.requireId()
        val existing = dao.find(userId) ?: return
        dao.upsert(
            existing.copy(
                encryptedEmbedding = null,
                embeddingDim = 0,
                clarityPercent = 0,
                sampleCount = 0,
                calibratedAt = null,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }
}
