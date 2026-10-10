package com.example.guardianangel.ui.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.guardianangel.data.berkeley.BerkeleySafetyDataSource
import com.example.guardianangel.data.places.PlacePrediction
import com.example.guardianangel.data.places.PlacesSearchProvider
import com.example.guardianangel.data.platform.LocationTracker
import com.example.guardianangel.data.weather.WeatherProvider
import com.example.guardianangel.domain.GuardianSafetyStateResolver
import com.example.guardianangel.domain.model.CrimeCell
import com.example.guardianangel.domain.model.DataConfidence
import com.example.guardianangel.domain.model.Destination
import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.domain.model.RoutePlan
import com.example.guardianangel.domain.model.RoutePreference
import com.example.guardianangel.domain.model.SafeHavenPoi
import com.example.guardianangel.domain.model.SafeLocation
import com.example.guardianangel.domain.model.SafeRoute
import com.example.guardianangel.domain.model.UserLocationSnapshot
import com.example.guardianangel.domain.repository.GuardianRepository
import com.example.guardianangel.domain.repository.PermissionProbe
import com.example.guardianangel.domain.repository.RouteRepository
import com.example.guardianangel.domain.repository.SafeLocationRepository
import com.example.guardianangel.ui.components.BottomBarClearance
import com.example.guardianangel.ui.components.GuardianPrimaryButton
import com.example.guardianangel.ui.home.components.TonalPill
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.map.components.AngelLocationCallout
import com.example.guardianangel.ui.map.components.AngelStatusBubble
import com.example.guardianangel.ui.map.components.HomeConfigDialog
import com.example.guardianangel.ui.map.components.MapLegendAndFiltersDialog
import com.example.guardianangel.ui.map.components.MarkerDetailSheet
import com.example.guardianangel.ui.map.components.WalkingDirectionsCard
import com.example.guardianangel.ui.map.components.MarkerFilterState
import com.example.guardianangel.ui.map.components.SelectedMapFeature
import com.example.guardianangel.ui.mascot.AngelMood
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.SafetyLevel
import com.example.guardianangel.ui.theme.safetyColorsFor
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tab 2 — Map.
 *
 * Berkeley-first prototype integrating Google Maps, live GPS movement tracking,
 * Angel mascot user marker, floating status bubble, Home sanctuary management,
 * privacy-preserving crime clusters, verified safe havens, and deterministic route ranking.
 */
@Composable
fun MapRoute(
    routeRepository: RouteRepository,
    guardianRepository: GuardianRepository,
    crimeDataService: com.example.guardianangel.data.crime.CrimeDataService? = null,
    locationTracker: LocationTracker? = null,
    permissionProbe: PermissionProbe? = null,
    safeLocationRepository: SafeLocationRepository? = null,
    safetyDataSource: BerkeleySafetyDataSource = BerkeleySafetyDataSource(),
    placesSearchProvider: PlacesSearchProvider? = null,
    weatherProvider: WeatherProvider? = null,
    onJourneyStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val plan by routeRepository.observeRoutePlan()
        .collectAsStateWithLifecycle(initialValue = emptyPlan())
    val quickDestinations by routeRepository.observeQuickDestinations()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val homeLocation by (safeLocationRepository?.observeHome() ?: flowOf(null))
        .collectAsStateWithLifecycle(initialValue = null)
    val safeLocations by (safeLocationRepository?.observeLocations() ?: flowOf(emptyList()))
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var hasLocationPermission by remember {
        mutableStateOf(locationTracker?.hasLocationPermission() ?: false)
    }

    var userLocationSnapshot by remember { mutableStateOf<UserLocationSnapshot?>(null) }
    val currentLocation = userLocationSnapshot?.point

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (granted && locationTracker != null) {
            scope.launch {
                val loc = locationTracker.getCurrentLocation()
                if (loc != null) {
                    userLocationSnapshot = UserLocationSnapshot(point = loc)
                    routeRepository.updateOrigin(loc)
                }
            }
        }
    }

    // Refresh permissions on resume
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, locationTracker, permissionProbe) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = permissionProbe?.hasLocation()
                    ?: locationTracker?.hasLocationPermission()
                    ?: false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    // Stream live location updates
    LaunchedEffect(hasLocationPermission, locationTracker) {
        if (hasLocationPermission && locationTracker != null) {
            locationTracker.observeUserLocation().collect { snapshot ->
                userLocationSnapshot = snapshot
                routeRepository.updateOrigin(snapshot.point)
            }
        }
    }

    val safeHavens = remember(currentLocation) {
        if (currentLocation != null) {
            safetyDataSource.getNearbySafeHavens(currentLocation, maxDistanceMeters = 3000.0)
        } else {
            safetyDataSource.getAllSafeHavens()
        }
    }

    val crimeCells = remember(currentLocation) {
        if (currentLocation != null) {
            safetyDataSource.getNearbyCrimeCells(currentLocation, maxDistanceMeters = 3000.0)
        } else {
            safetyDataSource.getAllCrimeCells()
        }
    }

    // Derive central safety state
    val resolvedState = remember(currentLocation, homeLocation, safeLocations, plan) {
        GuardianSafetyStateResolver.resolve(
            currentLocation = currentLocation,
            home = homeLocation,
            safeLocations = safeLocations,
            isArmed = true,
            isRecording = false,
            inDuress = false,
            activeNavigationDestination = plan.destination,
            environmentalScore = plan.recommended?.safetyScore,
            dataConfidence = plan.recommended?.assessment?.confidence ?: DataConfidence.High,
        )
    }

    MapScreen(
        plan = plan,
        quickDestinations = quickDestinations,
        homeLocation = homeLocation,
        safeLocations = safeLocations,
        currentLocation = currentLocation,
        userLocationSnapshot = userLocationSnapshot,
        hasLocationPermission = hasLocationPermission,
        safeHavens = safeHavens,
        crimeCells = crimeCells,
        resolvedState = resolvedState,
        placesSearchProvider = placesSearchProvider,
        onRequestLocation = {
            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        },
        onSelectDestination = { destId ->
            scope.launch { routeRepository.selectDestination(destId) }
        },
        onSelectCustomDestination = { dest ->
            scope.launch { routeRepository.selectCustomDestination(dest) }
        },
        onClearDestination = { scope.launch { routeRepository.clearDestination() } },
        onPreferenceChange = { scope.launch { routeRepository.setPreference(it) } },
        onSaveHome = { name, point, address, radius ->
            scope.launch {
                safeLocationRepository?.setHome(name, point, address, radius)
            }
        },
        onRemoveHome = {
            scope.launch {
                homeLocation?.id?.let { safeLocationRepository?.removeSafeLocation(it) }
            }
        },
        onNavigateHome = {
            if (homeLocation != null) {
                scope.launch {
                    val homeDest = Destination(
                        id = homeLocation!!.id,
                        name = homeLocation!!.name,
                        address = homeLocation!!.address.ifBlank { "Designated Sanctuary Base" },
                        point = homeLocation!!.point,
                        walkingMinutes = 12,
                        isSafeHaven = true,
                    )
                    routeRepository.selectCustomDestination(homeDest)
                }
            }
        },
        onWalk = {
            scope.launch {
                guardianRepository.armGuardian()
                onJourneyStarted()
            }
        },
        modifier = modifier,
    )
}

private fun emptyPlan() = RoutePlan(
    origin = Destination("default-origin", "Current Location", "Downtown Berkeley", GeoPoint(37.8715, -122.2730)),
    destination = null,
    preference = RoutePreference.Safest,
    routes = emptyList(),
    isNightPatrolActive = true,
    areaIlluminationPercent = 92,
)

@Composable
fun MapScreen(
    plan: RoutePlan,
    quickDestinations: List<Destination>,
    homeLocation: SafeLocation?,
    safeLocations: List<SafeLocation>,
    currentLocation: GeoPoint?,
    userLocationSnapshot: UserLocationSnapshot?,
    hasLocationPermission: Boolean,
    safeHavens: List<SafeHavenPoi>,
    crimeCells: List<CrimeCell>,
    resolvedState: com.example.guardianangel.domain.ResolvedSafetyState,
    placesSearchProvider: PlacesSearchProvider?,
    onRequestLocation: () -> Unit,
    onSelectDestination: (String) -> Unit,
    onSelectCustomDestination: (Destination) -> Unit,
    onClearDestination: () -> Unit,
    onPreferenceChange: (RoutePreference) -> Unit,
    onSaveHome: (String, GeoPoint, String, Float) -> Unit,
    onRemoveHome: () -> Unit,
    onNavigateHome: () -> Unit,
    onWalk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val planning = plan.destination != null
    var searchQuery by remember { mutableStateOf("") }
    var searchPredictions by remember { mutableStateOf<List<PlacePrediction>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    var followMode by remember { mutableStateOf(true) }
    var showLegendDialog by remember { mutableStateOf(false) }
    var showHomeDialog by remember { mutableStateOf(false) }
    var selectedFeature by remember { mutableStateOf<SelectedMapFeature?>(null) }
    var filterState by remember { mutableStateOf(MarkerFilterState()) }
    var statusBubbleDismissed by remember { mutableStateOf(false) }

    // What Angel said when she was last tapped, and a counter that re-runs her pulse. The
    // counter is separate because tapping her twice in the same place must animate twice,
    // and an identical callout would not re-trigger a keyed effect.
    var angelCallout by remember { mutableStateOf<AngelWhereAmI.Callout?>(null) }
    var angelPulse by remember { mutableStateOf(0) }

    // The route being walked, once she taps "Walk with me". Null the rest of the time.
    // Holding the id rather than the route itself means a recalculated path with the
    // same id keeps the walk going instead of silently ending it.
    var walkingRouteId by remember { mutableStateOf<String?>(null) }
    val walkingRoute = plan.routes.firstOrNull { it.id == walkingRouteId }

    val scope = rememberCoroutineScope()

    // Default map center: user position or downtown Berkeley
    val defaultCenter = currentLocation ?: GeoPoint(37.8715, -122.2730)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(defaultCenter.latitude, defaultCenter.longitude),
            14.6f
        )
    }

    // Camera follow mode: smoothly center camera when followMode is active
    LaunchedEffect(currentLocation, followMode) {
        if (followMode && currentLocation != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLng(LatLng(currentLocation.latitude, currentLocation.longitude))
            )
        }
    }

    // Detect user manual camera movement to pause followMode
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.isMoving }
            .collect { isMoving ->
                if (isMoving && cameraPositionState.cameraMoveStartedReason == com.google.maps.android.compose.CameraMoveStartedReason.GESTURE) {
                    followMode = false
                }
            }
    }

    // Debounced destination search
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank() && placesSearchProvider != null) {
            isSearching = true
            searchPredictions = placesSearchProvider.searchPredictions(searchQuery)
        } else {
            searchPredictions = emptyList()
            isSearching = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GuardianTheme.colors.canvas),
    ) {
        // --- 1. Real Google Map View -----------------------------------------------
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false, // We use Angel mascot instead
                mapToolbarEnabled = false,
                compassEnabled = true,
            ),
            properties = MapProperties(
                mapType = MapType.NORMAL,
                isMyLocationEnabled = false,
            ),
            // Tapping the map itself puts the sheet and the callout away, the way every
            // map app behaves. Without it the only exit was the close button.
            onMapClick = {
                selectedFeature = null
                angelCallout = null
            },
            onMapLongClick = { latLng ->
                // Long press opens Home configuration for tapped coordinate
                showHomeDialog = true
            },
        ) {
            // Live Angel Mascot Marker — one state for the life of the screen, so she
            // walks with the user instead of reappearing at each new fix.
            if (currentLocation != null) {
                AngelLocationMarker(
                    location = currentLocation,
                    mood = resolvedState.mood,
                    snippet = resolvedState.heroHeadline,
                    onTap = {
                        angelCallout = AngelWhereAmI.describe(
                            locationState = resolvedState.locationState,
                            accuracyMeters = userLocationSnapshot?.accuracyMeters,
                        )
                        angelPulse += 1
                    },
                    pulseKey = angelPulse,
                )
            }

            // Home Base Marker
            if (homeLocation != null) {
                Marker(
                    state = rememberMarkerState(homeLocation.point.latitude, homeLocation.point.longitude),
                    icon = GuardianMapMarkers.pin(GuardianMapMarkers.Kind.Home),
                    anchor = PIN_ANCHOR,
                    title = homeLocation.name,
                    snippet = "Your home — score stays at 100% inside",
                    onClick = {
                        selectedFeature = SelectedMapFeature.HomeFeature(homeLocation)
                        true
                    },
                )
            }

            // Additional Safe Locations
            safeLocations.filterNot { it.isHome }.forEach { safeLoc ->
                Marker(
                    state = rememberMarkerState(safeLoc.point.latitude, safeLoc.point.longitude),
                    icon = GuardianMapMarkers.pin(GuardianMapMarkers.Kind.SafePlace),
                    anchor = PIN_ANCHOR,
                    title = safeLoc.name,
                    snippet = "A place you marked safe (${safeLoc.radiusMeters.toInt()} m)",
                    onClick = {
                        selectedFeature = SelectedMapFeature.HomeFeature(safeLoc)
                        true
                    },
                )
            }

            // Safe Haven Markers (Police, Hospitals, Fire, SafeStops)
            if (filterState.showSafeHavens) {
                safeHavens.forEach { haven ->
                    Marker(
                        state = rememberMarkerState(haven.location.latitude, haven.location.longitude),
                        icon = GuardianMapMarkers.pin(GuardianMapMarkers.Kind.SafeHaven),
                        anchor = PIN_ANCHOR,
                        title = haven.name,
                        snippet = "${haven.openHoursDescription} · ${haven.address}",
                        onClick = {
                            selectedFeature = SelectedMapFeature.Haven(haven)
                            true
                        },
                    )
                }
            }

            // Privacy-preserving Crime Cluster Markers (150m grid cells)
            if (filterState.showCrimeClusters) {
                crimeCells.forEach { cell ->
                    Marker(
                        state = rememberMarkerState(cell.center.latitude, cell.center.longitude),
                        // Sized and badged by how much was reported here: a cluster of
                        // forty is a different place from a cluster of three, and
                        // identical pins hid exactly that.
                        icon = GuardianMapMarkers.pin(
                            kind = GuardianMapMarkers.Kind.Hazard,
                            badge = cell.incidentCount,
                            emphasis = hazardEmphasis(cell.incidentCount),
                        ),
                        anchor = PIN_ANCHOR,
                        title = "${cell.incidentCount} reported nearby",
                        snippet = "${cell.primaryOffense.displayName} · ${cell.jurisdiction}",
                        onClick = {
                            selectedFeature = SelectedMapFeature.Hazard(cell)
                            true
                        },
                    )
                }
            }

            // Destination Pin
            if (plan.destination != null) {
                Marker(
                    state = rememberMarkerState(plan.destination.point.latitude, plan.destination.point.longitude),
                    icon = GuardianMapMarkers.pin(GuardianMapMarkers.Kind.Destination),
                    anchor = PIN_ANCHOR,
                    title = plan.destination.name,
                    snippet = plan.destination.address,
                )
            }

            // Route Polylines. Once a walk starts, only the chosen one: the point of
            // following a route is not having to work out which of three lines is yours.
            val drawnRoutes = if (walkingRoute != null) listOf(walkingRoute) else plan.routes
            drawnRoutes.forEach { r ->
                if (r.path.isNotEmpty()) {
                    val pts = r.path.map { LatLng(it.latitude, it.longitude) }
                    Polyline(
                        points = pts,
                        color = if (r.isRecommended) Color(0xFFE57373) else Color(0xFFFFB74D),
                        width = if (r.isRecommended) 16f else 10f,
                        zIndex = if (r.isRecommended) 2f else 1f,
                    )
                }
            }
        }

        // --- 2. Top Floating Controls ----------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(GuardianTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
        ) {
            // Location Permission Alert Banner
            if (!hasLocationPermission) {
                LocationPermissionBanner(onRequestLocation = onRequestLocation)
            }

            // Turn-by-turn, in place of the search bar. Walking is a mode, not another
            // overlay on top of planning: a search field during a walk is one more thing
            // between her and the next turn.
            if (walkingRoute != null) {
                WalkingDirectionsCard(
                    route = walkingRoute,
                    currentLocation = currentLocation,
                    onEndWalk = {
                        walkingRouteId = null
                        onClearDestination()
                    },
                )
            }

            // Destination Search Bar
            if (!planning && walkingRoute == null) {
                InteractiveSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClear = {
                        searchQuery = ""
                        searchPredictions = emptyList()
                    },
                )

                // Places Autocomplete Predictions Dropdown
                if (searchPredictions.isNotEmpty()) {
                    SearchPredictionsDropdown(
                        predictions = searchPredictions,
                        onSelectPrediction = { pred ->
                            scope.launch {
                                val dest = placesSearchProvider?.fetchPlaceDetails(pred.placeId)
                                    ?: Destination(
                                        id = pred.placeId,
                                        name = pred.primaryText,
                                        address = pred.secondaryText,
                                        point = GeoPoint(37.8715, -122.2730),
                                        walkingMinutes = 12,
                                    )
                                onSelectCustomDestination(dest)
                                searchQuery = ""
                                searchPredictions = emptyList()
                            }
                        },
                    )
                }

                // Quick Destinations Row
                QuickDestinationsRow(
                    destinations = quickDestinations,
                    onSelect = onSelectDestination,
                )
            } else {
                DestinationBar(
                    destination = plan.destination!!,
                    onClear = onClearDestination,
                )
            }

            // Status Pills & Quick Actions Row
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.xs),
            ) {
                // Map Layers & Legend Toggle
                TonalPill(
                    text = "Legend & Layers",
                    icon = GuardianIcons.Eye,
                    container = GuardianTheme.materialColors.surfaceContainerLowest,
                    content = GuardianTheme.materialColors.primary,
                    modifier = Modifier
                        .clickable { showLegendDialog = true }
                        .padding(vertical = 2.dp),
                )

                // Navigate Home or Set Home shortcut
                if (homeLocation != null) {
                    TonalPill(
                        text = "Navigate Home",
                        icon = GuardianIcons.Moon,
                        container = GuardianTheme.materialColors.surfaceContainerLowest,
                        content = GuardianTheme.materialColors.primary,
                        modifier = Modifier
                            .clickable(onClick = onNavigateHome)
                            .padding(vertical = 2.dp),
                    )
                } else {
                    TonalPill(
                        text = "Set Home Base",
                        icon = GuardianIcons.Plus,
                        container = GuardianTheme.materialColors.surfaceContainerLowest,
                        content = GuardianTheme.materialColors.onSurface,
                        modifier = Modifier
                            .clickable { showHomeDialog = true }
                            .padding(vertical = 2.dp),
                    )
                }

                // Illumination metric
                TonalPill(
                    text = "${plan.areaIlluminationPercent}% lighting",
                    icon = GuardianIcons.Sun,
                    container = GuardianTheme.materialColors.surfaceContainerLowest,
                    content = GuardianTheme.materialColors.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp),
                )

                // Safe havens metric
                TonalPill(
                    text = "${safeHavens.size} havens",
                    icon = GuardianIcons.Shield,
                    container = GuardianTheme.materialColors.surfaceContainerLowest,
                    content = GuardianTheme.materialColors.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }

            // Angel's answer to "where am I?" — shown only on tap, and it stands down on
            // its own so a user who tapped by accident is not left closing a card.
            LaunchedEffect(angelCallout, angelPulse) {
                if (angelCallout != null) {
                    delay(ANGEL_CALLOUT_MILLIS)
                    angelCallout = null
                }
            }
            AngelLocationCallout(
                callout = angelCallout,
                onDismiss = { angelCallout = null },
                modifier = Modifier.padding(top = 4.dp),
            )

            // Floating Angel Text Status Bubble. Hidden while the callout is up: two
            // stacked bubbles saying overlapping things is noise, and the one the user
            // asked for wins.
            AngelStatusBubble(
                message = resolvedState.statusBubbleMessage,
                category = resolvedState.statusCategory,
                mood = resolvedState.mood,
                visible = !statusBubbleDismissed && angelCallout == null,
                onDismiss = { statusBubbleDismissed = true },
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // --- 3. Recenter & Follow Mode Floating Button -----------------------------
        FloatingActionButton(
            onClick = {
                followMode = true
                if (currentLocation != null) {
                    scope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(currentLocation.latitude, currentLocation.longitude),
                                15.5f
                            )
                        )
                    }
                }
            },
            containerColor = if (followMode) GuardianTheme.materialColors.primaryContainer else GuardianTheme.materialColors.surfaceContainerLowest,
            contentColor = if (followMode) GuardianTheme.materialColors.onPrimaryContainer else GuardianTheme.materialColors.primary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    bottom = if (planning && walkingRoute == null) 240.dp else 100.dp,
                    end = 16.dp,
                )
                .size(48.dp)
                .semantics {
                    contentDescription = if (followMode) "Camera following user" else "Recenter camera on user"
                },
        ) {
            Icon(
                imageVector = if (followMode) GuardianIcons.Walk else GuardianIcons.MapPin,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }

        // --- 4. Route Sheet (Multiple Walking Alternatives) -----------------------
        AnimatedVisibility(
            visible = planning && walkingRoute == null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            RouteAlternativesSheet(
                routes = plan.routes,
                selectedPreference = plan.preference,
                onSelectPreference = onPreferenceChange,
                onWalk = {
                    // Enters walking mode here rather than navigating away. Tapping
                    // "Walk with me" used to drop her on the home screen, which is the
                    // one place the route she just chose is not visible.
                    walkingRouteId = plan.recommended?.id
                    followMode = true
                    onWalk()
                },
            )
        }

        // --- 5. Selected Feature Detail Sheet --------------------------------------
        // Lifted clear of the floating bottom navigation, and of the route sheet when a
        // walk is being planned. Sitting behind the nav bar it was unreadable; sitting on
        // top of it, it swallowed taps meant for another tab.
        MarkerDetailSheet(
            feature = selectedFeature,
            onDismiss = { selectedFeature = null },
            bottomInset = if (planning && walkingRoute == null) {
                PLANNING_SHEET_CLEARANCE
            } else {
                BottomBarClearance
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // --- 6. Home Configuration Dialog ------------------------------------------
        if (showHomeDialog) {
            HomeConfigDialog(
                currentHome = homeLocation,
                currentLocation = currentLocation,
                onSaveHome = onSaveHome,
                onRemoveHome = onRemoveHome,
                onDismiss = { showHomeDialog = false },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // --- 7. Map Legend & Filter Controls Dialog -------------------------------
        if (showLegendDialog) {
            MapLegendAndFiltersDialog(
                filters = filterState,
                onFilterChange = { filterState = it },
                onDismiss = { showLegendDialog = false },
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun rememberMarkerState(latitude: Double, longitude: Double): MarkerState =
    remember(latitude, longitude) { MarkerState(position = LatLng(latitude, longitude)) }

@Composable
private fun LocationPermissionBanner(
    onRequestLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = GuardianTheme.shapes.lg,
        colors = CardDefaults.cardColors(
            containerColor = GuardianTheme.materialColors.surfaceContainerLowest
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, GuardianTheme.colors.accentSoft),
    ) {
        Row(
            modifier = Modifier.padding(GuardianTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.md),
        ) {
            Icon(
                imageVector = GuardianIcons.MapPin,
                contentDescription = null,
                tint = GuardianTheme.materialColors.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Location Permission Needed",
                    style = GuardianTheme.type.labelMd,
                    color = GuardianTheme.materialColors.onSurface,
                )
                Text(
                    text = "Enable location to display live movement, safe havens, and crime markers.",
                    style = GuardianTheme.type.bodySm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
            }
            GuardianPrimaryButton(
                text = "Allow",
                onClick = onRequestLocation,
                modifier = Modifier.height(36.dp),
            )
        }
    }
}

@Composable
private fun InteractiveSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(GuardianTheme.shapes.pill)
            .background(GuardianTheme.materialColors.surfaceContainerLowest)
            .border(1.dp, GuardianTheme.colors.borderDefault, GuardianTheme.shapes.pill)
            .padding(horizontal = GuardianTheme.spacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        Icon(
            imageVector = GuardianIcons.MapPin,
            contentDescription = null,
            tint = GuardianTheme.materialColors.primary,
            modifier = Modifier.size(20.dp),
        )

        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search Berkeley addresses, campus, or shops",
                    style = GuardianTheme.type.bodyMd,
                    color = GuardianTheme.colors.iconMuted,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(
                    color = GuardianTheme.materialColors.onSurface,
                    fontSize = 15.sp,
                ),
                cursorBrush = SolidColor(GuardianTheme.materialColors.primary),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (query.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(20.dp)) {
                Icon(
                    imageVector = GuardianIcons.Close,
                    contentDescription = "Clear search",
                    tint = GuardianTheme.colors.iconMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchPredictionsDropdown(
    predictions: List<PlacePrediction>,
    onSelectPrediction: (PlacePrediction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = GuardianTheme.materialColors.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GuardianTheme.colors.borderEmphasis),
    ) {
        LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
            items(predictions.take(5)) { pred ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPrediction(pred) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = GuardianIcons.MapPin,
                        contentDescription = null,
                        tint = GuardianTheme.materialColors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pred.primaryText,
                            style = GuardianTheme.type.labelMd,
                            color = GuardianTheme.materialColors.onSurface,
                        )
                        if (pred.secondaryText.isNotBlank()) {
                            Text(
                                text = pred.secondaryText,
                                style = GuardianTheme.type.labelSm,
                                color = GuardianTheme.materialColors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickDestinationsRow(
    destinations: List<Destination>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        destinations.forEach { destination ->
            TonalPill(
                text = destination.walkingMinutes?.let { "${destination.name} · ${it}m" } ?: destination.name,
                icon = if (destination.isSafeHaven) GuardianIcons.Shield else GuardianIcons.MapPin,
                container = GuardianTheme.materialColors.surfaceContainerLowest,
                content = GuardianTheme.materialColors.onSurface,
                modifier = Modifier
                    .clickable { onSelect(destination.id) }
                    .padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun DestinationBar(
    destination: Destination,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(GuardianTheme.shapes.lg)
            .background(GuardianTheme.materialColors.surfaceContainerLowest)
            .border(1.dp, GuardianTheme.colors.borderDefault, GuardianTheme.shapes.lg)
            .padding(GuardianTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = destination.name,
                style = GuardianTheme.type.headlineMd,
                color = GuardianTheme.materialColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = destination.address,
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onClear,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = GuardianTheme.materialColors.surfaceContainer,
                contentColor = GuardianTheme.materialColors.onSurface,
            ),
        ) {
            Icon(
                imageVector = GuardianIcons.Close,
                contentDescription = "Clear destination",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun RouteAlternativesSheet(
    routes: List<SafeRoute>,
    selectedPreference: RoutePreference,
    onSelectPreference: (RoutePreference) -> Unit,
    onWalk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (routes.isEmpty()) return
    val recommended = routes.firstOrNull { it.isRecommended } ?: routes.first()
    val palette = safetyColorsFor(SafetyLevel.fromScore(recommended.safetyScore))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(GuardianTheme.spacing.md)
            .padding(bottom = BottomBarClearance)
            .clip(GuardianTheme.shapes.xl)
            .background(GuardianTheme.materialColors.surfaceContainerLowest)
            .border(1.dp, GuardianTheme.colors.borderEmphasis, GuardianTheme.shapes.xl)
            .padding(GuardianTheme.spacing.md),
    ) {
        // Preference selection tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                RoutePreference.Safest to "Safest",
                RoutePreference.Fastest to "Fastest",
                RoutePreference.WellLitOnly to "Well lit",
            ).forEach { (pref, label) ->
                val isSelected = pref == selectedPreference
                TonalPill(
                    text = label,
                    icon = when (pref) {
                        RoutePreference.Safest -> GuardianIcons.Shield
                        RoutePreference.Fastest -> GuardianIcons.Activity
                        RoutePreference.WellLitOnly -> GuardianIcons.Sun
                    },
                    container = if (isSelected) GuardianTheme.materialColors.primaryContainer else GuardianTheme.materialColors.surfaceContainer,
                    content = if (isSelected) GuardianTheme.materialColors.onPrimaryContainer else GuardianTheme.materialColors.onSurface,
                    modifier = Modifier
                        .clickable { onSelectPreference(pref) }
                        .padding(vertical = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Recommended corridor hero header
        Row(verticalAlignment = Alignment.CenterVertically) {
            TonalPill(
                text = "Angel's recommendation · ${recommended.safetyScore}% index",
                icon = GuardianIcons.ShieldCheck,
                container = palette.container,
                content = palette.onContainer,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${recommended.distanceMiles} mi · ${recommended.durationMinutes} min",
                style = GuardianTheme.type.labelMd,
                color = GuardianTheme.materialColors.onSurface,
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = recommended.label,
            style = GuardianTheme.type.headlineMd,
            color = GuardianTheme.materialColors.onSurface,
        )

        // Highlights & Warnings
        if (recommended.highlights.isNotEmpty()) {
            Text(
                text = "Highlights: " + recommended.highlights.joinToString(" · "),
                style = GuardianTheme.type.bodySm,
                color = GuardianTheme.materialColors.primary,
            )
        }
        if (recommended.warnings.isNotEmpty()) {
            Text(
                text = "Notice: " + recommended.warnings.first().label,
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.colors.accentWarm,
            )
        }

        Spacer(Modifier.height(12.dp))

        GuardianPrimaryButton(
            text = "Walk with Angel (${recommended.durationMinutes} min)",
            onClick = onWalk,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Where a pin's point sits in its bitmap.
 *
 * Taken from the renderer rather than guessed. Without an anchor the SDK centres the
 * whole bitmap on the coordinate, which puts every pin about half its own height north
 * of the thing it marks — a consistent, invisible lie on a map whose entire purpose is
 * where things are.
 */
private val PIN_ANCHOR = Offset(GuardianMapMarkers.ANCHOR_X, GuardianMapMarkers.ANCHOR_Y)

/**
 * How much bigger a dense hazard cluster is drawn.
 *
 * Capped, and rounded to the nearest tenth so the bitmap cache stays small. The growth
 * is deliberately gentle: a cluster forty times larger must not be forty times the pin,
 * or one bad block hides the whole neighbourhood underneath it.
 */
private fun hazardEmphasis(incidentCount: Int): Float =
    (1f + (incidentCount.coerceAtMost(40) / 40f) * 0.5f)

/** How long Angel's "where am I?" answer stays on screen before standing itself down. */
private const val ANGEL_CALLOUT_MILLIS = 7_000L

/**
 * Room left under the marker sheet while a route is being planned.
 *
 * The route sheet is already sitting on the bottom-nav clearance, so the marker sheet has
 * to clear both. Matched to the recenter button's own planning offset so the three move
 * together.
 */
private val PLANNING_SHEET_CLEARANCE = 268.dp
