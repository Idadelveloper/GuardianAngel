package com.example.guardianangel.agent

import android.util.Log
import com.example.guardianangel.domain.repository.ActivityRepository
import com.example.guardianangel.domain.session.SessionNarrator
import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

private const val TAG = "SummaryAgent"

/**
 * Reads a finished recording and writes down what it was.
 *
 * ## Why this is its own agent, and runs afterwards
 *
 * The other agents work in the moment and have to be fast: the notification agent is on
 * the path between a codeword and a text message, and anything slow there is time
 * somebody spends waiting. Summarising is the opposite kind of job — it needs the
 * *whole* session, which by definition does not exist until the recording stops, and
 * nobody is waiting on the answer. Running it as a separate pass after `finish()` means
 * a slow or failed summary can never delay or break a live alert.
 *
 * It owns the session's **title and summary**, which until now were placeholders the
 * recorder generated from a duration. The key moments it derives are not stored: what
 * counts as notable changes as the heuristics improve, and a walk recorded last month
 * should benefit from today's understanding of it — the same reason trail incidents are
 * derived on read.
 *
 * ## Why it is not a language model
 *
 * An LLM would write better prose. It would also need either a gigabyte of RAM and
 * several seconds on-device, or a network round trip that sends a transcript of someone's
 * worst night to a server. Neither is worth better adjectives on a card. `SessionNarrator`
 * is deterministic, testable, and provably cannot invent an event that is not in the
 * evidence — which matters when this text ends up in an export.
 */
class SessionSummaryAgent(
    private val activity: ActivityRepository,
) : BaseAgent(
    name = "SessionSummaryAgent",
    description = "Derives a title, summary and key moments for a finished recording",
) {

    /**
     * Summarises [sessionId] and saves the result.
     *
     * Never throws. It runs on the teardown path of a recording, where an exception
     * would take down the service that just finished holding the microphone.
     *
     * @return the narrative written, or null if there was nothing to write.
     */
    suspend fun summarise(sessionId: String): SessionNarrator.Narrative? = try {
        val session = activity.observeSession(sessionId).first()
        if (session == null) {
            Log.w(TAG, "No session $sessionId to summarise")
            null
        } else {
            val narrative = SessionNarrator.narrate(
                startedAtMillis = session.startedAtEpochMillis,
                endedAtMillis = session.endedAtEpochMillis,
                entries = session.entries,
                locationLabel = session.locationLabel,
                triggeredTierName = session.triggeredByTier?.name,
                guardiansNotified = session.guardiansNotified,
                lowestSafetyScore = session.lowestSafetyScore,
            )
            activity.updateNarrative(
                sessionId = sessionId,
                title = narrative.title,
                summary = narrative.summary,
            )
            Log.i(TAG, "Summarised $sessionId as \"${narrative.title}\"")
            narrative
        }
    } catch (e: Exception) {
        Log.e(TAG, "Could not summarise $sessionId", e)
        null
    }

    override fun runAsyncImpl(context: InvocationContext): Flow<Event> = flow {
        emit(
            Event(
                invocationId = context.invocationId,
                author = name,
                content = Content(
                    role = Role.MODEL,
                    parts = listOf(Part(text = "Summary agent ready")),
                ),
            )
        )
    }
}
