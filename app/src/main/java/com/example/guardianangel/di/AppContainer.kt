package com.example.guardianangel.di

import com.example.guardianangel.data.FakeAccountRepository
import com.example.guardianangel.data.FakeActivityRepository
import com.example.guardianangel.data.FakeCodewordRepository
import com.example.guardianangel.data.FakeContactsRepository
import com.example.guardianangel.data.FakeGuardianRepository
import com.example.guardianangel.data.FakeListeningRepository
import com.example.guardianangel.data.FakeRouteRepository
import com.example.guardianangel.domain.repository.AccountRepository
import com.example.guardianangel.domain.repository.ActivityRepository
import com.example.guardianangel.data.sync.CloudSync
import com.example.guardianangel.domain.repository.AuthRepository
import com.example.guardianangel.domain.repository.AuthUser
import com.example.guardianangel.domain.repository.CodewordRepository
import com.example.guardianangel.domain.repository.ContactsRepository
import com.example.guardianangel.domain.repository.GuardianRepository
import com.example.guardianangel.domain.repository.ListeningRepository
import com.example.guardianangel.domain.repository.PermissionProbe
import com.example.guardianangel.domain.repository.RouteRepository
import com.example.guardianangel.domain.repository.SessionRecorder
import com.example.guardianangel.domain.repository.VoiceProfileRepository

/**
 * The application's object graph.
 *
 * Deliberately hand-rolled rather than Hilt: at this size a single container is easier to
 * read than generated components, and it keeps the swap point obvious — when the database
 * lands, each `Fake…` below is replaced by its Room- or network-backed sibling and no
 * screen, view model or component changes.
 *
 * Constructed once in `GuardianAngelApp` and passed down. Screens never reach for it
 * directly; they receive the one repository they need, which keeps their dependencies
 * honest and testable.
 */
interface AppContainer {
    val guardianRepository: GuardianRepository
    val accountRepository: AccountRepository
    val contactsRepository: ContactsRepository
    val codewordRepository: CodewordRepository
    val activityRepository: ActivityRepository
    val routeRepository: RouteRepository
    val listeningRepository: ListeningRepository
    val authRepository: AuthRepository

    /**
     * Writes the live recording to storage as it happens.
     *
     * On the container because the listening service has no activity to hand it
     * anything, and because the Activity screens must read exactly what it wrote.
     */
    val sessionRecorder: SessionRecorder

    /** The enrolled voiceprint. Encrypted at rest and never uploaded. */
    val voiceProfileRepository: VoiceProfileRepository

    /**
     * Live permission state.
     *
     * Exposed so screens can ask the system rather than cache an answer — a cached
     * permission is wrong the moment the user changes it in system settings.
     */
    val permissionProbe: PermissionProbe

    /** Optional cloud backup. Reports Unavailable when no Firebase project is set up. */
    val cloudSync: CloudSync

    val crimeDataService: com.example.guardianangel.data.crime.CrimeDataService
    val locationTracker: com.example.guardianangel.data.platform.LocationTracker?
    val angelOrchestrator: com.example.guardianangel.agent.AngelAgentOrchestrator
    val safeLocationRepository: com.example.guardianangel.domain.repository.SafeLocationRepository
    val berkeleySafetyDataSource: com.example.guardianangel.data.berkeley.BerkeleySafetyDataSource
    val weatherProvider: com.example.guardianangel.data.weather.WeatherProvider
    val placesSearchProvider: com.example.guardianangel.data.places.PlacesSearchProvider

    /**
     * The one path a guardian alert takes, whatever triggered it.
     *
     * On the container because both the SOS hold (UI) and a spoken codeword (the
     * listening service, which has no activity) have to reach the same code.
     */
    val alertDispatcher: com.example.guardianangel.domain.alert.AlertDispatcher

    /** Names a recording once it is finished. See `SessionSummaryAgent`. */
    val summaryAgent: com.example.guardianangel.agent.SessionSummaryAgent

    /** The signed-in user's id, for anything scoped to them. */
    suspend fun currentUserId(): String

    /**
     * Prepares local storage for a freshly authenticated account.
     *
     * Called by the auth screens after a successful sign-up or sign-in. Before the login
     * gate existed, this work happened during a silent anonymous bootstrap; now an
     * account can first appear from the UI, so the UI has to say when.
     */
    suspend fun onAuthenticated(user: AuthUser)

    /**
     * Whether this device holds an account at all, signed in or not.
     *
     * Decides whether the auth gate opens on log-in or sign-up. Distinct from the auth
     * state, which only says whether a session is *open*.
     */
    fun observeHasAccount(): kotlinx.coroutines.flow.Flow<Boolean>
}

/**
 * The in-memory container used until persistence exists.
 *
 * @param startSignedIn false launches into onboarding; true lands straight on Home. Lets
 *   the onboarding flow be exercised without clearing app data every time.
 */
class InMemoryAppContainer(
    startSignedIn: Boolean = true,
) : AppContainer {
    override val guardianRepository: GuardianRepository = FakeGuardianRepository()
    override val accountRepository: AccountRepository = FakeAccountRepository(startSignedIn)
    override val contactsRepository: ContactsRepository = FakeContactsRepository()
    override val codewordRepository: CodewordRepository = FakeCodewordRepository()
    override val activityRepository: ActivityRepository = FakeActivityRepository()
    override val routeRepository: RouteRepository = FakeRouteRepository()
    override val listeningRepository: ListeningRepository =
        FakeListeningRepository(startEnrolled = startSignedIn)
    override val authRepository: AuthRepository =
        com.example.guardianangel.data.auth.PreviewAuthRepository(startSignedIn)
    override val voiceProfileRepository: VoiceProfileRepository =
        com.example.guardianangel.data.FakeVoiceProfileRepository(enrolled = startSignedIn)
    override val sessionRecorder: SessionRecorder =
        com.example.guardianangel.data.NoOpSessionRecorder()
    override val permissionProbe: PermissionProbe =
        com.example.guardianangel.data.GrantedPermissions()

    override val crimeDataService = com.example.guardianangel.data.crime.CrimeDataService()
    override val locationTracker: com.example.guardianangel.data.platform.LocationTracker? = null
    override val safeLocationRepository: com.example.guardianangel.domain.repository.SafeLocationRepository =
        com.example.guardianangel.data.FakeSafeLocationRepository()
    override val berkeleySafetyDataSource = com.example.guardianangel.data.berkeley.BerkeleySafetyDataSource()
    override val weatherProvider: com.example.guardianangel.data.weather.WeatherProvider =
        com.example.guardianangel.data.weather.FakeWeatherProvider()
    override val placesSearchProvider: com.example.guardianangel.data.places.PlacesSearchProvider =
        com.example.guardianangel.data.places.GooglePlacesSearchProvider(apiKeyProvider = { "" })
    override val angelOrchestrator = com.example.guardianangel.agent.AngelAgentOrchestrator(
        crimeDataService,
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    )

    // Previews never touch the network or a database.
    override val cloudSync = CloudSync(
        database = throw UnsupportedOperationException("previews do not sync"),
        firestore = null,
    )

    override suspend fun currentUserId(): String = "preview-user"

    override val alertDispatcher: com.example.guardianangel.domain.alert.AlertDispatcher =
        com.example.guardianangel.domain.alert.AlertDispatcher(
            notificationAgent = com.example.guardianangel.agent.GuardianNotificationAgent(
                com.example.guardianangel.data.PreviewGuardianNotifier()
            ),
            contacts = contactsRepository,
            guardianRepository = guardianRepository,
            recorder = sessionRecorder,
            profileName = { "Preview" },
            currentLocation = { null },
            sessionEntries = { emptyList() },
        )

    override val summaryAgent =
        com.example.guardianangel.agent.SessionSummaryAgent(activityRepository)

    override suspend fun onAuthenticated(user: AuthUser) = Unit

    override fun observeHasAccount() = kotlinx.coroutines.flow.flowOf(true)
}
