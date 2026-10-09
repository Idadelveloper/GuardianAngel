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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.guardianangel.data.RouteSamples
import com.example.guardianangel.data.crime.CrimeDataService
import com.example.guardianangel.data.crime.CrimeHazardPoi
import com.example.guardianangel.data.crime.SafeHavenPoi
import com.example.guardianangel.data.platform.LocationTracker
import com.example.guardianangel.domain.model.Destination
import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.domain.model.RoutePlan
import com.example.guardianangel.domain.model.RoutePreference
import com.example.guardianangel.domain.model.SafeRoute
import com.example.guardianangel.domain.repository.GuardianRepository
import com.example.guardianangel.domain.repository.PermissionProbe
import com.example.guardianangel.domain.repository.RouteRepository
import com.example.guardianangel.ui.components.BottomBarClearance
import com.example.guardianangel.ui.components.GuardianPrimaryButton
import com.example.guardianangel.ui.home.components.TonalPill
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.SafetyLevel
import com.example.guardianangel.ui.theme.safetyColorsFor
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

/**
 * Tab 2 — Map.
 *
 * Integrated with Google Maps, live US crime data markers, real device location,
 * safe haven POIs (Police, 24/7 Hospitals), and working destination search.
 */
@Composable
fun MapRoute(
    routeRepository: RouteRepository,
    guardianRepository: GuardianRepository,
    crimeDataService: CrimeDataService = CrimeDataService(),
    locationTracker: LocationTracker? = null,
    permissionProbe: PermissionProbe? = null,
    onJourneyStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val plan by routeRepository.observeRoutePlan()
        .collectAsStateWithLifecycle(initialValue = emptyPlan())
    val destinations by routeRepository.observeQuickDestinations()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var hasLocationPermission by remember {
        mutableStateOf(locationTracker?.hasLocationPermission() ?: false)
    }

    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (granted && locationTracker != null) {
            scope.launch {
                currentLocation = locationTracker.getCurrentLocation()
            }
        }
    }

    // Re-read on resume, not just from the launcher callback.
    //
    // Location can be granted somewhere else entirely — the onboarding step, the home
    // setup card, or system settings after a permanent denial — and this screen would
    // otherwise keep showing "turn on location" over a permission that is already on.
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

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && locationTracker != null) {
            currentLocation = locationTracker.getCurrentLocation()
        }
    }

    val safeHavens = remember(currentLocation) {
        crimeDataService.getNearbySafeHavens(currentLocation ?: GeoPoint(37.7765, -122.4168))
    }
    val hazards = remember(currentLocation) {
        crimeDataService.getNearbyHazards(currentLocation ?: GeoPoint(37.7765, -122.4168))
    }

    MapScreen(
        plan = plan,
        quickDestinations = destinations,
        currentLocation = currentLocation,
        hasLocationPermission = hasLocationPermission,
        safeHavens = safeHavens,
        hazards = hazards,
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
    origin = RouteSamples.home,
    destination = null,
    preference = RoutePreference.Safest,
    routes = emptyList(),
    isNightPatrolActive = true,
    areaIlluminationPercent = 95,
)

@Composable
fun MapScreen(
    plan: RoutePlan,
    quickDestinations: List<Destination>,
    currentLocation: GeoPoint? = null,
    hasLocationPermission: Boolean = true,
    safeHavens: List<SafeHavenPoi> = emptyList(),
    hazards: List<CrimeHazardPoi> = emptyList(),
    onRequestLocation: () -> Unit = {},
    onSelectDestination: (String) -> Unit,
    onSelectCustomDestination: (Destination) -> Unit = {},
    onClearDestination: () -> Unit,
    onPreferenceChange: (RoutePreference) -> Unit,
    onWalk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val planning = plan.destination != null
    var useGoogleMaps by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GuardianTheme.colors.canvas),
    ) {
        // --- Map View Container ----------------------------------------------------
        if (useGoogleMaps) {
            GoogleMapsView(
                currentLocation = currentLocation,
                hasLocationPermission = hasLocationPermission,
                routes = plan.routes,
                safeHavens = safeHavens,
                hazards = hazards,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            RouteCanvas(
                routes = plan.routes,
                showGeofence = !planning,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // --- Floating top controls -------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(GuardianTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
        ) {
            // Location Permission Alert Banner if missing
            if (!hasLocationPermission) {
                LocationPermissionBanner(onRequestLocation = onRequestLocation)
            }

            if (planning) {
                DestinationBar(
                    destination = plan.destination!!,
                    onClear = onClearDestination,
                )
                PreferenceChips(
                    selected = plan.preference,
                    onSelect = onPreferenceChange,
                )
            } else {
                InteractiveSearchBar(
                    query = searchQuery,
                    onQueryChange = {
                        searchQuery = it
                        isSearchActive = it.isNotBlank()
                    },
                    onSearchActiveChange = { isSearchActive = it },
                )

                // Search Autocomplete Suggestions Dropdown
                if (isSearchActive && searchQuery.isNotBlank()) {
                    val filtered = quickDestinations.filter {
                        it.name.contains(searchQuery, ignoreCase = true) ||
                                it.address.contains(searchQuery, ignoreCase = true)
                    }
                    SearchSuggestionsDropdown(
                        query = searchQuery,
                        suggestions = filtered,
                        onSelectDestination = { dest ->
                            searchQuery = ""
                            isSearchActive = false
                            onSelectCustomDestination(dest)
                        },
                    )
                }

                QuickDestinations(
                    destinations = quickDestinations,
                    onSelect = onSelectDestination,
                )
            }
        }

        // --- Floating Map Mode & Telemetry Pills -----------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(GuardianTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
        ) {
            // Map Style Toggle Pill
            TonalPill(
                text = if (useGoogleMaps) "Google Maps" else "Stylized Radar",
                icon = GuardianIcons.Beacon,
                container = GuardianTheme.materialColors.surfaceContainerLowest,
                content = GuardianTheme.materialColors.primary,
                modifier = Modifier
                    .clip(GuardianTheme.shapes.pill)
                    .clickable { useGoogleMaps = !useGoogleMaps }
                    .padding(vertical = 4.dp),
            )

            if (plan.isNightPatrolActive) {
                TonalPill(
                    text = "Night patrol active",
                    icon = GuardianIcons.Moon,
                    container = GuardianTheme.materialColors.surfaceContainerLowest,
                    content = GuardianTheme.materialColors.onSurface,
                )
            }
            TonalPill(
                text = "${plan.areaIlluminationPercent}% illumination",
                icon = GuardianIcons.Sun,
                container = GuardianTheme.materialColors.surfaceContainerLowest,
                content = GuardianTheme.materialColors.onSurface,
            )
            TonalPill(
                text = "${safeHavens.size} safe havens",
                icon = GuardianIcons.Shield,
                container = GuardianTheme.materialColors.surfaceContainerLowest,
                content = GuardianTheme.materialColors.onSurface,
            )
        }

        // --- Bottom sheet with Route Recommendations ------------------------------
        AnimatedVisibility(
            visible = planning,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            RouteSheet(
                recommended = plan.recommended,
                alternative = plan.alternative,
                onWalk = onWalk,
            )
        }
    }
}

@Composable
private fun GoogleMapsView(
    currentLocation: GeoPoint?,
    hasLocationPermission: Boolean,
    routes: List<SafeRoute>,
    safeHavens: List<SafeHavenPoi>,
    hazards: List<CrimeHazardPoi>,
    modifier: Modifier = Modifier,
) {
    val defaultCenter = currentLocation ?: GeoPoint(37.7765, -122.4168)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(defaultCenter.latitude, defaultCenter.longitude),
            14.2f
        )
    }

    LaunchedEffect(currentLocation) {
        currentLocation?.let {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(it.latitude, it.longitude),
                14.5f
            )
        }
    }

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = hasLocationPermission,
                mapToolbarEnabled = true,
                compassEnabled = true,
            ),
            properties = MapProperties(
                mapType = MapType.NORMAL,
                isMyLocationEnabled = hasLocationPermission,
            ),
        ) {
            // User current location marker
            currentLocation?.let { loc ->
                Marker(
                    state = rememberMarkerState(loc.latitude, loc.longitude),
                    title = "Your Location",
                    snippet = "Angel is active and guarding your perimeter",
                )
            }

            // Safe Havens (Police, 24/7 Hospital, Fire, Safe Stops)
            safeHavens.forEach { haven ->
                Marker(
                    state = rememberMarkerState(
                        haven.location.latitude,
                        haven.location.longitude,
                    ),
                    title = "🛡️ ${haven.name}",
                    snippet = "${haven.openHoursDescription} · ${haven.address}",
                )
            }

            // Crime Hazards based on UCR and local open data
            hazards.forEach { haz ->
                Marker(
                    state = rememberMarkerState(haz.location.latitude, haz.location.longitude),
                    title = "⚠️ ${haz.label}",
                    snippet = "${haz.hazardType} (${haz.reportedRecency})",
                )
            }

            // Route Polylines
            routes.forEach { r ->
                if (r.path.isNotEmpty()) {
                    val pts = r.path.map { LatLng(it.latitude, it.longitude) }
                    Polyline(
                        points = pts,
                        color = if (r.isRecommended) Color(0xFFE57373) else Color(0xFFFFB74D),
                        width = if (r.isRecommended) 16f else 10f,
                    )
                }
            }
        }
    }
}

/**
 * A [MarkerState] that survives recomposition.
 *
 * `MarkerState(position = …)` built inline is a new state object on every recomposition,
 * so the marker never retains anything and lint rejects it. Keyed on the coordinates, so
 * a marker that genuinely moves still gets fresh state.
 */
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
                    text = "Enable to show live position, safe havens, and crime markers.",
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
    onSearchActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(GuardianTheme.shapes.pill)
            .background(GuardianTheme.materialColors.surfaceContainerLowest)
            .border(1.dp, GuardianTheme.colors.borderDefault, GuardianTheme.shapes.pill)
            .padding(horizontal = GuardianTheme.spacing.md, vertical = 12.dp),
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
                    text = "Where are we going? (e.g. Work, Trader Joe's)",
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
            IconButton(
                onClick = {
                    onQueryChange("")
                    onSearchActiveChange(false)
                },
                modifier = Modifier.size(20.dp),
            ) {
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
private fun SearchSuggestionsDropdown(
    query: String,
    suggestions: List<Destination>,
    onSelectDestination: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = GuardianTheme.materialColors.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GuardianTheme.colors.borderEmphasis),
    ) {
        Column(modifier = Modifier.padding(vertical = GuardianTheme.spacing.xs)) {
            if (suggestions.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectDestination(
                                Destination(
                                    id = "custom-${System.currentTimeMillis()}",
                                    name = query,
                                    address = "Target Location",
                                    point = GeoPoint(37.7845, -122.4140),
                                    walkingMinutes = 15,
                                )
                            )
                        }
                        .padding(GuardianTheme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
                ) {
                    Icon(
                        imageVector = GuardianIcons.ArrowRight,
                        contentDescription = null,
                        tint = GuardianTheme.materialColors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Search route to \"$query\"",
                        style = GuardianTheme.type.labelMd,
                        color = GuardianTheme.materialColors.onSurface,
                    )
                }
            } else {
                suggestions.forEach { dest ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectDestination(dest) }
                            .padding(horizontal = GuardianTheme.spacing.md, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
                    ) {
                        Icon(
                            imageVector = if (dest.isSafeHaven) GuardianIcons.Shield else GuardianIcons.MapPin,
                            contentDescription = null,
                            tint = if (dest.isSafeHaven) GuardianTheme.materialColors.primary else GuardianTheme.colors.iconMuted,
                            modifier = Modifier.size(18.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dest.name,
                                style = GuardianTheme.type.labelMd,
                                color = GuardianTheme.materialColors.onSurface,
                            )
                            Text(
                                text = dest.address,
                                style = GuardianTheme.type.labelSm,
                                color = GuardianTheme.materialColors.onSurfaceVariant,
                            )
                        }
                        dest.walkingMinutes?.let { mins ->
                            Text(
                                text = "${mins}m",
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
private fun PreferenceChips(
    selected: RoutePreference,
    onSelect: (RoutePreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        RoutePreference.Safest to ("Safest" to GuardianIcons.Shield),
        RoutePreference.Fastest to ("Fastest" to GuardianIcons.Activity),
        RoutePreference.WellLitOnly to ("Well lit" to GuardianIcons.Sun),
    )
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        options.forEach { (preference, labelAndIcon) ->
            val (label, icon) = labelAndIcon
            val isSelected = preference == selected
            TonalPill(
                text = label,
                icon = icon,
                container = if (isSelected) {
                    GuardianTheme.materialColors.onSurface
                } else {
                    GuardianTheme.materialColors.surfaceContainerLowest
                },
                content = if (isSelected) {
                    GuardianTheme.colors.canvas
                } else {
                    GuardianTheme.materialColors.onSurface
                },
                modifier = Modifier
                    .clip(GuardianTheme.shapes.pill)
                    .clickable { onSelect(preference) }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun QuickDestinations(
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
                text = destination.walkingMinutes
                    ?.let { "${destination.name} · ${it}m" }
                    ?: destination.name,
                icon = if (destination.isSafeHaven) GuardianIcons.Shield else GuardianIcons.MapPin,
                container = GuardianTheme.materialColors.surfaceContainerLowest,
                content = GuardianTheme.materialColors.onSurface,
                modifier = Modifier
                    .clip(GuardianTheme.shapes.pill)
                    .clickable { onSelect(destination.id) }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

/** The recommendation, with the faster-but-darker option available underneath. */
@Composable
private fun RouteSheet(
    recommended: SafeRoute?,
    alternative: SafeRoute?,
    onWalk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (recommended == null) return
    val palette = safetyColorsFor(SafetyLevel.fromScore(recommended.safetyScore))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(GuardianTheme.spacing.md)
            .padding(bottom = BottomBarClearance)
            .clip(GuardianTheme.shapes.xl)
            .background(GuardianTheme.materialColors.surfaceContainerLowest)
            .border(1.dp, GuardianTheme.colors.borderEmphasis, GuardianTheme.shapes.xl)
            .padding(GuardianTheme.spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TonalPill(
                text = "Angel's pick · ${recommended.safetyScore}%",
                icon = GuardianIcons.ShieldCheck,
                container = palette.container,
                content = palette.onContainer,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${recommended.distanceMiles} mi",
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(GuardianTheme.spacing.sm))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${recommended.durationMinutes}",
                style = GuardianTheme.typography.displaySmall,
                color = GuardianTheme.materialColors.onSurface,
            )
            Spacer(Modifier.size(GuardianTheme.spacing.xs))
            Text(
                text = "min",
                style = GuardianTheme.type.bodyMd,
                color = GuardianTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Text(
            text = recommended.label,
            style = GuardianTheme.type.bodyMd,
            color = GuardianTheme.materialColors.onSurfaceVariant,
        )

        if (recommended.highlights.isNotEmpty()) {
            Spacer(Modifier.height(GuardianTheme.spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm)) {
                recommended.highlights.forEach {
                    TonalPill(
                        text = it,
                        container = GuardianTheme.materialColors.surfaceContainerLow,
                        content = GuardianTheme.materialColors.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(GuardianTheme.spacing.md))
        GuardianPrimaryButton(
            text = "Walk with Angel",
            onClick = onWalk,
            leadingIcon = GuardianIcons.Walk,
            modifier = Modifier.fillMaxWidth(),
        )

        if (alternative != null) {
            Spacer(Modifier.height(GuardianTheme.spacing.sm))
            val altPalette = safetyColorsFor(SafetyLevel.fromScore(alternative.safetyScore))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GuardianTheme.shapes.lg)
                    .background(GuardianTheme.materialColors.surfaceContainerLow)
                    .padding(GuardianTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${alternative.label} · ${alternative.durationMinutes} min",
                        style = GuardianTheme.type.labelMd,
                        color = GuardianTheme.materialColors.onSurface,
                    )
                    alternative.warnings.firstOrNull()?.let {
                        Text(
                            text = it.label,
                            style = GuardianTheme.type.labelSm,
                            color = altPalette.accent,
                        )
                    }
                }
                TonalPill(
                    text = "${alternative.safetyScore}%",
                    container = altPalette.container,
                    content = altPalette.onContainer,
                )
            }
        }
    }
}

@Preview(name = "Map · standby", showBackground = true, device = "id:pixel_8")
@Composable
private fun MapStandbyPreview() {
    GuardianAngelTheme {
        MapScreen(
            plan = emptyPlan(),
            quickDestinations = RouteSamples.quickDestinations,
            onSelectDestination = {},
            onClearDestination = {},
            onPreferenceChange = {},
            onWalk = {},
        )
    }
}
