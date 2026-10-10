package com.example.guardianangel.data.platform

import android.content.Context
import android.content.pm.PackageManager

/**
 * Whether this build has a Maps API key that can actually fetch tiles.
 *
 * ## Why this exists
 *
 * With the placeholder key the Maps SDK does not error — it composes a map, draws the
 * Google watermark, and renders *nothing*. Every session row would show an empty box
 * that looks like a loading bug, and the user would have no way to know the difference
 * between "no key" and "no path recorded".
 *
 * So the app asks first and draws its own trail when the answer is no. The fallback is
 * not a degraded placeholder: for a short walk it is arguably clearer than a map, because
 * it shows only the path and what happened on it.
 */
object MapsAvailability {

    /** The value `build.gradle.kts` substitutes when no key is configured. */
    private const val PLACEHOLDER = "AIzaSyPlaceholderGuardianAngelKey"

    private const val META_DATA_KEY = "com.google.android.geo.API_KEY"

    @Volatile
    private var cached: Boolean? = null

    /**
     * True when a real key is present.
     *
     * Cached: the manifest cannot change while the process is alive, and this is read
     * on every list row.
     */
    fun hasMapKey(context: Context): Boolean = cached ?: compute(context).also { cached = it }

    private fun compute(context: Context): Boolean = runCatching {
        val info = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )
        val key = info.metaData?.getString(META_DATA_KEY).orEmpty()
        key.isNotBlank() && key != PLACEHOLDER && !key.contains("Placeholder", ignoreCase = true)
    }.getOrDefault(false)
}
