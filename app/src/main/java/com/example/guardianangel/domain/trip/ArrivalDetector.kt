package com.example.guardianangel.domain.trip

import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.domain.route.WalkDirections

/**
 * Decides when someone has actually arrived.
 *
 * This is the trigger for a text message to another person, so both mistakes are
 * expensive in opposite directions. Firing early — as she passes the end of the street
 * her destination is on — tells a guardian she is safe while she still has two minutes
 * to walk. Never firing leaves the guardian with an unanswered "are you there?" and the
 * trip stuck open in her history.
 *
 * So arrival needs two things, not one: **close enough**, and **still there**. A
 * dwell requirement is what separates arriving from walking past, and it costs only the
 * twenty seconds it takes to put a key in a door.
 *
 * Pure and time-injected so every edge can be tested without walking anywhere.
 */
class ArrivalDetector(
    private val destination: GeoPoint,
    /**
     * How close counts as "there".
     *
     * Forty metres: a building's footprint plus ordinary GPS error in a street with
     * tall buildings. Tighter and a fix that lands on the wrong side of the road never
     * arrives at all.
     */
    private val radiusMeters: Double = DEFAULT_RADIUS_METERS,
    /** How long she has to stay inside the radius before it counts. */
    private val dwellMillis: Long = DEFAULT_DWELL_MILLIS,
) {

    sealed interface State {
        /** Still on the way. */
        data class Approaching(val metersAway: Int) : State

        /** Inside the radius, waiting out the dwell. */
        data class Settling(val millisRemaining: Long) : State

        /** Arrived, at this moment. Emitted once. */
        data class Arrived(val atMillis: Long) : State
    }

    private var insideSince: Long? = null
    private var announced = false

    /**
     * Feeds one location fix in.
     *
     * @param accuracyMeters the fix's own error estimate, when the platform supplies
     *   one. A wildly imprecise fix is ignored rather than trusted: a 300 m cell-tower
     *   fix that happens to land on the destination is not evidence of anything, and
     *   acting on it would text a guardian from the wrong side of a neighbourhood.
     */
    fun onLocation(
        point: GeoPoint,
        atMillis: Long,
        accuracyMeters: Float? = null,
    ): State {
        if (announced) return State.Arrived(atMillis)

        val distance = WalkDirections.distanceMeters(point, destination)

        if (accuracyMeters != null && accuracyMeters > MAX_TRUSTED_ACCURACY_METERS) {
            // Deliberately does not clear `insideSince`: a single bad fix in the middle
            // of a good run should not restart the dwell she has already served.
            return State.Approaching(distance.toInt())
        }

        if (distance > radiusMeters) {
            insideSince = null
            return State.Approaching(distance.toInt())
        }

        val since = insideSince ?: atMillis.also { insideSince = it }
        val elapsed = atMillis - since
        return if (elapsed >= dwellMillis) {
            announced = true
            State.Arrived(atMillis)
        } else {
            State.Settling(dwellMillis - elapsed)
        }
    }

    companion object {
        const val DEFAULT_RADIUS_METERS = 40.0
        const val DEFAULT_DWELL_MILLIS = 20_000L

        /**
         * Worse than this and the fix says nothing useful about a 40 m radius.
         *
         * Matches the threshold `AngelWhereAmI` uses to admit a vague position, so the
         * app does not call a fix trustworthy in one place and doubtful in another.
         */
        const val MAX_TRUSTED_ACCURACY_METERS = 60f
    }
}
