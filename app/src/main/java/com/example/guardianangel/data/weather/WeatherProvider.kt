package com.example.guardianangel.data.weather

import com.example.guardianangel.domain.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class WeatherCondition(
    val temperatureFahrenheit: Int,
    val conditionDescription: String,
    val precipitationProbabilityPercent: Int = 0,
    val windSpeedMph: Int = 5,
    val isSevereAlert: Boolean = false,
    val alertDescription: String? = null,
    val walkabilityRisk: Float = 0.0f, // 0.0 (ideal walking) to 1.0 (severe hazard)
    val isAvailable: Boolean = true,
)

interface WeatherProvider {
    suspend fun getConditions(location: GeoPoint): WeatherCondition?
}

/**
 * National Weather Service (NWS) API provider for Berkeley and US coordinates.
 *
 * Free, official public API (api.weather.gov). Caches hourly results to preserve battery.
 * If NWS is unreachable, returns null rather than fabricating conditions.
 */
class NationalWeatherServiceApiProvider : WeatherProvider {
    private var cachedCondition: WeatherCondition? = null
    private var cacheTimestampMillis: Long = 0
    private val cacheTtlMillis: Long = 30 * 60 * 1000L // 30 mins

    override suspend fun getConditions(location: GeoPoint): WeatherCondition? {
        val now = System.currentTimeMillis()
        if (cachedCondition != null && (now - cacheTimestampMillis) < cacheTtlMillis) {
            return cachedCondition
        }

        return withContext(Dispatchers.IO) {
            try {
                // Round to 4 decimal places for NWS grid point lookup
                val lat = String.format(java.util.Locale.US, "%.4f", location.latitude)
                val lon = String.format(java.util.Locale.US, "%.4f", location.longitude)
                val pointUrl = URL("https://api.weather.gov/points/$lat,$lon")

                val conn = (pointUrl.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("User-Agent", "GuardianAngelSafetyApp/1.0 (contact@guardianangel.local)")
                    setRequestProperty("Accept", "application/geo+json")
                }

                if (conn.responseCode != 200) {
                    return@withContext cachedCondition
                }

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val pointJson = JSONObject(body)
                val properties = pointJson.getJSONObject("properties")
                val forecastHourlyUrl = properties.getString("forecastHourly")

                val forecastConn = (URL(forecastHourlyUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("User-Agent", "GuardianAngelSafetyApp/1.0 (contact@guardianangel.local)")
                    setRequestProperty("Accept", "application/geo+json")
                }

                if (forecastConn.responseCode != 200) {
                    return@withContext cachedCondition
                }

                val forecastBody = forecastConn.inputStream.bufferedReader().use { it.readText() }
                forecastConn.disconnect()

                val forecastJson = JSONObject(forecastBody)
                val periods = forecastJson.getJSONObject("properties").getJSONArray("periods")
                if (periods.length() == 0) return@withContext cachedCondition

                val currentPeriod = periods.getJSONObject(0)
                val temp = currentPeriod.getInt("temperature")
                val shortForecast = currentPeriod.getString("shortForecast")
                val windSpeedStr = currentPeriod.getString("windSpeed")
                val windSpeed = windSpeedStr.filter { it.isDigit() }.toIntOrNull() ?: 5

                var precip = 0
                if (currentPeriod.has("probabilityOfPrecipitation") && !currentPeriod.isNull("probabilityOfPrecipitation")) {
                    precip = currentPeriod.getJSONObject("probabilityOfPrecipitation").optInt("value", 0)
                }

                val lowerDesc = shortForecast.lowercase()
                val isSevere = lowerDesc.contains("flood") || lowerDesc.contains("thunder") ||
                    lowerDesc.contains("tornado") || lowerDesc.contains("gale")

                val walkabilityRisk = when {
                    isSevere -> 0.85f
                    lowerDesc.contains("heavy rain") -> 0.40f
                    lowerDesc.contains("rain") || lowerDesc.contains("shower") -> 0.20f
                    lowerDesc.contains("fog") || lowerDesc.contains("mist") -> 0.15f
                    else -> 0.02f
                }

                val condition = WeatherCondition(
                    temperatureFahrenheit = temp,
                    conditionDescription = shortForecast,
                    precipitationProbabilityPercent = precip,
                    windSpeedMph = windSpeed,
                    isSevereAlert = isSevere,
                    alertDescription = if (isSevere) shortForecast else null,
                    walkabilityRisk = walkabilityRisk,
                    isAvailable = true,
                )

                cachedCondition = condition
                cacheTimestampMillis = now
                condition
            } catch (e: Exception) {
                // Network unavailable or rate limited — return cached if available or null
                cachedCondition
            }
        }
    }
}

class FakeWeatherProvider(
    private val condition: WeatherCondition = WeatherCondition(
        temperatureFahrenheit = 65,
        conditionDescription = "Clear and calm",
        precipitationProbabilityPercent = 0,
        windSpeedMph = 4,
        isSevereAlert = false,
        walkabilityRisk = 0.02f,
        isAvailable = true,
    )
) : WeatherProvider {
    override suspend fun getConditions(location: GeoPoint): WeatherCondition? = condition
}
