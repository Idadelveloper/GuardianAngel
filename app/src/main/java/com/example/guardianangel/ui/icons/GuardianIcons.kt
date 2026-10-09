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

    val Close: ImageVector by lazy {
        strokeIcon("Close") {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
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

    val ChevronLeft: ImageVector by lazy {
        strokeIcon("ChevronLeft") {
            moveTo(14.75f, 4.75f)
            lineTo(7.5f, 12f)
            lineTo(14.75f, 19.25f)
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

    val Mic: ImageVector by lazy {
        strokeIcon("Mic") {
            moveTo(12f, 2.75f)
            curveTo(13.52f, 2.75f, 14.75f, 3.98f, 14.75f, 5.5f)
            lineTo(14.75f, 11.5f)
            curveTo(14.75f, 13.02f, 13.52f, 14.25f, 12f, 14.25f)
            curveTo(10.48f, 14.25f, 9.25f, 13.02f, 9.25f, 11.5f)
            lineTo(9.25f, 5.5f)
            curveTo(9.25f, 3.98f, 10.48f, 2.75f, 12f, 2.75f)
            close()
            moveTo(5.5f, 10.75f)
            curveTo(5.5f, 14.34f, 8.41f, 17.25f, 12f, 17.25f)
            curveTo(15.59f, 17.25f, 18.5f, 14.34f, 18.5f, 10.75f)
            moveTo(12f, 17.25f)
            lineTo(12f, 21.25f)
        }
    }

    /** Mic with a slash: dormant, not listening. */
    val MicOff: ImageVector by lazy {
        strokeIcon("MicOff") {
            moveTo(9.25f, 6.1f)
            curveTo(9.4f, 4.73f, 10.57f, 3.68f, 11.96f, 3.68f)
            curveTo(13.48f, 3.68f, 14.72f, 4.91f, 14.72f, 6.43f)
            lineTo(14.72f, 11.1f)
            moveTo(14.2f, 14.1f)
            curveTo(13.6f, 14.7f, 12.82f, 15.03f, 12f, 15f)
            curveTo(10.48f, 14.95f, 9.25f, 13.6f, 9.25f, 12.08f)
            lineTo(9.25f, 9.6f)
            moveTo(5.5f, 10.75f)
            curveTo(5.5f, 14.34f, 8.41f, 17.25f, 12f, 17.25f)
            curveTo(13.3f, 17.25f, 14.5f, 16.87f, 15.5f, 16.2f)
            moveTo(18.5f, 10.75f)
            curveTo(18.5f, 11.7f, 18.3f, 12.6f, 17.93f, 13.4f)
            moveTo(12f, 17.25f)
            lineTo(12f, 21.25f)
            moveTo(3.75f, 3.75f)
            lineTo(20.25f, 20.25f)
        }
    }

    /** Audio waveform: ambient listening is live. */
    val Waveform: ImageVector by lazy {
        strokeIcon("Waveform") {
            moveTo(3.25f, 10.5f)
            lineTo(3.25f, 13.5f)
            moveTo(7.25f, 7.5f)
            lineTo(7.25f, 16.5f)
            moveTo(11.25f, 4.25f)
            lineTo(11.25f, 19.75f)
            moveTo(15.25f, 8.5f)
            lineTo(15.25f, 15.5f)
            moveTo(19.25f, 10.5f)
            lineTo(19.25f, 13.5f)
        }
    }

    /** Pulse line for the Activities tab. */
    val Activity: ImageVector by lazy {
        strokeIcon("Activity") {
            moveTo(2.75f, 12f)
            lineTo(7f, 12f)
            lineTo(9.5f, 5.75f)
            lineTo(14f, 18.25f)
            lineTo(16.5f, 12f)
            lineTo(21.25f, 12f)
        }
    }

    /** Settings, drawn as a true 8-tooth cog. */
    val Gear: ImageVector by lazy {
        strokeIcon("Gear") {
            moveTo(9.09f, 4.98f)
            lineTo(9.97f, 2.21f)
            lineTo(14.03f, 2.21f)
            lineTo(14.91f, 4.98f)
            lineTo(14.91f, 4.98f)
            lineTo(17.49f, 3.64f)
            lineTo(20.36f, 6.51f)
            lineTo(19.02f, 9.09f)
            lineTo(19.02f, 9.09f)
            lineTo(21.79f, 9.97f)
            lineTo(21.79f, 14.03f)
            lineTo(19.02f, 14.91f)
            lineTo(19.02f, 14.91f)
            lineTo(20.36f, 17.49f)
            lineTo(17.49f, 20.36f)
            lineTo(14.91f, 19.02f)
            lineTo(14.91f, 19.02f)
            lineTo(14.03f, 21.79f)
            lineTo(9.97f, 21.79f)
            lineTo(9.09f, 19.02f)
            lineTo(9.09f, 19.02f)
            lineTo(6.51f, 20.36f)
            lineTo(3.64f, 17.49f)
            lineTo(4.98f, 14.91f)
            lineTo(4.98f, 14.91f)
            lineTo(2.21f, 14.03f)
            lineTo(2.21f, 9.97f)
            lineTo(4.98f, 9.09f)
            lineTo(4.98f, 9.09f)
            lineTo(3.64f, 6.51f)
            lineTo(6.51f, 3.64f)
            lineTo(9.09f, 4.98f)
            close()
            // Hub
            moveTo(15.30f, 12.00f)
            curveTo(15.30f, 13.82f, 13.82f, 15.30f, 12.00f, 15.30f)
            curveTo(10.18f, 15.30f, 8.70f, 13.82f, 8.70f, 12.00f)
            curveTo(8.70f, 10.18f, 10.18f, 8.70f, 12.00f, 8.70f)
            curveTo(13.82f, 8.70f, 15.30f, 10.18f, 15.30f, 12.00f)
            close()
        }
    }

    val Battery: ImageVector by lazy {
        strokeIcon("Battery") {
            moveTo(3.25f, 8.25f)
            lineTo(16.25f, 8.25f)
            lineTo(16.25f, 15.75f)
            lineTo(3.25f, 15.75f)
            close()
            moveTo(19f, 10.5f)
            lineTo(19f, 13.5f)
        }
    }

    val Eye: ImageVector by lazy {
        strokeIcon("Eye") {
            moveTo(2.25f, 12f)
            curveTo(4.5f, 7.5f, 8f, 5.25f, 12f, 5.25f)
            curveTo(16f, 5.25f, 19.5f, 7.5f, 21.75f, 12f)
            curveTo(19.5f, 16.5f, 16f, 18.75f, 12f, 18.75f)
            curveTo(8f, 18.75f, 4.5f, 16.5f, 2.25f, 12f)
            close()
            moveTo(15f, 12f)
            curveTo(15f, 13.66f, 13.66f, 15f, 12f, 15f)
            curveTo(10.34f, 15f, 9f, 13.66f, 9f, 12f)
            curveTo(9f, 10.34f, 10.34f, 9f, 12f, 9f)
            curveTo(13.66f, 9f, 15f, 10.34f, 15f, 12f)
            close()
        }
    }

    val EyeOff: ImageVector by lazy {
        strokeIcon("EyeOff") {
            moveTo(9.9f, 5.55f)
            curveTo(10.58f, 5.35f, 11.28f, 5.25f, 12f, 5.25f)
            curveTo(16f, 5.25f, 19.5f, 7.5f, 21.75f, 12f)
            curveTo(20.9f, 13.7f, 19.86f, 15.1f, 18.68f, 16.18f)
            moveTo(15.6f, 18.2f)
            curveTo(14.47f, 18.57f, 13.27f, 18.75f, 12f, 18.75f)
            curveTo(8f, 18.75f, 4.5f, 16.5f, 2.25f, 12f)
            curveTo(3.4f, 9.7f, 4.9f, 7.98f, 6.62f, 6.85f)
            moveTo(9.9f, 9.9f)
            curveTo(9.35f, 10.44f, 9f, 11.18f, 9f, 12f)
            curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
            curveTo(12.82f, 15f, 13.56f, 14.65f, 14.1f, 14.1f)
            moveTo(3.75f, 3.75f)
            lineTo(20.25f, 20.25f)
        }
    }

    val Lock: ImageVector by lazy {
        strokeIcon("Lock") {
            moveTo(6.25f, 10.5f)
            lineTo(17.75f, 10.5f)
            lineTo(17.75f, 20.25f)
            lineTo(6.25f, 20.25f)
            close()
            moveTo(8.75f, 10.5f)
            lineTo(8.75f, 7.25f)
            curveTo(8.75f, 5.45f, 10.2f, 4f, 12f, 4f)
            curveTo(13.8f, 4f, 15.25f, 5.45f, 15.25f, 7.25f)
            lineTo(15.25f, 10.5f)
        }
    }

    /** Standby / night watch. */
    val Moon: ImageVector by lazy {
        strokeIcon("Moon") {
            moveTo(20.5f, 14.4f)
            curveTo(19.3f, 14.95f, 17.97f, 15.25f, 16.57f, 15.25f)
            curveTo(11.35f, 15.25f, 7.12f, 11.02f, 7.12f, 5.8f)
            curveTo(7.12f, 4.84f, 7.26f, 3.92f, 7.52f, 3.05f)
            curveTo(4.64f, 4.42f, 2.65f, 7.36f, 2.65f, 10.76f)
            curveTo(2.65f, 15.48f, 6.47f, 19.3f, 11.19f, 19.3f)
            curveTo(15.45f, 19.3f, 18.98f, 16.18f, 19.63f, 12.1f)
            close()
        }
    }

    val Refresh: ImageVector by lazy {
        strokeIcon("Refresh") {
            moveTo(20.25f, 12f)
            curveTo(20.25f, 16.56f, 16.56f, 20.25f, 12f, 20.25f)
            curveTo(7.44f, 20.25f, 3.75f, 16.56f, 3.75f, 12f)
            curveTo(3.75f, 7.44f, 7.44f, 3.75f, 12f, 3.75f)
            curveTo(15.1f, 3.75f, 17.8f, 5.46f, 19.2f, 8f)
            moveTo(19.75f, 3.5f)
            lineTo(19.75f, 8.25f)
            lineTo(15f, 8.25f)
        }
    }

    val ArrowRight: ImageVector by lazy {
        strokeIcon("ArrowRight") {
            moveTo(4.25f, 12f)
            lineTo(19.75f, 12f)
            moveTo(13.5f, 5.75f)
            lineTo(19.75f, 12f)
            lineTo(13.5f, 18.25f)
        }
    }

    /** Safe Walk. */
    val Walk: ImageVector by lazy {
        strokeIcon("Walk") {
            moveTo(13.25f, 5.25f)
            curveTo(14.08f, 5.25f, 14.75f, 4.58f, 14.75f, 3.75f)
            curveTo(14.75f, 2.92f, 14.08f, 2.25f, 13.25f, 2.25f)
            curveTo(12.42f, 2.25f, 11.75f, 2.92f, 11.75f, 3.75f)
            curveTo(11.75f, 4.58f, 12.42f, 5.25f, 13.25f, 5.25f)
            close()
            moveTo(10.5f, 21.75f)
            lineTo(12.25f, 15.25f)
            lineTo(9f, 12.5f)
            lineTo(10f, 7.75f)
            lineTo(14.25f, 9.5f)
            lineTo(16.5f, 12.5f)
            moveTo(10f, 7.75f)
            lineTo(6.75f, 9.75f)
            lineTo(5.75f, 12.75f)
            moveTo(12.25f, 15.25f)
            lineTo(15.75f, 18.75f)
            lineTo(17.25f, 21.75f)
        }
    }

    val TrendingDown: ImageVector by lazy {
        strokeIcon("TrendingDown") {
            moveTo(3.25f, 7.5f)
            lineTo(10f, 14.25f)
            lineTo(13.5f, 10.75f)
            lineTo(20.75f, 18f)
            moveTo(20.75f, 12.75f)
            lineTo(20.75f, 18f)
            lineTo(15.5f, 18f)
        }
    }

    val TrendingUp: ImageVector by lazy {
        strokeIcon("TrendingUp") {
            moveTo(3.25f, 16.5f)
            lineTo(10f, 9.75f)
            lineTo(13.5f, 13.25f)
            lineTo(20.75f, 6f)
            moveTo(20.75f, 11.25f)
            lineTo(20.75f, 6f)
            lineTo(15.5f, 6f)
        }
    }

    /** Street illumination factor. */
    val Sun: ImageVector by lazy {
        strokeIcon("Sun") {
            moveTo(12f, 8.25f)
            curveTo(14.07f, 8.25f, 15.75f, 9.93f, 15.75f, 12f)
            curveTo(15.75f, 14.07f, 14.07f, 15.75f, 12f, 15.75f)
            curveTo(9.93f, 15.75f, 8.25f, 14.07f, 8.25f, 12f)
            curveTo(8.25f, 9.93f, 9.93f, 8.25f, 12f, 8.25f)
            close()
            moveTo(12f, 2.75f)
            lineTo(12f, 4.25f)
            moveTo(12f, 19.75f)
            lineTo(12f, 21.25f)
            moveTo(4.22f, 4.22f)
            lineTo(5.28f, 5.28f)
            moveTo(18.72f, 18.72f)
            lineTo(19.78f, 19.78f)
            moveTo(2.75f, 12f)
            lineTo(4.25f, 12f)
            moveTo(19.75f, 12f)
            lineTo(21.25f, 12f)
            moveTo(4.22f, 19.78f)
            lineTo(5.28f, 18.72f)
            moveTo(18.72f, 5.28f)
            lineTo(19.78f, 4.22f)
        }
    }

    val Warning: ImageVector by lazy {
        strokeIcon("Warning") {
            moveTo(12f, 3.5f)
            lineTo(21.5f, 20f)
            lineTo(2.5f, 20f)
            close()
            moveTo(12f, 9.5f)
            lineTo(12f, 14f)
            moveTo(12f, 16.75f)
            lineTo(12.01f, 16.75f)
        }
    }

    val Hourglass: ImageVector by lazy {
        strokeIcon("Hourglass") {
            moveTo(6.25f, 3.25f)
            lineTo(17.75f, 3.25f)
            moveTo(6.25f, 20.75f)
            lineTo(17.75f, 20.75f)
            moveTo(7.5f, 3.25f)
            lineTo(7.5f, 7.5f)
            lineTo(12f, 12f)
            lineTo(7.5f, 16.5f)
            lineTo(7.5f, 20.75f)
            moveTo(16.5f, 3.25f)
            lineTo(16.5f, 7.5f)
            lineTo(12f, 12f)
            lineTo(16.5f, 16.5f)
            lineTo(16.5f, 20.75f)
        }
    }

    /** Ping a guardian / live telemetry. */
    val Broadcast: ImageVector by lazy {
        strokeIcon("Broadcast") {
            moveTo(12f, 10.25f)
            curveTo(12.97f, 10.25f, 13.75f, 11.03f, 13.75f, 12f)
            curveTo(13.75f, 12.97f, 12.97f, 13.75f, 12f, 13.75f)
            curveTo(11.03f, 13.75f, 10.25f, 12.97f, 10.25f, 12f)
            curveTo(10.25f, 11.03f, 11.03f, 10.25f, 12f, 10.25f)
            close()
            moveTo(8.1f, 15.9f)
            curveTo(5.95f, 13.75f, 5.95f, 10.25f, 8.1f, 8.1f)
            moveTo(15.9f, 8.1f)
            curveTo(18.05f, 10.25f, 18.05f, 13.75f, 15.9f, 15.9f)
            moveTo(5.3f, 18.7f)
            curveTo(1.9f, 15.3f, 1.9f, 8.7f, 5.3f, 5.3f)
            moveTo(18.7f, 5.3f)
            curveTo(22.1f, 8.7f, 22.1f, 15.3f, 18.7f, 18.7f)
        }
    }

    /** The user's safe base. */
    val HomePin: ImageVector by lazy {
        strokeIcon("HomePin") {
            moveTo(4.25f, 10.5f)
            lineTo(12f, 4.25f)
            lineTo(19.75f, 10.5f)
            lineTo(19.75f, 19.75f)
            lineTo(4.25f, 19.75f)
            close()
            moveTo(12f, 10.75f)
            curveTo(13.1f, 10.75f, 14f, 11.65f, 14f, 12.75f)
            curveTo(14f, 14.23f, 12f, 16.5f, 12f, 16.5f)
            curveTo(12f, 16.5f, 10f, 14.23f, 10f, 12.75f)
            curveTo(10f, 11.65f, 10.9f, 10.75f, 12f, 10.75f)
            close()
        }
    }

    /** The duress / SOS mark: a shield carrying an exclamation. */
    val CrisisAlert: ImageVector by lazy {
        strokeIcon("CrisisAlert") {
            moveTo(12f, 2.75f)
            lineTo(19.75f, 5.75f)
            lineTo(19.75f, 11.5f)
            curveTo(19.75f, 16.1f, 16.6f, 19.4f, 12f, 21.25f)
            curveTo(7.4f, 19.4f, 4.25f, 16.1f, 4.25f, 11.5f)
            lineTo(4.25f, 5.75f)
            close()
            moveTo(12f, 7.75f)
            lineTo(12f, 12.75f)
            moveTo(12f, 15.75f)
            lineTo(12.01f, 15.75f)
        }
    }

    /** Arm / disarm the guardian. */
    val Power: ImageVector by lazy {
        strokeIcon("Power") {
            moveTo(12f, 3.25f)
            lineTo(12f, 11.5f)
            moveTo(17.4f, 6.4f)
            curveTo(18.95f, 7.95f, 19.75f, 9.9f, 19.75f, 12.25f)
            curveTo(19.75f, 16.53f, 16.28f, 20f, 12f, 20f)
            curveTo(7.72f, 20f, 4.25f, 16.53f, 4.25f, 12.25f)
            curveTo(4.25f, 9.9f, 5.05f, 7.95f, 6.6f, 6.4f)
        }
    }

    /** Stop the active recording — the "this was accidental" control. */
    val StopSquare: ImageVector by lazy {
        strokeIcon("StopSquare") {
            moveTo(7.5f, 7.5f)
            lineTo(16.5f, 7.5f)
            lineTo(16.5f, 16.5f)
            lineTo(7.5f, 16.5f)
            close()
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
