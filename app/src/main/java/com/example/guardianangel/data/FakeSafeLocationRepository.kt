package com.example.guardianangel.data

import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.domain.model.SafeLocation
import com.example.guardianangel.domain.model.SafeLocationKind
import com.example.guardianangel.domain.repository.SafeLocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * In-memory implementation of [SafeLocationRepository] for previews and unit tests.
 */
class FakeSafeLocationRepository(
    initialHome: SafeLocation? = SafeLocation(
        id = "fake-home",
        name = "Home",
        point = GeoPoint(37.8715, -122.2730),
        radiusMeters = 80f,
        kind = SafeLocationKind.Home,
        address = "Downtown Berkeley Sanctuary",
    ),
    initialLocations: List<SafeLocation> = emptyList(),
) : SafeLocationRepository {

    private val locationsState = MutableStateFlow(
        listOfNotNull(initialHome) + initialLocations
    )

    override fun observeLocations(): Flow<List<SafeLocation>> = locationsState.asStateFlow()

    override fun observeHome(): Flow<SafeLocation?> =
        locationsState.map { list -> list.firstOrNull { it.isHome } }

    override suspend fun getHome(): SafeLocation? =
        locationsState.value.firstOrNull { it.isHome }

    override suspend fun setHome(
        name: String,
        point: GeoPoint,
        address: String,
        radiusMeters: Float,
    ): SafeLocation {
        val newHome = SafeLocation(
            id = "home-${UUID.randomUUID()}",
            name = name.ifBlank { "Home" },
            point = point,
            radiusMeters = radiusMeters,
            kind = SafeLocationKind.Home,
            address = address,
            updatedAtMillis = System.currentTimeMillis(),
        )
        locationsState.update { list ->
            listOf(newHome) + list.filterNot { it.isHome }
        }
        return newHome
    }

    override suspend fun addSafeLocation(location: SafeLocation) {
        locationsState.update { list ->
            list.filterNot { it.id == location.id } + location
        }
    }

    override suspend fun updateSafeLocation(location: SafeLocation) {
        locationsState.update { list ->
            list.map { if (it.id == location.id) location else it }
        }
    }

    override suspend fun removeSafeLocation(id: String) {
        locationsState.update { list ->
            list.filterNot { it.id == id }
        }
    }
}
