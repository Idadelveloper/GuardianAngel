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
 * Google ADK Kotlin Agent responsible for assessing temporal, darkness, and weather risks.
 */
class WeatherTimeAgent : BaseAgent(
    name = "WeatherTimeAgent",
    description = "Evaluates temporal circadian rhythms, ambient darkness, and weather risks",
) {
    private var lastScore: Int = 85
    private var lastDescription: String = "Daylight"

    fun assess(hourOfDay: Int, isAdverseWeather: Boolean = false): Int {
        var score = when (hourOfDay) {
            in 0..4 -> 55   // Deep night (highest historical crime window)
            in 5..6 -> 75   // Early dawn
            in 7..18 -> 98  // Broad daylight
            in 19..21 -> 85 // Dusk / evening
            in 22..23 -> 68 // Late night
            else -> 80
        }

        if (isAdverseWeather) {
            score -= 12 // Reduced visibility, deserted streets
        }

        val finalScore = score.coerceIn(10, 100)
        lastScore = finalScore
        lastDescription = describe(hourOfDay)
        return finalScore
    }

    fun describe(hourOfDay: Int): String = when (hourOfDay) {
        in 0..4 -> "Late night hours (high vigilance recommended)"
        in 22..23 -> "Nighttime corridor"
        in 7..18 -> "Daylight hours (optimal visibility)"
        else -> "Evening hours"
    }

    override fun runAsyncImpl(context: InvocationContext): Flow<Event> = flow {
        emit(
            Event(
                invocationId = context.invocationId,
                author = name,
                content = Content(
                    role = Role.MODEL,
                    parts = listOf(Part(text = "WeatherTimeScore: $lastScore | Description: $lastDescription"))
                ),
                output = lastScore,
            )
        )
    }
}
