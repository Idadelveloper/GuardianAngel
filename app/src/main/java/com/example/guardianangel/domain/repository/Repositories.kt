package com.example.guardianangel.domain.repository

import com.example.guardianangel.domain.model.AccountSnapshot
import com.example.guardianangel.domain.model.ActivityFilter
import com.example.guardianangel.domain.model.AnalyticsRange
import com.example.guardianangel.domain.model.Codeword
import com.example.guardianangel.domain.model.Destination
import com.example.guardianangel.domain.model.EmergencyContact
import com.example.guardianangel.domain.model.ListeningSensitivity
import com.example.guardianangel.domain.model.MonitoredSession
import com.example.guardianangel.domain.model.MovementAnalytics
import com.example.guardianangel.domain.model.OnboardingStep
import com.example.guardianangel.domain.model.RoutePlan
import com.example.guardianangel.domain.model.RoutePreference
import kotlinx.coroutines.flow.Flow

/**
 * One repository per feature area, each the sole seam between its screens and storage.
 *
 * Split by area rather than one god-repository so that, when the database lands, each can
 * be migrated independently — contacts and codewords might move to an encrypted Room
 * table first, while analytics stays derived. The split also keeps the fakes small enough
 * to read.
 */

/** Account, onboarding progress, permissions, voice enrolment and the disarm PIN. */
interface AccountRepository {
    fun observeAccount(): Flow<AccountSnapshot>

    suspend fun createAccount(fullName: String, phoneNumber: String, password: String)
    suspend fun grantPermissions(location: Boolean, microphone: Boolean)
    suspend fun saveVoiceProfile(clarityPercent: Int)
    suspend fun setSensitivity(sensitivity: ListeningSensitivity)
    suspend fun setWhisperDetection(enabled: Boolean)
    suspend fun setNoiseCancellation(enabled: Boolean)
    suspend fun setDisarmPin(pin: String)
    suspend fun advanceOnboarding(step: OnboardingStep)
    suspend fun completeOnboarding()
    suspend fun signOut()
}

/** The trusted circle. */
interface ContactsRepository {
    fun observeContacts(): Flow<List<EmergencyContact>>

    suspend fun addContact(contact: EmergencyContact)
    suspend fun updateContact(contact: EmergencyContact)
    suspend fun removeContact(contactId: String)
    /** Fire a silent test packet so the user can confirm a guardian is reachable. */
    suspend fun sendTestPing(contactId: String)
}

/** Codeword tiers and their phrases. */
interface CodewordRepository {
    fun observeCodewords(): Flow<List<Codeword>>

    suspend fun updateCodeword(codeword: Codeword)
    /** Run a phrase through the spotter without arming anything. */
    suspend fun rehearse(codewordId: String): Boolean
}

/** Recorded sessions, their transcripts, and the analytics derived from them. */
interface ActivityRepository {
    fun observeSessions(filter: ActivityFilter): Flow<List<MonitoredSession>>
    fun observeSession(sessionId: String): Flow<MonitoredSession?>
    fun observeAnalytics(range: AnalyticsRange): Flow<MovementAnalytics>

    /** Produces an encrypted ledger for sharing. Returns a user-facing file name. */
    suspend fun exportSession(sessionId: String): String
    suspend fun deleteSession(sessionId: String)
}

/** Destinations and safest-route planning. */
interface RouteRepository {
    fun observeRoutePlan(): Flow<RoutePlan>
    fun observeQuickDestinations(): Flow<List<Destination>>

    suspend fun selectDestination(destinationId: String)
    suspend fun clearDestination()
    suspend fun setPreference(preference: RoutePreference)
}
