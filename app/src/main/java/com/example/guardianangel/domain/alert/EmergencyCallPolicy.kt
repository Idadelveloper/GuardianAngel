package com.example.guardianangel.domain.alert

import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.EmergencyContact

/**
 * Whether an alert should also ring a guardian's phone, and whose phone.
 *
 * A text can wait in a pocket for twenty minutes. A ringing phone cannot be ignored, and
 * a voice line lets a guardian hear what a transcript can only summarise. So the top tier
 * places a call — but only the top tier, and only one call.
 *
 * The restraint is the point. If Caution rang someone's phone the user would turn the
 * whole feature off within a week, and then nothing would ring on the night it mattered.
 * One call to one person also keeps the line free: three guardians dialled in sequence
 * means two of them get a dead line and no information.
 *
 * Pure and separate from the dialler so the rule can be read and tested without a SIM.
 */
object EmergencyCallPolicy {

    /** The lowest tier that rings a phone. */
    val CALLING_TIER = CodewordTier.Emergency

    sealed interface Decision {
        /** Ring this guardian. */
        data class Call(val contact: EmergencyContact) : Decision

        /** Do not ring anyone, for this stated reason. */
        data class Skip(val reason: String) : Decision
    }

    /**
     * @param tier what the alert was raised at.
     * @param guardians the user's guardians, in any order; priority is applied here.
     * @param enabled the user's setting. Off means off, at every tier.
     * @param alreadyCalledInSession true once this session has placed a call, so a
     *   repeated emergency codeword does not hang up the call it just started.
     */
    fun decide(
        tier: CodewordTier,
        guardians: List<EmergencyContact>,
        enabled: Boolean,
        alreadyCalledInSession: Boolean = false,
    ): Decision {
        if (!enabled) return Decision.Skip("Calling a guardian is turned off in settings")
        if (tier.ordinal < CALLING_TIER.ordinal) {
            return Decision.Skip("${tier.name} alerts text rather than call")
        }
        if (alreadyCalledInSession) return Decision.Skip("A guardian has already been called")

        val callable = guardians
            .filter { it.phoneNumber.isNotBlank() }
            .sortedBy { it.priority }
        val first = callable.firstOrNull()
            ?: return Decision.Skip("No guardian has a phone number saved")

        return Decision.Call(first)
    }
}
