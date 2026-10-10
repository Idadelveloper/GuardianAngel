package com.example.guardianangel.domain.repository

import com.example.guardianangel.domain.model.Destination
import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.domain.model.Trip
import com.example.guardianangel.domain.model.TripOutcome
import kotlinx.coroutines.flow.Flow

/**
 * Journeys, and whether she got there.
 *
 * The active trip is the single source of truth for "am I navigating" — not a flag in a
 * composable, not a field on a service. A screen reads it, a service writes to it, and
 * both survive each other being destroyed.
 */
interface TripRepository {

    /** The journey in progress, or null. At most one at a time. */
    fun observeActiveTrip(): Flow<Trip?>

    /** Newest first, for the journeys log. */
    fun observeRecentTrips(limit: Int = 50): Flow<List<Trip>>

    /**
     * Opens a trip, closing any that was left open.
     *
     * A stale in-progress row is the normal result of the process being killed mid-walk,
     * so starting a new journey quietly closes the old one as [TripOutcome.Ended] rather
     * than refusing or leaving two active.
     */
    suspend fun start(
        destination: Destination,
        origin: GeoPoint?,
        routeId: String?,
        routeLabel: String?,
        notifyContactId: String?,
        notifyContactName: String?,
        notifyMessage: String?,
    ): Trip

    /** Marks the active trip arrived or ended, with the distance actually covered. */
    suspend fun complete(tripId: String, outcome: TripOutcome, distanceMeters: Int?)

    /** Records what happened to the arrival text, success or failure. */
    suspend fun noteArrivalNotified(tripId: String, atMillis: Long?, error: String?)

    suspend fun delete(tripId: String)
}
