package com.example.guardianangel.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.example.guardianangel.ui.mascot.AngelMood
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * Generates and caches custom Google Maps marker bitmaps for the Angel mascot.
 *
 * Invariants:
 * 1. Cached by [AngelMood] tier: never regenerates expensive bitmap assets on GPS tick.
 * 2. Visual design reflects the five Angel mood tiers: Resting, Sanctuary, Cautious,
 *    Warning, Critical.
 * 3. Shows distinct halo, aura, and wings matching the mascot design system.
 */
object AngelMarkerRenderer {

    private val cache = mutableMapOf<AngelMood, BitmapDescriptor>()

    fun getMarkerBitmapDescriptor(mood: AngelMood): BitmapDescriptor {
        return cache.getOrPut(mood) {
            val bitmap = createAngelBitmap(mood)
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }

    private fun createAngelBitmap(mood: AngelMood): Bitmap {
        val size = 120
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val auraColor = when (mood) {
            AngelMood.Resting -> 0x88FFE8A3.toInt()
            AngelMood.Sanctuary -> 0xAAFFF8B5.toInt()
            AngelMood.Cautious -> 0xAAFFAA00.toInt()
            AngelMood.Warning -> 0xCCFF7043.toInt()
            AngelMood.Critical -> 0xEEFF334B.toInt()
        }

        val haloColor = when (mood) {
            AngelMood.Resting, AngelMood.Sanctuary -> 0xFFFFD166.toInt()
            AngelMood.Cautious -> 0xFFFFAA00.toInt()
            AngelMood.Warning -> 0xFFFF7043.toInt()
            AngelMood.Critical -> 0xFFFF2A42.toInt()
        }

        val bodyColor = 0xFFFFF9F9.toInt()
        val blushColor = 0xFFFFB1B1.toInt()
        val wingColor = 0xFFFFCCD3.toInt()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Soft pulsing aura background
        paint.color = auraColor
        paint.style = Paint.Style.FILL
        canvas.drawCircle(60f, 64f, 44f, paint)

        // 2. Wings (left and right arcs)
        paint.color = wingColor
        paint.style = Paint.Style.FILL
        // Left wing
        val leftWing = RectF(14f, 48f, 48f, 80f)
        canvas.drawOval(leftWing, paint)
        // Right wing
        val rightWing = RectF(72f, 48f, 106f, 80f)
        canvas.drawOval(rightWing, paint)

        // 3. Central Angel body
        paint.color = bodyColor
        canvas.drawCircle(60f, 66f, 28f, paint)

        // Outline for contrast on light or dark maps
        paint.color = 0x33442222
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        canvas.drawCircle(60f, 66f, 28f, paint)

        // 4. Glowing halo above head
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4.5f
        paint.color = haloColor
        val haloRect = RectF(40f, 22f, 80f, 36f)
        canvas.drawOval(haloRect, paint)

        // 5. Facial features
        paint.style = Paint.Style.FILL
        paint.color = 0xFF2D181C.toInt() // Espresso face pigment
        when (mood) {
            AngelMood.Sanctuary, AngelMood.Resting -> {
                // Happy curved eyes
                paint.strokeWidth = 3f
                paint.style = Paint.Style.STROKE
                canvas.drawArc(RectF(48f, 58f, 56f, 66f), 200f, 140f, false, paint)
                canvas.drawArc(RectF(64f, 58f, 72f, 66f), 200f, 140f, false, paint)
                // Smile
                canvas.drawArc(RectF(55f, 68f, 65f, 76f), 0f, 180f, false, paint)
                // Rosy cheeks
                paint.style = Paint.Style.FILL
                paint.color = blushColor
                canvas.drawCircle(47f, 68f, 4f, paint)
                canvas.drawCircle(73f, 68f, 4f, paint)
            }
            AngelMood.Cautious -> {
                // Alert round eyes
                canvas.drawCircle(52f, 62f, 3.5f, paint)
                canvas.drawCircle(68f, 62f, 3.5f, paint)
                // Small observant mouth
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                canvas.drawArc(RectF(57f, 69f, 63f, 74f), 0f, 180f, false, paint)
            }
            AngelMood.Warning, AngelMood.Critical -> {
                // Wide watchful eyes
                canvas.drawCircle(51f, 61f, 4.5f, paint)
                canvas.drawCircle(69f, 61f, 4.5f, paint)
                // Alert mouth
                paint.color = 0xFFCC2222.toInt()
                canvas.drawCircle(60f, 73f, 3f, paint)
            }
        }

        return bitmap
    }
}
