package com.example.guardianangel.ui.map

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.guardianangel.domain.model.SafeRoute
import com.example.guardianangel.ui.theme.GuardianTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * A stylised map canvas.
 *
 * Guardian Angel has no Maps SDK key yet, and shipping a half-wired map view would be
 * worse than an honest abstraction. This draws the things the screen actually needs to
 * communicate — the safe corridor, the unlit shortcut, the home geofence and the warning
 * markers — on a soft street-grid backdrop.
 *
 * Routes arrive as [SafeRoute.pathNormalised], points in a 0..1 square, so swapping in a
 * real map later means replacing this composable and nothing else.
 */
@Composable
fun RouteCanvas(
    routes: List<SafeRoute>,
    modifier: Modifier = Modifier,
    showGeofence: Boolean = false,
    highlightedRouteId: String? = null,
) {
    val transition = rememberInfiniteTransition(label = "routeFlow")
    // A slow dash crawl implies direction of travel without an animated marker.
    val dashPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "dashPhase",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "geofencePulse",
    )

    val canvasColor = GuardianTheme.materialColors.surfaceContainerLow
    val gridColor = GuardianTheme.colors.borderDefault
    val parkColor = GuardianTheme.materialColors.surfaceContainerHigh
    val safeColor = GuardianTheme.materialColors.primary
    val warnColor = GuardianTheme.colors.duress
    val havenColor = GuardianTheme.colors.accentSoft

    Canvas(modifier = modifier) {
        drawRect(color = canvasColor)
        drawStreetGrid(gridColor, parkColor)

        routes.forEach { route ->
            val points = route.pathNormalised.map { (x, y) ->
                Offset(x * size.width, y * size.height)
            }
            if (points.size < 2) return@forEach

            val isHighlighted = highlightedRouteId == null || route.id == highlightedRouteId
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }

            if (route.warnings.isEmpty()) {
                // Safe corridor: a wide soft glow under a solid line.
                drawPath(
                    path = path,
                    color = safeColor.copy(alpha = if (isHighlighted) 0.22f else 0.08f),
                    style = Stroke(width = 26f, cap = StrokeCap.Round),
                )
                drawPath(
                    path = path,
                    color = safeColor.copy(alpha = if (isHighlighted) 1f else 0.35f),
                    style = Stroke(width = 8f, cap = StrokeCap.Round),
                )
            } else {
                // Unlit shortcut: dashed, so it reads as "possible but not advised".
                drawPath(
                    path = path,
                    color = warnColor.copy(alpha = if (isHighlighted) 0.75f else 0.3f),
                    style = Stroke(
                        width = 6f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(18f, 14f),
                            phase = -dashPhase,
                        ),
                    ),
                )
                route.warnings.forEach { warning ->
                    val at = pointAlong(points, warning.atFraction)
                    drawCircle(color = warnColor.copy(alpha = 0.18f), radius = 26f, center = at)
                    drawCircle(color = warnColor, radius = 7f, center = at)
                }
            }

            // Start and end caps.
            drawCircle(color = havenColor, radius = 13f, center = points.first())
            drawCircle(color = safeColor, radius = 6f, center = points.first())
            drawCircle(color = safeColor, radius = 11f, center = points.last())
            drawCircle(color = Color.White, radius = 5f, center = points.last())
        }

        if (showGeofence) {
            val centre = Offset(size.width * 0.5f, size.height * 0.52f)
            val radius = size.minDimension * 0.26f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(havenColor.copy(alpha = 0.30f * pulse), Color.Transparent),
                    center = centre,
                    radius = radius * 1.25f,
                ),
                radius = radius * 1.25f,
                center = centre,
            )
            drawCircle(
                color = havenColor.copy(alpha = 0.9f),
                radius = radius,
                center = centre,
                style = Stroke(width = 3f),
            )
            drawCircle(color = safeColor, radius = 10f, center = centre)
        }
    }
}

/** A soft, abstract street grid so the corridors have something to sit on. */
private fun DrawScope.drawStreetGrid(gridColor: Color, parkColor: Color) {
    // Two organic green-space blobs.
    drawCircle(parkColor, radius = size.minDimension * 0.17f, center = Offset(size.width * 0.78f, size.height * 0.68f))
    drawCircle(parkColor, radius = size.minDimension * 0.11f, center = Offset(size.width * 0.18f, size.height * 0.26f))

    val spacing = size.minDimension / 7f
    var x = spacing / 2f
    while (x < size.width) {
        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
        x += spacing
    }
    var y = spacing / 2f
    while (y < size.height) {
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
        y += spacing
    }
    // A couple of diagonals so it does not read as graph paper.
    drawLine(gridColor, Offset(0f, size.height * 0.82f), Offset(size.width, size.height * 0.22f), strokeWidth = 3f)
    drawLine(gridColor, Offset(size.width * 0.1f, 0f), Offset(size.width * 0.62f, size.height), strokeWidth = 3f)
}

/** Point at [fraction] along a polyline, used to place warning markers. */
private fun pointAlong(points: List<Offset>, fraction: Float): Offset {
    if (points.size < 2) return points.firstOrNull() ?: Offset.Zero
    val target = (points.size - 1) * fraction.coerceIn(0f, 1f)
    val index = target.toInt().coerceAtMost(points.size - 2)
    val t = target - index
    val a = points[index]
    val b = points[index + 1]
    return Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
}
