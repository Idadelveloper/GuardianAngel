package com.example.guardianangel.domain.codeword

import com.example.guardianangel.domain.model.Codeword
import com.example.guardianangel.domain.model.CodewordTier

/**
 * Decides whether a spoken codeword should actually do anything.
 *
 * Pure and time-injected so every rule below is testable without a device, which matters
 * because the rules are the product: getting them wrong means either calling the police
 * because a friend said "lighthouse" in a sentence, or staying silent while the user
 * repeats her danger word.
 *
 * ## The four rules
 *
 * **1. Once per utterance.** Matching runs over a rolling window of recent transcript, so
 * a phrase said once appears in many consecutive evaluations. Without de-duplication the
 * action fired on every chunk for as long as the phrase stayed in the window — tens of
 * alerts from one word.
 *
 * **2. It has to be her.** A codeword is an instruction to act on someone's behalf, so it
 * only counts from the enrolled voice. An unverifiable voice — no voiceprint enrolled, or
 * a segment too short to judge — is *not* treated as a stranger: it is allowed through
 * for the tiers that only gather evidence, and held for the ones that contact people.
 * Refusing everything unverifiable would make codewords useless for anyone who skipped
 * enrolment; accepting everything would let a stranger holding her phone cancel an alert.
 *
 * **3. Acting on people waits a moment.** Danger and Emergency are held briefly before
 * dispatch so an accident can be taken back. The hold is short — the cost of a few
 * seconds against the cost of a false alarm that teaches her to stop trusting the app.
 * Caution records and needs no hold; Safe cancels and must be instant.
 *
 * **4. Safe cancels anything pending.** That is what makes rule 3 worth having.
 */
class CodewordGate(
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** A tier waiting out its grace period. */
    data class Pending(
        val tier: CodewordTier,
        val phrase: String,
        val dispatchAtMillis: Long,
        val saidByEnrolledUser: Boolean,
    )

    sealed interface Decision {
        /** Nothing in this chunk, or it was already handled. */
        data object None : Decision

        /** Act now: record, or cancel. */
        data class Act(val tier: CodewordTier, val phrase: String) : Decision

        /** Held back; [Pending.dispatchAtMillis] is when it fires unless cancelled. */
        data class Holding(val pending: Pending) : Decision

        /** A pending tier was taken back, either by the Safe word or by a tap. */
        data class Cancelled(val tier: CodewordTier) : Decision

        /**
         * Heard, but not acted on.
         *
         * Surfaced rather than swallowed so the session record can show that a phrase was
         * recognised and deliberately ignored. Silence here would look like a bug to the
         * user and like missing evidence to anyone reading the transcript later.
         */
        data class Withheld(
            val tier: CodewordTier,
            val phrase: String,
            val reason: WithheldReason,
        ) : Decision
    }

    enum class WithheldReason {
        /** Said by a voice that is positively not the enrolled user. */
        DifferentSpeaker,

        /** Could not be verified, and this tier contacts people. */
        UnverifiedSpeaker,
    }

    private var pending: Pending? = null
    private val firedAt = mutableMapOf<CodewordTier, Long>()

    val currentlyPending: Pending? get() = pending

    /**
     * Evaluates one transcript line.
     *
     * @param isEnrolledUser true when this segment matched the voiceprint, false when it
     *   matched someone else, null when it could not be judged.
     */
    fun evaluate(
        text: String,
        isEnrolledUser: Boolean?,
        codewords: List<Codeword>,
    ): Decision {
        val match = findMatch(text, codewords) ?: return Decision.None
        val (tier, phrase) = match

        // Safe first and unconditionally: cancelling has to work even while another tier
        // is mid-hold, and it is the control a frightened user will reach for.
        if (tier == CodewordTier.Safe) {
            if (!speakerAllowed(isEnrolledUser, tier)) {
                return Decision.Withheld(tier, phrase, reasonFor(isEnrolledUser))
            }
            val cancelled = pending
            pending = null
            markFired(tier)
            return cancelled
                ?.let { Decision.Cancelled(it.tier) }
                ?: Decision.Act(tier, phrase)
        }

        if (recentlyFired(tier)) return Decision.None

        if (!speakerAllowed(isEnrolledUser, tier)) {
            // Recorded as fired so the same phrase in the same breath is not re-reported
            // on every subsequent chunk.
            markFired(tier)
            return Decision.Withheld(tier, phrase, reasonFor(isEnrolledUser))
        }

        markFired(tier)

        val hold = holdMillis(tier)
        if (hold <= 0) return Decision.Act(tier, phrase)

        val next = Pending(
            tier = tier,
            phrase = phrase,
            dispatchAtMillis = now() + hold,
            saidByEnrolledUser = isEnrolledUser == true,
        )
        // A more serious tier replaces a less serious one that is still waiting; the
        // reverse does not, so saying "caution" after "danger" cannot defuse it.
        pending = pending?.takeIf { it.tier.ordinal > tier.ordinal } ?: next
        return Decision.Holding(pending!!)
    }

    /**
     * Whether the pending tier is due.
     *
     * Polled rather than scheduled so the caller owns the timing: a service that has been
     * killed should not have a timer resurrect an alert, and a test should not have to
     * wait ten real seconds.
     */
    fun dueForDispatch(): Pending? = pending?.takeIf { now() >= it.dispatchAtMillis }

    /** Marks a pending tier as dispatched. */
    fun consumePending(): Pending? = pending.also { pending = null }

    /** Cancels from a tap — the "I didn't mean that" button. */
    fun cancelPending(): CodewordTier? = pending?.tier.also { pending = null }

    /** Forgets everything. Called when a session ends. */
    fun reset() {
        pending = null
        firedAt.clear()
    }

    /**
     * Finds the highest tier mentioned.
     *
     * Highest rather than first so "I'm fine, lighthouse" is treated as the danger word
     * it contains. Word-boundary matched, so "safe" does not fire inside "safely".
     */
    private fun findMatch(text: String, codewords: List<Codeword>): Pair<CodewordTier, String>? {
        val haystack = text.lowercase()
        return CodewordTier.entries
            .sortedByDescending { it.ordinal }
            .firstNotNullOfOrNull { tier ->
                codewords.firstOrNull { it.tier == tier && it.phrase.isNotBlank() }
                    ?.let { codeword ->
                        val phrase = codeword.phrase.trim().lowercase()
                        if (Regex("\\b${Regex.escape(phrase)}\\b").containsMatchIn(haystack)) {
                            tier to codeword.phrase.trim()
                        } else {
                            null
                        }
                    }
            }
    }

    private fun recentlyFired(tier: CodewordTier): Boolean =
        firedAt[tier]?.let { now() - it < REPEAT_WINDOW_MILLIS } == true

    private fun markFired(tier: CodewordTier) {
        firedAt[tier] = now()
    }

    /**
     * Whether this speaker may trigger this tier.
     *
     * A positive mismatch always blocks. An *unknown* speaker is allowed for Caution,
     * which only starts gathering evidence, and blocked for the tiers that reach out to
     * people or cancel an alert someone may be relying on.
     */
    private fun speakerAllowed(isEnrolledUser: Boolean?, tier: CodewordTier): Boolean =
        when (isEnrolledUser) {
            true -> true
            false -> false
            null -> tier == CodewordTier.Caution
        }

    private fun reasonFor(isEnrolledUser: Boolean?): WithheldReason =
        if (isEnrolledUser == false) {
            WithheldReason.DifferentSpeaker
        } else {
            WithheldReason.UnverifiedSpeaker
        }

    private fun holdMillis(tier: CodewordTier): Long = when (tier) {
        CodewordTier.Safe -> 0
        CodewordTier.Caution -> 0
        CodewordTier.Danger -> DANGER_HOLD_MILLIS
        CodewordTier.Emergency -> EMERGENCY_HOLD_MILLIS
    }

    companion object {
        /**
         * How long the same tier is ignored after firing.
         *
         * Long enough to cover the rolling transcript window that caused the repeat
         * problem, short enough that saying the word again a minute later is heard.
         */
        const val REPEAT_WINDOW_MILLIS = 45_000L

        /** Grace before guardians are alerted. */
        const val DANGER_HOLD_MILLIS = 10_000L

        /**
         * Grace before emergency services are called.
         *
         * Shorter than Danger, deliberately. Both costs are real — a wrongly placed 911
         * call is serious, and so is three extra seconds — but someone who says her
         * emergency word has made the most explicit request the app accepts, and the
         * design should not second-guess it for long.
         */
        const val EMERGENCY_HOLD_MILLIS = 4_000L
    }
}
