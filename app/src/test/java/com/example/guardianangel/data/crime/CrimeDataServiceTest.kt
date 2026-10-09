package com.example.guardianangel.data.crime

import com.example.guardianangel.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CrimeDataServiceTest {

    private lateinit var crimeService: CrimeDataService

    @Before
    fun setUp() {
        crimeService = CrimeDataService()
    }

    @Test
    fun `high risk crime hotspots produce significantly lower safety scores`() {
        // Isolated blind alley hotspot
        val hazardPoint = GeoPoint(37.7750, -122.4110)
        // Marina / Presidio coordinates (low crime index in SF database)
        val marina = GeoPoint(37.8030, -122.4360)

        val hazardScoreDay = crimeService.getCrimeSafetyScoreForLocation(hazardPoint, hourOfDay = 14)
        val hazardScoreNight = crimeService.getCrimeSafetyScoreForLocation(hazardPoint, hourOfDay = 23)
        val marinaScoreDay = crimeService.getCrimeSafetyScoreForLocation(marina, hourOfDay = 14)

        // Marina should be safer than isolated hazard hotspot
        assertTrue(marinaScoreDay > hazardScoreDay)

        // Hazard hotspot at night should have an added nighttime penalty
        assertTrue(hazardScoreNight < hazardScoreDay)
    }

    @Test
    fun `safe haven query returns nearby emergency and safe haven facilities`() {
        val centralSF = GeoPoint(37.7749, -122.4194)
        val havens = crimeService.getNearbySafeHavens(centralSF, radiusMeters = 5000.0)

        assertTrue("Should return safe havens in SF", havens.isNotEmpty())
        assertTrue("Should contain a Police Station or Hospital", havens.any {
            it.type in setOf(SafeHavenType.PoliceStation, SafeHavenType.Hospital24x7)
        })
    }

    @Test
    fun `route recommendations provide safest option with crime mitigations`() {
        val origin = GeoPoint(37.7845, -122.4140) // Market / 5th St
        val destination = GeoPoint(37.7890, -122.4010) // Embarcadero / Financial

        val routes = crimeService.planSafeRoutes(origin, destination, hourOfDay = 22)

        assertTrue("Should provide at least 2 route choices", routes.size >= 2)

        val safest = routes.first()
        assertTrue("First route should be Angel's recommended safest route", safest.isRecommended)
        assertTrue("Safest route should have high lighting", safest.illuminationPercent >= 80)

        val fastest = routes.find { !it.isRecommended }
        assertNotNull("Should provide alternative fastest route", fastest)
        assertTrue(
            "Safest route safety score should exceed fastest route safety score in high crime area",
            safest.safetyScore >= fastest!!.safetyScore
        )
    }

    @Test
    fun `destination search returns relevant results`() {
        val query = "Market"
        val results = crimeService.searchDestinations(query)

        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.name.contains("Market", ignoreCase = true) })
    }
}
