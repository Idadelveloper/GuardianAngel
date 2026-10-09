package com.example.guardianangel.domain

import com.example.guardianangel.domain.model.GuardianCapability
import com.example.guardianangel.domain.model.guardianSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules behind "what Angel still cannot do".
 *
 * Every one of these is a claim the home screen makes to the user about whether a safety
 * feature works. Getting one wrong in the reassuring direction — saying voice matching is
 * on when it is not — is the kind of bug that is only discovered by the person it fails.
 */
class GuardianSetupTest {

    @Test
    fun `nothing set up means nothing is ready`() {
        val setup = guardianSetup(
            hasWakeWord = false,
            voiceprintUsable = false,
            hasLocationPermission = false,
            codewordCount = 0,
            guardianCount = 0,
        )
        assertTrue(setup.ready.isEmpty())
        assertFalse(setup.isComplete)
        assertEquals(GuardianCapability.entries.size, setup.missing.size)
    }

    @Test
    fun `everything set up means nothing is missing`() {
        val setup = guardianSetup(
            hasWakeWord = true,
            voiceprintUsable = true,
            hasLocationPermission = true,
            codewordCount = 4,
            guardianCount = 1,
        )
        assertTrue("Still reporting ${setup.missing}", setup.isComplete)
        assertEquals(null, setup.mostImportantMissing)
    }

    @Test
    fun `voice matching needs a wake word to gate`() {
        // A usable voiceprint with no wake word gates nothing: there is no wake to gate.
        // Claiming "only wakes for your voice" here would be claiming a protection over
        // a feature that is switched off.
        val setup = guardianSetup(
            hasWakeWord = false,
            voiceprintUsable = true,
            hasLocationPermission = true,
            codewordCount = 4,
            guardianCount = 1,
        )
        assertFalse(GuardianCapability.VoiceMatch in setup.ready)
        assertFalse(GuardianCapability.HandsFree in setup.ready)
    }

    @Test
    fun `an unusable voiceprint does not count as voice matching`() {
        // The service refuses to gate on a low-clarity voiceprint, so the UI must not
        // say it is gating. These two decisions have to agree or the app lies.
        val setup = guardianSetup(
            hasWakeWord = true,
            voiceprintUsable = false,
            hasLocationPermission = true,
            codewordCount = 4,
            guardianCount = 1,
        )
        assertTrue(GuardianCapability.HandsFree in setup.ready)
        assertFalse(GuardianCapability.VoiceMatch in setup.ready)
    }

    @Test
    fun `having nobody to alert is raised before anything else`() {
        val setup = guardianSetup(
            hasWakeWord = false,
            voiceprintUsable = false,
            hasLocationPermission = false,
            codewordCount = 0,
            guardianCount = 0,
        )
        assertEquals(
            "With everything missing, the gap that matters most is having no guardian",
            GuardianCapability.GuardianAlerts,
            setup.mostImportantMissing,
        )
    }

    @Test
    fun `convenience gaps are raised last`() {
        // Only the two optional-feeling ones left: the voiceprint is the quietest loss,
        // so location comes first.
        val setup = guardianSetup(
            hasWakeWord = true,
            voiceprintUsable = false,
            hasLocationPermission = false,
            codewordCount = 4,
            guardianCount = 1,
        )
        assertEquals(
            GuardianCapability.LocationSharing,
            setup.mostImportantMissing,
        )
    }

    @Test
    fun `one codeword is enough to act on`() {
        val setup = guardianSetup(
            hasWakeWord = true,
            voiceprintUsable = true,
            hasLocationPermission = true,
            codewordCount = 1,
            guardianCount = 1,
        )
        assertTrue(GuardianCapability.CodewordActions in setup.ready)
    }
}
