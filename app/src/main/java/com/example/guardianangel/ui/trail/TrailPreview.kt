package com.example.guardianangel.ui.trail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.guardianangel.data.platform.MapsAvailability
import com.example.guardianangel.domain.model.SessionTrail
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme

/**
 * The small map on a session row.
 *
 * Renders a real Google map in **lite mode** when a Maps key is configured — lite mode
 * exists precisely for "a number of maps in a stream": it is a bitmap rather than a live
 * map, so a list of them does not cost a GL surface each. Without a key it falls back to
 * [TrailCanvas], because the SDK's behaviour with the placeholder key is to draw an
 * empty box, which reads as a loading bug on every row.
 *
 * Tapping opens the session. Lite mode's default tap behaviour is to launch the Google
 * Maps app, which would throw the user out of a safety app mid-review, so the whole
 * preview is wrapped in a click target instead.
 */
@Composable
fun TrailPreview(
    trail: SessionTrail,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
) {
    val shape = GuardianTheme.shapes.lg

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .border(1.dp, GuardianTheme.colors.borderDefault, shape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        when {
            trail.points.isEmpty() -> NoPathPlaceholder()
            useRealMap() -> TrailLiteMap(trail = trail, modifier = Modifier.fillMaxSize())
            else -> TrailCanvas(
                trail = trail,
                modifier = Modifier.fillMaxSize(),
                // Keeps the path clear of the badges drawn over the bottom-left corner.
                bottomInset = 30.dp,
            )
        }

        if (trail.points.isNotEmpty()) {
            TrailBadges(
                trail = trail,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(GuardianTheme.spacing.sm),
            )
        }
    }
}

/**
 * True when a real map should be drawn.
 *
 * False in `@Preview`, where the Maps SDK cannot render and would leave a designer
 * looking at an empty rectangle.
 */
@Composable
internal fun useRealMap(): Boolean {
    if (LocalInspectionMode.current) return false
    return MapsAvailability.hasMapKey(LocalContext.current)
}

/**
 * Shown when a session has no breadcrumbs at all.
 *
 * Says *why* there is no map rather than drawing an empty one. A session recorded with
 * location switched off is a normal thing to have, and the row should explain it rather
 * than look broken.
 */
@Composable
private fun NoPathPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GuardianTheme.materialColors.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
        ) {
            Icon(
                imageVector = GuardianIcons.MapPin,
                contentDescription = null,
                tint = GuardianTheme.colors.iconMuted,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "No location recorded for this session",
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

/** Distance walked and how many pins, so the row says something before it is opened. */
@Composable
private fun TrailBadges(trail: SessionTrail, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrailChip(
            text = if (trail.hasPath) trail.distanceLabel() else "Stationary",
            icon = GuardianIcons.Walk,
        )
        if (trail.incidents.isNotEmpty()) {
            TrailChip(
                text = "${trail.incidents.size} flagged",
                icon = GuardianIcons.Warning,
                emphasised = true,
            )
        }
    }
}

@Composable
private fun TrailChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    emphasised: Boolean = false,
) {
    Row(
        modifier = Modifier
            .clip(GuardianTheme.shapes.pill)
            .background(
                if (emphasised) {
                    GuardianTheme.colors.duress
                } else {
                    GuardianTheme.materialColors.surfaceContainerLowest
                }
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (emphasised) {
                GuardianTheme.colors.onDuress
            } else {
                GuardianTheme.materialColors.onSurfaceVariant
            },
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = text,
            style = GuardianTheme.type.labelSm,
            color = if (emphasised) {
                GuardianTheme.colors.onDuress
            } else {
                GuardianTheme.materialColors.onSurfaceVariant
            },
        )
    }
}
