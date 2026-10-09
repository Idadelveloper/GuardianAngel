package com.example.guardianangel.data.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.guardianangel.domain.model.GeoPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

private const val TAG = "LocationTracker"

/** A timestamped breadcrumb record for evidentiary and routing purposes. */
data class LocationBreadcrumb(
    val point: GeoPoint,
    val timestampMillis: Long,
    val accuracyMeters: Float,
    val address: String? = null,
)

/**
 * Provides live device location, geocoding, and breadcrumb trails.
 *
 * Uses Google Play Services [FusedLocationProviderClient] with fallback to Android's
 * system [LocationManager]. If permissions are absent, provides safe fallbacks.
 */
class LocationTracker(
    private val context: Context,
) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _breadcrumbs = MutableStateFlow<List<LocationBreadcrumb>>(emptyList())
    val breadcrumbs = _breadcrumbs.asStateFlow()

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): GeoPoint? {
        if (!hasLocationPermission()) return null
        return suspendCancellableCoroutine { continuation ->
            try {
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            val point = GeoPoint(location.latitude, location.longitude)
                            recordBreadcrumb(point, location.accuracy)
                            continuation.resume(point)
                        } else {
                            // Fallback to last known location
                            fusedClient.lastLocation
                                .addOnSuccessListener { lastLoc: Location? ->
                                    val fallback = lastLoc?.let { GeoPoint(it.latitude, it.longitude) }
                                    if (fallback != null) recordBreadcrumb(fallback, lastLoc.accuracy)
                                    continuation.resume(fallback)
                                }
                                .addOnFailureListener { continuation.resume(null) }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Fused location lookup failed: ${e.message}")
                        continuation.resume(null)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting location", e)
                continuation.resume(null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun observeLocation(): Flow<GeoPoint> = callbackFlow {
        if (!hasLocationPermission()) {
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .setMinUpdateDistanceMeters(5f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val point = GeoPoint(loc.latitude, loc.longitude)
                recordBreadcrumb(point, loc.accuracy)
                trySend(point)
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: Exception) {
            Log.e(TAG, "Failed requesting location updates", e)
            close(e)
        }

        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }

    private fun recordBreadcrumb(point: GeoPoint, accuracy: Float) {
        val entry = LocationBreadcrumb(
            point = point,
            timestampMillis = System.currentTimeMillis(),
            accuracyMeters = accuracy,
            address = reverseGeocode(point),
        )
        _breadcrumbs.value = (_breadcrumbs.value + entry).takeLast(50)
    }

    /** Translates lat/lon into human-readable street/neighborhood label. */
    fun reverseGeocode(point: GeoPoint): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Tiramisu has an async listener, but for quick synchronous snapshots:
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocation(point.latitude, point.longitude, 1)
                results?.firstOrNull()?.let { addr ->
                    val street = addr.thoroughfare ?: addr.featureName
                    val city = addr.locality ?: addr.subAdminArea
                    listOfNotNull(street, city).joinToString(", ")
                }
            } else {
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocation(point.latitude, point.longitude, 1)
                results?.firstOrNull()?.let { addr ->
                    val street = addr.thoroughfare ?: addr.featureName
                    val city = addr.locality ?: addr.subAdminArea
                    listOfNotNull(street, city).joinToString(", ")
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
