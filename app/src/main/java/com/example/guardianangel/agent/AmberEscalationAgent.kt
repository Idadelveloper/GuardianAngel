package com.example.guardianangel.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.example.guardianangel.audio.AudioEvent
import com.example.guardianangel.domain.model.CodewordTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Google ADK Kotlin Agent responsible for triggering a critical Amber/Emergency alarm on the device
 * when threat levels reach extreme danger ("get the hell out" alert).
 */
class AmberEscalationAgent : BaseAgent(
    name = "AmberEscalationAgent",
    description = "Evaluates extreme threat corroboration to trigger Amber Emergency escalation alarms",
) {

    data class AmberStatus(
        val isTriggered: Boolean,
        val rationale: String?,
    )

    private var lastStatus = AmberStatus(isTriggered = false, rationale = null)

    fun evaluate(
        compositeScore: Int,
        matchedCodeword: CodewordTier?,
        dangerEvents: List<AudioEvent>,
        isVerbalDistress: Boolean,
    ): AmberStatus {
        val hasSevereAcoustic = dangerEvents.any { it.isDangerSignal && it.confidence >= 0.70f }

        val status = when {
            matchedCodeword == CodewordTier.Emergency -> AmberStatus(
                isTriggered = true,
                rationale = "Emergency codeword uttered"
            )
            compositeScore <= 28 -> AmberStatus(
                isTriggered = true,
                rationale = "Composite safety score dropped to critical $compositeScore%"
            )
            hasSevereAcoustic && isVerbalDistress -> AmberStatus(
                isTriggered = true,
                rationale = "Scream/distress sound corroborated with verbal distress"
            )
            else -> AmberStatus(
                isTriggered = false,
                rationale = null
            )
        }
        lastStatus = status
        return status
    }

    override fun runAsyncImpl(context: InvocationContext): Flow<Event> = flow {
        emit(
            Event(
                invocationId = context.invocationId,
                author = name,
                content = Content(
                    role = Role.MODEL,
                    parts = listOf(Part(text = "AmberStatus: triggered=${lastStatus.isTriggered} rationale=${lastStatus.rationale}"))
                ),
                output = lastStatus,
            )
        )
    }
}
