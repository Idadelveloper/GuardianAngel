package com.example.guardianangel.ui.map

import com.example.guardianangel.domain.model.Destination
import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.domain.model.GuardianLocationState
import com.example.guardianangel.domain.model.SafeLocation
import com.example.guardianangel.domain.model.SafeLocationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AngelWhereAmITest {

    private fun home(name: String = "Sweet Home") = SafeLocation(
        id = "home",
        name = name,
        address = "",
        point = GeoPoint(37.87, -122.27),
        radiusMeters = 80f,
        kind = SafeLocationKind.Home,
    )

    @Test
    fun `at home says you are home and names it`() {
        val callout = AngelWhereAmI.describe(GuardianLocationState.AtHome(home()))

        assertEquals("You're home", callout.headline)
        assertTrue(callout.detail.contains("Sweet Home"))
        assertTrue(callout.isSanctuary)
    }

    @Test
    fun `at a saved safe place names the place and still counts as sanctuary`() {
        val library = home(name = "Doe Library").copy(id = "l", kind = SafeLocationKind.SafeHaven)

        val callout = AngelWhereAmI.describe(GuardianLocationState.AtSafeLocation(library))

        assertTrue(callout.detail.contains("Doe Library"))
        assertTrue(callout.isSanctuary)
    }

    @Test
    fun `out and about is not dressed up as safe`() {
        val callout = AngelWhereAmI.describe(GuardianLocationState.OutAndAbout)

        assertFalse(callout.isSanctuary)
        assertFalse(callout.detail.contains("safe place."))
    }

    @Test
    fun `navigating names the destination`() {
        val state = GuardianLocationState.Navigating(
            destination = Destination(id = "d", name = "Ashby BART", address = "", point = GeoPoint(37.85, -122.27)),
            isArmed = true,
        )

        assertTrue(AngelWhereAmI.describe(state).detail.contains("Ashby BART"))
    }

    @Test
    fun `a good fix is not mentioned`() {
        val callout = AngelWhereAmI.describe(GuardianLocationState.AtHome(home()), accuracyMeters = 6f)

        assertFalse(callout.detail.contains("accurate"))
    }

    @Test
    fun `a vague fix is admitted so the misplaced dot makes sense`() {
        val callout = AngelWhereAmI.describe(GuardianLocationState.OutAndAbout, accuracyMeters = 85f)

        assertTrue(callout.detail.contains("85 m"))
    }

    @Test
    fun `an emergency repeats the reason rather than inventing one`() {
        val callout = AngelWhereAmI.describe(GuardianLocationState.Emergency("Emergency codeword heard"))

        assertEquals("Emergency codeword heard", callout.detail)
    }
}
