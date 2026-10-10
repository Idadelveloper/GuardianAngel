package com.example.guardianangel.data

import com.example.guardianangel.domain.alert.GuardianNotifier
import com.example.guardianangel.domain.alert.NotifyOutcome
import com.example.guardianangel.domain.model.EmergencyContact

/**
 * Sends nothing, for previews.
 *
 * Reports blocked rather than success. A preview that claims an alert went out is the
 * kind of fake that makes a screen look like it works.
 */
class PreviewGuardianNotifier : GuardianNotifier {
    override val canSendSilently: Boolean = false

    override suspend fun notify(guardians: List<EmergencyContact>, message: String) =
        NotifyOutcome.blocked("Previews never send messages.")
}
