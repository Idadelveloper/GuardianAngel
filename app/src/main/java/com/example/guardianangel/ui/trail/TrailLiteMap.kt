package com.example.guardianangel.ui.trail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.example.guardianangel.domain.model.SessionTrail
import com.example.guardianangel.ui.theme.GuardianTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * A non-interactive map thumbnail of one session's path.
 *
 * **Lite mode.** The Maps SDK renders a lite-mode map as a bitmap rather than a live GL
 * surface, which is what makes it safe to put one in every row of a scrolling list —
 * that is the use case Google documents it for. Gestures, Street View and tile overlays
 * are unavailable in lite mode, none of which a thumbnail needs.
 *
 * Every UI control is switched off: at this size a zoom button is unhittable, and the
 * row already has its own tap target.
 */
@Composable
fun TrailLiteMap(
    trail: SessionTrail,
    modifier: Modifier = Modifier,
) {
    val points = remember(trail) { trail.points.map { LatLng(it.latitude, it.longitude) } }
    if (points.isEmpty()) return

    val pathColor = GuardianTheme.materialColors.primary
    val camera = rememberCameraPositionState()

    // Framed to the whole walk rather than centred on one end, so the thumbnail shows
    // the shape of the route. Lite mode moves the camera instantly; there is no
    // animation to wait for.
    androidx.compose.runtime.LaunchedEffect(points) {
        if (points.size == 1) {
            camera.move(CameraUpdateFactory.newLatLngZoom(points.first(), SINGLE_POINT_ZOOM))
        } else {
            val bounds = LatLngBounds.builder().apply { points.forEach(::include) }.build()
            runCatching { camera.move(CameraUpdateFactory.newLatLngBounds(bounds, BOUNDS_PADDING)) }
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
        properties = MapProperties(isMyLocationEnabled = false),
        uiSettings = MapUiSettings(
            compassEnabled = false,
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
            scrollGesturesEnabled = false,
            zoomGesturesEnabled = false,
            tiltGesturesEnabled = false,
            rotationGesturesEnabled = false,
        ),
    ) {
        if (points.size >= 2) {
            Polyline(
                points = points,
                color = pathColor,
                width = 10f,
            )
        }
    }
}

/** Zoom used when a session never moved, so one point still shows its surroundings. */
private const val SINGLE_POINT_ZOOM = 16f

/** Pixels of breathing room around the bounding box. */
private const val BOUNDS_PADDING = 48
