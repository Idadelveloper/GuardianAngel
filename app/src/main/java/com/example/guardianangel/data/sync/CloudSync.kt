package com.example.guardianangel.data.sync

import android.util.Log
import com.example.guardianangel.data.local.CodewordEntity
import com.example.guardianangel.data.local.GuardianDatabase
import com.example.guardianangel.data.local.GuardianEntity
import com.example.guardianangel.data.local.SafePlaceEntity
import com.example.guardianangel.data.local.SessionEntity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "CloudSync"

/** Where a sync got to, so the settings row can say something true. */
sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data class Succeeded(val at: Long, val rows: Int) : SyncStatus
    data class Failed(val message: String) : SyncStatus

    /** No Firebase project configured in this build. */
    data object Unavailable : SyncStatus
}

/**
 * Optional backup of a user's setup to Firestore.
 *
 * ## What goes up, and what does not
 *
 * Uploaded: guardians, codewords, safe places, and session *metadata* — what is needed
 * to get someone protected again on a new phone.
 *
 * **Never uploaded:** the voiceprint, the disarm PIN, and transcript text. The first two
 * are credentials and stay in the keystore that encrypted them. Transcripts are the most
 * sensitive thing this app holds — a record of someone's worst night — and the promise
 * is that they do not leave the phone. The sync is written so it *cannot* break that:
 * there is no code path here that reads the transcript tables, and the uploaded shapes
 * are hand-written maps rather than reflection, so adding a column to an entity can
 * never silently start syncing it.
 *
 * ## Incremental by construction
 *
 * Rows carry `syncedAt`, so a push is "everything where `syncedAt` is null or older than
 * `updatedAt`". No separate queue to fall out of step with the data, and a sync killed
 * halfway just leaves the unsent rows pending.
 */
class CloudSync(
    private val database: GuardianDatabase,
    private val firestore: FirebaseFirestore?,
) {
    private val _status = MutableStateFlow<SyncStatus>(
        if (firestore == null) SyncStatus.Unavailable else SyncStatus.Idle
    )
    val status: Flow<SyncStatus> = _status.asStateFlow()

    val isAvailable: Boolean get() = firestore != null

    /** Pushes everything changed since the last successful sync. */
    suspend fun push(userId: String): SyncStatus = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext SyncStatus.Unavailable.also { _status.value = it }
        _status.value = SyncStatus.Running

        try {
            val sync = database.syncDao()
            val root = db.collection("users").document(userId)
            val now = System.currentTimeMillis()
            var rows = 0

            val guardians = sync.pendingGuardians(userId)
            guardians.forEach {
                root.collection("guardians").document(it.id)
                    .set(it.toMap(), SetOptions.merge()).await()
            }
            if (guardians.isNotEmpty()) {
                sync.markGuardiansSynced(guardians.map { it.id }, now)
                rows += guardians.size
            }

            val codewords = sync.pendingCodewords(userId)
            codewords.forEach {
                root.collection("codewords").document(it.id)
                    .set(it.toMap(), SetOptions.merge()).await()
            }
            if (codewords.isNotEmpty()) {
                sync.markCodewordsSynced(codewords.map { it.id }, now)
                rows += codewords.size
            }

            val places = sync.pendingSafePlaces(userId)
            places.forEach {
                root.collection("safePlaces").document(it.id)
                    .set(it.toMap(), SetOptions.merge()).await()
            }
            if (places.isNotEmpty()) {
                sync.markSafePlacesSynced(places.map { it.id }, now)
                rows += places.size
            }

            // Metadata only. Note there is no transcript read anywhere in this method.
            val sessions = sync.pendingSessions(userId)
            sessions.forEach {
                root.collection("sessions").document(it.id)
                    .set(it.toMetadataMap(), SetOptions.merge()).await()
            }
            if (sessions.isNotEmpty()) {
                sync.markSessionsSynced(sessions.map { it.id }, now)
                rows += sessions.size
            }

            Log.i(TAG, "Pushed $rows rows")
            SyncStatus.Succeeded(now, rows).also { _status.value = it }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            // Nothing is marked synced on failure, so the next attempt retries exactly
            // the rows that did not make it.
            SyncStatus.Failed(describe(e)).also { _status.value = it }
        }
    }

    /**
     * Restores a setup onto this device.
     *
     * Merges rather than replaces: the common case is signing in on a second phone that
     * has already been through onboarding, and silently discarding that work would be
     * the wrong surprise.
     */
    suspend fun pull(userId: String): SyncStatus = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext SyncStatus.Unavailable.also { _status.value = it }
        _status.value = SyncStatus.Running

        try {
            val root = db.collection("users").document(userId)
            val now = System.currentTimeMillis()
            var rows = 0

            root.collection("guardians").get().await().documents.forEach { doc ->
                doc.toGuardian(userId, now)?.let { database.guardianDao().upsert(it); rows++ }
            }
            root.collection("codewords").get().await().documents.forEach { doc ->
                doc.toCodeword(userId, now)?.let { database.codewordDao().upsert(it); rows++ }
            }

            Log.i(TAG, "Pulled $rows rows")
            SyncStatus.Succeeded(now, rows).also { _status.value = it }
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            SyncStatus.Failed(describe(e)).also { _status.value = it }
        }
    }

    private fun describe(error: Throwable): String = when {
        error.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ->
            "Firestore rejected this. Check the security rules in your Firebase console."
        error.message?.contains("UNAVAILABLE", ignoreCase = true) == true ||
            error.message?.contains("network", ignoreCase = true) == true ->
            "No connection. Everything is still saved on this phone."
        else -> "Couldn't back up right now. Your data is safe on this phone."
    }
}

private fun GuardianEntity.toMap() = mapOf(
    "name" to name,
    "relationship" to relationship,
    "phoneNumber" to phoneNumber,
    "priority" to priority,
    "shareLiveLocation" to shareLiveLocation,
    "shareAmbientAudio" to shareAmbientAudio,
    "autoSms" to autoSms,
    "updatedAt" to updatedAt,
)

private fun CodewordEntity.toMap() = mapOf(
    "tier" to tier,
    "phrase" to phrase,
    "notifyAllContacts" to notifyAllContacts,
    "isArmed" to isArmed,
    "updatedAt" to updatedAt,
)

private fun SafePlaceEntity.toMap() = mapOf(
    "name" to name,
    "latitude" to latitude,
    "longitude" to longitude,
    "radiusMeters" to radiusMeters,
    "kind" to kind,
    "updatedAt" to updatedAt,
)

/** Metadata only — deliberately no transcript, events or breadcrumbs. */
private fun SessionEntity.toMetadataMap() = mapOf(
    "kind" to kind,
    "title" to title,
    "startedAt" to startedAt,
    "endedAt" to endedAt,
    "locationLabel" to locationLabel,
    "peakDecibels" to peakDecibels,
    "lowestSafetyScore" to lowestSafetyScore,
    "isBookmarked" to isBookmarked,
    "updatedAt" to updatedAt,
)

private fun DocumentSnapshot.toGuardian(userId: String, now: Long): GuardianEntity? {
    val name = getString("name") ?: return null
    return GuardianEntity(
        id = id,
        userId = userId,
        name = name,
        relationship = getString("relationship").orEmpty(),
        phoneNumber = getString("phoneNumber").orEmpty(),
        priority = (getLong("priority") ?: 1L).toInt(),
        shareLiveLocation = getBoolean("shareLiveLocation") ?: true,
        shareAmbientAudio = getBoolean("shareAmbientAudio") ?: true,
        autoSms = getBoolean("autoSms") ?: true,
        updatedAt = getLong("updatedAt") ?: now,
        // Marked synced: it came from the server, so re-uploading it would be noise.
        syncedAt = now,
    )
}

private fun DocumentSnapshot.toCodeword(userId: String, now: Long): CodewordEntity? {
    val tier = getString("tier") ?: return null
    return CodewordEntity(
        id = id,
        userId = userId,
        tier = tier,
        phrase = getString("phrase").orEmpty(),
        notifyAllContacts = getBoolean("notifyAllContacts") ?: true,
        isArmed = getBoolean("isArmed") ?: true,
        updatedAt = getLong("updatedAt") ?: now,
        syncedAt = now,
    )
}
