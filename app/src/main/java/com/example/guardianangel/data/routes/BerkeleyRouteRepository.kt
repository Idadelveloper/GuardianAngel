package com.example.guardianangel.data.routes

import com.example.guardianangel.agent.berkeley.BerkeleyRouteCoordinator
import com.example.guardianangel.data.berkeley.BerkeleySafetyDataSource
import com.example.guardianangel.domain.model.Destination
import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.domain.model.RoutePlan
import com.example.guardianangel.domain.model.RoutePreference
import com.example.guardianangel.domain.model.SafeLocation
import com.example.guardianangel.domain.model.SafeRoute
import com.example.guardianangel.domain.repository.RouteRepository
import com.example.guardianangel.domain.repository.SafeLocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Concrete [RouteRepository] powered by real Berkeley public safety data and route providers.
 *
 * Invariants:
 * 1. Current user location is used as origin when available.
 * 2. Walking routes are deterministically scored using Berkeley data and weather.
 * 3. Never fabricates routes or synthetic crime points.
 */
class BerkeleyRouteRepository(
    private val coordinator: BerkeleyRouteCoordinator,
    private val safetyDataSource: BerkeleySafetyDataSource,
    private val safeLocationRepository: SafeLocationRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : RouteRepository {

    private val defaultOrigin = Destination(
        id = "current-loc",
        name = "Current Location",
        address = "Downtown Berkeley, CA",
        point = GeoPoint(37.8715, -122.2730),
    )

    private val quickDestinationsList = listOf(
        Destination("pl-doe-library", "Doe Memorial Library", "UC Berkeley Campus", GeoPoint(37.8722, -122.2595), walkingMinutes = 10, isSafeHaven = true),
        Destination("pl-bpd", "Berkeley Police Headquarters", "2100 Martin Luther King Jr Way", GeoPoint(37.8696, -122.2736), walkingMinutes = 8, isSafeHaven = true),
        Destination("pl-sproul", "Sproul Plaza", "Bancroft Way & Telegraph Ave", GeoPoint(37.8698, -122.2588), walkingMinutes = 11, isSafeHaven = true),
        Destination("pl-alta-bates", "Alta Bates Emergency Dept", "2450 Ashby Ave", GeoPoint(37.8564, -122.2575), walkingMinutes = 18, isSafeHaven = true),
        Destination("pl-trader-joes", "Trader Joe's", "1885 University Ave", GeoPoint(37.8715, -122.2738), walkingMinutes = 6),
        Destination("pl-bart-downtown", "Downtown Berkeley BART", "2160 Shattuck Ave", GeoPoint(37.8701, -122.2682), walkingMinutes = 5),
    )

    private val _routePlan = MutableStateFlow(
        RoutePlan(
            origin = defaultOrigin,
            destination = null,
            preference = RoutePreference.Safest,
            routes = emptyList(),
            isNightPatrolActive = true,
            areaIlluminationPercent = 92,
        )
    )

    private val _quickDestinations = MutableStateFlow(quickDestinationsList)

    init {
        // Observe Home changes to update quick destinations and origin if needed
        scope.launch {
            safeLocationRepository.observeHome().collect { homeLoc ->
                if (homeLoc != null) {
                    val homeDest = Destination(
                        id = homeLoc.id,
                        name = homeLoc.name,
                        address = homeLoc.address.ifBlank { "Designated Sanctuary Base" },
                        point = homeLoc.point,
                        walkingMinutes = 12,
                        isSafeHaven = true,
                    )
                    _quickDestinations.update { list ->
                        listOf(homeDest) + list.filterNot { it.id == homeLoc.id }
                    }
                }
            }
        }
    }

    override fun observeRoutePlan(): Flow<RoutePlan> = _routePlan.asStateFlow()

    override fun observeQuickDestinations(): Flow<List<Destination>> = _quickDestinations.asStateFlow()

    override suspend fun updateOrigin(originPoint: GeoPoint) {
        val updatedOrigin = _routePlan.value.origin.copy(point = originPoint)
        _routePlan.update { it.copy(origin = updatedOrigin) }

        val currentDest = _routePlan.value.destination
        if (currentDest != null) {
            recalculateRoutes(originPoint, currentDest)
        }
    }

    override suspend fun selectDestination(destinationId: String) {
        val target = _quickDestinations.value.firstOrNull { it.id == destinationId } ?: return
        selectCustomDestination(target)
    }

    override suspend fun selectCustomDestination(destination: Destination) {
        val originPoint = _routePlan.value.origin.point
        _routePlan.update { it.copy(destination = destination) }
        recalculateRoutes(originPoint, destination)
    }

    override suspend fun clearDestination() {
        _routePlan.update { it.copy(destination = null, routes = emptyList()) }
    }

    override suspend fun setPreference(preference: RoutePreference) {
        val currentPlan = _routePlan.value
        val originPoint = currentPlan.origin.point
        val currentDest = currentPlan.destination

        _routePlan.update { it.copy(preference = preference) }

        if (currentDest != null) {
            val (routes, _) = coordinator.planAndRankRoutes(
                origin = originPoint,
                destination = currentDest.point,
                preference = preference,
            )
            _routePlan.update { it.copy(routes = routes) }
        }
    }

    private suspend fun recalculateRoutes(origin: GeoPoint, destination: Destination) {
        val preference = _routePlan.value.preference
        val (routes, _) = coordinator.planAndRankRoutes(
            origin = origin,
            destination = destination.point,
            preference = preference,
        )
        val avgIllum = if (routes.isNotEmpty()) {
            routes.map { it.illuminationPercent }.average().toInt()
        } else 92

        _routePlan.update {
            it.copy(
                routes = routes,
                areaIlluminationPercent = avgIllum,
            )
        }
    }
}
