package com.example.guardianangel.agent

import android.util.Log
import com.example.guardianangel.domain.alert.AlertComposer
import com.example.guardianangel.domain.alert.GuardianNotifier
import com.example.guardianangel.domain.alert.NotifyOutcome
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.DiarizedEntry
import com.example.guardianangel.domain.model.EmergencyContact
import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val TAG = "NotificationAgent"

/**
 * The agent that decides whether to reach a human, and what to tell them.
 *
 * Separated from the agents that *perceive* because its mistakes are a different kind.
 * A perception agent that is wrong moves a score; this one wakes someone at 2am or fails
 * to. So it owns the judgement the others do not:
 *
 * - **Who gets told.** Caution reaches the first guardian only; Danger and Emergency
 *   reach everyone. Waking five people because a recording started would train them to
 *   ignore the next one, and the next one is the one that matters.
 * - **Not twice for the same thing.** An alert for a tier already sent in this session
 *   is suppressed unless it has escalated. The codeword gate already de-duplicates
 *   utterances; this guards the rest — a heuristic escalation and a spoken codeword
 *   arriving together must not produce two texts.
 * - **Escalation always gets through.** Danger after Caution, or Emergency after
 *   Danger, sends again. Only a repeat at the same level or lower is dropped.
 * - **It reports what actually happened**, per guardian, so the evidence log and the UI
 *   can say "reached Amara, could not reach David" instead of a hopeful "sent".
 */
class GuardianNotificationAgent(
    private val notifier: GuardianNotifier,
) : BaseAgent(
    name = "GuardianNotificationAgent",
    description = "Composes and dispatches guardian alerts, deciding who to reach and when",
) {
    /** Highest tier already dispatched this session. */
    private var highestSent: CodewordTier? = null

    data class Dispatch(
        val tier: CodewordTier,
        val message: String,
        val outcome: NotifyOutcome,
        val suppressed: Boolean = false,
    ) {
        val summaryLine: String
            get() = when {
                suppressed -> "${tier.name} alert already sent this session; not repeated."
                outcome.anyReached ->
                    "${tier.name} alert sent to ${outcome.reached.joinToString(", ")}" +
                        outcome.failed.takeIf { it.isNotEmpty() }
                            ?.let { "; could not reach ${it.joinToString(", ")}" }
                            .orEmpty()
                outcome.blockedReason != null -> "${tier.name} alert not sent — ${outcome.blockedReason}"
                else -> "${tier.name} alert could not be delivered to anyone."
            }
    }

    override fun runAsyncImpl(context: InvocationContext): Flow<Event> = flow {
        emit(
            Event(
                invocationId = context.invocationId,
                author = name,
                content = Content(
                    role = Role.MODEL,
                    parts = listOf(
                        Part(text = "Notification agent ready; highest sent: $highestSent")
                    ),
                ),
                output = highestSent,
            )
        )
    }

    /**
     * Composes and sends.
     *
     * Suspends until the send has been attempted, because the caller needs the outcome
     * to write into the session record — an alert that is not recorded is indistinguishable
     * afterwards from one that never fired.
     */
    suspend fun dispatch(
        tier: CodewordTier,
        userName: String,
        guardians: List<EmergencyContact>,
        entries: List<DiarizedEntry>,
        latitude: Double?,
        longitude: Double?,
        placeLabel: String?,
    ): Dispatch {
        // Safe is a stand-down, not an escalation: always send, never deduped, because
        // the people who were alerted are owed the all-clear.
        if (tier != CodewordTier.Safe && shouldSuppress(tier)) {
            Log.i(TAG, "Suppressing repeat $tier (already sent $highestSent)")
            return Dispatch(tier, "", NotifyOutcome(emptyList(), emptyList()), suppressed = true)
        }

        val alert = AlertComposer.compose(
            tier = tier,
            userName = userName,
            latitude = latitude,
            longitude = longitude,
            placeLabel = placeLabel,
            entries = entries,
        )

        val recipients = recipientsFor(tier, guardians)
        val outcome = notifier.notify(recipients, alert.body)

        if (tier != CodewordTier.Safe && outcome.anyReached) {
            highestSent = tier
        }
        Log.i(TAG, "dispatch $tier -> ${outcome.reached.size} reached, ${outcome.failed.size} failed")
        return Dispatch(tier, alert.body, outcome)
    }

    /**
     * Who hears about this tier.
     *
     * Caution means "I have started recording, nothing is wrong yet", so it goes to the
     * first guardian only — the one the user ranked top. Anything above that goes to
     * everyone, because at that point the cost of over-contacting is far below the cost
     * of the one person who could have helped not knowing.
     */
    private fun recipientsFor(
        tier: CodewordTier,
        guardians: List<EmergencyContact>,
    ): List<EmergencyContact> {
        val ranked = guardians.sortedBy { it.priority }
        return when (tier) {
            CodewordTier.Caution -> ranked.take(1)
            else -> ranked
        }
    }

    private fun shouldSuppress(tier: CodewordTier): Boolean {
        val sent = highestSent ?: return false
        return tier.ordinal <= sent.ordinal
    }

    /** Called when a session ends, so the next one starts fresh. */
    fun reset() {
        highestSent = null
    }
}
