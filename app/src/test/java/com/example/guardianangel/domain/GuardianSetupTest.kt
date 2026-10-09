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
            hasMicrophonePermission = false,
            customisedCodewordCount = 0,
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
            hasMicrophonePermission = true,
            customisedCodewordCount = 4,
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
            hasMicrophonePermission = true,
            customisedCodewordCount = 4,
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
            hasMicrophonePermission = true,
            customisedCodewordCount = 4,
            guardianCount = 1,
        )
        assertTrue(GuardianCapability.HandsFree in setup.ready)
        assertFalse(GuardianCapability.VoiceMatch in setup.ready)
    }

    @Test
    fun `seeded codeword suggestions do not count as setup`() {
        // A new account is seeded with four suggestions so no tier is empty. Counting
        // rows reported them as configured, so the prompt to choose her own phrases
        // never appeared and she could end up relying on "yellow submarine".
        val setup = guardianSetup(
            hasWakeWord = true,
            voiceprintUsable = true,
            hasLocationPermission = true,
            hasMicrophonePermission = true,
            customisedCodewordCount = 0,
            guardianCount = 1,
        )
        assertFalse(GuardianCapability.CodewordActions in setup.ready)
        assertTrue(GuardianCapability.CodewordActions in setup.missing)
    }

    @Test
    fun `hands-free needs the microphone, not just a phrase`() {
        // A wake word with no microphone is a phrase nothing is listening for. Reporting
        // hands-free as ready there would be the app claiming a protection it has not got.
        val setup = guardianSetup(
            hasWakeWord = true,
            voiceprintUsable = true,
            hasLocationPermission = true,
            hasMicrophonePermission = false,
            customisedCodewordCount = 4,
            guardianCount = 1,
        )
        assertFalse(GuardianCapability.HandsFree in setup.ready)
        assertFalse(GuardianCapability.MicrophoneAccess in setup.ready)
        assertEquals(
            "The microphone is the most consequential thing to be missing",
            GuardianCapability.MicrophoneAccess,
            setup.mostImportantMissing,
        )
    }

    @Test
    fun `the microphone is raised before anything else`() {
        val setup = guardianSetup(
            hasWakeWord = false,
            voiceprintUsable = false,
            hasLocationPermission = false,
            hasMicrophonePermission = false,
            customisedCodewordCount = 0,
            guardianCount = 0,
        )
        // Nothing else in the list does anything without it, so asking for a guardian
        // first would be asking her to set up a feature that cannot run.
        assertEquals(
            GuardianCapability.MicrophoneAccess,
            setup.mostImportantMissing,
        )
    }

    @Test
    fun `having nobody to alert is raised once the microphone is granted`() {
        val setup = guardianSetup(
            hasWakeWord = false,
            voiceprintUsable = false,
            hasLocationPermission = false,
            hasMicrophonePermission = true,
            customisedCodewordCount = 0,
            guardianCount = 0,
        )
        assertEquals(
            "An alert with nobody to send it to is the next worst gap",
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
            hasMicrophonePermission = true,
            customisedCodewordCount = 4,
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
            hasMicrophonePermission = true,
            customisedCodewordCount = 1,
            guardianCount = 1,
        )
        assertTrue(GuardianCapability.CodewordActions in setup.ready)
    }
}
