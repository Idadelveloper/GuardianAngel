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
                    fullName = "Maya Lin",
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

class FakeRouteRepository : RouteRepository {

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
        MutableStateFlow(RouteSamples.quickDestinations).asStateFlow()

    override suspend fun selectDestination(destinationId: String) {
        val destination = RouteSamples.quickDestinations.firstOrNull { it.id == destinationId }
            ?: return
        state.update {
            it.copy(destination = destination, routes = RouteSamples.routesTo(destination))
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
