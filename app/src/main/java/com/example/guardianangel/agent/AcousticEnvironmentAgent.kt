package com.example.guardianangel.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.example.guardianangel.audio.AudioEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Google ADK Kotlin Agent responsible for classifying surrounding acoustic events and sound signatures.
 */
class AcousticEnvironmentAgent : BaseAgent(
    name = "AcousticEnvironmentAgent",
    description = "Tags acoustic surroundings for gunshots, screams, shattering glass, or alarms",
) {

    data class AcousticResult(
        val score: Int,
        val detectedDangerEvents: List<AudioEvent>,
        val soundDescription: String,
        val isAcousticDanger: Boolean,
    )

    private var lastResult = AcousticResult(
        score = 95,
        detectedDangerEvents = emptyList(),
        soundDescription = "Normal ambient noise",
        isAcousticDanger = false,
    )

    fun assess(recentEvents: List<AudioEvent>): AcousticResult {
        val dangerEvents = recentEvents.filter { it.isDangerSignal }
        var score = 95
        var danger = false

        dangerEvents.forEach { event ->
            // Confidence weighted penalty
            val penalty = (35 * event.confidence).toInt().coerceAtLeast(15)
            score -= penalty
            if (event.confidence >= 0.4f) {
                danger = true
            }
        }

        val description = if (dangerEvents.isNotEmpty()) {
            val top = dangerEvents.maxByOrNull { it.confidence }
            "${top?.label ?: "Distress sound"} detected (${((top?.confidence ?: 0f) * 100).toInt()}% conf)"
        } else if (recentEvents.isNotEmpty()) {
            "Ambient: ${recentEvents.last().label}"
        } else {
            "Normal ambient noise"
        }

        val result = AcousticResult(
            score = score.coerceIn(5, 100),
            detectedDangerEvents = dangerEvents,
            soundDescription = description,
            isAcousticDanger = danger,
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
                    parts = listOf(Part(text = "AcousticScore: ${lastResult.score} | Description: ${lastResult.soundDescription}"))
                ),
                output = lastResult,
            )
        )
    }
}
