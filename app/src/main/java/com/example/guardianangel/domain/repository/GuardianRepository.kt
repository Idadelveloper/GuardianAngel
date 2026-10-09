package com.example.guardianangel.domain.repository

import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.GuardianSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * The single seam between the UI and wherever Guardian Angel's data actually lives.
 *
 * The home screen talks only to this interface, so moving from the in-memory
 * [com.example.guardianangel.data.FakeGuardianRepository] to Room, Firestore or a backend
 * means writing one new implementation and changing one construction site — no screen or
 * component changes.
 *
 * Reads are a [Flow] rather than a suspend function because this data is genuinely live:
 * the safety score re-evaluates as the user moves, contacts come online, and a recording
 * session accumulates transcript lines while the screen is open.
 */
interface GuardianRepository {

    /** The continuously updating snapshot the home screen renders. */
    fun observeSnapshot(): Flow<GuardianSnapshot>

    /** Turn ambient codeword listening on. */
    suspend fun armGuardian()

    /** Return to standby and stop listening. */
    suspend fun disarmGuardian()

    /**
     * Begin recording and transcribing.
     *
     * @param trigger which tier caused this, or null when the user started it by hand.
     */
    suspend fun startRecording(trigger: CodewordTier? = null)

    /** Stop the active session — the "this was accidental" path. */
    suspend fun stopRecording()

    /** Alert the contacts attached to [tier] and share current location. */
    suspend fun dispatchAlert(tier: CodewordTier)

    /** Re-evaluate the safety score for the current position. */
    suspend fun rescanArea()
}
