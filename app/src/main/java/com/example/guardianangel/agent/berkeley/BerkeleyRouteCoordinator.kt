package com.example.guardianangel.agent.berkeley

import com.example.guardianangel.data.berkeley.BerkeleySafetyDataSource
import com.example.guardianangel.data.routes.DeterministicRouteRanker
import com.example.guardianangel.data.routes.RawRouteCandidate
import com.example.guardianangel.data.routes.RouteProvider
import com.example.guardianangel.data.weather.WeatherCondition
import com.example.guardianangel.data.weather.WeatherProvider
import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.domain.model.RoutePreference
import com.example.guardianangel.domain.model.SafeRoute
import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * ADK explanation agent that produces calm, non-alarmist route rationale from structured evidence.
 *
 * Invariant: Never invents incidents, never modifies route scores, never claims any route
 * is "guaranteed safe", and does not run on the audio hot path.
 */
class RouteExplanationAgent : BaseAgent(
    name = "RouteExplanationAgent",
    description = "Generates calm, structured natural-language explanations of route safety trade-offs",
) {
    private var lastExplanation: String = "Corridor evaluated."

    fun explain(recommended: SafeRoute, weather: WeatherCondition?): String {
        val parts = mutableListOf<String>()

        parts.add("Angel recommends the ${recommended.label} (${recommended.durationMinutes} min, ${recommended.distanceMiles} mi).")

        if (recommended.safeHavenCount > 0) {
            parts.add("This corridor connects ${recommended.safeHavenCount} verified safe havens.")
        }
        if (recommended.illuminationPercent >= 85) {
            parts.add("It offers well-lit street illumination (${recommended.illuminationPercent}%).")
        }
        if (recommended.warnings.isNotEmpty()) {
            parts.add(recommended.warnings.first().label + ".")
        }
        if (weather != null && weather.walkabilityRisk > 0.15f) {
            parts.add("Weather note: ${weather.conditionDescription}.")
        }

        val text = parts.joinToString(" ")
        lastExplanation = text
        return text
    }

    override fun runAsyncImpl(context: InvocationContext): Flow<Event> = flow {
        emit(
            Event(
                invocationId = context.invocationId,
                author = name,
                content = Content(
                    role = Role.MODEL,
                    parts = listOf(Part(text = lastExplanation))
                ),
                output = lastExplanation,
            )
        )
    }
}

/**
 * Orchestrates route planning across route candidates, safety data, weather,
 * deterministic ranking, and ADK explanation.
 */
class BerkeleyRouteCoordinator(
    private val routeProvider: RouteProvider,
    private val safetyDataSource: BerkeleySafetyDataSource,
    private val weatherProvider: WeatherProvider,
    private val ranker: DeterministicRouteRanker,
    private val explanationAgent: RouteExplanationAgent = RouteExplanationAgent(),
) {

    suspend fun planAndRankRoutes(
        origin: GeoPoint,
        destination: GeoPoint,
        preference: RoutePreference = RoutePreference.Safest,
    ): Pair<List<SafeRoute>, String> {
        // 1. Fetch walking route candidates
        val rawCandidates = routeProvider.getWalkingRoutes(origin, destination)
        if (rawCandidates.isEmpty()) {
            return emptyList<SafeRoute>() to "No walking routes available for this destination."
        }

        // 2. Fetch environmental weather conditions
        val weather = weatherProvider.getConditions(origin)

        // 3. Deterministic safety ranking
        val rankedSafeRoutes = ranker.rankRoutes(
            candidates = rawCandidates,
            weather = weather,
            preference = preference,
        )

        // 4. Generate structured explanation for the recommended route
        val recommended = rankedSafeRoutes.firstOrNull { it.isRecommended } ?: rankedSafeRoutes.first()
        val rationale = explanationAgent.explain(recommended, weather)

        return rankedSafeRoutes to rationale
    }
}
