package com.example.guardianangel.data.routes

import com.example.guardianangel.domain.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Real implementation calling Google Routes API v2 computeRoutes.
 *
 * Invariant: Direct API call is suitable for restricted development keys;
 * production deployment documents the transition to a secure backend proxy.
 * If the API key is unconfigured or call fails, gracefully defers to [fallbackProvider].
 */
class GoogleRoutesApiProvider(
    private val apiKeyProvider: () -> String,
    private val fallbackProvider: RouteProvider? = null,
) : RouteProvider {

    override suspend fun getWalkingRoutes(
        origin: GeoPoint,
        destination: GeoPoint,
    ): List<RawRouteCandidate> {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank() || apiKey.startsWith("AIzaSyPlaceholder")) {
            return fallbackProvider?.getWalkingRoutes(origin, destination) ?: emptyList()
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://routes.googleapis.com/directions/v2:computeRoutes")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Goog-Api-Key", apiKey)
                    setRequestProperty(
                        "X-Goog-FieldMask",
                        "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline,routes.description"
                    )
                    doOutput = true
                }

                val payload = JSONObject().apply {
                    put("origin", JSONObject().apply {
                        put("location", JSONObject().apply {
                            put("latLng", JSONObject().apply {
                                put("latitude", origin.latitude)
                                put("longitude", origin.longitude)
                            })
                        })
                    })
                    put("destination", JSONObject().apply {
                        put("location", JSONObject().apply {
                            put("latLng", JSONObject().apply {
                                put("latitude", destination.latitude)
                                put("longitude", destination.longitude)
                            })
                        })
                    })
                    put("travelMode", "WALK")
                    put("computeAlternativeRoutes", true)
                }

                conn.outputStream.bufferedWriter().use { it.write(payload.toString()) }

                if (conn.responseCode != 200) {
                    conn.disconnect()
                    return@withContext fallbackProvider?.getWalkingRoutes(origin, destination) ?: emptyList()
                }

                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val json = JSONObject(responseText)
                if (!json.has("routes")) {
                    return@withContext fallbackProvider?.getWalkingRoutes(origin, destination) ?: emptyList()
                }

                val routesArray = json.getJSONArray("routes")
                val candidates = mutableListOf<RawRouteCandidate>()

                for (i in 0 until routesArray.length()) {
                    val r = routesArray.getJSONObject(i)
                    val distanceMeters = r.optInt("distanceMeters", 800)
                    val durationStr = r.optString("duration", "600s")
                    val durationSeconds = durationStr.removeSuffix("s").toIntOrNull() ?: (distanceMeters / 1.3).toInt()
                    val durationMinutes = (durationSeconds + 59) / 60

                    val encodedPolyline = r.getJSONObject("polyline").getString("encodedPolyline")
                    val points = PolylineDecoder.decode(encodedPolyline)
                    val description = r.optString("description", if (i == 0) "Primary Street Corridor" else "Alternative Corridor ${i + 1}")

                    candidates.add(
                        RawRouteCandidate(
                            routeId = "google-route-$i",
                            label = description,
                            durationMinutes = durationMinutes,
                            distanceMeters = distanceMeters,
                            polylinePoints = points,
                        )
                    )
                }

                if (candidates.isNotEmpty()) candidates else (fallbackProvider?.getWalkingRoutes(origin, destination) ?: emptyList())
            } catch (e: Exception) {
                fallbackProvider?.getWalkingRoutes(origin, destination) ?: emptyList()
            }
        }
    }
}
