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
import com.example.guardianangel.domain.model.SessionTrail
import com.example.guardianangel.domain.model.TrailPoint
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

    /**
     * Updates the profile the settings and home screens read.
     *
     * Separate from [createAccount] because that one also advances the wizard. There was
     * previously no way to change a name after sign-up, so a typo in the one string shown
     * on every screen was permanent.
     */
    suspend fun updateProfile(fullName: String, phoneNumber: String)
    suspend fun grantPermissions(location: Boolean, microphone: Boolean)
    suspend fun saveVoiceProfile(clarityPercent: Int)
    suspend fun setSensitivity(sensitivity: ListeningSensitivity)
    suspend fun setWhisperDetection(enabled: Boolean)
    suspend fun setNoiseCancellation(enabled: Boolean)
    suspend fun setDisarmPin(pin: String)

    /**
     * Turns calling a guardian on an emergency alert on or off.
     *
     * The text always goes out; this only governs the phone ringing.
     */
    suspend fun setCallGuardianOnEmergency(enabled: Boolean)
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

    /**
     * Where one session went, and what happened along the way.
     *
     * Separate from [observeSession] because the detail screen needs it and the list
     * does not — loading every transcript line to draw a thumbnail would be wasteful.
     */
    fun observeTrail(sessionId: String): Flow<SessionTrail>

    /**
     * Paths only, for the previews in a list.
     *
     * Batched into one query rather than one per row: a screen of twenty sessions
     * should not mean twenty round trips, and the preview needs no incidents to draw
     * the shape of a walk.
     */
    fun observeTrailPaths(): Flow<Map<String, List<TrailPoint>>>

    /**
     * Replaces the title and summary a finished session was saved with.
     *
     * Written by `SessionSummaryAgent` once the recording is closed. The recorder can
     * only name a session by how long it ran, because while it is running nobody knows
     * yet what it was.
     */
    suspend fun updateNarrative(sessionId: String, title: String, summary: String)

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
    suspend fun selectCustomDestination(destination: Destination) = Unit
    suspend fun updateOrigin(originPoint: com.example.guardianangel.domain.model.GeoPoint) = Unit
}
