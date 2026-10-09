package com.example.guardianangel.domain.model

/**
 * Destinations and safest-route planning.
 *
 * A future `places` and `routes` schema maps onto these directly. Coordinates are plain
 * doubles rather than a Maps SDK type so the domain layer stays independent of whichever
 * mapping provider the app ends up using.
 */

/** A point on the earth. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** A saved or suggested destination. */
data class Destination(
    val id: String,
    val name: String,
    val address: String,
    val point: GeoPoint,
    /** Typical walking time in minutes, when known. */
    val walkingMinutes: Int? = null,
    val isSafeHaven: Boolean = false,
)

/** How a route was optimised. */
enum class RoutePreference {
    /** Highest safety score, even if slower. */
    Safest,

    /** Shortest travel time. */
    Fastest,

    /** Only corridors above the lighting threshold. */
    WellLitOnly,
}

/** A stretch of a route the user should know about. */
data class RouteWarning(
    val label: String,
    /** Roughly where along the route it occurs, 0f..1f. */
    val atFraction: Float,
)

/**
 * One candidate route.
 *
 * @param safetyScore 0–100, computed from the same inputs as the live location score.
 * @param pathNormalised the corridor as points in a 0..1 square, so the renderer can draw
 *   it without knowing the map projection. Real coordinates live in [path].
 */
data class SafeRoute(
    val id: String,
    val label: String,
    val preference: RoutePreference,
    val safetyScore: Int,
    val durationMinutes: Int,
    val distanceMiles: Double,
    val illuminationPercent: Int,
    val safeHavenCount: Int,
    val highlights: List<String> = emptyList(),
    val warnings: List<RouteWarning> = emptyList(),
    val path: List<GeoPoint> = emptyList(),
    val pathNormalised: List<Pair<Float, Float>> = emptyList(),
    /** True for the corridor Angel recommends. */
    val isRecommended: Boolean = false,
)

/** The map tab's state. */
data class RoutePlan(
    val origin: Destination,
    val destination: Destination?,
    val preference: RoutePreference,
    val routes: List<SafeRoute>,
    val isNightPatrolActive: Boolean,
    val areaIlluminationPercent: Int,
) {
    val recommended: SafeRoute? get() = routes.firstOrNull { it.isRecommended } ?: routes.firstOrNull()
    val alternative: SafeRoute? get() = routes.firstOrNull { !it.isRecommended }
}
