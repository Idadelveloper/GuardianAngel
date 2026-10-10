package com.example.guardianangel.data.places

import com.example.guardianangel.domain.model.Destination
import com.example.guardianangel.domain.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class PlacePrediction(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
    val fullText: String,
)

interface PlacesSearchProvider {
    suspend fun searchPredictions(query: String, sessionToken: String? = null): List<PlacePrediction>
    suspend fun fetchPlaceDetails(placeId: String, sessionToken: String? = null): Destination?
}

/**
 * Places Search Provider supporting Places API (New) with Berkeley-area biasing.
 *
 * Includes fallback catalog of key Berkeley civic, campus, and landmark destinations
 * so that offline testing and missing API key states always function smoothly.
 */
class GooglePlacesSearchProvider(
    private val apiKeyProvider: () -> String,
) : PlacesSearchProvider {

    private val localCatalog = listOf(
        Destination("pl-doe-library", "Doe Memorial Library", "UC Berkeley Campus, Berkeley, CA", GeoPoint(37.8722, -122.2595), walkingMinutes = 10, isSafeHaven = true),
        Destination("pl-bpd", "Berkeley Police Headquarters", "2100 Martin Luther King Jr Way, Berkeley, CA", GeoPoint(37.8696, -122.2736), walkingMinutes = 8, isSafeHaven = true),
        Destination("pl-sproul", "Sproul Plaza & Hall", "Bancroft Way & Telegraph Ave, Berkeley, CA", GeoPoint(37.8698, -122.2588), walkingMinutes = 11, isSafeHaven = true),
        Destination("pl-alta-bates", "Alta Bates Summit Emergency Room", "2450 Ashby Ave, Berkeley, CA", GeoPoint(37.8564, -122.2575), walkingMinutes = 18, isSafeHaven = true),
        Destination("pl-trader-joes", "Trader Joe's (Downtown)", "1885 University Ave, Berkeley, CA", GeoPoint(37.8715, -122.2738), walkingMinutes = 6),
        Destination("pl-target", "Target Berkeley", "2187 Shattuck Ave, Berkeley, CA", GeoPoint(37.8690, -122.2680), walkingMinutes = 7),
        Destination("pl-bart-downtown", "Downtown Berkeley BART Station", "2160 Shattuck Ave, Berkeley, CA", GeoPoint(37.8701, -122.2682), walkingMinutes = 5),
        Destination("pl-bart-ashby", "Ashby BART Station", "3100 Adeline St, Berkeley, CA", GeoPoint(37.8530, -122.2698), walkingMinutes = 19),
        Destination("pl-fire-station-1", "Berkeley Fire Station 1", "2680 Durant Ave, Berkeley, CA", GeoPoint(37.8678, -122.2555), walkingMinutes = 12, isSafeHaven = true),
        Destination("pl-safeway-north", "Safeway (Shattuck)", "1444 Shattuck Ave, Berkeley, CA", GeoPoint(37.8812, -122.2692), walkingMinutes = 14),
    )

    override suspend fun searchPredictions(
        query: String,
        sessionToken: String?,
    ): List<PlacePrediction> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank() || apiKey.startsWith("AIzaSyPlaceholder")) {
            return searchLocalCatalog(trimmed)
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://places.googleapis.com/v1/places:autocomplete")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Goog-Api-Key", apiKey)
                    doOutput = true
                }

                val payload = JSONObject().apply {
                    put("input", trimmed)
                    put("locationBias", JSONObject().apply {
                        put("circle", JSONObject().apply {
                            put("center", JSONObject().apply {
                                put("latitude", 37.8715)
                                put("longitude", -122.2730)
                            })
                            put("radius", 15000.0) // 15km bias around Berkeley
                        })
                    })
                    if (sessionToken != null) {
                        put("sessionToken", sessionToken)
                    }
                }

                conn.outputStream.bufferedWriter().use { it.write(payload.toString()) }

                if (conn.responseCode != 200) {
                    conn.disconnect()
                    return@withContext searchLocalCatalog(trimmed)
                }

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val json = JSONObject(response)
                val suggestions = json.optJSONArray("suggestions") ?: return@withContext searchLocalCatalog(trimmed)
                val results = mutableListOf<PlacePrediction>()

                for (i in 0 until suggestions.length()) {
                    val s = suggestions.getJSONObject(i)
                    if (s.has("placePrediction")) {
                        val p = s.getJSONObject("placePrediction")
                        val placeId = p.getString("placeId")
                        val textObj = p.getJSONObject("text")
                        val full = textObj.getString("text")
                        val structured = p.optJSONObject("structuredFormat")
                        val primary = structured?.optJSONObject("mainText")?.optString("text") ?: full
                        val secondary = structured?.optJSONObject("secondaryText")?.optString("text") ?: ""

                        results.add(
                            PlacePrediction(
                                placeId = placeId,
                                primaryText = primary,
                                secondaryText = secondary,
                                fullText = full,
                            )
                        )
                    }
                }

                if (results.isNotEmpty()) results else searchLocalCatalog(trimmed)
            } catch (e: Exception) {
                searchLocalCatalog(trimmed)
            }
        }
    }

    override suspend fun fetchPlaceDetails(
        placeId: String,
        sessionToken: String?,
    ): Destination? {
        val local = localCatalog.firstOrNull { it.id == placeId }
        if (local != null) return local

        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank() || apiKey.startsWith("AIzaSyPlaceholder")) {
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://places.googleapis.com/v1/places/$placeId")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("X-Goog-Api-Key", apiKey)
                    setRequestProperty("X-Goog-FieldMask", "id,displayName,formattedAddress,location")
                }

                if (conn.responseCode != 200) {
                    conn.disconnect()
                    return@withContext null
                }

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val json = JSONObject(response)
                val displayNameObj = json.optJSONObject("displayName")
                val name = displayNameObj?.optString("text") ?: "Selected Location"
                val address = json.optString("formattedAddress", "")
                val locObj = json.getJSONObject("location")
                val lat = locObj.getDouble("latitude")
                val lon = locObj.getDouble("longitude")

                Destination(
                    id = placeId,
                    name = name,
                    address = address,
                    point = GeoPoint(lat, lon),
                    walkingMinutes = 12,
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun searchLocalCatalog(query: String): List<PlacePrediction> {
        val q = query.lowercase()
        return localCatalog.filter {
            it.name.lowercase().contains(q) || it.address.lowercase().contains(q)
        }.map {
            PlacePrediction(
                placeId = it.id,
                primaryText = it.name,
                secondaryText = it.address,
                fullText = "${it.name}, ${it.address}",
            )
        }
    }
}
