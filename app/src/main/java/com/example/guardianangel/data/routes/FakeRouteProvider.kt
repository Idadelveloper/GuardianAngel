package com.example.guardianangel.data.routes

import com.example.guardianangel.data.berkeley.BerkeleySafetyDataSource
import com.example.guardianangel.domain.model.GeoPoint
import kotlin.math.roundToInt

/**
 * Hermetic route provider generating realistic walking route alternatives.
 *
 * Used for previews, unit tests, and offline fallback when Google Routes API
 * or network access is unconfigured.
 */
class FakeRouteProvider : RouteProvider {

    override suspend fun getWalkingRoutes(
        origin: GeoPoint,
        destination: GeoPoint,
    ): List<RawRouteCandidate> {
        val directDistanceMeters = BerkeleySafetyDataSource.distanceMeters(origin, destination)
        val walkingSpeedMps = 1.33 // approx 3.0 mph walking pace
        val baseDurationMinutes = ((directDistanceMeters / walkingSpeedMps) / 60.0).roundToInt().coerceAtLeast(3)

        // Route 1: Well-lit Main Avenue corridor (slight westward bend toward Shattuck/Oxford)
        val route1Points = interpolateCurvedPath(origin, destination, bendOffset = -0.0018)
        val route1Dist = (directDistanceMeters * 1.10).roundToInt()
        val route1Mins = baseDurationMinutes + 2

        // Route 2: Direct Street corridor (fastest)
        val route2Points = interpolateCurvedPath(origin, destination, bendOffset = 0.0)
        val route2Dist = (directDistanceMeters * 1.0).roundToInt()
        val route2Mins = baseDurationMinutes

        // Route 3: Residential / Eastern corridor
        val route3Points = interpolateCurvedPath(origin, destination, bendOffset = 0.0022)
        val route3Dist = (directDistanceMeters * 1.18).roundToInt()
        val route3Mins = baseDurationMinutes + 3

        return listOf(
            RawRouteCandidate(
                routeId = "candidate-safest",
                label = "Well-lit Transit Corridor",
                durationMinutes = route1Mins,
                distanceMeters = route1Dist,
                polylinePoints = route1Points,
            ),
            RawRouteCandidate(
                routeId = "candidate-fastest",
                label = "Direct Street",
                durationMinutes = route2Mins,
                distanceMeters = route2Dist,
                polylinePoints = route2Points,
            ),
            RawRouteCandidate(
                routeId = "candidate-alternative",
                label = "Side Street Option",
                durationMinutes = route3Mins,
                distanceMeters = route3Dist,
                polylinePoints = route3Points,
            ),
        )
    }

    private fun interpolateCurvedPath(
        start: GeoPoint,
        end: GeoPoint,
        bendOffset: Double,
        steps: Int = 10,
    ): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        for (i in 0..steps) {
            val t = i.toDouble() / steps.toDouble()
            // Parabolic perpendicular deflection
            val arc = 4.0 * t * (1.0 - t)
            val lat = start.latitude + t * (end.latitude - start.latitude) + arc * (bendOffset * 0.4)
            val lon = start.longitude + t * (end.longitude - start.longitude) + arc * bendOffset
            points.add(GeoPoint(lat, lon))
        }
        return points
    }
}
