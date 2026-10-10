package com.example.guardianangel.ui.trail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.SessionTrail
import com.example.guardianangel.domain.model.TrailIncident
import com.example.guardianangel.domain.model.TrailSeverity
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.home.components.SectionHeader
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Where this session went, and what happened on the way.
 *
 * Shows the path — a real interactive map when a Maps key is configured, the app's own
 * drawing when not — with the flagged moments listed underneath.
 *
 * ## Why the list is there even when the map works
 *
 * Map pins are only discoverable by tapping them, and the one reading this may be doing
 * it in a hurry or handing the phone to someone else. The list says up front how many
 * moments were flagged and what they were; selecting one highlights it on the drawing,
 * so the two halves stay in step. It also means the feature is fully usable before
 * anyone has set up a Maps key.
 */
@Composable
fun SessionTrailSection(
    trail: SessionTrail,
    modifier: Modifier = Modifier,
) {
    if (trail.points.isEmpty()) {
        NoTrailCard(modifier)
        return
    }

    var selected by remember(trail.sessionId) { mutableStateOf<String?>(null) }
    val realMap = useRealMap()
    val context = LocalContext.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm)) {
        SectionHeader(title = "Where this happened", icon = GuardianIcons.MapPin) {
            Text(
                text = trail.distanceLabel(),
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (realMap) 280.dp else 220.dp)
                .clip(GuardianTheme.shapes.lg)
                .border(1.dp, GuardianTheme.colors.borderDefault, GuardianTheme.shapes.lg),
        ) {
            if (realMap) {
                TrailDetailMap(
                    trail = trail,
                    onIncidentSelected = { selected = it.id },
                )
            } else {
                TrailCanvas(
                    trail = trail,
                    highlightedIncidentId = selected,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrailLegend(trail, modifier = Modifier.weight(1f))
            if (realMap) {
                // The embedded map is deliberately fixed to the route, so this is the
                // way out for anyone who wants to pan around properly.
                Text(
                    text = "Open in Maps",
                    style = GuardianTheme.type.labelSm,
                    color = GuardianTheme.materialColors.primary,
                    modifier = Modifier
                        .clip(GuardianTheme.shapes.sm)
                        .clickable(role = Role.Button) { context.openTrailInMaps(trail) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }

        if (trail.incidents.isEmpty()) {
            Text(
                text = "Nothing along this route was flagged.",
                style = GuardianTheme.type.bodySm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        } else {
            trail.incidents.forEach { incident ->
                IncidentRow(
                    incident = incident,
                    expanded = selected == incident.id,
                    onToggle = {
                        selected = if (selected == incident.id) null else incident.id
                    },
                )
            }
        }
    }
}

/** Start, end and how many pins — the key to reading the drawing above it. */
@Composable
private fun TrailLegend(trail: SessionTrail, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(filled = true, label = if (trail.hasPath) "Start" else "Recorded here")
        if (trail.hasPath) LegendDot(filled = false, label = "End")
        trail.lowestScore?.let {
            Text(
                text = "Lowest score $it",
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendDot(filled: Boolean, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(
                    if (filled) {
                        GuardianTheme.materialColors.primary
                    } else {
                        GuardianTheme.materialColors.surfaceContainerHighest
                    }
                )
                .border(
                    width = if (filled) 0.dp else 2.dp,
                    color = GuardianTheme.materialColors.primary,
                    shape = androidx.compose.foundation.shape.CircleShape,
                ),
        )
        Text(
            text = label,
            style = GuardianTheme.type.labelSm,
            color = GuardianTheme.materialColors.onSurfaceVariant,
        )
    }
}

/**
 * One flagged moment.
 *
 * Collapsed to a time and a title, expanding to the captured detail. Collapsed by
 * default because a session can hold several and a wall of quoted speech is harder to
 * scan than a list of moments.
 */
@Composable
private fun IncidentRow(
    incident: TrailIncident,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val accent = when (incident.severity) {
        TrailSeverity.Critical -> GuardianTheme.colors.duress
        TrailSeverity.High -> GuardianTheme.colors.accentWarm
        TrailSeverity.Elevated -> GuardianTheme.colors.peachCream
    }

    GuardianCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GuardianTheme.shapes.lg)
            .clickable(role = Role.Button, onClick = onToggle),
        contentPadding = GuardianTheme.spacing.md,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(GuardianTheme.shapes.sm)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = incident.kind.icon(),
                    contentDescription = null,
                    tint = GuardianTheme.materialColors.onSurface,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.size(GuardianTheme.spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = incident.title,
                    style = GuardianTheme.type.labelMd,
                    color = GuardianTheme.materialColors.onSurface,
                )
                Text(
                    text = CLOCK.format(Date(incident.atEpochMillis)),
                    style = GuardianTheme.type.labelSm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = GuardianIcons.ChevronRight,
                contentDescription = if (expanded) "Collapse" else "Show what happened",
                tint = GuardianTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(GuardianTheme.spacing.sm))
                Text(
                    text = incident.detail,
                    style = GuardianTheme.type.bodySm,
                    color = GuardianTheme.materialColors.onSurface,
                )
                incident.safetyScore?.let { score ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Safety score here: $score",
                        style = GuardianTheme.type.labelSm,
                        color = GuardianTheme.materialColors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoTrailCard(modifier: Modifier = Modifier) {
    GuardianCard(modifier = modifier, contentPadding = GuardianTheme.spacing.lg) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
        ) {
            Icon(
                imageVector = GuardianIcons.MapPin,
                contentDescription = null,
                tint = GuardianTheme.colors.iconMuted,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = "No path recorded",
                    style = GuardianTheme.type.labelLg,
                    color = GuardianTheme.materialColors.onSurface,
                )
                Text(
                    text = "Location was off, or no fix was available while this ran.",
                    style = GuardianTheme.type.bodySm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
            }
        }
    }
}

private val CLOCK = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

/**
 * Opens the walk in the Maps app.
 *
 * Centred on the most serious thing that happened rather than the start, because that is
 * what someone opening a wider map is looking for. A `geo:` URI cannot carry a path, so
 * the label names the session and the embedded map keeps the route.
 */
private fun Context.openTrailInMaps(trail: SessionTrail) {
    val focus = trail.incidents.maxByOrNull { it.severity.ordinal }
        ?: trail.points.firstOrNull()?.let { point ->
            TrailIncident(
                id = "start",
                atEpochMillis = point.atEpochMillis,
                latitude = point.latitude,
                longitude = point.longitude,
                kind = com.example.guardianangel.domain.model.TrailIncidentKind.ScoreDrop,
                title = "Session start",
                detail = "",
            )
        }
        ?: return

    val label = Uri.encode(focus.title)
    val uri = "geo:${focus.latitude},${focus.longitude}?q=${focus.latitude},${focus.longitude}($label)"
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
}
