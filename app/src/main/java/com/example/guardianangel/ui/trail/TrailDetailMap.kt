package com.example.guardianangel.ui.trail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.SessionTrail
import com.example.guardianangel.domain.model.TrailIncident
import com.example.guardianangel.domain.model.TrailIncidentKind
import com.example.guardianangel.domain.model.TrailSeverity
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerInfoWindow
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The full-size breadcrumb map for one session.
 *
 * ## Why its gestures are locked
 *
 * The map sits inside a vertically scrolling screen. With panning enabled, a drag meant
 * for the page moved the map instead — and once the camera had wandered off the path
 * there was no way back to it, leaving an empty street view where the walk should be.
 * The camera is therefore fitted to the route and stays there; markers remain tappable,
 * and anyone who wants to explore properly gets a button into the Maps app.
 *
 * Incident markers carry a custom info window — `MarkerInfoWindow` renders Compose
 * content into the bubble, so the popup uses the app's own type and colour rather than
 * the SDK's default white box.
 *
 * Marker colour follows severity, and the hue values are the SDK's own constants so they
 * stay legible against map tiles in both light and dark map styles.
 */
@Composable
fun TrailDetailMap(
    trail: SessionTrail,
    modifier: Modifier = Modifier,
    onIncidentSelected: (TrailIncident) -> Unit = {},
) {
    val points = remember(trail) { trail.points.map { LatLng(it.latitude, it.longitude) } }
    if (points.isEmpty()) return

    val pathColor = GuardianTheme.materialColors.primary
    val camera = rememberCameraPositionState()

    androidx.compose.runtime.LaunchedEffect(points) {
        if (points.size == 1) {
            camera.move(CameraUpdateFactory.newLatLngZoom(points.first(), 16.5f))
        } else {
            val bounds = LatLngBounds.builder().apply { points.forEach(::include) }.build()
            runCatching { camera.move(CameraUpdateFactory.newLatLngBounds(bounds, 96)) }
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        properties = MapProperties(isMyLocationEnabled = false),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
            compassEnabled = false,
            scrollGesturesEnabled = false,
            scrollGesturesEnabledDuringRotateOrZoom = false,
            zoomGesturesEnabled = false,
            tiltGesturesEnabled = false,
            rotationGesturesEnabled = false,
        ),
    ) {
        if (points.size >= 2) {
            Polyline(points = points, color = pathColor, width = 14f)
        }

        trail.incidents.forEach { incident ->
            MarkerInfoWindow(
                state = rememberMarker(incident.latitude, incident.longitude),
                icon = BitmapDescriptorFactory.defaultMarker(incident.markerHue()),
                title = incident.title,
                // Selecting the row below is the primary effect, not the bubble.
                //
                // The info window is a Compose layer the SDK rasterises, and it does not
                // always draw inside a clipped, non-interactive map. The list underneath
                // is also the more discoverable and more accessible surface — a screen
                // reader can reach it, a map pin it cannot. Returning false lets the
                // default behaviour run as well, so the bubble still shows where it can.
                onClick = {
                    onIncidentSelected(incident)
                    false
                },
                onInfoWindowClick = { onIncidentSelected(incident) },
            ) {
                IncidentBubble(incident)
            }
        }
    }
}

/**
 * What the popup shows when a pin is tapped.
 *
 * Time first, because the question someone asks of a pin is "when was this". The detail
 * is the captured text verbatim — this may end up being read as evidence, and a summary
 * written by the app would be a layer of interpretation between the reader and the
 * recording.
 */
@Composable
private fun IncidentBubble(incident: TrailIncident) {
    Column(
        modifier = Modifier
            .widthIn(max = 260.dp)
            .clip(GuardianTheme.shapes.md)
            .background(GuardianTheme.materialColors.surfaceContainerLowest)
            .padding(GuardianTheme.spacing.md),
    ) {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = incident.kind.icon(),
                contentDescription = null,
                tint = incident.severityColor(),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = incident.title,
                style = GuardianTheme.type.labelMd,
                color = GuardianTheme.materialColors.onSurface,
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = CLOCK.format(Date(incident.atEpochMillis)),
            style = GuardianTheme.type.labelSm,
            color = GuardianTheme.materialColors.onSurfaceVariant,
        )

        Spacer(Modifier.height(GuardianTheme.spacing.xs))
        Text(
            text = incident.detail,
            style = GuardianTheme.type.bodySm,
            color = GuardianTheme.materialColors.onSurface,
        )

        incident.safetyScore?.let { score ->
            Spacer(Modifier.height(GuardianTheme.spacing.xs))
            Text(
                text = "Safety score here: $score",
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun rememberMarker(latitude: Double, longitude: Double): MarkerState =
    remember(latitude, longitude) { MarkerState(position = LatLng(latitude, longitude)) }

/** The SDK's own hues, so pins stay legible against map tiles. */
private fun TrailIncident.markerHue(): Float = when (severity) {
    TrailSeverity.Critical -> BitmapDescriptorFactory.HUE_RED
    TrailSeverity.High -> BitmapDescriptorFactory.HUE_ORANGE
    TrailSeverity.Elevated -> BitmapDescriptorFactory.HUE_YELLOW
}

@Composable
private fun TrailIncident.severityColor(): Color = when (severity) {
    TrailSeverity.Critical, TrailSeverity.High -> GuardianTheme.colors.duress
    TrailSeverity.Elevated -> GuardianTheme.materialColors.onSurfaceVariant
}

internal fun TrailIncidentKind.icon() = when (this) {
    TrailIncidentKind.Codeword -> GuardianIcons.CrisisAlert
    TrailIncidentKind.DangerSound -> GuardianIcons.Waveform
    TrailIncidentKind.FlaggedSpeech -> GuardianIcons.Warning
    TrailIncidentKind.UnknownVoice -> GuardianIcons.Users
    TrailIncidentKind.ScoreDrop -> GuardianIcons.TrendingDown
}

private val CLOCK = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
