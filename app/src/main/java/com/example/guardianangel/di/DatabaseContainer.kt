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
import com.example.guardianangel.domain.repository.SessionRecorder
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    private val tokenizer: SentencePieceTokenizer? by lazy {
        runCatching {
            SentencePieceTokenizer.fromAssets(
                context.assets,
                "sherpa-onnx-kws-zipformer-gigaspeech/bpe.model",
            )
        }.onFailure { Log.i(TAG, "No keyword vocabulary; wake phrases will not be tokenised") }
            .getOrNull()
    }

    override val authRepository: AuthRepository =
        if (FirebaseAvailability.isConfigured(context)) {
            FirebaseAuthRepository(FirebaseAuth.getInstance(), activityProvider)
        } else {
            LocalAuthRepository(database.userDao())
        }

    private val wakeWordStore = RoomWakeWordStore(
        dao = database.wakeWordDao(),
        currentUser = currentUser,
        tokenizerProvider = { tokenizer },
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

    /**
     * True once this device has an account row, signed in or not.
     *
     * Read through the DAO rather than the auth state because a signed-out account still
     * exists — that is the difference between showing a returning user the log-in screen
     * and showing her a sign-up form.
     */
    override fun observeHasAccount(): kotlinx.coroutines.flow.Flow<Boolean> =
        database.userDao().observeAccountCount()
            .map { it > 0 }
            .distinctUntilChanged()

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

    override val safeLocationRepository: com.example.guardianangel.domain.repository.SafeLocationRepository =
        com.example.guardianangel.data.local.RoomSafeLocationRepository(database.safePlaceDao(), currentUser)

    override val berkeleySafetyDataSource = com.example.guardianangel.data.berkeley.BerkeleySafetyDataSource(context)
    override val weatherProvider: com.example.guardianangel.data.weather.WeatherProvider =
        com.example.guardianangel.data.weather.NationalWeatherServiceApiProvider()

    private fun getMapsApiKey(): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    override val placesSearchProvider: com.example.guardianangel.data.places.PlacesSearchProvider =
        com.example.guardianangel.data.places.GooglePlacesSearchProvider(apiKeyProvider = { getMapsApiKey() })

    private val rawRouteProvider = com.example.guardianangel.data.routes.GoogleRoutesApiProvider(
        apiKeyProvider = { getMapsApiKey() },
        fallbackProvider = com.example.guardianangel.data.routes.FakeRouteProvider(),
    )
    private val deterministicRanker = com.example.guardianangel.data.routes.DeterministicRouteRanker(berkeleySafetyDataSource)
    private val routeCoordinator = com.example.guardianangel.agent.berkeley.BerkeleyRouteCoordinator(
        routeProvider = rawRouteProvider,
        safetyDataSource = berkeleySafetyDataSource,
        weatherProvider = weatherProvider,
        ranker = deterministicRanker,
    )

    // Derived from live Berkeley safety data & routing engines
    override val guardianRepository: GuardianRepository = FakeGuardianRepository()
    override val routeRepository: RouteRepository = com.example.guardianangel.data.routes.BerkeleyRouteRepository(
        coordinator = routeCoordinator,
        safetyDataSource = berkeleySafetyDataSource,
        safeLocationRepository = safeLocationRepository,
        scope = scope,
    )
    /** Writes the live recording. Shared with the listening service via this container. */
    override val sessionRecorder: SessionRecorder =
        com.example.guardianangel.data.local.RoomSessionRecorder(
            dao = database.sessionDao(),
            currentUser = currentUser,
        )

    /**
     * Real sessions, read back from Room.
     *
     * Replaces `FakeActivityRepository`, which served three invented incidents to every
     * account. An app that shows imaginary evidence teaches the user its records cannot
     * be trusted, which for this app is the whole product.
     */
    override val activityRepository: ActivityRepository =
        com.example.guardianangel.data.local.RoomActivityRepository(
            dao = database.sessionDao(),
            currentUser = currentUser,
            exporter = com.example.guardianangel.data.local.FileTranscriptExporter(context),
        )

    override val crimeDataService = com.example.guardianangel.data.crime.CrimeDataService()
    override val locationTracker = com.example.guardianangel.data.platform.LocationTracker(context)
    /**
     * The agent that reaches a human, and the one path to it.
     *
     * Built here so the SOS hold and a spoken codeword share a single notifier, a single
     * de-duplication state and a single place that writes the outcome into the session.
     * Two instances would mean an alert from the button and one from a codeword could
     * both fire for the same incident.
     */
    override val tripRepository: com.example.guardianangel.domain.repository.TripRepository =
        com.example.guardianangel.data.local.RoomTripRepository(
            dao = database.tripDao(),
            currentUser = currentUser,
        )

    override val guardianNotifier: com.example.guardianangel.domain.alert.GuardianNotifier =
        com.example.guardianangel.data.platform.SmsGuardianNotifier(context)

    val notificationAgent = com.example.guardianangel.agent.GuardianNotificationAgent(
        guardianNotifier
    )

    override val alertDispatcher = com.example.guardianangel.domain.alert.AlertDispatcher(
        notificationAgent = notificationAgent,
        contacts = contactsRepository,
        guardianRepository = guardianRepository,
        recorder = sessionRecorder,
        profileName = {
            accountRepository.observeAccount().first().profile?.fullName.orEmpty()
        },
        currentLocation = {
            locationTracker.getCurrentLocation()?.let { point ->
                com.example.guardianangel.domain.alert.AlertDispatcher.LocationFix(
                    latitude = point.latitude,
                    longitude = point.longitude,
                    placeLabel = runCatching { locationTracker.reverseGeocode(point) }.getOrNull(),
                )
            }
        },
        sessionEntries = {
            // The live session's lines, so the alert quotes what was just said rather
            // than describing the situation in the abstract.
            val id = sessionRecorder.activeSessionId
            if (id.isNullOrBlank()) {
                emptyList()
            } else {
                runCatching {
                    activityRepository.observeSession(id).first()?.entries
                }.getOrNull().orEmpty()
            }
        },
        caller = com.example.guardianangel.data.platform.TelephonyGuardianCaller(context),
        callingEnabled = {
            accountRepository.observeAccount().first().profile?.callGuardianOnEmergency ?: false
        },
    )

    override val summaryAgent =
        com.example.guardianangel.agent.SessionSummaryAgent(activityRepository)

    override val angelOrchestrator: com.example.guardianangel.agent.AngelAgentOrchestrator by lazy {
        com.example.guardianangel.agent.AngelAgentOrchestrator(crimeDataService, scope)
    }

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
                    onAuthenticated(result.user)
                    Log.i(TAG, "Resumed session for ${result.user.id} (${result.user.provider})")
                }
                is com.example.guardianangel.domain.repository.AuthResult.Failure ->
                    // Not an error: no session means the auth gate is about to be shown.
                    Log.i(TAG, "No session to resume (${result.message})")
            }
        }
    }

    /**
     * Prepares the local database for a freshly authenticated account.
     *
     * Called on session resume *and* straight after sign-up or sign-in from the UI,
     * because an account can now come into existence either way. Idempotent, so running
     * it twice for the same user is harmless.
     *
     * Note what it deliberately does **not** do: set a wake word, or claim voice
     * enrolment. An earlier version seeded a phrase and three fake enrolment takes, which
     * made a brand-new account report hands-free as configured and silenced the prompt
     * telling the user to set it up. Codewords are seeded because the four tiers must
     * never be empty, but they are flagged as suggestions rather than choices — see
     * `CodewordEntity.isCustomised`.
     */
    override suspend fun onAuthenticated(
        user: com.example.guardianangel.domain.repository.AuthUser,
    ) {
        ensureUserRow(user)
        roomCodewords.seedDefaults(user.id)
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
