package com.example.guardianangel.data.local

import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.domain.model.SafeLocation
import com.example.guardianangel.domain.model.SafeLocationKind
import com.example.guardianangel.domain.repository.SafeLocationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Room implementation of [SafeLocationRepository].
 *
 * Persists Home and additional safe locations locally. Coordinates are never uploaded
 * by default, ensuring user privacy and safety.
 */
class RoomSafeLocationRepository(
    private val dao: SafePlaceDao,
    private val currentUser: CurrentUser,
) : SafeLocationRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeLocations(): Flow<List<SafeLocation>> =
        currentUser.observeId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                dao.observeAll(userId).map { entities ->
                    entities.map { it.toModel() }
                }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeHome(): Flow<SafeLocation?> =
        currentUser.observeId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(null)
            } else {
                dao.observeAll(userId).map { entities ->
                    entities.firstOrNull { it.kind.equals("home", ignoreCase = true) }?.toModel()
                }
            }
        }

    override suspend fun getHome(): SafeLocation? {
        val userId = currentUser.requireId()
        val all = dao.observeAll(userId).first()
        return all.firstOrNull { it.kind.equals("home", ignoreCase = true) }?.toModel()
    }

    override suspend fun setHome(
        name: String,
        point: GeoPoint,
        address: String,
        radiusMeters: Float,
    ): SafeLocation {
        val userId = currentUser.requireId()
        val homeId = "safe-$userId-home"
        val entity = SafePlaceEntity(
            id = homeId,
            userId = userId,
            name = name.ifBlank { "Home" },
            latitude = point.latitude,
            longitude = point.longitude,
            radiusMeters = radiusMeters,
            kind = "home",
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsert(entity)
        return entity.toModel().copy(address = address)
    }

    override suspend fun addSafeLocation(location: SafeLocation) {
        val userId = currentUser.requireId()
        val entity = SafePlaceEntity(
            id = location.id.ifBlank { "safe-$userId-${UUID.randomUUID()}" },
            userId = userId,
            name = location.name,
            latitude = location.point.latitude,
            longitude = location.point.longitude,
            radiusMeters = location.radiusMeters,
            kind = when (location.kind) {
                SafeLocationKind.Home -> "home"
                SafeLocationKind.SafeHaven -> "haven"
                SafeLocationKind.Custom -> "custom"
            },
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsert(entity)
    }

    override suspend fun updateSafeLocation(location: SafeLocation) {
        val userId = currentUser.requireId()
        val entity = SafePlaceEntity(
            id = location.id,
            userId = userId,
            name = location.name,
            latitude = location.point.latitude,
            longitude = location.point.longitude,
            radiusMeters = location.radiusMeters,
            kind = when (location.kind) {
                SafeLocationKind.Home -> "home"
                SafeLocationKind.SafeHaven -> "haven"
                SafeLocationKind.Custom -> "custom"
            },
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsert(entity)
    }

    override suspend fun removeSafeLocation(id: String) {
        dao.delete(id)
    }
}

private fun SafePlaceEntity.toModel(): SafeLocation =
    SafeLocation(
        id = id,
        name = name,
        point = GeoPoint(latitude, longitude),
        radiusMeters = radiusMeters,
        kind = when (kind.lowercase()) {
            "home" -> SafeLocationKind.Home
            "haven" -> SafeLocationKind.SafeHaven
            else -> SafeLocationKind.Custom
        },
        updatedAtMillis = updatedAt,
    )
