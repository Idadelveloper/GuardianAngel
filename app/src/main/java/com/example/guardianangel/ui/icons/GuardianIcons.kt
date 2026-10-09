package com.example.guardianangel.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Stroke-based icon set for Guardian Angel.
 *
 * `design.md › Refined Icons` asks for 1.75–2px strokes with softened terminal caps, to
 * mirror the rounded geometry of the type. Material's bundled icons are solid-filled
 * glyphs, so they cannot express that; these are drawn as outlines on a 24x24 viewport
 * with round caps and joins instead.
 *
 * Each icon is lazily built once and cached. Tint through `Icon(tint = …)` as usual — the
 * declared stroke colour is a placeholder that the icon's colour filter replaces.
 */
object GuardianIcons {

    val Shield: ImageVector by lazy {
        strokeIcon("Shield") {
            moveTo(12f, 2.75f)
            lineTo(19.75f, 5.75f)
            lineTo(19.75f, 11.5f)
            curveTo(19.75f, 16.1f, 16.6f, 19.4f, 12f, 21.25f)
            curveTo(7.4f, 19.4f, 4.25f, 16.1f, 4.25f, 11.5f)
            lineTo(4.25f, 5.75f)
            close()
        }
    }

    /** The "guardian is watching, all clear" mark. */
    val ShieldCheck: ImageVector by lazy {
        strokeIcon("ShieldCheck") {
            moveTo(12f, 2.75f)
            lineTo(19.75f, 5.75f)
            lineTo(19.75f, 11.5f)
            curveTo(19.75f, 16.1f, 16.6f, 19.4f, 12f, 21.25f)
            curveTo(7.4f, 19.4f, 4.25f, 16.1f, 4.25f, 11.5f)
            lineTo(4.25f, 5.75f)
            close()
            moveTo(8.75f, 11.5f)
            lineTo(11.25f, 14f)
            lineTo(15.5f, 9.5f)
        }
    }

    val Home: ImageVector by lazy {
        strokeIcon("Home") {
            moveTo(3.25f, 10.25f)
            lineTo(12f, 3.25f)
            lineTo(20.75f, 10.25f)
            lineTo(20.75f, 20.25f)
            lineTo(3.25f, 20.25f)
            close()
            moveTo(9.5f, 20.25f)
            lineTo(9.5f, 13.75f)
            lineTo(14.5f, 13.75f)
            lineTo(14.5f, 20.25f)
        }
    }

    val MapPin: ImageVector by lazy {
        strokeIcon("MapPin") {
            // Teardrop body
            moveTo(12f, 2.5f)
            curveTo(8.27f, 2.5f, 5.25f, 5.52f, 5.25f, 9.25f)
            curveTo(5.25f, 14.6f, 12f, 21.5f, 12f, 21.5f)
            curveTo(12f, 21.5f, 18.75f, 14.6f, 18.75f, 9.25f)
            curveTo(18.75f, 5.52f, 15.73f, 2.5f, 12f, 2.5f)
            close()
            // Inner aperture
            moveTo(12f, 6.75f)
            curveTo(13.38f, 6.75f, 14.5f, 7.87f, 14.5f, 9.25f)
            curveTo(14.5f, 10.63f, 13.38f, 11.75f, 12f, 11.75f)
            curveTo(10.62f, 11.75f, 9.5f, 10.63f, 9.5f, 9.25f)
            curveTo(9.5f, 7.87f, 10.62f, 6.75f, 12f, 6.75f)
            close()
        }
    }

    val Bell: ImageVector by lazy {
        strokeIcon("Bell") {
            // Dome
            moveTo(6f, 16.25f)
            lineTo(6f, 10.5f)
            curveTo(6f, 7.19f, 8.69f, 4.5f, 12f, 4.5f)
            curveTo(15.31f, 4.5f, 18f, 7.19f, 18f, 10.5f)
            lineTo(18f, 16.25f)
            // Lip
            moveTo(4.5f, 16.25f)
            lineTo(19.5f, 16.25f)
            // Clapper
            moveTo(10.25f, 19.25f)
            curveTo(10.6f, 20.05f, 11.25f, 20.5f, 12f, 20.5f)
            curveTo(12.75f, 20.5f, 13.4f, 20.05f, 13.75f, 19.25f)
        }
    }

    val Users: ImageVector by lazy {
        strokeIcon("Users") {
            // Front figure
            moveTo(9.5f, 11.25f)
            curveTo(11.3f, 11.25f, 12.75f, 9.8f, 12.75f, 8f)
            curveTo(12.75f, 6.2f, 11.3f, 4.75f, 9.5f, 4.75f)
            curveTo(7.7f, 4.75f, 6.25f, 6.2f, 6.25f, 8f)
            curveTo(6.25f, 9.8f, 7.7f, 11.25f, 9.5f, 11.25f)
            close()
            moveTo(3.25f, 19.25f)
            curveTo(3.25f, 15.8f, 6.05f, 13.5f, 9.5f, 13.5f)
            curveTo(12.95f, 13.5f, 15.75f, 15.8f, 15.75f, 19.25f)
            // Companion behind
            moveTo(15.25f, 5.1f)
            curveTo(16.95f, 5.5f, 18.1f, 7.03f, 18.05f, 8.77f)
            curveTo(18.01f, 10.19f, 17.19f, 11.46f, 15.95f, 12.1f)
            moveTo(17.1f, 14.1f)
            curveTo(19.3f, 14.9f, 20.75f, 16.9f, 20.75f, 19.25f)
        }
    }

    val Check: ImageVector by lazy {
        strokeIcon("Check") {
            moveTo(5f, 12.75f)
            lineTo(9.5f, 17.25f)
            lineTo(19f, 6.75f)
        }
    }

    val Plus: ImageVector by lazy {
        strokeIcon("Plus") {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }

    val ChevronRight: ImageVector by lazy {
        strokeIcon("ChevronRight") {
            moveTo(9.25f, 4.75f)
            lineTo(16.5f, 12f)
            lineTo(9.25f, 19.25f)
        }
    }

    val Phone: ImageVector by lazy {
        strokeIcon("Phone") {
            moveTo(7.3f, 3.5f)
            lineTo(9.6f, 3.5f)
            lineTo(11f, 8.1f)
            lineTo(8.9f, 9.6f)
            curveTo(9.8f, 12.1f, 11.9f, 14.2f, 14.4f, 15.1f)
            lineTo(15.9f, 13f)
            lineTo(20.5f, 14.4f)
            lineTo(20.5f, 16.7f)
            curveTo(20.5f, 18.9f, 18.7f, 20.6f, 16.5f, 20.4f)
            curveTo(9.3f, 19.7f, 4.3f, 14.7f, 3.6f, 7.5f)
            curveTo(3.4f, 5.3f, 5.1f, 3.5f, 7.3f, 3.5f)
            close()
        }
    }

    val Clock: ImageVector by lazy {
        strokeIcon("Clock") {
            moveTo(12f, 3.25f)
            curveTo(16.83f, 3.25f, 20.75f, 7.17f, 20.75f, 12f)
            curveTo(20.75f, 16.83f, 16.83f, 20.75f, 12f, 20.75f)
            curveTo(7.17f, 20.75f, 3.25f, 16.83f, 3.25f, 12f)
            curveTo(3.25f, 7.17f, 7.17f, 3.25f, 12f, 3.25f)
            close()
            moveTo(12f, 7.5f)
            lineTo(12f, 12f)
            lineTo(15.25f, 14.25f)
        }
    }

    /** Settings, drawn as sliders to stay in the stroke idiom. */
    val Sliders: ImageVector by lazy {
        strokeIcon("Sliders") {
            moveTo(4f, 7.5f)
            lineTo(20f, 7.5f)
            moveTo(4f, 16.5f)
            lineTo(20f, 16.5f)
            moveTo(9.5f, 5.25f)
            curveTo(10.74f, 5.25f, 11.75f, 6.26f, 11.75f, 7.5f)
            curveTo(11.75f, 8.74f, 10.74f, 9.75f, 9.5f, 9.75f)
            curveTo(8.26f, 9.75f, 7.25f, 8.74f, 7.25f, 7.5f)
            curveTo(7.25f, 6.26f, 8.26f, 5.25f, 9.5f, 5.25f)
            close()
            moveTo(15f, 14.25f)
            curveTo(16.24f, 14.25f, 17.25f, 15.26f, 17.25f, 16.5f)
            curveTo(17.25f, 17.74f, 16.24f, 18.75f, 15f, 18.75f)
            curveTo(13.76f, 18.75f, 12.75f, 17.74f, 12.75f, 16.5f)
            curveTo(12.75f, 15.26f, 13.76f, 14.25f, 15f, 14.25f)
            close()
        }
    }

    /** Live-location beacon: a pin with concentric broadcast arcs. */
    val Beacon: ImageVector by lazy {
        strokeIcon("Beacon") {
            moveTo(12f, 9.25f)
            curveTo(13.24f, 9.25f, 14.25f, 10.26f, 14.25f, 11.5f)
            curveTo(14.25f, 12.74f, 13.24f, 13.75f, 12f, 13.75f)
            curveTo(10.76f, 13.75f, 9.75f, 12.74f, 9.75f, 11.5f)
            curveTo(9.75f, 10.26f, 10.76f, 9.25f, 12f, 9.25f)
            close()
            moveTo(7.4f, 16.1f)
            curveTo(4.87f, 13.56f, 4.87f, 9.44f, 7.4f, 6.9f)
            moveTo(16.6f, 6.9f)
            curveTo(19.13f, 9.44f, 19.13f, 13.56f, 16.6f, 16.1f)
            moveTo(4.9f, 19.6f)
            curveTo(1.6f, 15.2f, 1.6f, 8.8f, 4.9f, 4.4f)
            moveTo(19.1f, 4.4f)
            curveTo(22.4f, 8.8f, 22.4f, 15.2f, 19.1f, 19.6f)
        }
    }
}

private const val STROKE_WIDTH = 2f

private fun strokeIcon(name: String, pathBuilder: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder,
        )
    }.build()
