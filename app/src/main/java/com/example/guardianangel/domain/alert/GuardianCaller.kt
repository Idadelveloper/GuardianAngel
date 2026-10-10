package com.example.guardianangel.domain.alert

import com.example.guardianangel.domain.model.EmergencyContact

/**
 * Places the call to a guardian.
 *
 * Separated from [EmergencyCallPolicy] because the two fail for unrelated reasons: the
 * policy can decline on the user's own settings, while this can be defeated by a missing
 * permission, a tablet with no SIM, or Android refusing a background activity start.
 *
 * The outcome distinguishes a call that is **ringing** from one the user still has to
 * confirm. Nothing in the app may report the second as the first — a guardian who was
 * never actually dialled is the one thing the person holding the phone must not be told
 * has been handled.
 */
interface GuardianCaller {

    sealed interface Outcome {
        /** The dialler is calling [contact] now. */
        data class Dialling(val contact: EmergencyContact) : Outcome

        /**
         * A one-tap call notification was posted but the call has not started.
         *
         * This is what happens when the phone is locked in a pocket: Android will not let
         * a background app open the dialler, so the user (or whoever picks up the phone)
         * taps once to connect.
         */
        data class AwaitingTap(val contact: EmergencyContact) : Outcome

        /** Nothing was placed. [reason] is written to the session as-is. */
        data class Failed(val reason: String) : Outcome
    }

    suspend fun call(contact: EmergencyContact): Outcome

    /** Whether this device can place calls at all, with the permissions it has now. */
    fun canCall(): Boolean
}
