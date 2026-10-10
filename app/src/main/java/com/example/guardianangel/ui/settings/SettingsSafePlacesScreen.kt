package com.example.guardianangel.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.guardianangel.data.platform.LocationTracker
import com.example.guardianangel.domain.model.SafeLocation
import com.example.guardianangel.domain.model.SafeLocationKind
import com.example.guardianangel.domain.repository.SafeLocationRepository
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.components.GuardianOutlinedButton
import com.example.guardianangel.ui.components.GuardianPrimaryButton
import com.example.guardianangel.ui.components.GuardianStackScaffold
import com.example.guardianangel.ui.components.GuardianTextField
import com.example.guardianangel.ui.home.components.TonalPill
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.onboarding.AssuranceCard
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Home, and up to two other places that count as safe.
 *
 * ## Why this screen matters more than it looks
 *
 * Almost everything else reads from it. "Am I at home" decides the safety score's
 * baseline, which decides Angel's mood, which decides what the home screen says. Until
 * this existed the app simply asserted "Resting at home" to everyone, always — including
 * someone standing on a street at midnight.
 *
 * Capped at three. A list of twenty safe places is a list of places that are not
 * special, and the score's whole meaning comes from the contrast between here and
 * somewhere safe.
 */
@Composable
fun SettingsSafePlacesRoute(
    repository: SafeLocationRepository,
    locationTracker: LocationTracker?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val places by repository.observeLocations()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    SettingsSafePlacesScreen(
        places = places,
        status = status,
        busy = busy,
        canUseLocation = locationTracker?.hasLocationPermission() == true,
        onBack = onBack,
        onSave = { name, isHome ->
            busy = true
            status = null
            scope.launch {
                val point = locationTracker?.getCurrentLocation()
                if (point == null) {
                    status = "Couldn't get a location fix. Step outside or try again."
                    busy = false
                    return@launch
                }
                val address = locationTracker.reverseGeocode(point).orEmpty()
                if (isHome) {
                    repository.setHome(name.ifBlank { "Home" }, point, address)
                } else {
                    repository.addSafeLocation(
                        SafeLocation(
                            id = "place-${UUID.randomUUID()}",
                            name = name,
                            point = point,
                            kind = SafeLocationKind.SafeHaven,
                            address = address,
                        )
                    )
                }
                status = "Saved${address.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()}"
                busy = false
            }
        },
        onRemove = { id -> scope.launch { repository.removeSafeLocation(id) } },
        modifier = modifier,
    )
}

@Composable
fun SettingsSafePlacesScreen(
    places: List<SafeLocation>,
    onBack: () -> Unit,
    onSave: (name: String, isHome: Boolean) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    status: String? = null,
    busy: Boolean = false,
    canUseLocation: Boolean = true,
) {
    val home = places.firstOrNull { it.isHome }
    val others = places.filterNot { it.isHome }
    var draftName by remember { mutableStateOf("") }

    // Home first, then two havens. Adding home is offered until it exists, because
    // without it the score has no baseline to measure anything against.
    val addingHome = home == null
    val atCapacity = !addingHome && others.size >= MAX_HAVENS

    GuardianStackScaffold(
        title = "Safe places",
        onBack = onBack,
        modifier = modifier,
    ) {
        AssuranceCard(
            icon = GuardianIcons.HomePin,
            title = "How Angel knows you're safe",
            body = "Your score sits at 100% inside one of these and starts moving the " +
                "moment you leave. Coordinates stay on this phone.",
        )

        if (home != null) {
            PlaceCard(place = home, onRemove = { onRemove(home.id) })
        }
        others.forEach { place ->
            PlaceCard(place = place, onRemove = { onRemove(place.id) })
        }

        if (!atCapacity) {
            GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
                Text(
                    text = if (addingHome) "Set your home" else "Add a safe place",
                    style = GuardianTheme.type.labelLg,
                    color = GuardianTheme.materialColors.onSurface,
                )
                Spacer(Modifier.height(GuardianTheme.spacing.xs))
                Text(
                    text = if (addingHome) {
                        "Stand where you want the centre to be — your doorstep is ideal."
                    } else {
                        "A workplace, a friend's flat, anywhere you'd be fine arriving at."
                    },
                    style = GuardianTheme.type.bodySm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )

                Spacer(Modifier.height(GuardianTheme.spacing.md))
                GuardianTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    label = "Name",
                    placeholder = if (addingHome) "Home" else "Mum's place",
                    leadingIcon = GuardianIcons.HomePin,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(GuardianTheme.spacing.md))
                GuardianPrimaryButton(
                    text = when {
                        busy -> "Getting your location…"
                        addingHome -> "Use my current location as home"
                        else -> "Save this spot"
                    },
                    onClick = { onSave(draftName.trim(), addingHome); draftName = "" },
                    enabled = canUseLocation && !busy && (addingHome || draftName.isNotBlank()),
                    leadingIcon = GuardianIcons.MapPin,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!canUseLocation) {
                    Spacer(Modifier.height(GuardianTheme.spacing.xs))
                    Text(
                        text = "Turn on location to save a place.",
                        style = GuardianTheme.type.labelSm,
                        color = GuardianTheme.materialColors.error,
                    )
                }
                status?.let {
                    Spacer(Modifier.height(GuardianTheme.spacing.xs))
                    Text(
                        text = it,
                        style = GuardianTheme.type.labelSm,
                        color = GuardianTheme.materialColors.onSurfaceVariant,
                    )
                }
            }
        }

        if (home == null) {
            Text(
                text = "Until you set a home, Angel can't tell when you've arrived " +
                    "somewhere safe — so your score stays based on your surroundings alone.",
                style = GuardianTheme.type.bodySm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaceCard(place: SafeLocation, onRemove: () -> Unit) {
    GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(GuardianTheme.shapes.sm)
                    .background(
                        if (place.isHome) {
                            GuardianTheme.colors.safeContainer
                        } else {
                            GuardianTheme.materialColors.surfaceContainerHigh
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (place.isHome) GuardianIcons.HomePin else GuardianIcons.Shield,
                    contentDescription = null,
                    tint = if (place.isHome) {
                        GuardianTheme.colors.onSafeContainer
                    } else {
                        GuardianTheme.materialColors.onSurfaceVariant
                    },
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.size(GuardianTheme.spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = place.name,
                        style = GuardianTheme.type.labelLg,
                        color = GuardianTheme.materialColors.onSurface,
                    )
                    if (place.isHome) TonalPill(text = "Home")
                }
                Text(
                    text = place.address.ifBlank {
                        "%.4f, %.4f".format(place.point.latitude, place.point.longitude)
                    },
                    style = GuardianTheme.type.labelSm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
                Text(
                    text = "Counts as safe within ${place.radiusMeters.toInt()} m",
                    style = GuardianTheme.type.labelSm,
                    color = GuardianTheme.colors.iconMuted,
                )
            }
            GuardianOutlinedButton(text = "Remove", onClick = onRemove)
        }
    }
}

/** Home plus two. More than that and none of them mean anything. */
private const val MAX_HAVENS = 2

@Preview(showBackground = true, device = "id:pixel_8", heightDp = 1100)
@Composable
private fun SafePlacesPreview() {
    GuardianAngelTheme {
        SettingsSafePlacesScreen(
            places = emptyList(),
            onBack = {},
            onSave = { _, _ -> },
            onRemove = {},
        )
    }
}
