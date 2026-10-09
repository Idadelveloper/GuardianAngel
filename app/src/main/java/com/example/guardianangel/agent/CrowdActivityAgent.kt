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
 * Google ADK Kotlin Agent responsible for assessing foot-traffic density, open establishments, and isolation risks.
 */
class CrowdActivityAgent : BaseAgent(
    name = "CrowdActivityAgent",
    description = "Estimates pedestrian foot-traffic density, open establishments, and isolation risks",
) {
    private var lastScore: Int = 80
    private var lastDescription: String = "Moderate street activity"

    fun assess(hourOfDay: Int, nearbyOpenHavensCount: Int): Int {
        var score = when {
            nearbyOpenHavensCount >= 2 -> 92 // Open businesses/services nearby
            nearbyOpenHavensCount == 1 -> 85
            else -> 65 // Isolated corridor / no open businesses
        }

        // Adjust based on expected pedestrian presence by time of day
        if (hourOfDay in 1..5 && nearbyOpenHavensCount == 0) {
            score -= 20 // Deserted streets with no open refuges
        }

        val finalScore = score.coerceIn(10, 100)
        lastScore = finalScore
        lastDescription = describe(hourOfDay, nearbyOpenHavensCount)
        return finalScore
    }

    fun describe(hourOfDay: Int, nearbyOpenHavensCount: Int): String {
        return when {
            nearbyOpenHavensCount > 0 -> "$nearbyOpenHavensCount verified open locations within safe reach"
            hourOfDay in 0..5 -> "Isolated street with low pedestrian activity"
            else -> "Moderate street activity"
        }
    }

    override fun runAsyncImpl(context: InvocationContext): Flow<Event> = flow {
        emit(
            Event(
                invocationId = context.invocationId,
                author = name,
                content = Content(
                    role = Role.MODEL,
                    parts = listOf(Part(text = "CrowdActivityScore: $lastScore | Description: $lastDescription"))
                ),
                output = lastScore,
            )
        )
    }
}
