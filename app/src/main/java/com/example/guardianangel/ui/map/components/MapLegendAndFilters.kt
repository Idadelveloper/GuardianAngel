package com.example.guardianangel.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme

/**
 * Filter state for privacy-preserving map markers.
 */
data class MarkerFilterState(
    val showCrimeClusters: Boolean = true,
    val showSafeHavens: Boolean = true,
    val showRouteWarnings: Boolean = true,
)

@Composable
fun MapLegendAndFiltersDialog(
    filters: MarkerFilterState,
    onFilterChange: (MarkerFilterState) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(GuardianTheme.spacing.md),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = GuardianTheme.materialColors.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GuardianTheme.colors.borderEmphasis),
    ) {
        Column(
            modifier = Modifier.padding(GuardianTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Map Legend & Layers",
                    style = GuardianTheme.type.headlineMd,
                    color = GuardianTheme.materialColors.onSurface,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = GuardianIcons.Close,
                        contentDescription = "Close legend",
                        tint = GuardianTheme.colors.iconMuted,
                    )
                }
            }

            Text(
                text = "Map markers use reported data and verified public services. Colors are always paired with labels and icons.",
                style = GuardianTheme.type.bodySm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )

            // Legend Items
            Column(verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm)) {
                LegendRow(
                    icon = GuardianIcons.Moon,
                    title = "Home / Sanctuary Base",
                    subtitle = "Designated safe area (score 100%)",
                )
                LegendRow(
                    icon = GuardianIcons.Shield,
                    title = "Verified Safe Havens",
                    subtitle = "24/7 Police, Hospitals, Fire, and SafeStops",
                )
                LegendRow(
                    icon = GuardianIcons.Warning,
                    title = "Reported Incident Clusters",
                    subtitle = "Aggregated 150m cells (BPD / UCPD logs)",
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Layer Controls",
                style = GuardianTheme.type.labelMd,
                color = GuardianTheme.materialColors.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Show Safe Haven Markers",
                    style = GuardianTheme.type.bodyMd,
                    color = GuardianTheme.materialColors.onSurface,
                )
                Switch(
                    checked = filters.showSafeHavens,
                    onCheckedChange = { onFilterChange(filters.copy(showSafeHavens = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GuardianTheme.materialColors.surface,
                        checkedTrackColor = GuardianTheme.materialColors.primary,
                    ),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Show Incident Cluster Markers",
                    style = GuardianTheme.type.bodyMd,
                    color = GuardianTheme.materialColors.onSurface,
                )
                Switch(
                    checked = filters.showCrimeClusters,
                    onCheckedChange = { onFilterChange(filters.copy(showCrimeClusters = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GuardianTheme.materialColors.surface,
                        checkedTrackColor = GuardianTheme.materialColors.primary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun LegendRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(GuardianTheme.shapes.md)
            .background(GuardianTheme.materialColors.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GuardianTheme.materialColors.primary,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(
                text = title,
                style = GuardianTheme.type.labelMd,
                color = GuardianTheme.materialColors.onSurface,
            )
            Text(
                text = subtitle,
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}
