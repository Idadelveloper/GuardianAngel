package com.example.guardianangel.data

import com.example.guardianangel.domain.model.Destination
import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.domain.model.RoutePreference
import com.example.guardianangel.domain.model.RouteWarning
import com.example.guardianangel.domain.model.SafeRoute

/**
 * Sample destinations and corridors.
 *
 * `pathNormalised` carries each corridor as points in a 0..1 square so the map canvas can
 * draw it without a Maps SDK or an API key. When real mapping lands, the renderer swaps
 * for a map view and reads [SafeRoute.path] instead; nothing else has to change.
 */
object RouteSamples {

    val home = Destination(
        id = "home",
        name = "Home",
        address = "Your safe haven",
        point = GeoPoint(37.8715, -122.2730),
        isSafeHaven = true,
    )

    val quickDestinations = listOf(
        Destination("work", "Work office", "450 Kendall St", GeoPoint(37.8750, -122.2680), walkingMinutes = 18),
        Destination("library", "Campus library", "Doe Memorial", GeoPoint(37.8722, -122.2595), walkingMinutes = 12, isSafeHaven = true),
        Destination("market", "Trader Joe's", "Shattuck Ave", GeoPoint(37.8680, -122.2680), walkingMinutes = 9),
    )

    fun routesTo(destination: Destination): List<SafeRoute> = listOf(
        SafeRoute(
            id = "route-safest",
            label = "Angel reassurance corridor",
            preference = RoutePreference.Safest,
            safetyScore = 98,
            durationMinutes = (destination.walkingMinutes ?: 15) + 1,
            distanceMiles = 1.1,
            illuminationPercent = 95,
            safeHavenCount = 4,
            highlights = listOf("Continuous lighting", "4 safe havens"),
            pathNormalised = listOf(
                0.18f to 0.88f,
                0.24f to 0.70f,
                0.34f to 0.56f,
                0.46f to 0.44f,
                0.60f to 0.34f,
                0.74f to 0.22f,
                0.82f to 0.12f,
            ),
            isRecommended = true,
        ),
        SafeRoute(
            id = "route-fast",
            label = "Direct Main St",
            preference = RoutePreference.Fastest,
            safetyScore = 76,
            durationMinutes = (destination.walkingMinutes ?: 15) - 2,
            distanceMiles = 0.9,
            illuminationPercent = 52,
            safeHavenCount = 1,
            highlights = listOf("3 min faster"),
            warnings = listOf(RouteWarning("Dimly lit park shortcut", atFraction = 0.55f)),
            pathNormalised = listOf(
                0.18f to 0.88f,
                0.32f to 0.74f,
                0.48f to 0.58f,
                0.62f to 0.40f,
                0.74f to 0.24f,
                0.82f to 0.12f,
            ),
        ),
    )
}
