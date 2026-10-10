package com.example.guardianangel.data.local

import android.util.Log
import com.example.guardianangel.domain.model.Destination
import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.domain.model.Trip
import com.example.guardianangel.domain.model.TripOutcome
import com.example.guardianangel.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

private const val TAG = "RoomTrips"

/** [TripRepository] in Room, so a journey survives the process. */
class RoomTripRepository(
    private val dao: TripDao,
    private val currentUser: CurrentUser,
) : TripRepository {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeActiveTrip(): Flow<Trip?> =
        currentUser.observeId().flatMapLatest { id ->
            if (id == null) flowOf(null) else dao.observeActive(id).map { it?.toModel() }
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeRecentTrips(limit: Int): Flow<List<Trip>> =
        currentUser.observeId().flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyList())
            } else {
                dao.observeRecent(id, limit).map { rows -> rows.map { it.toModel() } }
            }
        }

    override suspend fun start(
        destination: Destination,
        origin: GeoPoint?,
        routeId: String?,
        routeLabel: String?,
        notifyContactId: String?,
        notifyContactName: String?,
        notifyMessage: String?,
    ): Trip {
        val userId = currentUser.requireId()
        val now = System.currentTimeMillis()

        // Anything still open is from a process that died mid-walk. Closing it as Ended
        // rather than Arrived matters: the log must not claim she reached somewhere on
        // the strength of the app having crashed.
        dao.closeAllActive(userId, TripOutcome.Ended.name, now)

        val entity = TripEntity(
            id = "trip-${UUID.randomUUID()}",
            userId = userId,
            destinationName = destination.name,
            destinationAddress = destination.address,
            destinationLat = destination.point.latitude,
            destinationLng = destination.point.longitude,
            originLat = origin?.latitude,
            originLng = origin?.longitude,
            routeId = routeId,
            routeLabel = routeLabel,
            startedAt = now,
            outcome = TripOutcome.InProgress.name,
            notifyContactId = notifyContactId,
            notifyContactName = notifyContactName,
            notifyMessage = notifyMessage?.trim()?.takeIf { it.isNotEmpty() },
            updatedAt = now,
        )
        dao.upsert(entity)
        Log.i(TAG, "Trip ${entity.id} started to ${destination.name}")
        return entity.toModel()
    }

    override suspend fun complete(tripId: String, outcome: TripOutcome, distanceMeters: Int?) {
        runCatching {
            val existing = dao.find(tripId) ?: return
            // Already closed: a service teardown racing a user tap must not overwrite
            // an Arrived with an Ended.
            if (existing.outcome != TripOutcome.InProgress.name) return
            val now = System.currentTimeMillis()
            dao.upsert(
                existing.copy(
                    outcome = outcome.name,
                    endedAt = now,
                    distanceMeters = distanceMeters ?: existing.distanceMeters,
                    updatedAt = now,
                )
            )
            Log.i(TAG, "Trip $tripId -> $outcome")
        }.onFailure { Log.w(TAG, "Could not close trip $tripId", it) }
    }

    override suspend fun noteArrivalNotified(tripId: String, atMillis: Long?, error: String?) {
        runCatching {
            val existing = dao.find(tripId) ?: return
            dao.upsert(
                existing.copy(
                    arrivalNotifiedAt = atMillis,
                    arrivalNotifyError = error,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }.onFailure { Log.w(TAG, "Could not record the arrival text for $tripId", it) }
    }

    override suspend fun delete(tripId: String) {
        runCatching { dao.deleteById(tripId) }
            .onFailure { Log.w(TAG, "Could not delete trip $tripId", it) }
    }
}

private fun TripEntity.toModel() = Trip(
    id = id,
    destinationName = destinationName,
    destinationAddress = destinationAddress,
    destination = GeoPoint(destinationLat, destinationLng),
    routeId = routeId,
    routeLabel = routeLabel,
    startedAtEpochMillis = startedAt,
    endedAtEpochMillis = endedAt,
    outcome = runCatching { TripOutcome.valueOf(outcome) }.getOrDefault(TripOutcome.Ended),
    distanceMeters = distanceMeters,
    notifyContactId = notifyContactId,
    notifyContactName = notifyContactName,
    notifyMessage = notifyMessage,
    arrivalNotifiedAtEpochMillis = arrivalNotifiedAt,
    arrivalNotifyError = arrivalNotifyError,
)
