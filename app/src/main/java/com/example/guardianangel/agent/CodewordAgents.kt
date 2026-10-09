package com.example.guardianangel.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.repository.GuardianRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Base Google ADK Agent for actions tied to specific codeword tiers.
 */
abstract class CodewordActionAgent(
    name: String,
    description: String,
) : BaseAgent(name = name, description = description) {
    abstract val tier: CodewordTier
    abstract suspend fun executeAction(guardianRepository: GuardianRepository, reason: String)

    override fun runAsyncImpl(context: InvocationContext): Flow<Event> = flow {
        emit(
            Event(
                invocationId = context.invocationId,
                author = name,
                content = Content(
                    role = Role.MODEL,
                    parts = listOf(Part(text = "Codeword Action Agent: $name ready for tier $tier"))
                ),
                output = tier,
            )
        )
    }
}

/**
 * Handles the "Safe" codeword: resets alarms, stops alert dispatch, logs resolution.
 */
class SafeActionAgent : CodewordActionAgent(
    name = "SafeActionAgent",
    description = "Resets alarms, stops alert dispatch, logs situation resolution",
) {
    override val tier: CodewordTier = CodewordTier.Safe

    override suspend fun executeAction(guardianRepository: GuardianRepository, reason: String) {
        guardianRepository.stopRecording()
    }
}

/**
 * Handles the "Caution" codeword: quietly initiates elevated monitoring and evidence capture.
 */
class CautionActionAgent : CodewordActionAgent(
    name = "CautionActionAgent",
    description = "Quietly initiates elevated surveillance and background forensic recording",
) {
    override val tier: CodewordTier = CodewordTier.Caution

    override suspend fun executeAction(guardianRepository: GuardianRepository, reason: String) {
        guardianRepository.startRecording(trigger = CodewordTier.Caution)
    }
}

/**
 * Handles the "Danger" codeword: notifies guardians with live GPS and audio evidence summary.
 */
class DangerActionAgent : CodewordActionAgent(
    name = "DangerActionAgent",
    description = "Notifies emergency guardian contacts with live GPS coordinates and evidence summary",
) {
    override val tier: CodewordTier = CodewordTier.Danger

    override suspend fun executeAction(guardianRepository: GuardianRepository, reason: String) {
        guardianRepository.dispatchAlert(CodewordTier.Danger)
    }
}

/**
 * Handles the "Emergency" codeword: dials 911 emergency services and alerts guardian circle.
 */
class EmergencyActionAgent : CodewordActionAgent(
    name = "EmergencyActionAgent",
    description = "Dials 911 emergency services and alerts full guardian circle with critical priority",
) {
    override val tier: CodewordTier = CodewordTier.Emergency

    override suspend fun executeAction(guardianRepository: GuardianRepository, reason: String) {
        guardianRepository.dispatchAlert(CodewordTier.Emergency)
    }
}
