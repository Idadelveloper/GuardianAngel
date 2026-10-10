package com.example.guardianangel.domain.trip

import com.example.guardianangel.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalDetectorTest {

    private val destination = GeoPoint(37.8715, -122.2730)
    private val t0 = 1_700_000_000_000L

    /** Metres north of the destination, as a point. */
    private fun north(meters: Double) =
        GeoPoint(destination.latitude + meters / 111_000.0, destination.longitude)

    private fun detector() = ArrivalDetector(destination)

    @Test
    fun `far away is approaching, with the distance`() {
        val state = detector().onLocation(north(500.0), t0)

        assertEquals(500.0, (state as ArrivalDetector.State.Approaching).metersAway.toDouble(), 10.0)
    }

    @Test
    fun `arriving in the radius starts the dwell rather than firing`() {
        val state = detector().onLocation(north(10.0), t0)

        assertTrue(state is ArrivalDetector.State.Settling)
    }

    @Test
    fun `staying put for the dwell counts as arrival`() {
        val detector = detector()

        detector.onLocation(north(10.0), t0)
        val state = detector.onLocation(north(8.0), t0 + 20_000)

        assertEquals(t0 + 20_000, (state as ArrivalDetector.State.Arrived).atMillis)
    }

    @Test
    fun `walking past does not count as arriving`() {
        val detector = detector()

        detector.onLocation(north(30.0), t0)            // enters the radius
        detector.onLocation(north(60.0), t0 + 8_000)    // keeps going, leaves it
        val state = detector.onLocation(north(55.0), t0 + 25_000)

        assertTrue("walked past: $state", state is ArrivalDetector.State.Approaching)
    }

    @Test
    fun `coming back after walking past restarts the dwell`() {
        val detector = detector()

        detector.onLocation(north(10.0), t0)
        detector.onLocation(north(200.0), t0 + 5_000)
        val state = detector.onLocation(north(10.0), t0 + 10_000)

        assertTrue("dwell must restart: $state", state is ArrivalDetector.State.Settling)
    }

    @Test
    fun `arrival is reported once and then stays arrived`() {
        val detector = detector()

        detector.onLocation(north(5.0), t0)
        val first = detector.onLocation(north(5.0), t0 + 20_000)
        val second = detector.onLocation(north(900.0), t0 + 60_000)

        assertTrue(first is ArrivalDetector.State.Arrived)
        assertTrue("must not un-arrive: $second", second is ArrivalDetector.State.Arrived)
    }

    @Test
    fun `a wildly imprecise fix cannot trigger an arrival`() {
        val detector = detector()

        detector.onLocation(north(5.0), t0, accuracyMeters = 300f)
        val state = detector.onLocation(north(5.0), t0 + 30_000, accuracyMeters = 300f)

        assertTrue("a 300 m fix is not evidence: $state", state is ArrivalDetector.State.Approaching)
    }

    @Test
    fun `one bad fix does not restart a dwell already served`() {
        val detector = detector()

        detector.onLocation(north(5.0), t0, accuracyMeters = 8f)
        detector.onLocation(north(5.0), t0 + 9_000, accuracyMeters = 400f)   // a blip
        val state = detector.onLocation(north(5.0), t0 + 21_000, accuracyMeters = 8f)

        assertTrue("blip should not reset the dwell: $state", state is ArrivalDetector.State.Arrived)
    }

    @Test
    fun `a tighter radius can be asked for`() {
        val detector = ArrivalDetector(destination, radiusMeters = 10.0, dwellMillis = 1_000)

        assertTrue(detector.onLocation(north(25.0), t0) is ArrivalDetector.State.Approaching)
    }
}
