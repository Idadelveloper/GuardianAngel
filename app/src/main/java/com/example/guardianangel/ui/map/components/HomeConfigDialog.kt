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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.domain.model.SafeLocation
import com.example.guardianangel.ui.components.GuardianPrimaryButton
import com.example.guardianangel.ui.home.components.TonalPill
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme

@Composable
fun HomeConfigDialog(
    currentHome: SafeLocation?,
    currentLocation: GeoPoint?,
    onSaveHome: (name: String, point: GeoPoint, address: String, radiusMeters: Float) -> Unit,
    onRemoveHome: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var homeName by remember { mutableStateOf(currentHome?.name ?: "Home") }
    var homeAddress by remember { mutableStateOf(currentHome?.address ?: "Downtown Berkeley Sanctuary") }
    var selectedPoint by remember {
        mutableStateOf(currentHome?.point ?: currentLocation ?: GeoPoint(37.8715, -122.2730))
    }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = GuardianIcons.Moon,
                        contentDescription = null,
                        tint = GuardianTheme.materialColors.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = if (currentHome != null) "Configure Home" else "Set Home Base",
                        style = GuardianTheme.type.headlineMd,
                        color = GuardianTheme.materialColors.onSurface,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = GuardianIcons.Close,
                        contentDescription = "Close dialog",
                        tint = GuardianTheme.colors.iconMuted,
                    )
                }
            }

            Text(
                text = "Home is your designated sanctuary base. When you are inside your home area, your safety score is 100% and Angel rests quietly. Home coordinates are stored locally on your device.",
                style = GuardianTheme.type.bodySm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )

            // Current location shortcut
            if (currentLocation != null) {
                TonalPill(
                    text = "Use Current Location as Home",
                    icon = GuardianIcons.MapPin,
                    container = GuardianTheme.materialColors.surfaceContainer,
                    content = GuardianTheme.materialColors.primary,
                    modifier = Modifier
                        .clip(GuardianTheme.shapes.pill)
                        .border(1.dp, GuardianTheme.colors.accentSoft, GuardianTheme.shapes.pill)
                        .clickable {
                            selectedPoint = currentLocation
                            homeAddress = "Current Device Location"
                        }
                        .padding(vertical = 4.dp),
                )
            }

            // Name field
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Location Name",
                    style = GuardianTheme.type.labelSm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
                BasicTextField(
                    value = homeName,
                    onValueChange = { homeName = it },
                    textStyle = TextStyle(
                        color = GuardianTheme.materialColors.onSurface,
                        fontSize = 15.sp,
                    ),
                    cursorBrush = SolidColor(GuardianTheme.materialColors.primary),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GuardianTheme.shapes.md)
                        .background(GuardianTheme.materialColors.surfaceContainer)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }

            // Address label
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Address / Description",
                    style = GuardianTheme.type.labelSm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
                BasicTextField(
                    value = homeAddress,
                    onValueChange = { homeAddress = it },
                    textStyle = TextStyle(
                        color = GuardianTheme.materialColors.onSurface,
                        fontSize = 15.sp,
                    ),
                    cursorBrush = SolidColor(GuardianTheme.materialColors.primary),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GuardianTheme.shapes.md)
                        .background(GuardianTheme.materialColors.surfaceContainer)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }

            Spacer(Modifier.height(4.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
            ) {
                if (currentHome != null) {
                    GuardianPrimaryButton(
                        text = "Remove",
                        onClick = {
                            onRemoveHome()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                GuardianPrimaryButton(
                    text = "Save Home",
                    onClick = {
                        onSaveHome(homeName, selectedPoint, homeAddress, 80f)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1.5f),
                )
            }
        }
    }
}
