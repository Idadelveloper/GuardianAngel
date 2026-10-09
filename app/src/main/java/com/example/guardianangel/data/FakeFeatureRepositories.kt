package com.example.guardianangel.data

import com.example.guardianangel.domain.model.AccountSnapshot
import com.example.guardianangel.domain.model.ActivityFilter
import com.example.guardianangel.domain.model.AnalyticsRange
import com.example.guardianangel.domain.model.Codeword
import com.example.guardianangel.domain.model.Destination
import com.example.guardianangel.domain.model.DisarmPin
import com.example.guardianangel.domain.model.EmergencyContact
import com.example.guardianangel.domain.model.ListeningSensitivity
import com.example.guardianangel.domain.model.MonitoredSession
import com.example.guardianangel.domain.model.MovementAnalytics
import com.example.guardianangel.domain.model.OnboardingStep
import com.example.guardianangel.domain.model.PermissionState
import com.example.guardianangel.domain.model.RoutePlan
import com.example.guardianangel.domain.model.RoutePreference
import com.example.guardianangel.domain.model.SessionKind
import com.example.guardianangel.domain.model.UserProfile
import com.example.guardianangel.domain.model.VoiceProfile
import com.example.guardianangel.domain.repository.AccountRepository
import com.example.guardianangel.domain.repository.ActivityRepository
import com.example.guardianangel.domain.repository.CodewordRepository
import com.example.guardianangel.domain.repository.ContactsRepository
import com.example.guardianangel.domain.repository.RouteRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory stand-ins for the whole data layer.
 *
 * Each mirrors the shape its database-backed replacement will have, so swapping one over
 * is a change in `AppContainer` and nothing else. State is held in `MutableStateFlow` so
 * screens already behave as if the data were live.
 */

class FakeAccountRepository(
    startSignedIn: Boolean = true,
) : AccountRepository {

    private val state = MutableStateFlow(
        AccountSnapshot(
            profile = if (startSignedIn) {
                UserProfile(
                    id = "u1",
                    fullName = "Ida Delphine",
                    phoneNumber = "+1 (555) 392-8174",
                    isPhoneVerified = true,
                )
            } else {
                null
            },
            permissions = PermissionState(
                locationGranted = startSignedIn,
                microphoneGranted = startSignedIn,
            ),
            voiceProfile = VoiceProfile(
                isCalibrated = startSignedIn,
                clarityPercent = if (startSignedIn) 94 else 0,
                calibratedOnEpochMillis = if (startSignedIn) System.currentTimeMillis() else null,
            ),
            disarmPin = DisarmPin(isSet = startSignedIn),
            onboardingStep = if (startSignedIn) OnboardingStep.Complete else OnboardingStep.SignUp,
        )
    )

    override fun observeAccount(): Flow<AccountSnapshot> = state.asStateFlow()

    override suspend fun createAccount(fullName: String, phoneNumber: String, password: String) {
        // The password is intentionally not retained: the real implementation hands it to
        // the keystore and keeps only a derived key.
        state.update {
            it.copy(
                profile = UserProfile("u1", fullName, phoneNumber, isPhoneVerified = true),
                onboardingStep = OnboardingStep.Permissions,
            )
        }
    }

    override suspend fun updateProfile(fullName: String, phoneNumber: String) {
        state.update { snapshot ->
            snapshot.copy(
                profile = snapshot.profile?.copy(
                    fullName = fullName.ifBlank { snapshot.profile.fullName },
                    phoneNumber = phoneNumber,
                ) ?: UserProfile("u1", fullName, phoneNumber, isPhoneVerified = false),
            )
        }
    }

    override suspend fun grantPermissions(location: Boolean, microphone: Boolean) {
        state.update {
            it.copy(permissions = PermissionState(location, microphone))
        }
    }

    override suspend fun saveVoiceProfile(clarityPercent: Int) {
        state.update {
            it.copy(
                voiceProfile = it.voiceProfile.copy(
                    isCalibrated = true,
                    clarityPercent = clarityPercent,
                    calibratedOnEpochMillis = System.currentTimeMillis(),
                )
            )
        }
    }

    override suspend fun setSensitivity(sensitivity: ListeningSensitivity) =
        state.update { it.copy(voiceProfile = it.voiceProfile.copy(sensitivity = sensitivity)) }

    override suspend fun setWhisperDetection(enabled: Boolean) =
        state.update { it.copy(voiceProfile = it.voiceProfile.copy(whisperDetection = enabled)) }

    override suspend fun setNoiseCancellation(enabled: Boolean) =
        state.update { it.copy(voiceProfile = it.voiceProfile.copy(noiseCancellation = enabled)) }

    override suspend fun setDisarmPin(pin: String) =
        state.update { it.copy(disarmPin = it.disarmPin.copy(isSet = pin.isNotBlank())) }

    override suspend fun advanceOnboarding(step: OnboardingStep) =
        state.update { it.copy(onboardingStep = step) }

    override suspend fun completeOnboarding() =
        state.update { it.copy(onboardingStep = OnboardingStep.Complete) }

    override suspend fun signOut() =
        state.update { it.copy(profile = null, onboardingStep = OnboardingStep.SignUp) }
}

class FakeContactsRepository : ContactsRepository {

    private val state = MutableStateFlow(GuardianSamples.contacts)

    override fun observeContacts(): Flow<List<EmergencyContact>> = state.asStateFlow()

    override suspend fun addContact(contact: EmergencyContact) =
        state.update { (it + contact).sortedBy(EmergencyContact::priority) }

    override suspend fun updateContact(contact: EmergencyContact) =
        state.update { list -> list.map { if (it.id == contact.id) contact else it } }

    override suspend fun removeContact(contactId: String) =
        state.update { list -> list.filterNot { it.id == contactId } }

    override suspend fun sendTestPing(contactId: String) {
        delay(900) // Stands in for the round trip to the guardian's device.
    }
}

class FakeCodewordRepository : CodewordRepository {

    private val state = MutableStateFlow(GuardianSamples.codewords)

    override fun observeCodewords(): Flow<List<Codeword>> = state.asStateFlow()

    override suspend fun updateCodeword(codeword: Codeword) =
        state.update { list -> list.map { if (it.id == codeword.id) codeword else it } }

    override suspend fun rehearse(codewordId: String): Boolean {
        delay(1200)
        return true
    }
}

class FakeActivityRepository : ActivityRepository {

    private val state = MutableStateFlow(ActivitySamples.sessions)

    override fun observeSessions(filter: ActivityFilter): Flow<List<MonitoredSession>> =
        state.map { sessions ->
            when (filter) {
                ActivityFilter.All -> sessions
                ActivityFilter.Incidents -> sessions.filter { it.kind == SessionKind.Incident }
                ActivityFilter.Conversations ->
                    sessions.filter { it.kind == SessionKind.Conversation }
            }
        }

    override fun observeSession(sessionId: String): Flow<MonitoredSession?> =
        state.map { sessions -> sessions.firstOrNull { it.id == sessionId } }

    override fun observeAnalytics(range: AnalyticsRange): Flow<MovementAnalytics> =
        MutableStateFlow(ActivitySamples.analytics(range)).asStateFlow()

    override suspend fun exportSession(sessionId: String): String {
        delay(800)
        return "guardian-angel-$sessionId.pdf"
    }

    override suspend fun deleteSession(sessionId: String) =
        state.update { list -> list.filterNot { it.id == sessionId } }
}

class FakeRouteRepository(
    private val crimeDataService: com.example.guardianangel.data.crime.CrimeDataService = com.example.guardianangel.data.crime.CrimeDataService()
) : RouteRepository {

    private val allDestinations = listOf(
        RouteSamples.home,
        Destination("work", "Work office", "450 Kendall St", com.example.guardianangel.domain.model.GeoPoint(37.7749, -122.4194), walkingMinutes = 18),
        Destination("library", "Campus Library", "Doe Memorial", com.example.guardianangel.domain.model.GeoPoint(37.7845, -122.4080), walkingMinutes = 12, isSafeHaven = true),
        Destination("market", "Trader Joe's", "Shattuck Ave", com.example.guardianangel.domain.model.GeoPoint(37.7780, -122.4160), walkingMinutes = 9),
        Destination("police", "Metro Police Precinct", "767 Bryant St", com.example.guardianangel.domain.model.GeoPoint(37.7765, -122.4168), walkingMinutes = 15, isSafeHaven = true),
        Destination("hospital", "Emergency Medical Hub", "900 Hyde St", com.example.guardianangel.domain.model.GeoPoint(37.7845, -122.4140), walkingMinutes = 20, isSafeHaven = true),
        Destination("ferry", "Ferry Building Plaza", "1 Ferry Plaza", com.example.guardianangel.domain.model.GeoPoint(37.7955, -122.3937), walkingMinutes = 25),
        Destination("civic", "Civic Center Station", "Market & 8th St", com.example.guardianangel.domain.model.GeoPoint(37.7797, -122.4141), walkingMinutes = 10, isSafeHaven = true),
    )

    private val state = MutableStateFlow(
        RoutePlan(
            origin = RouteSamples.home,
            destination = null,
            preference = RoutePreference.Safest,
            routes = emptyList(),
            isNightPatrolActive = true,
            areaIlluminationPercent = 95,
        )
    )

    override fun observeRoutePlan(): Flow<RoutePlan> = state.asStateFlow()

    override fun observeQuickDestinations(): Flow<List<Destination>> =
        MutableStateFlow(allDestinations.drop(1)).asStateFlow()

    override suspend fun selectDestination(destinationId: String) {
        val destination = allDestinations.firstOrNull { it.id == destinationId } ?: return
        selectCustomDestination(destination)
    }

    override suspend fun selectCustomDestination(destination: Destination) {
        val currentOrigin = state.value.origin
        val computedRoutes = crimeDataService.planSafeRoutes(currentOrigin.point, destination.point)
        state.update {
            it.copy(
                destination = destination,
                routes = computedRoutes,
            )
        }
    }

    override suspend fun clearDestination() =
        state.update { it.copy(destination = null, routes = emptyList()) }

    override suspend fun setPreference(preference: RoutePreference) {
        state.update { plan ->
            // Re-rank so whichever preference is active becomes the recommended corridor.
            val ranked = plan.routes.map { it.copy(isRecommended = it.preference == preference) }
            plan.copy(
                preference = preference,
                routes = if (ranked.none { it.isRecommended } && ranked.isNotEmpty()) {
                    ranked.mapIndexed { i, r -> r.copy(isRecommended = i == 0) }
                } else {
                    ranked
                },
            )
        }
    }
}
