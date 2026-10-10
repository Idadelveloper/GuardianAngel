package com.example.guardianangel.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * The app's own map pins.
 *
 * ## Why not the default markers
 *
 * `BitmapDescriptorFactory.defaultMarker()` gives a Google-red teardrop with a hue
 * applied. Every pin on the map was one of those, distinguished only by an emoji buried
 * in the title you could not see until you tapped it — so a police station, a cluster of
 * reported assaults and a saved safe place all looked identical. On a safety map that is
 * not a styling complaint: the whole point of looking at the map is telling those apart
 * at a glance.
 *
 * These are drawn to the app's palette and shape language: a soft rounded teardrop, a
 * white glyph plate, and a colour that says what kind of thing it is. Hazards carry
 * their incident count, because "3 reported" and "40 reported" are different places.
 *
 * ## Cost
 *
 * Bitmaps are generated once per (kind, badge, scale) and cached. A map can hold
 * hundreds of pins and re-render on every camera move; generating these per frame would
 * drop frames on exactly the screen where the user is walking.
 */
object GuardianMapMarkers {

    /** What a pin represents. Colour and glyph follow from this. */
    enum class Kind {
        Home,
        SafePlace,
        SafeHaven,
        Hazard,
        Destination,
    }

    private val cache = mutableMapOf<String, BitmapDescriptor>()

    fun pin(kind: Kind, badge: Int? = null, emphasis: Float = 1f): BitmapDescriptor {
        val rounded = (emphasis * 10).toInt() / 10f
        val key = "${kind.name}-${badge ?: 0}-$rounded"
        return cache.getOrPut(key) {
            BitmapDescriptorFactory.fromBitmap(draw(kind, badge, rounded))
        }
    }

    /** Clears the cache. Called when the theme changes, which invalidates every colour. */
    fun clear() = cache.clear()

    private fun draw(kind: Kind, badge: Int?, emphasis: Float): Bitmap {
        val size = (BASE_SIZE * emphasis).toInt().coerceIn(48, 160)
        // The glow and the count badge both reach past the pin itself. Without this
        // margin they were sliced off at the bitmap edge, and a radial fade cut by a
        // straight line reads as a grey box around every marker — which is exactly how
        // it looked on the device.
        val padSide = size * PAD_SIDE_RATIO
        val padTop = size * PAD_TOP_RATIO
        val bitmap = Bitmap.createBitmap(
            (size + padSide * 2).toInt(),
            (size * BODY_HEIGHT_RATIO + padTop).toInt(),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.translate(padSide, padTop)
        val fill = fillFor(kind)

        val cx = size / 2f
        val headRadius = size * 0.34f
        val cy = headRadius + size * 0.06f

        // Soft aura. A radial fade rather than a flat translucent disc: at 22% alpha a
        // hard-edged circle looked like a smudge of dirt on the tiles.
        val glowRadius = headRadius * 1.55f
        canvas.drawCircle(
            cx,
            cy,
            glowRadius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx,
                    cy,
                    glowRadius,
                    intArrayOf(withAlpha(fill, 0.30f), withAlpha(fill, 0.18f), withAlpha(fill, 0f)),
                    floatArrayOf(0f, 0.62f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )

        // Teardrop: a circle with a tail, rounder than Google's default pin so it sits
        // with the app's pill-and-soft-corner shapes.
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
        canvas.drawPath(
            Path().apply {
                moveTo(cx, cy + headRadius * 2.05f)
                quadTo(cx - headRadius * 0.92f, cy + headRadius * 0.72f, cx - headRadius, cy)
                addCircle(cx, cy, headRadius, Path.Direction.CW)
                moveTo(cx, cy + headRadius * 2.05f)
                quadTo(cx + headRadius * 0.92f, cy + headRadius * 0.72f, cx + headRadius, cy)
                close()
            },
            body,
        )
        canvas.drawCircle(cx, cy, headRadius, body)

        // White plate behind the glyph, so every kind reads at thumbnail size whatever
        // the map tiles underneath are doing.
        canvas.drawCircle(
            cx,
            cy,
            headRadius * 0.62f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PLATE },
        )

        drawGlyph(canvas, kind, cx, cy, headRadius * 0.40f, fill)

        if (badge != null && badge > 1) drawBadge(canvas, badge, size, cx, cy, headRadius, fill)
        return bitmap
    }

    /**
     * A simple mark per kind, drawn rather than drawn from a font.
     *
     * Emoji were the previous approach and render differently on every OEM skin, at
     * sizes nobody controls. These are a few paths that look the same everywhere.
     */
    private fun drawGlyph(
        canvas: Canvas,
        kind: Kind,
        cx: Float,
        cy: Float,
        r: Float,
        tint: Int,
    ) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            style = Paint.Style.STROKE
            strokeWidth = r * 0.42f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint }

        when (kind) {
            // A roof over a doorway.
            Kind.Home -> canvas.drawPath(
                Path().apply {
                    moveTo(cx - r, cy + r * 0.15f)
                    lineTo(cx, cy - r * 0.85f)
                    lineTo(cx + r, cy + r * 0.15f)
                    moveTo(cx - r * 0.62f, cy + r * 0.1f)
                    lineTo(cx - r * 0.62f, cy + r * 0.92f)
                    lineTo(cx + r * 0.62f, cy + r * 0.92f)
                    lineTo(cx + r * 0.62f, cy + r * 0.1f)
                },
                stroke,
            )

            // A shield, the app's own safety mark.
            Kind.SafePlace, Kind.SafeHaven -> canvas.drawPath(
                Path().apply {
                    moveTo(cx, cy - r)
                    lineTo(cx + r * 0.82f, cy - r * 0.55f)
                    lineTo(cx + r * 0.82f, cy + r * 0.18f)
                    quadTo(cx + r * 0.82f, cy + r * 0.86f, cx, cy + r * 1.05f)
                    quadTo(cx - r * 0.82f, cy + r * 0.86f, cx - r * 0.82f, cy + r * 0.18f)
                    lineTo(cx - r * 0.82f, cy - r * 0.55f)
                    close()
                },
                if (kind == Kind.SafeHaven) solid else stroke,
            )

            // An exclamation: the one glyph nobody has to learn.
            Kind.Hazard -> {
                canvas.drawLine(cx, cy - r * 0.78f, cx, cy + r * 0.22f, stroke)
                canvas.drawCircle(cx, cy + r * 0.76f, r * 0.21f, solid)
            }

            // Concentric rings, reading as "the place you are heading".
            Kind.Destination -> {
                canvas.drawCircle(cx, cy, r * 0.82f, stroke)
                canvas.drawCircle(cx, cy, r * 0.26f, solid)
            }
        }
    }

    /**
     * The incident count on a hazard pin.
     *
     * Shown because "3 reported" and "40 reported" are different places, and a map that
     * renders them identically is hiding the only number that matters.
     */
    private fun drawBadge(
        canvas: Canvas,
        count: Int,
        size: Int,
        cx: Float,
        cy: Float,
        headRadius: Float,
        fill: Int,
    ) {
        val label = if (count > 99) "99+" else count.toString()
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PLATE
            textSize = size * 0.20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val bx = cx + headRadius * 0.78f
        val by = cy - headRadius * 0.74f
        val halfWidth = (text.measureText(label) / 2f) + size * 0.055f
        val halfHeight = size * 0.105f

        canvas.drawRoundRect(
            RectF(bx - halfWidth, by - halfHeight, bx + halfWidth, by + halfHeight),
            halfHeight,
            halfHeight,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PLATE },
        )
        canvas.drawRoundRect(
            RectF(
                bx - halfWidth + size * 0.018f,
                by - halfHeight + size * 0.018f,
                bx + halfWidth - size * 0.018f,
                by + halfHeight - size * 0.018f,
            ),
            halfHeight,
            halfHeight,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill },
        )
        canvas.drawText(label, bx, by + text.textSize * 0.35f, text)
    }

    /**
     * Palette, mirroring `ui/theme/Color.kt`.
     *
     * Duplicated as ints because these are drawn on an Android `Canvas` outside
     * composition, where `GuardianTheme` is not reachable. Kept beside the glyphs so the
     * two are changed together; if the theme moves, this has to move with it.
     */
    private fun fillFor(kind: Kind): Int = when (kind) {
        Kind.Home -> 0xFFE8975B.toInt()       // apricot, warmer than the card tint
        Kind.SafePlace -> 0xFF6F9E72.toInt()  // muted green, calm rather than "go"
        Kind.SafeHaven -> 0xFF4E7F96.toInt()  // slate blue: institutional, not alarming
        Kind.Hazard -> 0xFFD1495B.toInt()     // rose-red, kin to DuressRed but softer
        Kind.Destination -> 0xFF1E2238.toInt() // Espresso
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha * 255).toInt().coerceIn(0, 255) shl 24)

    private const val PLATE = 0xFFFFFFFF.toInt()
    private const val BASE_SIZE = 96

    /** Pin height as a multiple of its width, before padding. */
    private const val BODY_HEIGHT_RATIO = 1.25f
    private const val PAD_SIDE_RATIO = 0.10f
    private const val PAD_TOP_RATIO = 0.12f

    /** Where the teardrop's tip sits, as a fraction of the pin body's height. */
    private const val TIP_RATIO = 1.097f

    /**
     * Where the pin points, for `Marker(anchor = ...)`.
     *
     * Derived from the geometry above rather than eyeballed, so padding can change
     * without every pin on the map silently drifting north of what it marks.
     */
    val ANCHOR_X = (PAD_SIDE_RATIO + 0.5f) / (1f + PAD_SIDE_RATIO * 2f)
    val ANCHOR_Y = (PAD_TOP_RATIO + TIP_RATIO) / (BODY_HEIGHT_RATIO + PAD_TOP_RATIO)
}
