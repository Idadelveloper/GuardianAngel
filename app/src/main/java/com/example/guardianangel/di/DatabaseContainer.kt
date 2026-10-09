package com.example.guardianangel.di

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.guardianangel.audio.SentencePieceTokenizer
import com.example.guardianangel.data.FakeActivityRepository
import com.example.guardianangel.data.FakeGuardianRepository
import com.example.guardianangel.data.FakeRouteRepository
import com.example.guardianangel.data.auth.FirebaseAuthRepository
import com.example.guardianangel.data.auth.FirebaseAvailability
import com.example.guardianangel.data.auth.LocalAuthRepository
import com.example.guardianangel.data.local.CurrentUser
import com.example.guardianangel.data.local.GuardianDatabase
import com.example.guardianangel.data.local.RoomAccountRepository
import com.example.guardianangel.data.local.RoomCodewordRepository
import com.example.guardianangel.data.local.RoomContactsRepository
import com.example.guardianangel.data.platform.AndroidPermissionProbe
import com.example.guardianangel.data.local.RoomListeningRepository
import com.example.guardianangel.domain.repository.PermissionProbe
import com.example.guardianangel.domain.repository.VoiceProfileRepository
import com.example.guardianangel.data.local.RoomVoiceProfileStore
import com.example.guardianangel.data.local.RoomWakeWordStore
import com.example.guardianangel.data.sync.CloudSync
import com.example.guardianangel.domain.repository.AccountRepository
import com.example.guardianangel.domain.repository.ActivityRepository
import com.example.guardianangel.domain.repository.AuthRepository
import com.example.guardianangel.domain.repository.CodewordRepository
import com.example.guardianangel.domain.repository.ContactsRepository
import com.example.guardianangel.domain.repository.GuardianRepository
import com.example.guardianangel.domain.repository.ListeningRepository
import com.example.guardianangel.domain.repository.RouteRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "DatabaseContainer"

/**
 * The real object graph: Room for anything that must outlive the process, plus whichever
 * auth backend is configured.
 *
 * Replaces [InMemoryAppContainer] as the default. The in-memory one is kept for previews
 * and tests, where a database on disk would be slower and would leak state between runs.
 *
 * Two things still come from fakes — the live guardian snapshot and route planning —
 * because they are derived from sensors and a routing service that do not exist yet,
 * not because the storage is missing. They are called out here rather than hidden so the
 * next person knows which seams are real.
 */
class DatabaseAppContainer(
    private val context: Context,
    private val activityProvider: () -> Activity?,
) : AppContainer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = GuardianDatabase.get(context)
    private val currentUser = CurrentUser(database.userDao())

    /**
     * Loaded from the keyword model's vocabulary so a typed phrase can be turned into
     * tokens at save time. Null when no model is installed; the wake word is still
     * stored, just without tokens, and the spotter reports itself unavailable.
     */
    private val tokenizer: SentencePieceTokenizer? = runCatching {
        SentencePieceTokenizer.fromAssets(
            context.assets,
            "sherpa-onnx-kws-zipformer-gigaspeech/bpe.model",
        )
    }.onFailure { Log.i(TAG, "No keyword vocabulary; wake phrases will not be tokenised") }
        .getOrNull()

    override val authRepository: AuthRepository =
        if (FirebaseAvailability.isConfigured(context)) {
            FirebaseAuthRepository(FirebaseAuth.getInstance(), activityProvider)
        } else {
            LocalAuthRepository(database.userDao())
        }

    private val wakeWordStore = RoomWakeWordStore(
        dao = database.wakeWordDao(),
        currentUser = currentUser,
        tokenizer = tokenizer,
    )

    override val voiceProfileRepository: VoiceProfileRepository =
        RoomVoiceProfileStore(database.voiceProfileDao(), currentUser)

    /** Null Firestore when no project is configured; CloudSync reports Unavailable. */
    override val cloudSync = CloudSync(
        database = database,
        firestore = if (FirebaseAvailability.isConfigured(context)) {
            FirebaseFirestore.getInstance()
        } else {
            null
        },
    )

    override suspend fun currentUserId(): String = currentUser.requireId()

    override val permissionProbe: PermissionProbe = AndroidPermissionProbe(context)

    override val listeningRepository: ListeningRepository =
        RoomListeningRepository(wakeWordStore, permissionProbe)

    override val accountRepository: AccountRepository = RoomAccountRepository(
        userDao = database.userDao(),
        voiceDao = database.voiceProfileDao(),
        pinDao = database.disarmPinDao(),
        currentUser = currentUser,
    )

    private val roomCodewords = RoomCodewordRepository(database.codewordDao(), currentUser)
    override val codewordRepository: CodewordRepository = roomCodewords

    override val contactsRepository: ContactsRepository =
        RoomContactsRepository(database.guardianDao(), currentUser)

    // Still derived rather than stored: these need sensors and a routing service.
    override val guardianRepository: GuardianRepository = FakeGuardianRepository()
    override val routeRepository: RouteRepository = FakeRouteRepository()
    override val activityRepository: ActivityRepository = FakeActivityRepository()

    /**
     * Signs in and seeds a new account.
     *
     * Called once from [com.example.guardianangel.GuardianAngelApplication]. Anonymous
     * sign-in happens before any UI so the app is usable immediately — nobody should
     * have to make an account before they can arm a panic button.
     */
    fun bootstrap() {
        scope.launch {
            when (val result = authRepository.ensureSignedIn()) {
                is com.example.guardianangel.domain.repository.AuthResult.Success -> {
                    ensureUserRow(result.user)
                    roomCodewords.seedDefaults(result.user.id)
                    Log.i(TAG, "Signed in as ${result.user.id} (${result.user.provider})")
                }
                is com.example.guardianangel.domain.repository.AuthResult.Failure ->
                    Log.e(TAG, "Sign-in failed: ${result.message}")
            }
        }
    }

    /**
     * Mirrors the auth identity into the users table.
     *
     * Firebase owns the identity but every foreign key points at a local row, so the two
     * have to be reconciled on launch — including after a reinstall, where Firebase
     * restores a session for a uid the database has never seen.
     */
    private suspend fun ensureUserRow(
        user: com.example.guardianangel.domain.repository.AuthUser,
    ) {
        val dao = database.userDao()
        val now = System.currentTimeMillis()
        val existing = dao.find(user.id)
        dao.upsert(
            existing?.copy(
                displayName = user.displayName.ifBlank { existing.displayName },
                email = user.email ?: existing.email,
                phoneNumber = user.phoneNumber ?: existing.phoneNumber,
                isAnonymous = user.isAnonymous,
                authProvider = user.provider,
                updatedAt = now,
            ) ?: com.example.guardianangel.data.local.UserEntity(
                id = user.id,
                displayName = user.displayName,
                email = user.email,
                phoneNumber = user.phoneNumber,
                isAnonymous = user.isAnonymous,
                authProvider = user.provider,
                createdAt = now,
                updatedAt = now,
            )
        )
    }
}
