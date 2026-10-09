package com.example.guardianangel.ui.map

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.guardianangel.data.FakeRouteRepository
import com.example.guardianangel.data.RouteSamples
import com.example.guardianangel.domain.model.Destination
import com.example.guardianangel.domain.model.RoutePlan
import com.example.guardianangel.domain.model.RoutePreference
import com.example.guardianangel.domain.model.SafeRoute
import com.example.guardianangel.domain.repository.GuardianRepository
import com.example.guardianangel.domain.repository.RouteRepository
import com.example.guardianangel.ui.components.BottomBarClearance
import com.example.guardianangel.ui.components.GuardianPrimaryButton
import com.example.guardianangel.ui.home.components.TonalPill
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.SafetyLevel
import com.example.guardianangel.ui.theme.safetyColorsFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Tab 2 — Map.
 *
 * Two states on one surface: standby (geofence, search, quick destinations) and route
 * comparison (corridors drawn, bottom sheet with the recommendation). The map itself is
 * [RouteCanvas], a stylised stand-in until a Maps SDK key exists — see its documentation.
 */
@Composable
fun MapRoute(
    routeRepository: RouteRepository,
    guardianRepository: GuardianRepository,
    onJourneyStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val plan by routeRepository.observeRoutePlan()
        .collectAsStateWithLifecycle(initialValue = emptyPlan())
    val destinations by routeRepository.observeQuickDestinations()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    MapScreen(
        plan = plan,
        quickDestinations = destinations,
        modifier = modifier,
        onSelectDestination = { scope.launch { routeRepository.selectDestination(it) } },
        onClearDestination = { scope.launch { routeRepository.clearDestination() } },
        onPreferenceChange = { scope.launch { routeRepository.setPreference(it) } },
        onWalk = {
            scope.launch {
                guardianRepository.armGuardian()
                onJourneyStarted()
            }
        },
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
    onSelectDestination: (String) -> Unit,
    onClearDestination: () -> Unit,
    onPreferenceChange: (RoutePreference) -> Unit,
    onWalk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val planning = plan.destination != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GuardianTheme.colors.canvas),
    ) {
        RouteCanvas(
            routes = plan.routes,
            showGeofence = !planning,
            modifier = Modifier.fillMaxSize(),
        )

        // --- Floating top controls -------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(GuardianTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
        ) {
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
                SearchBar()
                QuickDestinations(
                    destinations = quickDestinations,
                    onSelect = onSelectDestination,
                )
            }
        }

        // --- Floating telemetry ----------------------------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(GuardianTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
        ) {
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
        }

        // --- Bottom sheet -----------------------------------------------------------
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
private fun SearchBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(GuardianTheme.shapes.pill)
            .background(GuardianTheme.materialColors.surfaceContainerLowest)
            .border(1.dp, GuardianTheme.colors.borderDefault, GuardianTheme.shapes.pill)
            .padding(horizontal = GuardianTheme.spacing.md, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        Icon(
            imageVector = GuardianIcons.MapPin,
            contentDescription = null,
            tint = GuardianTheme.materialColors.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Where are we going?",
            style = GuardianTheme.type.bodyMd,
            color = GuardianTheme.colors.iconMuted,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = GuardianIcons.Mic,
            contentDescription = "Search by voice",
            tint = GuardianTheme.colors.iconMuted,
            modifier = Modifier.size(20.dp),
        )
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
            onSelectDestination = {}, onClearDestination = {},
            onPreferenceChange = {}, onWalk = {},
        )
    }
}

@Preview(name = "Map · routes", showBackground = true, device = "id:pixel_8")
@Composable
private fun MapRoutesPreview() {
    val destination = RouteSamples.quickDestinations.first()
    GuardianAngelTheme {
        MapScreen(
            plan = emptyPlan().copy(
                destination = destination,
                routes = RouteSamples.routesTo(destination),
            ),
            quickDestinations = RouteSamples.quickDestinations,
            onSelectDestination = {}, onClearDestination = {},
            onPreferenceChange = {}, onWalk = {},
        )
    }
}
