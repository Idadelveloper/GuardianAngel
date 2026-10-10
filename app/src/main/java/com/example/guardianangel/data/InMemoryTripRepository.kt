package com.example.guardianangel.data

import com.example.guardianangel.domain.model.Destination
import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.domain.model.Trip
import com.example.guardianangel.domain.model.TripOutcome
import com.example.guardianangel.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Journeys held in memory, for previews and tests.
 *
 * Starts empty rather than with a sample walk in progress: a preview that shows live
 * navigation nobody started is the kind of fake that makes a screen look finished.
 */
class InMemoryTripRepository : TripRepository {

    private val trips = MutableStateFlow<List<Trip>>(emptyList())

    override fun observeActiveTrip(): Flow<Trip?> =
        trips.map { list -> list.firstOrNull { it.isActive } }

    override fun observeRecentTrips(limit: Int): Flow<List<Trip>> =
        trips.map { list -> list.sortedByDescending { it.startedAtEpochMillis }.take(limit) }

    override suspend fun start(
        destination: Destination,
        origin: GeoPoint?,
        routeId: String?,
        routeLabel: String?,
        notifyContactId: String?,
        notifyContactName: String?,
        notifyMessage: String?,
    ): Trip {
        val now = System.currentTimeMillis()
        val trip = Trip(
            id = "trip-$now",
            destinationName = destination.name,
            destinationAddress = destination.address,
            destination = destination.point,
            routeId = routeId,
            routeLabel = routeLabel,
            startedAtEpochMillis = now,
            notifyContactId = notifyContactId,
            notifyContactName = notifyContactName,
            notifyMessage = notifyMessage,
        )
        trips.update { list ->
            list.map { if (it.isActive) it.copy(outcome = TripOutcome.Ended, endedAtEpochMillis = now) else it } + trip
        }
        return trip
    }

    override suspend fun complete(tripId: String, outcome: TripOutcome, distanceMeters: Int?) {
        trips.update { list ->
            list.map {
                if (it.id == tripId && it.isActive) {
                    it.copy(
                        outcome = outcome,
                        endedAtEpochMillis = System.currentTimeMillis(),
                        distanceMeters = distanceMeters ?: it.distanceMeters,
                    )
                } else {
                    it
                }
            }
        }
    }

    override suspend fun noteArrivalNotified(tripId: String, atMillis: Long?, error: String?) {
        trips.update { list ->
            list.map {
                if (it.id == tripId) {
                    it.copy(arrivalNotifiedAtEpochMillis = atMillis, arrivalNotifyError = error)
                } else {
                    it
                }
            }
        }
    }

    override suspend fun delete(tripId: String) {
        trips.update { list -> list.filterNot { it.id == tripId } }
    }
}
