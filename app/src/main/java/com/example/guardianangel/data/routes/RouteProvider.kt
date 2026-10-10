package com.example.guardianangel.data.routes

import com.example.guardianangel.domain.model.GeoPoint

/**
 * Standard Google Encoded Polyline algorithm decoder.
 *
 * Converts compressed polylines from the Routes API into [GeoPoint] list.
 */
object PolylineDecoder {
    fun decode(encoded: String): List<GeoPoint> {
        val poly = mutableListOf<GeoPoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            val pLat = lat.toDouble() / 1E5
            val pLng = lng.toDouble() / 1E5
            poly.add(GeoPoint(pLat, pLng))
        }
        return poly
    }
}

/**
 * A raw walking route candidate returned by a routing engine before safety scoring.
 */
data class RawRouteCandidate(
    val routeId: String,
    val label: String,
    val durationMinutes: Int,
    val distanceMeters: Int,
    val polylinePoints: List<GeoPoint>,
    val steps: List<String> = emptyList(),
)

/**
 * Provider interface for retrieving walking route candidates.
 */
interface RouteProvider {
    suspend fun getWalkingRoutes(
        origin: GeoPoint,
        destination: GeoPoint,
    ): List<RawRouteCandidate>
}
