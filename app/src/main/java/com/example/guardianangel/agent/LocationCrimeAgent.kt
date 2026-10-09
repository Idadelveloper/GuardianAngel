package com.example.guardianangel.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.example.guardianangel.data.crime.CrimeDataService
import com.example.guardianangel.domain.model.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Google ADK Kotlin Agent responsible for evaluating geospatial threat levels using US crime data.
 */
class LocationCrimeAgent(
    private val crimeDataService: CrimeDataService,
) : BaseAgent(
    name = "LocationCrimeAgent",
    description = "Evaluates geospatial US crime indices and proximity to safe havens or hazards",
) {
    private var lastScore: Int = 85
    private var lastContext: String = "Location signal pending"

    fun assess(location: GeoPoint?, hourOfDay: Int): Int {
        val score = if (location == null) 85 else crimeDataService.getCrimeSafetyScoreForLocation(location, hourOfDay)
        lastScore = score
        return score
    }

    fun describeContext(location: GeoPoint?, hourOfDay: Int): String {
        val desc = if (location == null) "Location signal pending"
        else {
            val havens = crimeDataService.getNearbySafeHavens(location)
            val hazards = crimeDataService.getNearbyHazards(location)
            when {
                hazards.any { crimeDataService.distanceMeters(location, it.location) < 300 } ->
                    "Near identified high-incident zone: ${hazards.first().label}"
                havens.isNotEmpty() ->
                    "Safe haven nearby: ${havens.first().name}"
                else -> "Standard residential zone"
            }
        }
        lastContext = desc
        return desc
    }

    override fun runAsyncImpl(context: InvocationContext): Flow<Event> = flow {
        emit(
            Event(
                invocationId = context.invocationId,
                author = name,
                content = Content(
                    role = Role.MODEL,
                    parts = listOf(Part(text = "CrimeSafetyScore: $lastScore | Context: $lastContext"))
                ),
                output = lastScore,
            )
        )
    }
}
