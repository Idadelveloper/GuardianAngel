package com.example.guardianangel.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The on-device schema.
 *
 * ## Shape of the data
 *
 * Everything hangs off a single [UserEntity]. That looks like overkill for an app with
 * one user, but it is what makes "sign in on a new phone and get your setup back" a
 * query rather than a rewrite, and it means an anonymous account upgrading to a real one
 * is an id change on one row instead of a migration.
 *
 * ## What is deliberately not stored
 *
 * **Raw audio.** Not one byte. A voiceprint is a 512-float embedding and a transcript is
 * text; neither can be played back. The recordings a user would most want destroyed are
 * the ones this app never writes down in the first place.
 *
 * ## Sync
 *
 * Every row that could be restored onto a new device carries [syncedAt]. Null means
 * "never uploaded", and a value older than [updatedAt] means "changed since upload", so
 * an incremental push is a `WHERE syncedAt IS NULL OR syncedAt < updatedAt` — no
 * separate queue to keep consistent, and it survives the process dying mid-sync.
 */

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val email: String?,
    val phoneNumber: String?,
    val isAnonymous: Boolean,
    /** "anonymous", "password" or "phone" — mirrors Firebase's provider ids. */
    val authProvider: String,
    val shieldActive: Boolean = true,
    /** Where the setup wizard got to, so it can resume rather than restart. */
    val onboardingStep: String = "SignUp",
    val createdAt: Long,
    val updatedAt: Long,
    val syncedAt: Long? = null,
)

/**
 * The enrolled voiceprint.
 *
 * [encryptedEmbedding] is AES-GCM ciphertext from
 * [com.example.guardianangel.data.crypto.KeystoreCrypto], never a raw vector — see that
 * class for why this field and the disarm PIN get treatment the rest of the schema does
 * not.
 */
@Entity(
    tableName = "voice_profiles",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class VoiceProfileEntity(
    @PrimaryKey val userId: String,
    val encryptedEmbedding: ByteArray?,
    val embeddingDim: Int = 0,
    val clarityPercent: Int = 0,
    val sampleCount: Int = 0,
    val calibratedAt: Long? = null,
    val sensitivity: String = "Balanced",
    val whisperDetection: Boolean = true,
    val noiseCancellation: Boolean = true,
    val updatedAt: Long = 0,
    /** Never uploaded. A voiceprint is a biometric; it stays on the device that made it. */
    val syncedAt: Long? = null,
) {
    // Arrays compare by reference, which would make every read look like a change.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VoiceProfileEntity) return false
        return userId == other.userId &&
            embeddingDim == other.embeddingDim &&
            clarityPercent == other.clarityPercent &&
            sampleCount == other.sampleCount &&
            calibratedAt == other.calibratedAt &&
            sensitivity == other.sensitivity &&
            whisperDetection == other.whisperDetection &&
            noiseCancellation == other.noiseCancellation &&
            encryptedEmbedding.contentEqualsOrBothNull(other.encryptedEmbedding)
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + (encryptedEmbedding?.contentHashCode() ?: 0)
        result = 31 * result + embeddingDim
        result = 31 * result + clarityPercent
        return result
    }
}

private fun ByteArray?.contentEqualsOrBothNull(other: ByteArray?): Boolean =
    if (this == null || other == null) this == null && other == null else contentEquals(other)

/** The phrase that wakes Angel. One per user. */
@Entity(
    tableName = "wake_words",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class WakeWordEntity(
    @PrimaryKey val userId: String,
    val phrase: String,
    /**
     * BPE tokens the spotter actually matches, cached at save time.
     *
     * Stored rather than recomputed so a tokeniser change cannot silently alter what an
     * already-armed phrase matches — the user would have no way of knowing her wake word
     * had shifted underneath her.
     */
    val tokens: String,
    val sensitivity: String = "Balanced",
    val requireVoiceMatch: Boolean = true,
    val voiceSamples: Int = 0,
    val updatedAt: Long = 0,
    val syncedAt: Long? = null,
)

/** An action codeword, said while already recording. */
@Entity(
    tableName = "codewords",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("userId"), Index(value = ["userId", "tier"], unique = true)],
)
data class CodewordEntity(
    @PrimaryKey val id: String,
    val userId: String,
    /** Safe, Caution, Danger or Emergency. */
    val tier: String,
    val phrase: String,
    /** True means every guardian; otherwise see [CodewordContactEntity]. */
    val notifyAllContacts: Boolean = true,
    val isArmed: Boolean = true,
    val updatedAt: Long = 0,
    val syncedAt: Long? = null,
)

/** Which guardians a particular codeword alerts, when it is not all of them. */
@Entity(
    tableName = "codeword_contacts",
    primaryKeys = ["codewordId", "guardianId"],
    foreignKeys = [
        ForeignKey(
            entity = CodewordEntity::class,
            parentColumns = ["id"],
            childColumns = ["codewordId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GuardianEntity::class,
            parentColumns = ["id"],
            childColumns = ["guardianId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("guardianId")],
)
data class CodewordContactEntity(
    val codewordId: String,
    val guardianId: String,
)

/** Someone in the trusted circle. */
@Entity(
    tableName = "guardians",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("userId")],
)
data class GuardianEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val relationship: String,
    val phoneNumber: String,
    /** 1 is first to be called. */
    val priority: Int,
    /**
     * Contacts-provider lookup key when the guardian was picked from the phonebook.
     *
     * A lookup key rather than a row id because contact ids are not stable across a
     * sync or a restore, and a guardian the app can no longer find is a guardian who
     * does not get called.
     */
    val systemContactLookupKey: String? = null,
    val shareLiveLocation: Boolean = true,
    val shareAmbientAudio: Boolean = true,
    val autoSms: Boolean = true,
    val updatedAt: Long = 0,
    val syncedAt: Long? = null,
)

/** One monitored session. */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("userId"), Index("startedAt")],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    /** Conversation, SafeTransit or Incident. */
    val kind: String,
    val title: String,
    val startedAt: Long,
    val endedAt: Long?,
    val locationLabel: String,
    val summary: String,
    val peakDecibels: Int?,
    val lowestSafetyScore: Int?,
    /** Which codeword tier opened the session, if any. */
    val triggeredByTier: String? = null,
    val guardiansNotified: String = "",
    val isEncrypted: Boolean = true,
    /** Set by the user; bookmarked sessions survive any future auto-cleanup. */
    val isBookmarked: Boolean = false,
    val userNotes: String? = null,
    val updatedAt: Long = 0,
    val syncedAt: Long? = null,
)

/** One attributed line of transcript. */
@Entity(
    tableName = "transcript_entries",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sessionId"), Index(value = ["sessionId", "atMillis"])],
)
data class TranscriptEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val atMillis: Long,
    val speakerKind: String,
    val speakerLabel: String,
    val speakerQualifier: String? = null,
    val text: String,
    val decibels: Int? = null,
    val isFlagged: Boolean = false,
    /** Diarization cluster, e.g. "spk0". Lets the UI colour turns consistently. */
    val speakerTag: String? = null,
)

/** A non-speech sound the tagger flagged. */
@Entity(
    tableName = "audio_events",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sessionId")],
)
data class AudioEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val atMillis: Long,
    val label: String,
    val confidence: Float,
    val isDangerSignal: Boolean,
)

/**
 * A breadcrumb.
 *
 * Tied to a session rather than logged continuously: Guardian Angel has no reason to
 * keep a permanent history of everywhere its user has been, and the transcript screen
 * only ever needs the trail for one incident.
 */
@Entity(
    tableName = "location_points",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sessionId"), Index(value = ["sessionId", "atMillis"])],
)
data class LocationPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val atMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    /** Score at this point, so the timeline can show where things worsened. */
    val safetyScore: Int? = null,
    val illuminationPercent: Int? = null,
    /** Reverse-geocoded label when one was available offline. */
    val placeLabel: String? = null,
)

/** Somewhere the user counts as safe — home, a campus library, a friend's flat. */
@Entity(
    tableName = "safe_places",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("userId")],
)
data class SafePlaceEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 120f,
    /** "home", "work", "haven". Home anchors the safety score's baseline. */
    val kind: String = "haven",
    val updatedAt: Long = 0,
    val syncedAt: Long? = null,
)

/**
 * The disarm PIN, and its decoy.
 *
 * Only ever a salted hash — the app never needs to know the PIN, only whether what was
 * typed matches it. [encryptedDecoyHash] is the reverse-entry PIN that pretends to stand
 * an alert down while quietly escalating.
 */
@Entity(
    tableName = "disarm_pins",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class DisarmPinEntity(
    @PrimaryKey val userId: String,
    val encryptedPinHash: ByteArray?,
    val encryptedDecoyHash: ByteArray?,
    val length: Int = 4,
    val updatedAt: Long = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DisarmPinEntity) return false
        return userId == other.userId &&
            length == other.length &&
            encryptedPinHash.contentEqualsOrBothNull(other.encryptedPinHash) &&
            encryptedDecoyHash.contentEqualsOrBothNull(other.encryptedDecoyHash)
    }

    override fun hashCode(): Int = userId.hashCode() * 31 + length
}
