package com.example.guardianangel.domain.route

import com.example.guardianangel.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkDirectionsTest {

    /** Berkeley, where a degree of longitude is about 88 km and latitude about 111 km. */
    private val base = GeoPoint(37.8715, -122.2730)

    private fun north(meters: Double) = meters / 111_000.0
    private fun east(meters: Double) = meters / 88_000.0

    /** Walk 200 m north, then 200 m east: one right turn. */
    private val rightAngle = listOf(
        base,
        GeoPoint(base.latitude + north(200.0), base.longitude),
        GeoPoint(base.latitude + north(200.0), base.longitude + east(200.0)),
    )

    @Test
    fun `a straight line has only a start and an arrival`() {
        val path = listOf(
            base,
            GeoPoint(base.latitude + north(100.0), base.longitude),
            GeoPoint(base.latitude + north(200.0), base.longitude),
        )

        val steps = WalkDirections.stepsFor(path)

        assertEquals(
            listOf(WalkDirections.Turn.Start, WalkDirections.Turn.Arrive),
            steps.map { it.turn },
        )
    }

    @Test
    fun `a right angle to the east is a right turn`() {
        val steps = WalkDirections.stepsFor(rightAngle)

        assertEquals(WalkDirections.Turn.Right, steps[1].turn)
        assertEquals("Turn right", steps[1].instruction)
    }

    @Test
    fun `a right angle to the west is a left turn`() {
        val path = listOf(
            base,
            GeoPoint(base.latitude + north(200.0), base.longitude),
            GeoPoint(base.latitude + north(200.0), base.longitude - east(200.0)),
        )

        assertEquals(WalkDirections.Turn.Left, WalkDirections.stepsFor(path)[1].turn)
    }

    @Test
    fun `the gentle curve of a road does not produce a step per vertex`() {
        // Twenty points each bending five degrees: a curving street, not twenty turns.
        val path = (0..20).map { i ->
            val drift = i * i * 0.5
            GeoPoint(base.latitude + north(i * 20.0), base.longitude + east(drift))
        }

        val steps = WalkDirections.stepsFor(path)

        assertTrue("got ${steps.size} steps", steps.size <= 4)
    }

    @Test
    fun `distance to the next turn counts along the path, not in a straight line`() {
        val steps = WalkDirections.stepsFor(rightAngle)

        // 200 m up to the corner, then 200 m across to the end.
        assertEquals(200.0, steps[1].metersFromPrevious.toDouble(), 5.0)
        assertEquals(200.0, steps[2].metersFromPrevious.toDouble(), 5.0)
    }

    @Test
    fun `progress at the start points at the first turn and the whole distance`() {
        val steps = WalkDirections.stepsFor(rightAngle)

        val progress = WalkDirections.progress(rightAngle, steps, base)

        assertEquals(WalkDirections.Turn.Right, progress.nextStep?.turn)
        assertEquals(200.0, progress.metersToNextStep.toDouble(), 5.0)
        assertEquals(400.0, progress.metersRemaining.toDouble(), 10.0)
        assertFalse(progress.isOffRoute)
    }

    @Test
    fun `progress past the corner points at the arrival`() {
        val steps = WalkDirections.stepsFor(rightAngle)

        val progress = WalkDirections.progress(rightAngle, steps, rightAngle[1])

        assertEquals(WalkDirections.Turn.Arrive, progress.nextStep?.turn)
        assertEquals(200.0, progress.metersRemaining.toDouble(), 5.0)
    }

    @Test
    fun `standing at the destination reports nothing left to do`() {
        val steps = WalkDirections.stepsFor(rightAngle)

        val progress = WalkDirections.progress(rightAngle, steps, rightAngle.last())

        assertEquals(null, progress.nextStep)
        assertEquals(0, progress.metersRemaining)
    }

    @Test
    fun `wandering far from every point on the route is off-route`() {
        val steps = WalkDirections.stepsFor(rightAngle)
        val strayed = GeoPoint(base.latitude + north(100.0), base.longitude + east(300.0))

        assertTrue(WalkDirections.progress(rightAngle, steps, strayed).isOffRoute)
    }

    @Test
    fun `a small deviation is not called off-route`() {
        val steps = WalkDirections.stepsFor(rightAngle)
        val crossedTheStreet = GeoPoint(base.latitude + north(100.0), base.longitude + east(12.0))

        assertFalse(WalkDirections.progress(rightAngle, steps, crossedTheStreet).isOffRoute)
    }

    @Test
    fun `an empty or single-point route produces no steps rather than throwing`() {
        assertTrue(WalkDirections.stepsFor(emptyList()).isEmpty())
        assertTrue(WalkDirections.stepsFor(listOf(base)).isEmpty())
        assertEquals(null, WalkDirections.progress(emptyList(), emptyList(), base).nextStep)
    }

    @Test
    fun `distances are rounded to something a person would say`() {
        assertEquals("now", WalkDirections.formatDistance(8))
        assertEquals("80 m", WalkDirections.formatDistance(84))
        assertEquals("1.2 km", WalkDirections.formatDistance(1_240))
    }

    @Test
    fun `bearing difference wraps across north`() {
        assertEquals(20.0, WalkDirections.angleDifference(350.0, 10.0), 0.001)
        assertEquals(-20.0, WalkDirections.angleDifference(10.0, 350.0), 0.001)
    }
}
