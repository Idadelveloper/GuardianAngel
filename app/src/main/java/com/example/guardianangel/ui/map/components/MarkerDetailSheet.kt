package com.example.guardianangel.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun MarkerDetailSheet(
    feature: SelectedMapFeature,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(GuardianTheme.spacing.md),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = GuardianTheme.materialColors.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GuardianTheme.colors.borderEmphasis),
    ) {
        Column(
            modifier = Modifier.padding(GuardianTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
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
