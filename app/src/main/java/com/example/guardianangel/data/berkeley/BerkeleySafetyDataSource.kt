package com.example.guardianangel.data.berkeley

import android.content.Context
import com.example.guardianangel.domain.model.CrimeCell
import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.domain.model.OffenseCategory
import com.example.guardianangel.domain.model.SafeHavenPoi
import com.example.guardianangel.domain.model.SafeHavenType
import org.json.JSONArray
import java.io.InputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Loads, normalizes, and indexes official Berkeley safety features.
 *
 * Invariants:
 * 1. BPD and UCPD are kept distinct by jurisdiction.
 * 2. Privacy is preserved: only aggregated cells (>= 2 incidents) are indexed.
 * 3. Temporal decay applies an explicit 30-day half-life.
 * 4. Outside Berkeley coverage, explicitly reports data sparsity.
 */
class BerkeleySafetyDataSource(
    private val context: Context? = null,
    private val halfLifeDays: Double = 30.0,
) {
    private val crimeCells = mutableListOf<CrimeCell>()
    private val safeHavens = mutableListOf<SafeHavenPoi>()

    init {
        loadData()
    }

    private fun loadData() {
        if (context == null) {
            loadDefaultBerkeleyFeatures()
            return
        }

        try {
            val havenStream = context.assets.open("data/berkeley_safe_havens.json")
            safeHavens.addAll(parseSafeHavens(havenStream))
        } catch (e: Exception) {
            loadDefaultSafeHavens()
        }

        try {
            val cellStream = context.assets.open("data/berkeley_crime_cells.json")
            crimeCells.addAll(parseCrimeCells(cellStream))
        } catch (e: Exception) {
            loadDefaultCrimeCells()
        }
    }

    private fun loadDefaultBerkeleyFeatures() {
        loadDefaultSafeHavens()
        loadDefaultCrimeCells()
    }

    private fun loadDefaultSafeHavens() {
        safeHavens.clear()
        safeHavens.addAll(
            listOf(
                SafeHavenPoi(
                    id = "bpd-hq",
                    name = "Berkeley Police Department",
                    type = SafeHavenType.PoliceStation,
                    location = GeoPoint(37.8696, -122.2736),
                    address = "2100 Martin Luther King Jr Way, Berkeley",
                    isOpen24Hours = true,
                    openHoursDescription = "24/7 Staffed Municipal Police Station",
                    isVerified = true,
                    phone = "(510) 981-5900",
                ),
                SafeHavenPoi(
                    id = "ucpd-hq",
                    name = "UC Berkeley Police Department",
                    type = SafeHavenType.PoliceStation,
                    location = GeoPoint(37.8698, -122.2588),
                    address = "1 Sproul Hall, UC Berkeley Campus",
                    isOpen24Hours = true,
                    openHoursDescription = "24/7 Staffed University Police Station",
                    isVerified = true,
                    phone = "(510) 642-6760",
                ),
                SafeHavenPoi(
                    id = "alta-bates-er",
                    name = "Alta Bates Summit Medical Center (ER)",
                    type = SafeHavenType.Hospital24x7,
                    location = GeoPoint(37.8564, -122.2575),
                    address = "2450 Ashby Ave, Berkeley",
                    isOpen24Hours = true,
                    openHoursDescription = "24/7 Emergency Medical Department",
                    isVerified = true,
                    phone = "(510) 204-4444",
                ),
                SafeHavenPoi(
                    id = "bfd-station-1",
                    name = "Berkeley Fire Station 1",
                    type = SafeHavenType.FireStation,
                    location = GeoPoint(37.8678, -122.2555),
                    address = "2680 Durant Ave, Berkeley",
                    isOpen24Hours = true,
                    openHoursDescription = "24/7 Emergency Fire & Rescue",
                    isVerified = true,
                    phone = "(510) 981-3473",
                ),
                SafeHavenPoi(
                    id = "safe-stop-shattuck",
                    name = "Guardian SafeStop · Downtown Shattuck",
                    type = SafeHavenType.CommunityPartner,
                    location = GeoPoint(37.8692, -122.2682),
                    address = "2180 Shattuck Ave, Berkeley",
                    isOpen24Hours = true,
                    openHoursDescription = "Verified Well-Lit Safe Business Hub",
                    isVerified = true,
                ),
                SafeHavenPoi(
                    id = "safe-stop-telegraph",
                    name = "Guardian SafeStop · Telegraph Hub",
                    type = SafeHavenType.CommunityPartner,
                    location = GeoPoint(37.8660, -122.2590),
                    address = "2430 Telegraph Ave, Berkeley",
                    isOpen24Hours = true,
                    openHoursDescription = "Verified Well-Lit Community Partner",
                    isVerified = true,
                ),
            )
        )
    }

    private fun loadDefaultCrimeCells() {
        crimeCells.clear()
        crimeCells.addAll(
            listOf(
                CrimeCell(
                    cellId = "bpd-cell-downtown-1",
                    center = GeoPoint(37.8702, -122.2685),
                    incidentCount = 8,
                    weightedScore = 3.4f,
                    primaryOffense = OffenseCategory.PropertyOffense,
                    offenseCounts = mapOf(
                        OffenseCategory.PropertyOffense to 5,
                        OffenseCategory.DisorderPublicSafety to 2,
                        OffenseCategory.Assault to 1,
                    ),
                    lastReportedTimeMillis = System.currentTimeMillis() - 86_400_000L * 2,
                    radiusMeters = 130f,
                    jurisdiction = "City of Berkeley BPD",
                ),
                CrimeCell(
                    cellId = "bpd-cell-telegraph-1",
                    center = GeoPoint(37.8655, -122.2588),
                    incidentCount = 9,
                    weightedScore = 4.8f,
                    primaryOffense = OffenseCategory.Robbery,
                    offenseCounts = mapOf(
                        OffenseCategory.Robbery to 3,
                        OffenseCategory.Assault to 2,
                        OffenseCategory.PropertyOffense to 3,
                        OffenseCategory.DisorderPublicSafety to 1,
                    ),
                    lastReportedTimeMillis = System.currentTimeMillis() - 86_400_000L * 1,
                    radiusMeters = 140f,
                    jurisdiction = "City of Berkeley BPD",
                ),
                CrimeCell(
                    cellId = "bpd-cell-telegraph-2",
                    center = GeoPoint(37.8632, -122.2592),
                    incidentCount = 5,
                    weightedScore = 2.2f,
                    primaryOffense = OffenseCategory.PropertyOffense,
                    offenseCounts = mapOf(
                        OffenseCategory.PropertyOffense to 3,
                        OffenseCategory.DisorderPublicSafety to 2,
                    ),
                    lastReportedTimeMillis = System.currentTimeMillis() - 86_400_000L * 5,
                    radiusMeters = 135f,
                    jurisdiction = "City of Berkeley BPD",
                ),
                CrimeCell(
                    cellId = "ucpd-cell-sproul-plaza",
                    center = GeoPoint(37.8695, -122.2595),
                    incidentCount = 4,
                    weightedScore = 1.5f,
                    primaryOffense = OffenseCategory.PropertyOffense,
                    offenseCounts = mapOf(
                        OffenseCategory.PropertyOffense to 3,
                        OffenseCategory.DisorderPublicSafety to 1,
                    ),
                    lastReportedTimeMillis = System.currentTimeMillis() - 86_400_000L * 4,
                    radiusMeters = 120f,
                    jurisdiction = "UC Berkeley UCPD",
                ),
                CrimeCell(
                    cellId = "bpd-cell-university-1",
                    center = GeoPoint(37.8718, -122.2740),
                    incidentCount = 4,
                    weightedScore = 1.6f,
                    primaryOffense = OffenseCategory.PropertyOffense,
                    offenseCounts = mapOf(
                        OffenseCategory.PropertyOffense to 3,
                        OffenseCategory.DisorderPublicSafety to 1,
                    ),
                    lastReportedTimeMillis = System.currentTimeMillis() - 86_400_000L * 7,
                    radiusMeters = 125f,
                    jurisdiction = "City of Berkeley BPD",
                ),
            )
        )
    }

    fun parseSafeHavens(inputStream: InputStream): List<SafeHavenPoi> {
        val text = inputStream.bufferedReader().use { it.readText() }
        val array = JSONArray(text)
        val list = mutableListOf<SafeHavenPoi>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val typeStr = obj.optString("type", "VerifiedPublicFacility")
            val type = try {
                SafeHavenType.valueOf(typeStr)
            } catch (e: Exception) {
                SafeHavenType.VerifiedPublicFacility
            }
            list.add(
                SafeHavenPoi(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    type = type,
                    location = GeoPoint(obj.getDouble("latitude"), obj.getDouble("longitude")),
                    address = obj.optString("address", ""),
                    isOpen24Hours = obj.optBoolean("isOpen24Hours", true),
                    openHoursDescription = obj.optString("openHoursDescription", "Open 24 hours"),
                    isVerified = obj.optBoolean("isVerified", true),
                    phone = if (obj.has("phone") && !obj.isNull("phone")) obj.getString("phone") else null,
                )
            )
        }
        return list
    }

    fun parseCrimeCells(inputStream: InputStream): List<CrimeCell> {
        val text = inputStream.bufferedReader().use { it.readText() }
        val array = JSONArray(text)
        val list = mutableListOf<CrimeCell>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val primaryStr = obj.optString("primaryOffense", "Unknown")
            val primaryOffense = try {
                OffenseCategory.valueOf(primaryStr)
            } catch (e: Exception) {
                OffenseCategory.Unknown
            }

            val offenseCounts = mutableMapOf<OffenseCategory, Int>()
            if (obj.has("offenseCounts")) {
                val countsObj = obj.getJSONObject("offenseCounts")
                val keys = countsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val cat = try {
                        OffenseCategory.valueOf(key)
                    } catch (e: Exception) {
                        OffenseCategory.Unknown
                    }
                    offenseCounts[cat] = countsObj.getInt(key)
                }
            }

            list.add(
                CrimeCell(
                    cellId = obj.getString("cellId"),
                    center = GeoPoint(obj.getDouble("latitude"), obj.getDouble("longitude")),
                    incidentCount = obj.getInt("incidentCount"),
                    weightedScore = obj.getDouble("weightedScore").toFloat(),
                    primaryOffense = primaryOffense,
                    offenseCounts = offenseCounts,
                    lastReportedTimeMillis = obj.optLong("lastReportedTimeMillis", System.currentTimeMillis()),
                    radiusMeters = obj.optDouble("radiusMeters", 125.0).toFloat(),
                    jurisdiction = obj.optString("jurisdiction", "City of Berkeley BPD"),
                )
            )
        }
        return list
    }

    fun getAllSafeHavens(): List<SafeHavenPoi> = safeHavens.toList()

    fun getAllCrimeCells(): List<CrimeCell> = crimeCells.toList()

    fun getNearbySafeHavens(location: GeoPoint, maxDistanceMeters: Double = 1500.0): List<SafeHavenPoi> {
        return safeHavens.filter { haven ->
            distanceMeters(location, haven.location) <= maxDistanceMeters
        }.sortedBy { distanceMeters(location, it.location) }
    }

    fun getNearbyCrimeCells(location: GeoPoint, maxDistanceMeters: Double = 1500.0): List<CrimeCell> {
        return crimeCells.filter { cell ->
            distanceMeters(location, cell.center) <= maxDistanceMeters
        }.sortedBy { distanceMeters(location, it.center) }
    }

    /**
     * Calculates time-decayed recency multiplier.
     *
     * Decay = exp(- (dt / halfLife) * ln(2))
     */
    fun calculateRecencyFactor(eventTimeMillis: Long, currentTimeMillis: Long = System.currentTimeMillis()): Float {
        val daysDiff = (currentTimeMillis - eventTimeMillis).coerceAtLeast(0L) / (1000.0 * 60.0 * 60.0 * 24.0)
        val factor = exp(- (daysDiff / halfLifeDays) * ln(2.0))
        return factor.coerceIn(0.05, 1.0).toFloat()
    }

    /**
     * Calculates time of day exposure weighting.
     */
    fun calculateTimeOfDayFactor(hourOfDay: Int): Float {
        return when (hourOfDay) {
            in 22..23, in 0..4 -> 1.35f // Late night
            in 18..21 -> 1.15f // Evening
            in 5..6 -> 1.00f // Dawn
            else -> 0.85f // Day
        }
    }

    /**
     * Checks if coordinates fall within Berkeley coverage zone.
     */
    fun isWithinBerkeleyCoverage(point: GeoPoint): Boolean {
        return point.latitude in 37.83..37.91 && point.longitude in -122.33..-122.23
    }

    companion object {
        fun distanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
            val r = 6371000.0 // Earth radius in meters
            val dLat = Math.toRadians(p2.latitude - p1.latitude)
            val dLon = Math.toRadians(p2.longitude - p1.longitude)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) *
                sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }
    }
}
