package com.example.guardianangel.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.CrimeCell
import com.example.guardianangel.domain.model.SafeHavenPoi
import com.example.guardianangel.domain.model.SafeLocation
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme

sealed class SelectedMapFeature {
    data class Haven(val haven: SafeHavenPoi) : SelectedMapFeature()
    data class Hazard(val cell: CrimeCell) : SelectedMapFeature()
    data class HomeFeature(val home: SafeLocation) : SelectedMapFeature()
}

/**
 * The detail card for a tapped map marker.
 *
 * Takes a nullable feature and owns its own slide animation, so the caller never has to
 * keep a copy of the dismissed feature alive to stop the card emptying mid-exit.
 */
@Composable
fun MarkerDetailSheet(
    feature: SelectedMapFeature?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Space to leave below the card.
     *
     * The map's bottom navigation floats over the content, so a sheet pinned to the
     * bottom edge is half-hidden behind it. Padding rather than a smaller card: the sheet
     * keeps its full width and legibility and simply sits higher.
     */
    bottomInset: Dp = 0.dp,
) {
    // Survives the exit animation; read in the same composition that writes it.
    val shown = remember { LastFeature() }
    feature?.let { shown.value = it }

    AnimatedVisibility(
        visible = feature != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        val current = shown.value ?: return@AnimatedVisibility
        SheetBody(feature = current, onDismiss = onDismiss, bottomInset = bottomInset)
    }
}

/** A plain holder, not state: nothing outside this composition observes it. */
private class LastFeature {
    var value: SelectedMapFeature? = null
}

@Composable
private fun SheetBody(
    feature: SelectedMapFeature,
    onDismiss: () -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(GuardianTheme.spacing.md)
            .padding(bottom = bottomInset),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = GuardianTheme.materialColors.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GuardianTheme.colors.borderEmphasis),
    ) {
        Column(
            modifier = Modifier
                .padding(GuardianTheme.spacing.md)
                // Capped and scrollable so a dense incident cell cannot grow a card that
                // runs off the top of the screen on a small phone.
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    // Weighted so a long place name wraps instead of pushing the close
                    // button off the edge. Without this there was no way to dismiss the
                    // sheet except by tapping a different marker.
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val icon = when (feature) {
                        is SelectedMapFeature.Haven -> GuardianIcons.Shield
                        is SelectedMapFeature.Hazard -> GuardianIcons.Warning
                        is SelectedMapFeature.HomeFeature -> GuardianIcons.Moon
                    }
                    val iconTint = when (feature) {
                        is SelectedMapFeature.Haven -> GuardianTheme.materialColors.primary
                        is SelectedMapFeature.Hazard -> GuardianTheme.colors.accentWarm
                        is SelectedMapFeature.HomeFeature -> GuardianTheme.materialColors.primary
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp),
                    )

                    val title = when (feature) {
                        is SelectedMapFeature.Haven -> feature.haven.name
                        is SelectedMapFeature.Hazard -> "Reported Incident Cluster"
                        is SelectedMapFeature.HomeFeature -> feature.home.name
                    }
                    Text(
                        text = title,
                        style = GuardianTheme.type.headlineMd,
                        color = GuardianTheme.materialColors.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = GuardianIcons.Close,
                        contentDescription = "Dismiss details",
                        tint = GuardianTheme.colors.iconMuted,
                    )
                }
            }

            when (feature) {
                is SelectedMapFeature.Haven -> {
                    Text(
                        text = "${feature.haven.type.displayName} · ${feature.haven.openHoursDescription}",
                        style = GuardianTheme.type.labelMd,
                        color = GuardianTheme.materialColors.primary,
                    )
                    Text(
                        text = feature.haven.address,
                        style = GuardianTheme.type.bodySm,
                        color = GuardianTheme.materialColors.onSurfaceVariant,
                    )
                    if (feature.haven.phone != null) {
                        Text(
                            text = "Phone: ${feature.haven.phone}",
                            style = GuardianTheme.type.labelSm,
                            color = GuardianTheme.materialColors.onSurfaceVariant,
                        )
                    }
                }
                is SelectedMapFeature.Hazard -> {
                    Text(
                        text = "Source: ${feature.cell.jurisdiction} · 90-day window",
                        style = GuardianTheme.type.labelSm,
                        color = GuardianTheme.materialColors.onSurfaceVariant,
                    )
                    Text(
                        text = "${feature.cell.incidentCount} reported public safety incidents in this 150m grid cell. Primary category: ${feature.cell.primaryOffense.displayName}.",
                        style = GuardianTheme.type.bodySm,
                        color = GuardianTheme.materialColors.onSurface,
                    )
                    Text(
                        text = "Privacy notice: Coordinates reflect approximate cell center. Victim identities and specific home addresses are never retained.",
                        style = GuardianTheme.type.labelSm,
                        color = GuardianTheme.materialColors.onSurfaceVariant,
                    )
                }
                is SelectedMapFeature.HomeFeature -> {
                    Text(
                        text = "Your designated sanctuary base (80m geofence). Safety score is 100% when you are within this zone.",
                        style = GuardianTheme.type.bodySm,
                        color = GuardianTheme.materialColors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
