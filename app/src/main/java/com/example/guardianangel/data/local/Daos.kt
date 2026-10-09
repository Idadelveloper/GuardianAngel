package com.example.guardianangel.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Data access.
 *
 * Reads return `Flow` so the UI updates itself when the listening service writes from
 * another process component — the alternative is screens that quietly show stale state
 * after a session ends while the app was backgrounded.
 *
 * Writes are `suspend` and, where more than one table is involved, `@Transaction`: a
 * session with half its transcript saved is worse than no session, because it looks
 * complete.
 */

@Dao
interface UserDao {
    /**
     * The signed-in user, or null when nobody is.
     *
     * Filtered on `sessionActive` so signing out hides the account without deleting it.
     * Everything else in the database hangs off this row by a cascading foreign key, so
     * deleting it to end a session would wipe the user's guardians and codewords.
     */
    @Query("SELECT * FROM users WHERE sessionActive = 1 LIMIT 1")
    fun observeCurrent(): Flow<UserEntity?>

    /** Any account on this device, signed in or not. Used by the login screen. */
    @Query("SELECT * FROM users LIMIT 1")
    suspend fun findAnyAccount(): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun findByEmail(email: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun accountCount(): Int

    /**
     * Live count, so the auth gate switches between sign-up and log-in as accounts come
     * and go. A one-shot read went stale the moment someone signed up, and a later
     * sign-out then showed them a sign-up form again.
     */
    @Query("SELECT COUNT(*) FROM users")
    fun observeAccountCount(): Flow<Int>

    @Query("UPDATE users SET sessionActive = :active, updatedAt = :now WHERE id = :id")
    suspend fun setSessionActive(id: String, active: Boolean, now: Long)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun find(id: String): UserEntity?

    /** One-shot read of the signed-in user, for code that cannot collect a Flow. */
    @Query("SELECT * FROM users WHERE sessionActive = 1 LIMIT 1")
    suspend fun observeCurrentOnce(): UserEntity?

    @Upsert
    suspend fun upsert(user: UserEntity)

    @Query("UPDATE users SET onboardingStep = :step, updatedAt = :now WHERE id = :id")
    suspend fun setOnboardingStep(id: String, step: String, now: Long)

    /**
     * Moves everything from an anonymous account onto a real one.
     *
     * Needed because upgrading an anonymous Firebase user issues a new uid, and every
     * row is keyed by the old one. Done in a single transaction with foreign keys set
     * to cascade updates, so a crash halfway cannot orphan a user's guardians.
     */
    @Transaction
    suspend fun reassign(oldId: String, newUser: UserEntity) {
        upsert(newUser)
        reassignGuardians(oldId, newUser.id)
        reassignCodewords(oldId, newUser.id)
        reassignSessions(oldId, newUser.id)
        reassignSafePlaces(oldId, newUser.id)
        reassignWakeWord(oldId, newUser.id)
        delete(oldId)
    }

    @Query("UPDATE guardians SET userId = :newId WHERE userId = :oldId")
    suspend fun reassignGuardians(oldId: String, newId: String)

    @Query("UPDATE codewords SET userId = :newId WHERE userId = :oldId")
    suspend fun reassignCodewords(oldId: String, newId: String)

    @Query("UPDATE sessions SET userId = :newId WHERE userId = :oldId")
    suspend fun reassignSessions(oldId: String, newId: String)

    @Query("UPDATE safe_places SET userId = :newId WHERE userId = :oldId")
    suspend fun reassignSafePlaces(oldId: String, newId: String)

    @Query("UPDATE wake_words SET userId = :newId WHERE userId = :oldId")
    suspend fun reassignWakeWord(oldId: String, newId: String)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface VoiceProfileDao {
    @Query("SELECT * FROM voice_profiles WHERE userId = :userId")
    fun observe(userId: String): Flow<VoiceProfileEntity?>

    @Query("SELECT * FROM voice_profiles WHERE userId = :userId")
    suspend fun find(userId: String): VoiceProfileEntity?

    @Upsert
    suspend fun upsert(profile: VoiceProfileEntity)

    @Query("DELETE FROM voice_profiles WHERE userId = :userId")
    suspend fun clear(userId: String)
}

@Dao
interface WakeWordDao {
    @Query("SELECT * FROM wake_words WHERE userId = :userId")
    fun observe(userId: String): Flow<WakeWordEntity?>

    @Query("SELECT * FROM wake_words WHERE userId = :userId")
    suspend fun find(userId: String): WakeWordEntity?

    @Upsert
    suspend fun upsert(wakeWord: WakeWordEntity)
}

@Dao
interface CodewordDao {
    @Query("SELECT * FROM codewords WHERE userId = :userId ORDER BY tier")
    fun observeAll(userId: String): Flow<List<CodewordEntity>>

    /** Find by tier, so a save updates the seeded row instead of inserting beside it. */
    @Query("SELECT * FROM codewords WHERE userId = :userId AND tier = :tier LIMIT 1")
    suspend fun findByTier(userId: String, tier: String): CodewordEntity?

    @Upsert
    suspend fun upsert(codeword: CodewordEntity)

    @Upsert
    suspend fun upsertAll(codewords: List<CodewordEntity>)

    @Query("DELETE FROM codewords WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT guardianId FROM codeword_contacts WHERE codewordId = :codewordId")
    suspend fun contactsFor(codewordId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkContacts(links: List<CodewordContactEntity>)

    @Query("DELETE FROM codeword_contacts WHERE codewordId = :codewordId")
    suspend fun unlinkAll(codewordId: String)

    @Transaction
    suspend fun setNotifiedGuardians(codewordId: String, guardianIds: List<String>) {
        unlinkAll(codewordId)
        if (guardianIds.isNotEmpty()) {
            linkContacts(guardianIds.map { CodewordContactEntity(codewordId, it) })
        }
    }
}

@Dao
interface GuardianDao {
    @Query("SELECT * FROM guardians WHERE userId = :userId ORDER BY priority")
    fun observeAll(userId: String): Flow<List<GuardianEntity>>

    @Query("SELECT * FROM guardians WHERE userId = :userId ORDER BY priority")
    suspend fun findAll(userId: String): List<GuardianEntity>

    @Upsert
    suspend fun upsert(guardian: GuardianEntity)

    @Delete
    suspend fun delete(guardian: GuardianEntity)

    @Query("DELETE FROM guardians WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM guardians WHERE userId = :userId")
    suspend fun count(userId: String): Int
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE userId = :userId ORDER BY startedAt DESC")
    fun observeAll(userId: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE userId = :userId AND kind = :kind ORDER BY startedAt DESC")
    fun observeByKind(userId: String, kind: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: String): Flow<SessionEntity?>

    @Upsert
    suspend fun upsert(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM transcript_entries WHERE sessionId = :sessionId ORDER BY atMillis")
    fun observeTranscript(sessionId: String): Flow<List<TranscriptEntryEntity>>

    @Insert
    suspend fun addTranscriptEntry(entry: TranscriptEntryEntity): Long

    @Insert
    suspend fun addTranscriptEntries(entries: List<TranscriptEntryEntity>)

    @Query("SELECT * FROM audio_events WHERE sessionId = :sessionId ORDER BY atMillis")
    fun observeEvents(sessionId: String): Flow<List<AudioEventEntity>>

    @Insert
    suspend fun addEvent(event: AudioEventEntity): Long

    @Query("SELECT * FROM location_points WHERE sessionId = :sessionId ORDER BY atMillis")
    fun observeTrail(sessionId: String): Flow<List<LocationPointEntity>>

    @Insert
    suspend fun addLocation(point: LocationPointEntity): Long

    /** Everything one session produced, written atomically when it ends. */
    @Transaction
    suspend fun saveCompleted(
        session: SessionEntity,
        transcript: List<TranscriptEntryEntity>,
        events: List<AudioEventEntity>,
        trail: List<LocationPointEntity>,
    ) {
        upsert(session)
        if (transcript.isNotEmpty()) addTranscriptEntries(transcript)
        events.forEach { addEvent(it) }
        trail.forEach { addLocation(it) }
    }

    /** Counts for the analytics rollup, computed rather than stored. */
    @Query("SELECT COUNT(*) FROM sessions WHERE userId = :userId AND startedAt >= :since")
    suspend fun countSince(userId: String, since: Long): Int

    @Query(
        "SELECT COUNT(*) FROM sessions WHERE userId = :userId AND startedAt >= :since " +
            "AND kind = 'Incident'"
    )
    suspend fun countIncidentsSince(userId: String, since: Long): Int
}

@Dao
interface SafePlaceDao {
    @Query("SELECT * FROM safe_places WHERE userId = :userId")
    fun observeAll(userId: String): Flow<List<SafePlaceEntity>>

    @Upsert
    suspend fun upsert(place: SafePlaceEntity)

    @Query("DELETE FROM safe_places WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface DisarmPinDao {
    @Query("SELECT * FROM disarm_pins WHERE userId = :userId")
    fun observe(userId: String): Flow<DisarmPinEntity?>

    @Query("SELECT * FROM disarm_pins WHERE userId = :userId")
    suspend fun find(userId: String): DisarmPinEntity?

    @Upsert
    suspend fun upsert(pin: DisarmPinEntity)
}

/** Rows changed since their last upload, for the incremental cloud push. */
@Dao
interface SyncDao {
    @Query("SELECT * FROM guardians WHERE userId = :userId AND (syncedAt IS NULL OR syncedAt < updatedAt)")
    suspend fun pendingGuardians(userId: String): List<GuardianEntity>

    @Query("SELECT * FROM codewords WHERE userId = :userId AND (syncedAt IS NULL OR syncedAt < updatedAt)")
    suspend fun pendingCodewords(userId: String): List<CodewordEntity>

    @Query("SELECT * FROM sessions WHERE userId = :userId AND (syncedAt IS NULL OR syncedAt < updatedAt)")
    suspend fun pendingSessions(userId: String): List<SessionEntity>

    @Query("SELECT * FROM safe_places WHERE userId = :userId AND (syncedAt IS NULL OR syncedAt < updatedAt)")
    suspend fun pendingSafePlaces(userId: String): List<SafePlaceEntity>

    @Query("UPDATE guardians SET syncedAt = :at WHERE id IN (:ids)")
    suspend fun markGuardiansSynced(ids: List<String>, at: Long)

    @Query("UPDATE codewords SET syncedAt = :at WHERE id IN (:ids)")
    suspend fun markCodewordsSynced(ids: List<String>, at: Long)

    @Query("UPDATE sessions SET syncedAt = :at WHERE id IN (:ids)")
    suspend fun markSessionsSynced(ids: List<String>, at: Long)

    @Query("UPDATE safe_places SET syncedAt = :at WHERE id IN (:ids)")
    suspend fun markSafePlacesSynced(ids: List<String>, at: Long)
}
