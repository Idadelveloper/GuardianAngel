package com.example.guardianangel.ui.trail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.SessionTrail
import com.example.guardianangel.domain.model.TrailIncident
import com.example.guardianangel.domain.model.TrailPoint
import com.example.guardianangel.domain.model.TrailSeverity
import com.example.guardianangel.ui.theme.GuardianTheme
import kotlin.math.abs
import kotlin.math.max

/**
 * Draws a session's path without a map.
 *
 * ## Why this is not a placeholder
 *
 * It is what the app shows when no Maps API key is configured, which is the default
 * state of this project — with the placeholder key the SDK renders an empty box, and an
 * empty box in every Activity row reads as a bug.
 *
 * It is also, for a short walk, arguably the better drawing. A map thumbnail at this
 * size is mostly street names the user does not need; this shows the two things that
 * matter — the shape of where she went, and where something happened — with the path
 * coloured by the safety score along it.
 *
 * Coordinates are projected with a plain equirectangular fit to the bounding box, with
 * longitude scaled by latitude so a walk does not look stretched. Over the few hundred
 * metres a session covers, the error against a real projection is invisible.
 */
@Composable
fun TrailCanvas(
    trail: SessionTrail,
    modifier: Modifier = Modifier,
    incidents: List<TrailIncident> = trail.incidents,
    strokeWidth: Dp = 4.dp,
    markerRadius: Dp = 7.dp,
    highlightedIncidentId: String? = null,
    /**
     * Space kept clear at the bottom, for badges drawn over the canvas.
     *
     * Without it the start marker ended up underneath the distance chip — the path is
     * fitted to the full box, and the badges sit in the bottom-left corner where a walk
     * very often begins.
     */
    bottomInset: Dp = 0.dp,
) {
    val colors = GuardianTheme.colors
    val surface = GuardianTheme.materialColors.surfaceContainerLow
    val safeColor = colors.safeContainer
    val midColor = colors.peachCream
    val riskColor = colors.duress
    val pathBase = GuardianTheme.materialColors.onSurfaceVariant
    val startColor = GuardianTheme.materialColors.primary

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(listOf(surface, GuardianTheme.materialColors.surface))
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val projected = trail.points.project(
                size = size,
                inset = markerRadius.toPx() * 1.6f,
                bottomInset = bottomInset.toPx(),
            )
            if (projected.isEmpty()) return@Canvas

            drawGrid(pathBase.copy(alpha = 0.10f))

            if (projected.size >= 2) {
                drawTrail(
                    points = projected,
                    scores = trail.points.map { it.safetyScore },
                    width = strokeWidth.toPx(),
                    safeColor = safeColor,
                    midColor = midColor,
                    riskColor = riskColor,
                    fallback = pathBase,
                )
            }

            // Start and end, so the direction of travel is readable without an arrow.
            projected.firstOrNull()?.let { drawStart(it, startColor, markerRadius.toPx()) }
            if (projected.size >= 2) {
                projected.lastOrNull()?.let { drawEnd(it, startColor, markerRadius.toPx()) }
            }

            incidents.forEach { incident ->
                val at = trail.points.positionOf(incident, projected) ?: return@forEach
                drawIncident(
                    center = at,
                    radius = markerRadius.toPx(),
                    color = when (incident.severity) {
                        TrailSeverity.Critical -> riskColor
                        TrailSeverity.High -> riskColor.copy(alpha = 0.85f)
                        TrailSeverity.Elevated -> midColor
                    },
                    ring = surface,
                    emphasised = incident.id == highlightedIncidentId,
                )
            }
        }
    }
}

/** A faint grid, so an empty area still reads as a surface rather than a blank card. */
private fun DrawScope.drawGrid(color: Color) {
    val step = size.minDimension / 5f
    var x = step
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = step
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

/**
 * Draws the path, coloured segment by segment by the score at that moment.
 *
 * Per segment rather than one colour for the whole walk: the interesting thing about a
 * route is usually the stretch where it got worse, and a single average hides exactly
 * that.
 */
private fun DrawScope.drawTrail(
    points: List<Offset>,
    scores: List<Int?>,
    width: Float,
    safeColor: Color,
    midColor: Color,
    riskColor: Color,
    fallback: Color,
) {
    // A soft shadow under the line keeps it legible over the grid.
    val glow = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(glow, fallback.copy(alpha = 0.18f), style = Stroke(width * 2.2f, cap = StrokeCap.Round))

    points.zipWithNext().forEachIndexed { index, (from, to) ->
        val score = scores.getOrNull(index + 1) ?: scores.getOrNull(index)
        drawLine(
            color = when {
                score == null -> fallback
                score >= 75 -> safeColor
                score >= 50 -> midColor
                else -> riskColor
            },
            start = from,
            end = to,
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawStart(center: Offset, color: Color, radius: Float) {
    drawCircle(color.copy(alpha = 0.25f), radius * 1.9f, center)
    drawCircle(color, radius * 0.75f, center)
}

/** A ring rather than a disc, so the end of the walk is distinguishable from its start. */
private fun DrawScope.drawEnd(center: Offset, color: Color, radius: Float) {
    drawCircle(color.copy(alpha = 0.18f), radius * 1.9f, center)
    drawCircle(color, radius * 0.85f, center, style = Stroke(width = radius * 0.45f))
}

private fun DrawScope.drawIncident(
    center: Offset,
    radius: Float,
    color: Color,
    ring: Color,
    emphasised: Boolean,
) {
    val scale = if (emphasised) 1.35f else 1f
    drawCircle(color.copy(alpha = 0.22f), radius * 2.1f * scale, center)
    drawCircle(ring, radius * 1.05f * scale, center)
    drawCircle(color, radius * 0.72f * scale, center)
}

/**
 * Fits the points to the canvas.
 *
 * Longitude is scaled by `cos(latitude)` so a mile east is drawn the same length as a
 * mile north; without it, walks look stretched sideways at temperate latitudes. The
 * aspect ratio of the real path is preserved and centred, so a straight walk reads as
 * straight rather than being distorted to fill the box.
 */
private fun List<TrailPoint>.project(
    size: Size,
    inset: Float,
    bottomInset: Float = 0f,
): List<Offset> {
    if (isEmpty() || size.minDimension <= 0f) return emptyList()

    val latitudes = map { it.latitude }
    val longitudes = map { it.longitude }
    val midLatitude = (latitudes.max() + latitudes.min()) / 2
    val lonScale = kotlin.math.cos(Math.toRadians(midLatitude)).coerceAtLeast(0.05)

    val xs = longitudes.map { it * lonScale }
    val spanX = max(xs.max() - xs.min(), MIN_SPAN)
    val spanY = max(latitudes.max() - latitudes.min(), MIN_SPAN)

    val usableW = (size.width - inset * 2).coerceAtLeast(1f)
    val usableH = (size.height - inset * 2 - bottomInset).coerceAtLeast(1f)
    // One scale for both axes keeps the path's true shape.
    val scale = minOf(usableW / spanX, usableH / spanY)

    val drawnW = (spanX * scale).toFloat()
    val drawnH = (spanY * scale).toFloat()
    val offsetX = inset + (usableW - drawnW) / 2
    val offsetY = inset + (usableH - drawnH) / 2

    return mapIndexed { index, _ ->
        val nx = (xs[index] - xs.min()) * scale
        val ny = (latitudes[index] - latitudes.min()) * scale
        Offset(
            x = offsetX + nx.toFloat(),
            // Latitude increases north, screen y increases down.
            y = offsetY + drawnH - ny.toFloat(),
        )
    }
}

/** Finds the drawn position of the breadcrumb an incident was pinned to. */
private fun List<TrailPoint>.positionOf(
    incident: TrailIncident,
    projected: List<Offset>,
): Offset? {
    if (projected.isEmpty()) return null
    val index = indices.minByOrNull { abs(this[it].atEpochMillis - incident.atEpochMillis) }
        ?: return null
    return projected.getOrNull(index)
}

/** Degrees. Stops a stationary session collapsing to a single pixel. */
private const val MIN_SPAN = 0.00025
