package com.example.guardianangel.domain.route

import com.example.guardianangel.domain.model.GeoPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turn-by-turn directions worked out from the shape of a route.
 *
 * ## Why these are derived rather than given
 *
 * The routes this app draws are scored corridors, not a Directions API response: they
 * carry a polyline and a safety assessment, and no step list or street names. Deriving
 * the turns from the geometry gives real, usable guidance — "in 80 m, turn left" is
 * most of what walking directions are — without a second network call on a screen whose
 * whole point is working when the network is poor.
 *
 * What it deliberately does **not** do is invent street names. "Turn left onto Dwight
 * Way" from geometry alone would be a guess, and a confidently wrong street name at
 * night is worse than no name at all.
 */
object WalkDirections {

    enum class Turn { Start, Straight, SlightLeft, Left, SharpLeft, SlightRight, Right, SharpRight, Arrive }

    data class Step(
        val turn: Turn,
        /** Index into the route path this step happens at. */
        val atIndex: Int,
        val point: GeoPoint,
        /** Walking distance from the previous step. */
        val metersFromPrevious: Int,
        val instruction: String,
    )

    data class Progress(
        /** The step being walked toward, or null once the route is finished. */
        val nextStep: Step?,
        val metersToNextStep: Int,
        val metersRemaining: Int,
        val minutesRemaining: Int,
        /** True when the user is further from the route than [OFF_ROUTE_METERS]. */
        val isOffRoute: Boolean,
    )

    /**
     * Every turn on [path], in order.
     *
     * Consecutive vertices that continue roughly straight are merged, because a polyline
     * traces the curve of a road with dozens of points and a step for each would read as
     * "continue straight" forty times.
     */
    fun stepsFor(path: List<GeoPoint>): List<Step> {
        if (path.size < 2) return emptyList()

        val steps = mutableListOf(
            Step(
                turn = Turn.Start,
                atIndex = 0,
                point = path.first(),
                metersFromPrevious = 0,
                instruction = "Start walking",
            )
        )

        var runStartIndex = 0
        for (i in 1 until path.lastIndex) {
            val incoming = bearing(path[i - 1], path[i])
            val outgoing = bearing(path[i], path[i + 1])
            val turn = classify(angleDifference(incoming, outgoing))
            if (turn == Turn.Straight) continue

            steps += Step(
                turn = turn,
                atIndex = i,
                point = path[i],
                metersFromPrevious = distanceAlong(path, runStartIndex, i).roundToInt(),
                instruction = instructionFor(turn),
            )
            runStartIndex = i
        }

        steps += Step(
            turn = Turn.Arrive,
            atIndex = path.lastIndex,
            point = path.last(),
            metersFromPrevious = distanceAlong(path, runStartIndex, path.lastIndex).roundToInt(),
            instruction = "Arrive at your destination",
        )
        return steps
    }

    /**
     * Where the user is on the route now.
     *
     * The nearest path vertex is used as the position rather than a projection onto the
     * nearest segment: at walking speed and GPS accuracy the difference is a couple of
     * metres, and the simpler rule cannot produce the pathological jumps a projection
     * does when a route doubles back on itself.
     */
    fun progress(
        path: List<GeoPoint>,
        steps: List<Step>,
        current: GeoPoint,
        walkingMetersPerMinute: Double = WALKING_METERS_PER_MINUTE,
    ): Progress {
        if (path.isEmpty() || steps.isEmpty()) {
            return Progress(null, 0, 0, 0, isOffRoute = false)
        }

        var nearestIndex = 0
        var nearestDistance = Double.MAX_VALUE
        path.forEachIndexed { index, point ->
            val d = distanceMeters(point, current)
            if (d < nearestDistance) {
                nearestDistance = d
                nearestIndex = index
            }
        }

        val next = steps.firstOrNull { it.atIndex > nearestIndex } ?: steps.last()
        val toNext = distanceAlong(path, nearestIndex, next.atIndex)
        val remaining = distanceAlong(path, nearestIndex, path.lastIndex)

        return Progress(
            nextStep = next.takeIf { nearestIndex < path.lastIndex },
            metersToNextStep = toNext.roundToInt(),
            metersRemaining = remaining.roundToInt(),
            minutesRemaining = (remaining / walkingMetersPerMinute).roundToInt().coerceAtLeast(0),
            // Measured to the nearest point on the nearest *segment*, not to the nearest
            // vertex. A corridor drawn with a vertex every 200 m would otherwise report
            // someone walking correctly down the middle of it as 100 m off route.
            isOffRoute = distanceToPath(path, current) > OFF_ROUTE_METERS,
        )
    }

    /** "80 m" or "1.2 km" — the unit people actually use at each scale. */
    fun formatDistance(meters: Int): String = when {
        meters < 20 -> "now"
        meters < 1000 -> "${(meters / 10) * 10} m"
        else -> "%.1f km".format(meters / 1000.0)
    }

    // -- Geometry ---------------------------------------------------------------------

    private fun instructionFor(turn: Turn): String = when (turn) {
        Turn.Start -> "Start walking"
        Turn.Straight -> "Continue straight"
        Turn.SlightLeft -> "Bear left"
        Turn.Left -> "Turn left"
        Turn.SharpLeft -> "Sharp left"
        Turn.SlightRight -> "Bear right"
        Turn.Right -> "Turn right"
        Turn.SharpRight -> "Sharp right"
        Turn.Arrive -> "Arrive at your destination"
    }

    private fun classify(deltaDegrees: Double): Turn {
        val magnitude = abs(deltaDegrees)
        val left = deltaDegrees < 0
        return when {
            magnitude < STRAIGHT_DEGREES -> Turn.Straight
            magnitude < TURN_DEGREES -> if (left) Turn.SlightLeft else Turn.SlightRight
            magnitude < SHARP_DEGREES -> if (left) Turn.Left else Turn.Right
            else -> if (left) Turn.SharpLeft else Turn.SharpRight
        }
    }

    /** Signed difference in degrees, normalised to −180..180. Negative is to the left. */
    internal fun angleDifference(from: Double, to: Double): Double {
        var delta = (to - from) % 360.0
        if (delta > 180) delta -= 360
        if (delta < -180) delta += 360
        return delta
    }

    internal fun bearing(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Haversine. Accurate well past the scale of any walk. */
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            sin(dLon / 2) * sin(dLon / 2) * cos(lat1) * cos(lat2)
        return 2 * EARTH_RADIUS_METERS * atan2(sqrt(h), sqrt(1 - h))
    }

    /**
     * How far [point] is from the route itself.
     *
     * Projects onto each segment in a local flat-earth frame: over the tens of metres
     * that matter here the curvature is far below GPS noise, and the alternative is
     * spherical cross-track maths that is harder to read for no gain in the answer.
     */
    fun distanceToPath(path: List<GeoPoint>, point: GeoPoint): Double {
        if (path.isEmpty()) return Double.MAX_VALUE
        if (path.size == 1) return distanceMeters(path.first(), point)

        val metersPerDegLat = 111_320.0
        val metersPerDegLon = 111_320.0 * cos(Math.toRadians(point.latitude))
        fun x(p: GeoPoint) = (p.longitude - point.longitude) * metersPerDegLon
        fun y(p: GeoPoint) = (p.latitude - point.latitude) * metersPerDegLat

        var best = Double.MAX_VALUE
        for (i in 0 until path.lastIndex) {
            val ax = x(path[i]); val ay = y(path[i])
            val bx = x(path[i + 1]); val by = y(path[i + 1])
            val dx = bx - ax; val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            // t is where the foot of the perpendicular falls, clamped to the segment so
            // a point beyond either end measures to that end rather than to an
            // imaginary extension of the road.
            val t = if (lengthSquared == 0.0) 0.0 else {
                (((0.0 - ax) * dx + (0.0 - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
            }
            val cx = ax + t * dx
            val cy = ay + t * dy
            best = minOf(best, sqrt(cx * cx + cy * cy))
        }
        return best
    }

    private fun distanceAlong(path: List<GeoPoint>, fromIndex: Int, toIndex: Int): Double {
        if (toIndex <= fromIndex) return 0.0
        var total = 0.0
        for (i in fromIndex until toIndex) total += distanceMeters(path[i], path[i + 1])
        return total
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** Below this a vertex is the road bending, not a turn worth announcing. */
    private const val STRAIGHT_DEGREES = 25.0
    private const val TURN_DEGREES = 55.0
    private const val SHARP_DEGREES = 120.0

    /** Brisk urban walking, the pace route times are quoted at. */
    const val WALKING_METERS_PER_MINUTE = 80.0

    /** Further than this from every point on the route counts as having left it. */
    const val OFF_ROUTE_METERS = 45.0
}
