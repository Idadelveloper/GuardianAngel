package com.example.guardianangel.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Google ADK Kotlin Agent responsible for evaluating speaker voice identity, tone, and vocal stress indicators.
 */
class VoiceToneAgent : BaseAgent(
    name = "VoiceToneAgent",
    description = "Evaluates vocal stress, pitch elevation, speaker count, and enrolled voice matching",
) {

    data class VoiceToneResult(
        val score: Int,
        val isVerifiedUser: Boolean,
        val hasUnknownVoice: Boolean,
        val isDistressDetected: Boolean,
        val toneDescription: String,
    )

    private var lastResult = VoiceToneResult(
        score = 95,
        isVerifiedUser = true,
        hasUnknownVoice = false,
        isDistressDetected = false,
        toneDescription = "Calm, steady speaking tone",
    )

    fun assess(
        isVerifiedUser: Boolean,
        hasUnknownVoice: Boolean,
        speakerCount: Int,
        peakDecibels: Int?,
    ): VoiceToneResult {
        var score = 95
        var distressDetected = false
        val db = peakDecibels ?: 55

        val tone = when {
            db >= 82 -> {
                score -= 30
                distressDetected = true
                if (isVerifiedUser) "User voice elevated / high distress volume ($db dB)"
                else "Aggressive / raised shouting detected ($db dB)"
            }
            db >= 75 -> {
                score -= 15
                if (hasUnknownVoice) "Elevated verbal exchange ($db dB)"
                else "Elevated speaking volume ($db dB)"
            }
            hasUnknownVoice -> {
                score -= 10
                "Unfamiliar voice present · conversational tone ($db dB)"
            }
            else -> {
                "Calm, steady speaking tone ($db dB)"
            }
        }

        if (speakerCount >= 3) {
            score -= 10
        }

        val result = VoiceToneResult(
            score = score.coerceIn(10, 100),
            isVerifiedUser = isVerifiedUser,
            hasUnknownVoice = hasUnknownVoice,
            isDistressDetected = distressDetected,
            toneDescription = tone,
        )
        lastResult = result
        return result
    }

    override fun runAsyncImpl(context: InvocationContext): Flow<Event> = flow {
        emit(
            Event(
                invocationId = context.invocationId,
                author = name,
                content = Content(
                    role = Role.MODEL,
                    parts = listOf(Part(text = "VoiceToneScore: ${lastResult.score} | Description: ${lastResult.toneDescription}"))
                ),
                output = lastResult,
            )
        )
    }
}
