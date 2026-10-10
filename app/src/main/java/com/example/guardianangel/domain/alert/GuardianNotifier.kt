package com.example.guardianangel.domain.alert

import com.example.guardianangel.domain.model.EmergencyContact

/**
 * Gets a message to the people who can help.
 *
 * An interface because *how* a message leaves the phone is a deployment question with
 * real constraints attached, and the rest of the app should not care which route was
 * taken — only whether it worked, and who it reached.
 */
interface GuardianNotifier {

    /** Whether messages can be sent without the user doing anything. */
    val canSendSilently: Boolean

    suspend fun notify(
        guardians: List<EmergencyContact>,
        message: String,
    ): NotifyOutcome
}

/**
 * What happened when the alert went out.
 *
 * Reported per guardian rather than as one boolean: "two of three were reached" is the
 * truth, and rounding it to "sent" or "failed" would be the app lying at the moment it
 * matters most. The session record and the UI both show this.
 */
data class NotifyOutcome(
    val reached: List<String>,
    val failed: List<String>,
    /** Set when nothing could be sent at all, phrased for the user. */
    val blockedReason: String? = null,
) {
    val anyReached: Boolean get() = reached.isNotEmpty()

    companion object {
        fun blocked(reason: String) = NotifyOutcome(emptyList(), emptyList(), reason)
    }
}
