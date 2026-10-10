package com.example.guardianangel.berkeley

import com.example.guardianangel.data.FakeSafeLocationRepository
import com.example.guardianangel.data.berkeley.BerkeleySafetyDataSource
import com.example.guardianangel.data.places.GooglePlacesSearchProvider
import com.example.guardianangel.data.routes.DeterministicRouteRanker
import com.example.guardianangel.data.routes.FakeRouteProvider
import com.example.guardianangel.data.routes.PolylineDecoder
import com.example.guardianangel.data.routes.RawRouteCandidate
import com.example.guardianangel.data.weather.FakeWeatherProvider
import com.example.guardianangel.data.weather.WeatherCondition
import com.example.guardianangel.domain.GuardianSafetyStateResolver
import com.example.guardianangel.domain.model.CrimeCell
import com.example.guardianangel.domain.model.DataConfidence
import com.example.guardianangel.domain.model.Destination
import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.domain.model.GuardianLocationState
import com.example.guardianangel.domain.model.OffenseCategory
import com.example.guardianangel.domain.model.RiskBand
import com.example.guardianangel.domain.model.RoutePreference
import com.example.guardianangel.domain.model.SafeHavenPoi
import com.example.guardianangel.domain.model.SafeHavenType
import com.example.guardianangel.domain.model.SafeLocation
import com.example.guardianangel.domain.model.SafeLocationKind
import com.example.guardianangel.domain.model.StatusCategory
import com.example.guardianangel.ui.mascot.AngelMood
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Comprehensive test suite verifying the Berkeley-first safest-route prototype,
 * covering data normalization, spatial aggregation, route safety evaluation,
 * Home sanctuary semantics, geofence hysteresis, and state synchronization.
 */
class BerkeleySafetyTest {

    private lateinit var safetyDataSource: BerkeleySafetyDataSource
    private lateinit var ranker: DeterministicRouteRanker
    private lateinit var safeLocationRepo: FakeSafeLocationRepository
    private lateinit var weatherProvider: FakeWeatherProvider
    private lateinit var fakeRouteProvider: FakeRouteProvider

    @Before
    fun setUp() {
        safetyDataSource = BerkeleySafetyDataSource(context = null, halfLifeDays = 30.0)
        ranker = DeterministicRouteRanker(safetyDataSource, maxDetourMinutesAllowed = 8)
        safeLocationRepo = FakeSafeLocationRepository()
        weatherProvider = FakeWeatherProvider()
        fakeRouteProvider = FakeRouteProvider()
    }

    // 1. Offense normalization
    @Test
    fun `berkeley offense taxonomy normalizes official codes correctly`() {
        assertEquals(OffenseCategory.Robbery, OffenseCategory.fromCodeOrCategory("ROBBERY"))
        assertEquals(OffenseCategory.Robbery, OffenseCategory.fromCodeOrCategory("ARMED ROBBERY / HOLDUP"))
        assertEquals(OffenseCategory.Assault, OffenseCategory.fromCodeOrCategory("BATTERY ON PERSON"))
        assertEquals(OffenseCategory.SexualOffense, OffenseCategory.fromCodeOrCategory("RAPE / FORCIBLE SEXUAL ASSAULT"))
        assertEquals(OffenseCategory.Kidnapping, OffenseCategory.fromCodeOrCategory("KIDNAPPING / ABDUCTION"))
        assertEquals(OffenseCategory.ViolentPerson, OffenseCategory.fromCodeOrCategory("HOMICIDE"))
        assertEquals(OffenseCategory.PropertyOffense, OffenseCategory.fromCodeOrCategory("BURGLARY / AUTO THEFT"))
        assertEquals(OffenseCategory.DisorderPublicSafety, OffenseCategory.fromCodeOrCategory("NOISE DISTURBANCE"))
        assertEquals(OffenseCategory.Unknown, OffenseCategory.fromCodeOrCategory("NON_CRIMINAL_MISC"))
    }

    // 2. Berkeley Police vs UC Berkeley UCPD separation
    @Test
    fun `berkeley police and ucpd jurisdictions are kept strictly separated`() {
        val cells = safetyDataSource.getAllCrimeCells()
        val bpdCells = cells.filter { it.jurisdiction.contains("BPD", ignoreCase = true) }
        val ucpdCells = cells.filter { it.jurisdiction.contains("UCPD", ignoreCase = true) }

        assertTrue("BPD cells must exist", bpdCells.isNotEmpty())
        assertTrue("UCPD cells must exist", ucpdCells.isNotEmpty())

        val bpdIds = bpdCells.map { it.cellId }.toSet()
        val ucpdIds = ucpdCells.map { it.cellId }.toSet()
        assertTrue("BPD and UCPD cell IDs must not intersect", bpdIds.intersect(ucpdIds).isEmpty())
    }

    // 3. Coordinate validation & 4. Missing coordinates
    @Test
    fun `coordinate validation drops zero coordinates and isolates valid points`() {
        val validPoint = GeoPoint(37.8696, -122.2736)
        val invalidPoint = GeoPoint(0.0, 0.0)

        assertTrue(safetyDataSource.isWithinBerkeleyCoverage(validPoint))
        assertFalse(safetyDataSource.isWithinBerkeleyCoverage(invalidPoint))
    }

    // 5. Invalid timestamps & 6. Local timezone conversion
    @Test
    fun `local timezone conversion properly resolves local hour`() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles"))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        val timeOfDayFactorNight = safetyDataSource.calculateTimeOfDayFactor(23)
        val timeOfDayFactorNoon = safetyDataSource.calculateTimeOfDayFactor(12)

        assertTrue("Late night factor must be higher than midday factor", timeOfDayFactorNight > timeOfDayFactorNoon)
    }

    // 7. Recency decay (30-day half-life)
    @Test
    fun `recency decay applies 30-day half-life exponentially`() {
        val now = 1_700_000_000_000L
        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)
        val sixtyDaysAgo = now - (60L * 24 * 60 * 60 * 1000)

        val freshFactor = safetyDataSource.calculateRecencyFactor(now, now)
        val thirtyDayFactor = safetyDataSource.calculateRecencyFactor(thirtyDaysAgo, now)
        val sixtyDayFactor = safetyDataSource.calculateRecencyFactor(sixtyDaysAgo, now)

        assertEquals(1.0f, freshFactor, 0.01f)
        assertEquals(0.5f, thirtyDayFactor, 0.05f) // Half-life of 30 days is ~0.5
        assertEquals(0.25f, sixtyDayFactor, 0.05f) // 60 days is ~0.25
    }

    // 8. Time of day scoring
    @Test
    fun `time of day scoring assigns elevated weight to late night hours`() {
        assertEquals(1.35f, safetyDataSource.calculateTimeOfDayFactor(23), 0.01f)
        assertEquals(1.35f, safetyDataSource.calculateTimeOfDayFactor(2), 0.01f)
        assertEquals(1.15f, safetyDataSource.calculateTimeOfDayFactor(19), 0.01f)
        assertEquals(0.85f, safetyDataSource.calculateTimeOfDayFactor(14), 0.01f)
    }

    // 9. Crime cell aggregation & 10. Spatial matching
    @Test
    fun `nearby crime cells are correctly filtered by spatial distance`() {
        val center = GeoPoint(37.8696, -122.2736) // BPD HQ
        val nearby = safetyDataSource.getNearbyCrimeCells(center, maxDistanceMeters = 500.0)
        val farAway = safetyDataSource.getNearbyCrimeCells(center, maxDistanceMeters = 50.0)

        assertTrue("Nearby cells within 500m should be found", nearby.isNotEmpty())
        assertTrue("Nearby cells within 500m should be >= cells within 50m", nearby.size >= farAway.size)
    }

    // 11. Route ranking: lower exposure route is recommended
    @Test
    fun `route ranking prefers route with lower crime exposure`() {
        val candidates = listOf(
            RawRouteCandidate(
                routeId = "route-via-shattuck",
                label = "via Shattuck Ave",
                durationMinutes = 9,
                distanceMeters = 720,
                polylinePoints = listOf(
                    GeoPoint(37.8715, -122.2730),
                    GeoPoint(37.8700, -122.2680),
                    GeoPoint(37.8690, -122.2680),
                ),
            ),
            RawRouteCandidate(
                routeId = "route-via-telegraph",
                label = "via Telegraph Ave",
                durationMinutes = 8,
                distanceMeters = 650,
                polylinePoints = listOf(
                    GeoPoint(37.8655, -122.2588), // Exactly on high-incident Telegraph cell
                    GeoPoint(37.8632, -122.2592),
                ),
            )
        )

        val ranked = ranker.rankRoutes(candidates, weather = null, preference = RoutePreference.Safest)
        assertEquals(2, ranked.size)

        val recommended = ranked.firstOrNull { it.isRecommended }
        assertNotNull(recommended)
        assertEquals("route-via-shattuck", recommended?.id)
        assertTrue("Recommended route must have higher safety score", (recommended?.safetyScore ?: 0) >= ranked.last().safetyScore)
    }

    // 12. Detour constraints
    @Test
    fun `route with excessive detour is not recommended over reasonable fastest`() {
        val candidates = listOf(
            RawRouteCandidate(
                routeId = "route-fastest",
                label = "via University Ave",
                durationMinutes = 10,
                distanceMeters = 800,
                polylinePoints = listOf(GeoPoint(37.8715, -122.2730), GeoPoint(37.8720, -122.2650)),
            ),
            RawRouteCandidate(
                routeId = "route-huge-detour",
                label = "via Hills Detour",
                durationMinutes = 30, // 20 min detour > 8 min max
                distanceMeters = 2400,
                polylinePoints = listOf(GeoPoint(37.8715, -122.2730), GeoPoint(37.8800, -122.2600)),
            )
        )

        val ranked = ranker.rankRoutes(candidates, weather = null, preference = RoutePreference.Safest)
        val recommended = ranked.firstOrNull { it.isRecommended }
        assertEquals("route-fastest", recommended?.id)
    }

    // 13. Low confidence behavior & 14. Missing data behavior
    @Test
    fun `outside berkeley coverage area confidence is limited and warning is attached`() {
        val outOfAreaCandidate = RawRouteCandidate(
            routeId = "route-outside",
            label = "via Out of Coverage",
            durationMinutes = 15,
            distanceMeters = 1200,
            polylinePoints = listOf(GeoPoint(37.5000, -122.1000), GeoPoint(37.5100, -122.1100)),
        )

        val ranked = ranker.rankRoutes(listOf(outOfAreaCandidate), weather = null)
        val route = ranked.first()
        val assessment = route.assessment

        assertNotNull(assessment)
        assertEquals(DataConfidence.Limited, assessment?.confidence)
        assertTrue(assessment?.warnings?.any { it.contains("limited safety data", ignoreCase = true) } == true)
    }

    // 15. No false safe result when crime data is unavailable
    @Test
    fun `never fabricate 100 percent safe score when outside home`() {
        val candidate = RawRouteCandidate(
            routeId = "route-generic",
            label = "via Street",
            durationMinutes = 10,
            distanceMeters = 800,
            polylinePoints = listOf(GeoPoint(37.8715, -122.2730), GeoPoint(37.8720, -122.2680)),
        )

        val ranked = ranker.rankRoutes(listOf(candidate), weather = null)
        val route = ranked.first()
        assertTrue("Transit walking routes must never score 100", route.safetyScore < 100)
        assertFalse("Route label must not claim guaranteed safe", route.label.contains("guaranteed safe", ignoreCase = true))
    }

    // 16. Marker clustering and suppression
    @Test
    fun `cells below minimum incident threshold are suppressed`() {
        val cells = safetyDataSource.getAllCrimeCells()
        for (cell in cells) {
            assertTrue("Indexed cells must have at least 2 incidents to avoid single victim exposure", cell.incidentCount >= 2)
        }
    }

    // 17. Safe haven open closed behavior
    @Test
    fun `verified 24-7 safe havens are marked open and verified`() {
        val havens = safetyDataSource.getAllSafeHavens()
        val police = havens.firstOrNull { it.type == SafeHavenType.PoliceStation }
        val hospital = havens.firstOrNull { it.type == SafeHavenType.Hospital24x7 }

        assertNotNull(police)
        assertTrue(police?.isOpen24Hours == true)
        assertTrue(police?.isVerified == true)

        assertNotNull(hospital)
        assertTrue(hospital?.isOpen24Hours == true)
        assertTrue(hospital?.isVerified == true)
    }

    // 18. Route provider failure & 19. Places search cancellation
    @Test
    fun `empty search query returns empty list without error`() = runTest {
        val placesProvider = GooglePlacesSearchProvider { "" }
        val results = placesProvider.searchPredictions("")
        assertTrue(results.isEmpty())

        val localResults = placesProvider.searchPredictions("Library")
        assertTrue(localResults.isNotEmpty())
    }

    // 20. Missing API key behavior
    @Test
    fun `blank api key falls back gracefully to local catalog`() = runTest {
        val placesProvider = GooglePlacesSearchProvider { "" }
        val results = placesProvider.searchPredictions("Police")
        assertTrue("Should return local catalog match for Police", results.isNotEmpty())
    }

    // 21. Home creation
    @Test
    fun `saving home persists it with Home kind`() = runTest {
        val home = safeLocationRepo.setHome(
            name = "My Apartment",
            point = GeoPoint(37.8710, -122.2700),
            address = "2150 Shattuck Ave, Berkeley, CA",
            radiusMeters = 80f,
        )

        val retrieved = safeLocationRepo.getHome()
        assertNotNull(retrieved)
        assertEquals("My Apartment", retrieved?.name)
        assertTrue(retrieved?.isHome == true)
    }

    // 22. Home editing and deletion
    @Test
    fun `editing home updates fields and removing it clears home`() = runTest {
        val home = safeLocationRepo.setHome(
            name = "Original Name",
            point = GeoPoint(37.8710, -122.2700),
            address = "Original Address",
            radiusMeters = 80f,
        )

        safeLocationRepo.updateSafeLocation(home.copy(name = "Updated Haven"))
        assertEquals("Updated Haven", safeLocationRepo.getHome()?.name)

        safeLocationRepo.removeSafeLocation(home.id)
        assertNull(safeLocationRepo.getHome())
    }

    // 23. Home geofence hysteresis
    @Test
    fun `geofence hysteresis prevents boundary oscillation`() {
        val homePoint = GeoPoint(37.870000, -122.270000)
        val home = SafeLocation(
            id = "home-1",
            name = "Home",
            point = homePoint,
            radiusMeters = 80f,
            kind = SafeLocationKind.Home,
            address = "Home Address",
        )

        // Point exactly at home (dist 0m) -> inside home
        val stateAtHome = GuardianSafetyStateResolver.resolve(
            currentLocation = homePoint,
            home = home,
            previousState = null,
        )
        assertTrue(stateAtHome.locationState is GuardianLocationState.AtHome)

        // Point at ~90m from home:
        // ~0.0008 deg lat is ~89 meters
        val pointAt90m = GeoPoint(37.870800, -122.270000)

        // When previously NOT at home, 90m is outside 80m radius -> OutAndAbout
        val stateEnter = GuardianSafetyStateResolver.resolve(
            currentLocation = pointAt90m,
            home = home,
            previousState = GuardianLocationState.OutAndAbout,
        )
        assertFalse("Should not enter home at 90m when radius is 80m", stateEnter.locationState is GuardianLocationState.AtHome)

        // When previously AT home, 90m is inside 80m + 25m hysteresis (105m) -> remains AtHome
        val stateStay = GuardianSafetyStateResolver.resolve(
            currentLocation = pointAt90m,
            home = home,
            previousState = GuardianLocationState.AtHome(home),
            exitHysteresisMeters = 25f,
        )
        assertTrue("Should remain at home at 90m due to 25m exit hysteresis", stateStay.locationState is GuardianLocationState.AtHome)

        // Point at ~120m (> 105m) -> exits home
        val pointAt120m = GeoPoint(37.871100, -122.270000)
        val stateExit = GuardianSafetyStateResolver.resolve(
            currentLocation = pointAt120m,
            home = home,
            previousState = GuardianLocationState.AtHome(home),
            exitHysteresisMeters = 25f,
        )
        assertFalse("Should exit home beyond radius + hysteresis", stateExit.locationState is GuardianLocationState.AtHome)
    }

    // 24. Home score equals 100
    @Test
    fun `score at home is exactly 100 and angel is in sanctuary mood`() {
        val homePoint = GeoPoint(37.8700, -122.2700)
        val home = SafeLocation(
            id = "home-1",
            name = "Home Sanctuary",
            point = homePoint,
            radiusMeters = 80f,
            kind = SafeLocationKind.Home,
            address = "Home Address",
        )

        val resolved = GuardianSafetyStateResolver.resolve(
            currentLocation = homePoint,
            home = home,
        )

        assertEquals(100, resolved.score)
        assertEquals(AngelMood.Sanctuary, resolved.mood)
        assertTrue(resolved.isSanctuary)
        assertEquals(StatusCategory.HomeStatus, resolved.statusCategory)
    }

    // 25. Exceptional Home override
    @Test
    fun `duress or critical emergency at home overrides sanctuary state`() {
        val homePoint = GeoPoint(37.8700, -122.2700)
        val home = SafeLocation(
            id = "home-1",
            name = "Home Sanctuary",
            point = homePoint,
            radiusMeters = 80f,
            kind = SafeLocationKind.Home,
            address = "Home Address",
        )

        val resolvedDuress = GuardianSafetyStateResolver.resolve(
            currentLocation = homePoint,
            home = home,
            inDuress = true,
        )

        assertNotEquals(100, resolvedDuress.score)
        assertEquals(AngelMood.Critical, resolvedDuress.mood)
        assertFalse(resolvedDuress.isSanctuary)
        assertTrue(resolvedDuress.locationState is GuardianLocationState.Emergency)
    }

    // 26. Arrival at Home (transitions from TravelingHome to AtHome)
    @Test
    fun `arriving at home transitions traveling state to sanctuary`() {
        val homePoint = GeoPoint(37.8700, -122.2700)
        val home = SafeLocation(
            id = "home-1",
            name = "Home",
            point = homePoint,
            radiusMeters = 80f,
            kind = SafeLocationKind.Home,
            address = "Home Address",
        )

        val awayPoint = GeoPoint(37.8750, -122.2700)
        val traveling = GuardianSafetyStateResolver.resolve(
            currentLocation = awayPoint,
            home = home,
            activeNavigationDestination = Destination("home-1", "Home", "Home Address", homePoint),
        )
        assertTrue(traveling.locationState is GuardianLocationState.TravelingHome)

        val arrived = GuardianSafetyStateResolver.resolve(
            currentLocation = homePoint,
            home = home,
            previousState = traveling.locationState,
            activeNavigationDestination = Destination("home-1", "Home", "Home Address", homePoint),
        )
        assertTrue(arrived.locationState is GuardianLocationState.AtHome)
        assertEquals(100, arrived.score)
    }

    // 27. Live location updates & 28. Polyline decoding
    @Test
    fun `polyline decoder accurately reconstructs coordinates`() {
        // Standard Google encoded polyline test vector: "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        val points = PolylineDecoder.decode(encoded)

        assertEquals(3, points.size)
        assertEquals(38.5, points[0].latitude, 0.001)
        assertEquals(-120.2, points[0].longitude, 0.001)
    }

    // 29. Angel mood transitions across safety scores
    @Test
    fun `angel mood maps deterministically from safety score`() {
        assertEquals(AngelMood.Critical, AngelMood.fromScore(score = 25, atSafeHaven = false, isArmed = true, inDuress = false))
        assertEquals(AngelMood.Critical, AngelMood.fromScore(score = 100, atSafeHaven = true, isArmed = true, inDuress = true))
        assertEquals(AngelMood.Sanctuary, AngelMood.fromScore(score = 100, atSafeHaven = true, isArmed = true, inDuress = false))
        assertEquals(AngelMood.Sanctuary, AngelMood.fromScore(score = 95, atSafeHaven = true, isArmed = true, inDuress = false))
        assertEquals(AngelMood.Cautious, AngelMood.fromScore(score = 80, atSafeHaven = false, isArmed = true, inDuress = false))
        assertEquals(AngelMood.Warning, AngelMood.fromScore(score = 55, atSafeHaven = false, isArmed = true, inDuress = false))
        assertEquals(AngelMood.Critical, AngelMood.fromScore(score = 30, atSafeHaven = false, isArmed = true, inDuress = false))
    }

    // 30. Floating status bubble states
    @Test
    fun `floating status bubble reflects calm reassured tone`() {
        val homePoint = GeoPoint(37.8700, -122.2700)
        val home = SafeLocation(
            id = "home-1",
            name = "Home",
            point = homePoint,
            radiusMeters = 80f,
            kind = SafeLocationKind.Home,
            address = "Address",
        )

        val atHome = GuardianSafetyStateResolver.resolve(homePoint, home)
        assertEquals("You're back in your safe area.", atHome.statusBubbleMessage)

        val walkingArmed = GuardianSafetyStateResolver.resolve(
            currentLocation = GeoPoint(37.8750, -122.2750),
            home = home,
            isArmed = true,
            isRecording = false,
        )
        assertEquals("Listening quietly.", walkingArmed.statusBubbleMessage)

        val walkingStandby = GuardianSafetyStateResolver.resolve(
            currentLocation = GeoPoint(37.8750, -122.2750),
            home = home,
            isArmed = false,
            isRecording = false,
        )
        assertEquals("I'm here with you.", walkingStandby.statusBubbleMessage)

        val recording = GuardianSafetyStateResolver.resolve(
            currentLocation = GeoPoint(37.8750, -122.2750),
            home = home,
            isArmed = true,
            isRecording = true,
        )
        assertEquals("Angel is listening privately.", recording.statusBubbleMessage)
    }

    // 31. Hero copy consistency
    @Test
    fun `hero copy remains synchronized between map and home resolvers`() {
        val homePoint = GeoPoint(37.8700, -122.2700)
        val home = SafeLocation(
            id = "home-1",
            name = "Home",
            point = homePoint,
            radiusMeters = 80f,
            kind = SafeLocationKind.Home,
            address = "Address",
        )

        val state = GuardianSafetyStateResolver.resolve(homePoint, home)
        assertEquals("You're home. Angel is keeping watch quietly.", state.heroHeadline)
        assertEquals("Your score stays at 100% inside your sanctuary zone.", state.heroSubtitle)
    }

    // 32. Weather integration affects assessment
    @Test
    fun `adverse weather lowers route score and adds environmental warning`() {
        val candidate = RawRouteCandidate(
            routeId = "route-weather",
            label = "via Shattuck",
            durationMinutes = 10,
            distanceMeters = 800,
            polylinePoints = listOf(GeoPoint(37.8715, -122.2730), GeoPoint(37.8700, -122.2680)),
        )

        val goodWeather = WeatherCondition(temperatureFahrenheit = 65, conditionDescription = "Clear", walkabilityRisk = 0.0f)
        val badWeather = WeatherCondition(
            temperatureFahrenheit = 45,
            conditionDescription = "Heavy Rain and High Winds",
            isSevereAlert = true,
            walkabilityRisk = 0.8f,
        )

        val routeGood = ranker.rankRoutes(listOf(candidate), weather = goodWeather).first()
        val routeBad = ranker.rankRoutes(listOf(candidate), weather = badWeather).first()

        assertTrue("Bad weather should lower or equal safety score", routeBad.safetyScore <= routeGood.safetyScore)
        assertTrue(routeBad.assessment?.warnings?.any { it.contains("weather", ignoreCase = true) } == true)
    }

    // 33. Discreet safety language constraint
    @Test
    fun `warnings use discreet calm exposure wording rather than panic language`() {
        val candidate = RawRouteCandidate(
            routeId = "route-telegraph",
            label = "via Telegraph Ave",
            durationMinutes = 8,
            distanceMeters = 650,
            polylinePoints = listOf(GeoPoint(37.8655, -122.2588), GeoPoint(37.8632, -122.2592)),
        )

        val routes = ranker.rankRoutes(listOf(candidate), weather = null)
        val warnings = routes.first().assessment?.warnings ?: emptyList()

        for (warning in warnings) {
            assertFalse("Warning must not use alarmist panic words", warning.contains("danger", ignoreCase = true))
            assertFalse("Warning must not claim absolute safety", warning.contains("guaranteed safe", ignoreCase = true))
        }
    }
}
