package com.example.guardianangel.ui.map.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.domain.model.SafeRoute
import com.example.guardianangel.domain.route.WalkDirections
import com.example.guardianangel.ui.home.components.TonalPill
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme

/**
 * The next turn, how far to it, and how much walk is left.
 *
 * Deliberately one instruction at a time. A scrollable list of twelve steps is a thing
 * you read before you set off; what someone walking at night needs is the single next
 * decision, in text large enough to take in at a glance without stopping.
 *
 * It says where the directions came from. They are derived from the shape of the route
 * rather than from a turn-by-turn service, which means distances and directions are
 * real but street names are not available — and implying otherwise would be the app
 * sounding more certain than it is.
 */
@Composable
fun WalkingDirectionsCard(
    route: SafeRoute,
    currentLocation: GeoPoint?,
    onEndWalk: () -> Unit,
    modifier: Modifier = Modifier,
    /** Who gets a text on arrival, when she asked for that. */
    arrivalContactName: String? = null,
) {
    val steps = remember(route.id, route.path) { WalkDirections.stepsFor(route.path) }
    val progress = remember(steps, currentLocation) {
        currentLocation?.let { WalkDirections.progress(route.path, steps, it) }
    }

    val offRoute = progress?.isOffRoute == true
    val accent by animateColorAsState(
        targetValue = if (offRoute) {
            GuardianTheme.colors.accentWarm
        } else {
            GuardianTheme.materialColors.primary
        },
        label = "directionAccent",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, GuardianTheme.shapes.xl)
            .clip(GuardianTheme.shapes.xl)
            .background(GuardianTheme.materialColors.surfaceContainerLowest)
            .border(1.dp, GuardianTheme.colors.borderEmphasis, GuardianTheme.shapes.xl)
            .padding(GuardianTheme.spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val step = progress?.nextStep
            Icon(
                imageVector = turnIcon(step?.turn),
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .size(34.dp)
                    .rotate(turnRotation(step?.turn)),
            )
            Spacer(Modifier.size(GuardianTheme.spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        currentLocation == null -> "Waiting for your location"
                        offRoute -> "You've left the route"
                        step == null -> "You've arrived"
                        else -> step.instruction
                    },
                    style = GuardianTheme.type.headlineMd,
                    color = GuardianTheme.materialColors.onSurface,
                )
                Text(
                    text = when {
                        currentLocation == null ->
                            "I'll start guiding as soon as I have a fix."
                        offRoute ->
                            "Head back toward the line, or tap End walk to pick a new route."
                        step == null -> route.label
                        else -> "in ${WalkDirections.formatDistance(progress.metersToNextStep)}"
                    },
                    style = GuardianTheme.type.bodySm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
            }
        }

        if (progress != null && !offRoute && progress.nextStep != null) {
            Spacer(Modifier.height(GuardianTheme.spacing.sm))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription =
                            "${progress.minutesRemaining} minutes and " +
                                "${progress.metersRemaining} metres remaining"
                    },
                horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TonalPill(
                    text = "${progress.minutesRemaining} min left",
                    icon = GuardianIcons.Clock,
                    container = GuardianTheme.materialColors.surfaceContainerLow,
                    content = GuardianTheme.materialColors.onSurface,
                )
                TonalPill(
                    text = WalkDirections.formatDistance(progress.metersRemaining),
                    icon = GuardianIcons.MapPin,
                    container = GuardianTheme.materialColors.surfaceContainerLow,
                    content = GuardianTheme.materialColors.onSurface,
                )
                TonalPill(
                    text = "${route.safetyScore}% safe",
                    icon = GuardianIcons.Shield,
                    container = GuardianTheme.materialColors.surfaceContainerLow,
                    content = GuardianTheme.materialColors.onSurface,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // Said plainly rather than hidden. Derived turns are accurate about
                // distance and direction and have no street names, and a user who knows
                // that trusts the ones they do get. The arrival promise replaces it once
                // made, because that is the more useful reminder while walking.
                text = arrivalContactName
                    ?.let { "I'll text $it when you arrive." }
                    ?: "Turns worked out from the route shape — no street names.",
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEndWalk) { Text("End walk") }
        }
    }
}

private fun turnIcon(turn: WalkDirections.Turn?): ImageVector = when (turn) {
    null, WalkDirections.Turn.Arrive -> GuardianIcons.MapPin
    WalkDirections.Turn.Start, WalkDirections.Turn.Straight -> GuardianIcons.ArrowUp
    WalkDirections.Turn.SlightLeft, WalkDirections.Turn.Left, WalkDirections.Turn.SharpLeft,
    WalkDirections.Turn.SlightRight, WalkDirections.Turn.Right, WalkDirections.Turn.SharpRight,
    -> GuardianIcons.ArrowUp
}

/**
 * One arrow, rotated, rather than eight hand-drawn glyphs.
 *
 * The angles match the classifier's bands, so a "bear left" arrow really is shallower
 * than a "sharp left" one — the picture and the words cannot disagree.
 */
private fun turnRotation(turn: WalkDirections.Turn?): Float = when (turn) {
    WalkDirections.Turn.SlightLeft -> -40f
    WalkDirections.Turn.Left -> -90f
    WalkDirections.Turn.SharpLeft -> -135f
    WalkDirections.Turn.SlightRight -> 40f
    WalkDirections.Turn.Right -> 90f
    WalkDirections.Turn.SharpRight -> 135f
    else -> 0f
}
