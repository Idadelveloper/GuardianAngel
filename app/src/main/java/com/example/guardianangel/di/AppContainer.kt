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
import com.example.guardianangel.domain.repository.CodewordRepository
import com.example.guardianangel.domain.repository.ContactsRepository
import com.example.guardianangel.domain.repository.GuardianRepository
import com.example.guardianangel.domain.repository.ListeningRepository
import com.example.guardianangel.domain.repository.PermissionProbe
import com.example.guardianangel.domain.repository.RouteRepository
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

    /** The signed-in user's id, for anything scoped to them. */
    suspend fun currentUserId(): String
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
    override val permissionProbe: PermissionProbe =
        com.example.guardianangel.data.GrantedPermissions()

    // Previews never touch the network or a database.
    override val cloudSync = CloudSync(
        database = throw UnsupportedOperationException("previews do not sync"),
        firestore = null,
    )

    override suspend fun currentUserId(): String = "preview-user"
}
