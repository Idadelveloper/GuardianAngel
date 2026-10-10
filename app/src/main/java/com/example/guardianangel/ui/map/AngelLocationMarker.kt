package com.example.guardianangel.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.ui.mascot.AngelMood
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import kotlin.math.abs

/**
 * Angel, standing where the user is standing.
 *
 * Two things make this different from the other pins on the map:
 *
 * **She moves rather than reappears.** The obvious implementation — a new [MarkerState]
 * per coordinate — makes Angel vanish and rematerialise a few metres away on every GPS
 * tick, which on a walking route is roughly once a second. One state object lives for the
 * life of the screen and its position is interpolated toward each new fix, so she walks
 * alongside the user. A large jump (a first fix, a tunnel, a provider switch) is snapped
 * instead of animated, because gliding across half a city is a lie about where she was in
 * between.
 *
 * **She answers when tapped.** The pulse is the acknowledgement — a tap that changes
 * nothing visible reads as a dead control — and [onTap] is what puts the "you're home"
 * callout on screen.
 */
@Composable
@GoogleMapComposable
fun AngelLocationMarker(
    location: GeoPoint,
    mood: AngelMood,
    snippet: String,
    onTap: () -> Unit,
    /** Bumped by the caller on each tap, so a second tap re-runs the pulse. */
    pulseKey: Int,
) {
    val markerState = remember { MarkerState(position = LatLng(location.latitude, location.longitude)) }

    LaunchedEffect(location) {
        val from = markerState.position
        val to = LatLng(location.latitude, location.longitude)
        if (isFarJump(from, to)) {
            markerState.position = to
            return@LaunchedEffect
        }
        val startNanos = withFrameNanos { it }
        var fraction = 0f
        while (fraction < 1f) {
            withFrameNanos { frameNanos ->
                fraction = ((frameNanos - startNanos) / 1_000_000f / GLIDE_MILLIS).coerceIn(0f, 1f)
                val eased = FastOutSlowInEasing.transform(fraction)
                markerState.position = LatLng(
                    from.latitude + (to.latitude - from.latitude) * eased,
                    from.longitude + (to.longitude - from.longitude) * eased,
                )
            }
        }
    }

    // Quantised inside the renderer, so this continuous value costs a bounded number of
    // bitmaps rather than one per frame.
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(pulseKey) {
        if (pulseKey == 0) return@LaunchedEffect
        pulse.animateTo(1.35f, tween(durationMillis = 140, easing = FastOutSlowInEasing))
        pulse.animateTo(1f, tween(durationMillis = 260, easing = FastOutSlowInEasing))
    }
    val scale by pulse.asState()

    Marker(
        state = markerState,
        icon = AngelMarkerRenderer.getMarkerBitmapDescriptor(mood, scale),
        title = "You",
        snippet = snippet,
        // Angel's feet, not the centre of her aura, mark the spot.
        anchor = ANGEL_ANCHOR,
        zIndex = 2f,
        onClick = {
            onTap()
            // Consumed: the stock info window would cover the callout with a second,
            // worse copy of the same sentence.
            true
        },
    )
}

/** How long Angel takes to walk from her last drawn position to the new fix. */
private const val GLIDE_MILLIS = 650f

/**
 * Degrees of latitude or longitude beyond which a move is snapped, not animated.
 *
 * About 180 m at Berkeley's latitude — comfortably more than a walking pace between
 * fixes, and far less than the teleport a first fix or a provider switch produces.
 */
private const val FAR_JUMP_DEGREES = 0.0016

private fun isFarJump(from: LatLng, to: LatLng): Boolean =
    abs(from.latitude - to.latitude) > FAR_JUMP_DEGREES ||
        abs(from.longitude - to.longitude) > FAR_JUMP_DEGREES

private val ANGEL_ANCHOR = androidx.compose.ui.geometry.Offset(0.5f, 0.78f)
