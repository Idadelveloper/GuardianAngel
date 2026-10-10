package com.example.guardianangel.ui.trail

import com.example.guardianangel.domain.model.SessionTrail
import com.example.guardianangel.domain.model.TrailPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How far the walk went, in words.
 *
 * Imperial, matching the rest of the app's US copy. Short walks are reported in feet
 * because "0.1 mi" is not a useful thing to read about a two-minute recording.
 */
internal fun SessionTrail.distanceLabel(): String {
    val metres = points.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) }
    val feet = metres * 3.28084
    return when {
        feet < 1_000 -> "${feet.toInt()} ft"
        else -> "%.1f mi".format(feet / 5_280)
    }
}

/**
 * Great-circle distance in metres.
 *
 * Haversine rather than a flat approximation: it costs nothing here and does not need a
 * caveat about how far apart the points may be.
 */
internal fun TrailPoint.distanceTo(other: TrailPoint): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(other.latitude - latitude)
    val dLon = Math.toRadians(other.longitude - longitude)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) *
        sin(dLon / 2).pow(2)
    return 2 * earthRadius * asin(min(1.0, sqrt(a)))
}
