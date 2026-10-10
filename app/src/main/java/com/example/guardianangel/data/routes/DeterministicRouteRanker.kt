package com.example.guardianangel.data.routes

import com.example.guardianangel.data.berkeley.BerkeleySafetyDataSource
import com.example.guardianangel.data.weather.WeatherCondition
import com.example.guardianangel.domain.model.DataConfidence
import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.domain.model.RiskBand
import com.example.guardianangel.domain.model.RouteAssessment
import com.example.guardianangel.domain.model.RoutePreference
import com.example.guardianangel.domain.model.RouteWarning
import com.example.guardianangel.domain.model.SafeRoute
import com.example.guardianangel.domain.model.SegmentAssessment
import java.util.Calendar
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deterministic route safety evaluation and ranking engine.
 *
 * Invariant: Independent of ADK, Android UI, and Compose.
 * Never claims any route is "guaranteed safe". Uses calibrated terminology
 * such as "lower modeled exposure", "higher reported exposure", or "limited safety data".
 */
class DeterministicRouteRanker(
    private val safetyDataSource: BerkeleySafetyDataSource,
    private val maxDetourMinutesAllowed: Int = 8,
) {

    fun rankRoutes(
        candidates: List<RawRouteCandidate>,
        weather: WeatherCondition?,
        preference: RoutePreference = RoutePreference.Safest,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): List<SafeRoute> {
        if (candidates.isEmpty()) return emptyList()

        val calendar = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
        val timeOfDayFactor = safetyDataSource.calculateTimeOfDayFactor(hourOfDay)
        val fastestMinutes = candidates.minOf { it.durationMinutes }

        val evaluatedRoutes = candidates.map { candidate ->
            evaluateCandidate(
                candidate = candidate,
                weather = weather,
                timeOfDayFactor = timeOfDayFactor,
                fastestMinutes = fastestMinutes,
                currentTimeMillis = currentTimeMillis,
            )
        }

        // Determine recommendation based on active preference
        val recommendedId = when (preference) {
            RoutePreference.Safest -> {
                // Safest within detour allowance
                val allowed = evaluatedRoutes.filter { it.assessment.detourFromFastestMinutes <= maxDetourMinutesAllowed }
                val pick = (allowed.ifEmpty { evaluatedRoutes }).maxByOrNull { it.assessment.safetyScore }
                pick?.safeRoute?.id ?: evaluatedRoutes.first().safeRoute.id
            }
            RoutePreference.Fastest -> {
                evaluatedRoutes.minByOrNull { it.safeRoute.durationMinutes }?.safeRoute?.id
                    ?: evaluatedRoutes.first().safeRoute.id
            }
            RoutePreference.WellLitOnly -> {
                evaluatedRoutes.maxByOrNull { it.assessment.lightingCoveragePercent }?.safeRoute?.id
                    ?: evaluatedRoutes.first().safeRoute.id
            }
        }

        return evaluatedRoutes.map { item ->
            val isRec = item.safeRoute.id == recommendedId
            item.safeRoute.copy(
                isRecommended = isRec,
                preference = if (isRec) preference else RoutePreference.Fastest,
            )
        }
    }

    private data class EvaluatedCandidate(
        val safeRoute: SafeRoute,
        val assessment: RouteAssessment,
    )

    private fun evaluateCandidate(
        candidate: RawRouteCandidate,
        weather: WeatherCondition?,
        timeOfDayFactor: Float,
        fastestMinutes: Int,
        currentTimeMillis: Long,
    ): EvaluatedCandidate {
        val points = candidate.polylinePoints
        val segments = mutableListOf<SegmentAssessment>()
        var totalSegmentCrimeRisk = 0f
        var totalLighting = 0
        val encounteredHavenIds = mutableSetOf<String>()
        val warnings = mutableListOf<String>()
        val highlights = mutableListOf<String>()
        var pointsInBerkeley = 0

        val step = (points.size / 6).coerceAtLeast(1)
        var i = 0
        while (i < points.size - 1) {
            val start = points[i]
            val end = points[min(i + step, points.size - 1)]
            val mid = GeoPoint((start.latitude + end.latitude) / 2.0, (start.longitude + end.longitude) / 2.0)

            if (safetyDataSource.isWithinBerkeleyCoverage(mid)) {
                pointsInBerkeley++
            }

            // Match crime cells within 220 meters of segment midpoint
            val nearbyCells = safetyDataSource.getNearbyCrimeCells(mid, maxDistanceMeters = 220.0)
            var segmentCrimeIntensity = 0f

            for (cell in nearbyCells) {
                val recency = safetyDataSource.calculateRecencyFactor(cell.lastReportedTimeMillis, currentTimeMillis)
                val baseOffenseWeight = cell.primaryOffense.baseWeight
                val cellRisk = (cell.weightedScore * baseOffenseWeight * recency * timeOfDayFactor) / 10f
                segmentCrimeIntensity += cellRisk
            }
            val segmentRisk = segmentCrimeIntensity.coerceIn(0.0f, 1.0f)
            totalSegmentCrimeRisk += segmentRisk

            // Match safe havens within 250 meters
            val nearbyHavens = safetyDataSource.getNearbySafeHavens(mid, maxDistanceMeters = 250.0)
            encounteredHavenIds.addAll(nearbyHavens.map { it.id })

            // Estimate corridor lighting (major corridors in Berkeley have high illumination)
            val segmentLighting = when {
                mid.longitude in -122.271..-122.266 -> 94 // Shattuck Ave corridor
                mid.latitude in 37.868..37.873 && mid.longitude in -122.262..-122.257 -> 88 // Campus edge
                else -> 72 // Residential side streets
            }
            totalLighting += segmentLighting

            val isSegWarning = segmentRisk > 0.40f
            val warningMsg = if (isSegWarning) "Higher reported exposure along this stretch" else null
            if (isSegWarning && warningMsg != null && !warnings.contains(warningMsg)) {
                warnings.add(warningMsg)
            }

            segments.add(
                SegmentAssessment(
                    start = start,
                    end = end,
                    segmentRisk = segmentRisk,
                    lightingPercent = segmentLighting,
                    nearbyHavensCount = nearbyHavens.size,
                    reportedExposure = if (segmentRisk < 0.25f) "Lower exposure" else "Elevated exposure",
                    isWarning = isSegWarning,
                    warningMessage = warningMsg,
                )
            )

            i += step
        }

        val segmentCount = segments.size.coerceAtLeast(1)
        val avgCrimeExposure = (totalSegmentCrimeRisk / segmentCount).coerceIn(0f, 1f)
        val avgLighting = (totalLighting / segmentCount).coerceIn(30, 98)
        val safeHavenCount = encounteredHavenIds.size
        val detourMinutes = (candidate.durationMinutes - fastestMinutes).coerceAtLeast(0)

        // Weather assessment
        val weatherRisk = weather?.walkabilityRisk ?: 0.05f
        if (weather != null && weather.isSevereAlert) {
            warnings.add("Severe weather: ${weather.conditionDescription}")
        } else if (weather != null && weather.walkabilityRisk > 0.20f) {
            warnings.add("Weather alert: ${weather.conditionDescription}")
        }

        // Confidence determination
        val berkeleyCoverageFraction = pointsInBerkeley.toFloat() / segmentCount.toFloat()
        val confidence = when {
            berkeleyCoverageFraction >= 0.70f && weather != null -> DataConfidence.High
            berkeleyCoverageFraction >= 0.40f -> DataConfidence.Moderate
            else -> DataConfidence.Limited
        }

        if (confidence == DataConfidence.Limited) {
            warnings.add("Limited safety data: route extends beyond verified Berkeley model")
        }

        // Deterministic safety score computation
        val crimePenalty = avgCrimeExposure * 38f
        val lightingBonus = (avgLighting / 100f) * 14f
        val havenBonus = min(safeHavenCount * 3.5f, 14f)
        val weatherPenalty = weatherRisk * 15f
        val confidencePenalty = if (confidence == DataConfidence.Limited) 18f else 0f

        val rawScore = (72f - crimePenalty + lightingBonus + havenBonus - weatherPenalty - confidencePenalty)
            .roundToInt()
            .coerceIn(20, 98)

        val riskBand = when {
            rawScore >= 88 -> RiskBand.Minimal
            rawScore >= 74 -> RiskBand.Low
            rawScore >= 58 -> RiskBand.Moderate
            rawScore >= 42 -> RiskBand.Elevated
            else -> RiskBand.Severe
        }

        if (avgLighting >= 85) highlights.add("Well-lit street lighting ($avgLighting%)")
        if (safeHavenCount > 0) highlights.add("$safeHavenCount verified safe havens along route")
        if (avgCrimeExposure < 0.20f) highlights.add("Lower modeled reported exposure")

        val assessment = RouteAssessment(
            routeId = candidate.routeId,
            providerRouteLabel = candidate.label,
            durationMinutes = candidate.durationMinutes,
            distanceMeters = candidate.distanceMeters,
            safetyScore = rawScore,
            riskBand = riskBand,
            confidence = confidence,
            dataFreshnessDays = 2,
            crimeExposureScore = avgCrimeExposure,
            lightingCoveragePercent = avgLighting,
            weatherRiskScore = weatherRisk,
            safeHavenCount = safeHavenCount,
            detourFromFastestMinutes = detourMinutes,
            highlights = highlights,
            warnings = warnings,
            segmentAssessments = segments,
            sourceVersion = "BPD-UCPD-2026.1",
        )

        val routeWarnings = warnings.mapIndexed { idx, w ->
            RouteWarning(label = w, atFraction = (idx + 1).toFloat() / (warnings.size + 1))
        }

        val distanceMiles = (candidate.distanceMeters / 1609.34 * 10.0).roundToInt() / 10.0

        val safeRoute = SafeRoute(
            id = candidate.routeId,
            label = candidate.label,
            preference = RoutePreference.Safest,
            safetyScore = rawScore,
            durationMinutes = candidate.durationMinutes,
            distanceMiles = distanceMiles,
            illuminationPercent = avgLighting,
            safeHavenCount = safeHavenCount,
            highlights = highlights,
            warnings = routeWarnings,
            path = candidate.polylinePoints,
            isRecommended = false,
            assessment = assessment,
        )

        return EvaluatedCandidate(safeRoute, assessment)
    }
}
