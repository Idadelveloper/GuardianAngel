package com.example.guardianangel.ui.mascot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Angel — the app's guardian mascot.
 *
 * Drawn entirely with Compose `Canvas` primitives rather than shipped as five SVGs or
 * Lottie files. That buys three things the asset route could not: every tier is one
 * component so the geometry can never drift between moods, moods can **cross-fade**
 * through intermediate values as the safety score moves, and the whole thing costs a few
 * kilobytes of code instead of five animation payloads.
 *
 * Angel is decorative by default — the surrounding card always states the same thing in
 * words. Pass [contentDescription] only where Angel is the sole carrier of the message.
 */
@Composable
fun AngelMascot(
    mood: AngelMood,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    contentDescription: String? = null,
) {
    val style = remember(mood) { styleFor(mood) }

    // Previews and screenshot tests get a still frame; infinite transitions never settle,
    // which would otherwise leave the preview rendering forever.
    val isStatic = LocalInspectionMode.current

    val transition = rememberInfiniteTransition(label = "angel")

    @Composable
    fun pulse(periodMillis: Int, label: String) =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(periodMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = label,
        )

    val bodyPhase by pulse(style.bodyPeriodMillis, "body")
    val haloPhase by pulse(style.haloPeriodMillis, "halo")
    val auraPhase by pulse(style.auraPeriodMillis, "aura")
    val talismanPhase by pulse(style.talismanPeriodMillis, "talisman")
    val wingPhase by pulse(1800, "wing")

    // Blinks are discrete events rather than a continuous curve, so they are driven by a
    // small state machine: open for a beat, then a fast close/open.
    val blink = remember { Animatable(1f) }
    val blinkInterval = when (style.eyes) {
        EyeStyle.Anxious -> 900L
        EyeStyle.Alarmed -> 2600L
        else -> 4500L
    }
    LaunchedEffect(mood, isStatic) {
        if (isStatic || style.eyes == EyeStyle.Alarmed) return@LaunchedEffect
        val random = Random(mood.ordinal)
        while (true) {
            kotlinx.coroutines.delay(blinkInterval + random.nextLong(0, 900))
            blink.animateTo(0.1f, tween(90, easing = LinearEasing))
            blink.animateTo(1f, tween(110, easing = LinearEasing))
        }
    }

    // Always driven, then ignored unless Angel is scanning: a conditional
    // `animateFloat` would change the composable call count between moods.
    val scanDriver by pulse(4000, "scan")
    val scanPhase = if (style.eyes == EyeStyle.Scanning) scanDriver else 0f

    val frame = remember(
        style, bodyPhase, haloPhase, auraPhase, talismanPhase, wingPhase, blink.value, scanPhase,
    ) {
        AngelFrame(
            style = style,
            bodyPhase = if (isStatic) 0.25f else bodyPhase,
            haloPhase = if (isStatic) 0.5f else haloPhase,
            auraPhase = if (isStatic) 0.4f else auraPhase,
            talismanPhase = if (isStatic) 0.5f else talismanPhase,
            wingPhase = if (isStatic) 0.5f else wingPhase,
            eyeOpen = if (isStatic) 1f else blink.value,
            scanPhase = if (isStatic) 0.5f else scanPhase,
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
    ) {
        Canvas(modifier = Modifier.size(size)) { drawAngel(frame) }
    }
}

/** One resolved animation frame — all phases are 0..1. */
internal data class AngelFrame(
    val style: AngelStyle,
    val bodyPhase: Float,
    val haloPhase: Float,
    val auraPhase: Float,
    val talismanPhase: Float,
    val wingPhase: Float,
    val eyeOpen: Float,
    val scanPhase: Float,
)

// --- Drawing -------------------------------------------------------------------------

private fun DrawScope.drawAngel(frame: AngelFrame) {
    val s = frame.style
    val unit = size.minDimension
    val centre = Offset(size.width / 2f, size.height / 2f)

    // A single sine drives the bob so body, halo and wings stay in phase.
    val wave = sin(frame.bodyPhase * 2f * PI.toFloat())
    val bob = wave * unit * s.bobAmount
    val tremble = if (s.trembleAmount > 0f) {
        sin(frame.bodyPhase * 2f * PI.toFloat() * 7f) * unit * s.trembleAmount
    } else {
        0f
    }

    val headRadius = unit * 0.21f
    val headCentre = Offset(centre.x + tremble, centre.y + bob + unit * 0.03f)

    drawAura(frame, headCentre, unit)
    drawWings(frame, headCentre, headRadius, unit)
    drawHalo(frame, headCentre, headRadius, unit)
    drawHead(headCentre, headRadius)
    drawFace(frame, headCentre, headRadius)
    drawTalisman(frame, headCentre, headRadius, unit)
    if (s.sparkles) drawSparkles(frame, centre, unit)
    if (s.sweatDrops > 0) drawSweat(frame, headCentre, headRadius)
}

/** Concentric rings radiating outward: acoustic listening, radar pings, or shockwaves. */
private fun DrawScope.drawAura(frame: AngelFrame, centre: Offset, unit: Float) {
    val s = frame.style
    if (s.auraIntensity == 0) return
    val ringCount = when (s.auraIntensity) {
        1 -> 2
        2 -> 3
        else -> 4
    }
    val maxRadius = unit * when (s.auraIntensity) {
        1 -> 0.42f
        2 -> 0.46f
        else -> 0.52f
    }
    val startRadius = unit * 0.22f
    repeat(ringCount) { i ->
        // Stagger the rings so they chase each other outward.
        val phase = (frame.auraPhase + i.toFloat() / ringCount) % 1f
        val radius = startRadius + (maxRadius - startRadius) * phase
        val alpha = (1f - phase) * 0.6f
        drawCircle(
            color = s.auraColor.copy(alpha = alpha),
            radius = radius,
            center = centre,
            style = Stroke(width = unit * 0.012f),
        )
    }
}

/**
 * Two soft feathered wings behind the head.
 *
 * Built from three overlapping lobes per side rather than a single oval — one oval at
 * head height reads as an ear, whereas stacked lobes sitting low and wide read as the
 * cloud-like feathered puff the emblem uses.
 */
private fun DrawScope.drawWings(
    frame: AngelFrame,
    headCentre: Offset,
    headRadius: Float,
    unit: Float,
) {
    val s = frame.style
    // Alternating flutter, damped right down once the wings are held in a shield.
    val flutter = sin(frame.wingPhase * 2f * PI.toFloat()) * if (s.wingsShielding) 2f else 5f

    // Shielding pulls the wings up and in around the shoulders; otherwise they sweep out.
    val lift = if (s.wingsShielding) -headRadius * 0.30f else headRadius * 0.10f
    val spread = if (s.wingsShielding) headRadius * 0.62f else headRadius * 0.82f

    // Lobe offsets and radii, as fractions of the head radius, measured from the anchor.
    val lobes = listOf(
        Triple(0.42f, -0.06f, 0.56f),
        Triple(0.92f, 0.12f, 0.44f),
        Triple(1.32f, 0.32f, 0.30f),
    )

    listOf(-1f, 1f).forEach { dir ->
        val angle = dir * (s.wingAngle + flutter)
        val anchor = Offset(
            headCentre.x + dir * spread,
            headCentre.y + headRadius * 0.46f + lift,
        )
        rotate(degrees = angle, pivot = anchor) {
            lobes.forEachIndexed { i, (dx, dy, r) ->
                val centre = Offset(
                    anchor.x + dir * headRadius * dx,
                    anchor.y + headRadius * dy,
                )
                val radius = headRadius * r
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            s.wingColor,
                            s.wingColor.copy(alpha = 0.55f),
                        ),
                        center = centre,
                        radius = radius,
                    ),
                    radius = radius,
                    center = centre,
                )
                // A lighter inner lobe suggests layered feathers without drawing each one.
                if (i < 2) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.38f),
                        radius = radius * 0.62f,
                        center = Offset(centre.x, centre.y - radius * 0.18f),
                    )
                }
            }
        }
    }
}

/** The floating halo: an ellipse above the head that breathes, tilts and strobes. */
private fun DrawScope.drawHalo(
    frame: AngelFrame,
    headCentre: Offset,
    headRadius: Float,
    unit: Float,
) {
    val s = frame.style
    // Breathing for the calm tiers reads as a smooth scale; the alarmed tiers strobe, so
    // the same phase is squared to make the pulse sharp rather than sinusoidal.
    val raw = (sin(frame.haloPhase * 2f * PI.toFloat()) + 1f) / 2f
    val sharp = s.haloPeriodMillis <= 900
    val breath = if (sharp) raw * raw else raw
    val scale = 1f + 0.08f * breath
    val glowAlpha = 0.25f + 0.45f * breath

    val haloW = headRadius * 1.5f * scale
    val haloH = headRadius * 0.42f * scale
    val haloCentre = Offset(headCentre.x, headCentre.y - headRadius * 1.46f)

    rotate(degrees = s.haloTilt, pivot = haloCentre) {
        // Diffuse glow behind the ring.
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(s.haloGlow.copy(alpha = glowAlpha), Color.Transparent),
                center = haloCentre,
                radius = haloW * 1.3f,
            ),
            topLeft = Offset(haloCentre.x - haloW * 1.3f, haloCentre.y - haloH * 2.4f),
            size = Size(haloW * 2.6f, haloH * 4.8f),
        )
        drawOval(
            color = s.haloColor,
            topLeft = Offset(haloCentre.x - haloW, haloCentre.y - haloH),
            size = Size(haloW * 2f, haloH * 2f),
            style = Stroke(width = unit * 0.035f),
        )
    }
}

/** The head: a soft white orb with a blush underside. */
private fun DrawScope.drawHead(centre: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, Color(0xFFFFEFF1)),
            center = Offset(centre.x, centre.y - radius * 0.3f),
            radius = radius * 1.6f,
        ),
        radius = radius,
        center = centre,
    )
}

private fun DrawScope.drawFace(frame: AngelFrame, centre: Offset, radius: Float) {
    val s = frame.style
    val ink = Color(0xFF1E2238)

    val eyeY = centre.y - radius * 0.08f
    val eyeDx = radius * 0.40f
    val scanShift = if (s.eyes == EyeStyle.Scanning) {
        sin(frame.scanPhase * 2f * PI.toFloat()) * radius * 0.12f
    } else {
        0f
    }
    val eyeW = when (s.eyes) {
        EyeStyle.Anxious, EyeStyle.Alarmed -> radius * 0.20f
        else -> radius * 0.165f
    }
    val eyeH = eyeW * 1.35f * frame.eyeOpen

    listOf(-1f, 1f).forEach { dir ->
        val eyeCentre = Offset(centre.x + dir * eyeDx + scanShift, eyeY)
        drawOval(
            color = ink,
            topLeft = Offset(eyeCentre.x - eyeW, eyeCentre.y - eyeH),
            size = Size(eyeW * 2f, eyeH * 2f),
        )
        // Catchlight, only while the eye is meaningfully open.
        if (frame.eyeOpen > 0.5f) {
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = eyeW * 0.3f,
                center = Offset(eyeCentre.x - eyeW * 0.3f, eyeCentre.y - eyeH * 0.4f),
            )
        }
        // Furrowed protective brows at the critical tier.
        if (s.hasBrows) {
            val browY = eyeY - radius * 0.34f
            drawLine(
                color = ink,
                start = Offset(eyeCentre.x - dir * eyeW * 1.3f, browY - radius * 0.05f),
                end = Offset(eyeCentre.x + dir * eyeW * 1.1f, browY + radius * 0.06f),
                strokeWidth = radius * 0.075f,
                cap = StrokeCap.Round,
            )
        }
    }

    // Blush cheeks.
    listOf(-1f, 1f).forEach { dir ->
        drawOval(
            color = s.blushColor.copy(alpha = 0.55f),
            topLeft = Offset(
                centre.x + dir * radius * 0.56f - radius * 0.19f,
                centre.y + radius * 0.16f,
            ),
            size = Size(radius * 0.38f, radius * 0.24f),
        )
    }

    drawMouth(frame, centre, radius, ink)
}

private fun DrawScope.drawMouth(frame: AngelFrame, centre: Offset, radius: Float, ink: Color) {
    val mouthY = centre.y + radius * 0.30f
    val w = radius * 0.30f
    when (frame.style.mouth) {
        MouthStyle.Smile, MouthStyle.JoyfulSmile -> {
            val depth = if (frame.style.mouth == MouthStyle.JoyfulSmile) 0.9f else 0.6f
            drawArc(
                color = ink,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(centre.x - w, mouthY - w * depth),
                size = Size(w * 2f, w * 2f * depth),
                style = Stroke(width = radius * 0.06f, cap = StrokeCap.Round),
            )
        }
        MouthStyle.Flat -> drawLine(
            color = ink,
            start = Offset(centre.x - w * 0.6f, mouthY),
            end = Offset(centre.x + w * 0.6f, mouthY),
            strokeWidth = radius * 0.06f,
            cap = StrokeCap.Round,
        )
        MouthStyle.Quiver -> {
            // A tiny wobble that tracks the tremble, so the face reads as unsettled.
            val q = sin(frame.bodyPhase * 2f * PI.toFloat() * 9f) * radius * 0.035f
            drawArc(
                color = ink,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(centre.x - w * 0.7f, mouthY + q),
                size = Size(w * 1.4f, w * 0.8f),
                style = Stroke(width = radius * 0.055f, cap = StrokeCap.Round),
            )
        }
        MouthStyle.Open -> drawOval(
            color = ink,
            topLeft = Offset(centre.x - w * 0.45f, mouthY - w * 0.2f),
            size = Size(w * 0.9f, w * 1.05f),
        )
    }
}

/** The golden heart talisman on the chest, beating at the tier's rate. */
private fun DrawScope.drawTalisman(
    frame: AngelFrame,
    headCentre: Offset,
    headRadius: Float,
    unit: Float,
) {
    val beat = (sin(frame.talismanPhase * 2f * PI.toFloat()) + 1f) / 2f
    val scale = 0.92f + 0.16f * beat
    val heartW = headRadius * 0.46f * scale
    val centre = Offset(headCentre.x, headCentre.y + headRadius * 1.02f)

    drawCircle(
        color = frame.style.talismanColor.copy(alpha = 0.22f + 0.22f * beat),
        radius = heartW * 1.5f,
        center = centre,
    )
    translate(left = centre.x, top = centre.y) {
        drawPath(path = heartPath(heartW), color = frame.style.talismanColor)
    }
}

/** A heart centred on the origin, [w] half-width. */
private fun heartPath(w: Float): Path = Path().apply {
    val h = w * 0.95f
    moveTo(0f, h * 0.75f)
    cubicTo(-w * 1.5f, -h * 0.25f, -w * 0.6f, -h * 1.3f, 0f, -h * 0.45f)
    cubicTo(w * 0.6f, -h * 1.3f, w * 1.5f, -h * 0.25f, 0f, h * 0.75f)
    close()
}

/** Four-point sparkles orbiting the halo at the serene tier. */
private fun DrawScope.drawSparkles(frame: AngelFrame, centre: Offset, unit: Float) {
    val positions = listOf(
        Offset(-0.30f, -0.26f) to 0.0f,
        Offset(0.32f, -0.20f) to 0.35f,
        Offset(-0.24f, 0.04f) to 0.7f,
        Offset(0.26f, 0.08f) to 0.5f,
    )
    positions.forEach { (p, offsetPhase) ->
        val phase = (frame.bodyPhase + offsetPhase) % 1f
        val twinkle = sin(phase * 2f * PI.toFloat()).coerceAtLeast(0f)
        if (twinkle <= 0.02f) return@forEach
        val at = Offset(centre.x + p.x * unit, centre.y + p.y * unit)
        val r = unit * 0.026f * twinkle
        val colour = Color(0xFFFFD166).copy(alpha = twinkle)
        // A four-point star reads better than a dot at this size.
        drawPath(
            path = Path().apply {
                moveTo(at.x, at.y - r * 2f)
                quadraticTo(at.x, at.y, at.x + r * 2f, at.y)
                quadraticTo(at.x, at.y, at.x, at.y + r * 2f)
                quadraticTo(at.x, at.y, at.x - r * 2f, at.y)
                quadraticTo(at.x, at.y, at.x, at.y - r * 2f)
                close()
            },
            color = colour,
        )
    }
}

/** Anime worry droplets at the cautious and warning tiers. */
private fun DrawScope.drawSweat(frame: AngelFrame, headCentre: Offset, headRadius: Float) {
    val drip = (sin(frame.bodyPhase * 2f * PI.toFloat()) + 1f) / 2f
    val slots = listOf(1f, -1f).take(frame.style.sweatDrops)
    slots.forEachIndexed { i, dir ->
        val at = Offset(
            headCentre.x + dir * headRadius * 0.92f,
            headCentre.y - headRadius * 0.52f + drip * headRadius * 0.12f + i * headRadius * 0.1f,
        )
        val r = headRadius * 0.11f
        drawPath(
            path = Path().apply {
                moveTo(at.x, at.y - r * 1.5f)
                cubicTo(at.x + r, at.y - r * 0.2f, at.x + r, at.y + r, at.x, at.y + r)
                cubicTo(at.x - r, at.y + r, at.x - r, at.y - r * 0.2f, at.x, at.y - r * 1.5f)
                close()
            },
            color = Color(0xFF64B5F6).copy(alpha = 0.85f),
        )
    }
}
