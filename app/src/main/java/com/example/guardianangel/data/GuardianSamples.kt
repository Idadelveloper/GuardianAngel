package com.example.guardianangel.data

import com.example.guardianangel.domain.model.ActiveJourney
import com.example.guardianangel.domain.model.Codeword
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.ContactPresence
import com.example.guardianangel.domain.model.EmergencyContact
import com.example.guardianangel.domain.model.FactorDirection
import com.example.guardianangel.domain.model.GuardianMode
import com.example.guardianangel.domain.model.GuardianSnapshot
import com.example.guardianangel.domain.model.GuardianTelemetry
import com.example.guardianangel.domain.model.RecordingSession
import com.example.guardianangel.domain.model.SafeWalk
import com.example.guardianangel.domain.model.SafetyFactor
import com.example.guardianangel.domain.model.SafetyScore
import com.example.guardianangel.domain.model.TranscriptLine
import kotlin.time.Duration.Companion.minutes

/**
 * Sample content shared by [FakeGuardianRepository] and the `@Preview` functions.
 *
 * Kept in one place so a preview and the running app never drift apart, and so there is a
 * single file to delete once real data lands. Everything here is invented — no real
 * people, numbers or addresses.
 */
object GuardianSamples {

    val contacts = listOf(
        EmergencyContact(
            id = "c1",
            name = "Sarah Jenkins",
            relationship = "Mother",
            phoneNumber = "+1 555 0100",
            priority = 1,
            presence = ContactPresence.Online,
            locationLabel = "Home",
        ),
        EmergencyContact(
            id = "c2",
            name = "David Chen",
            relationship = "Partner",
            phoneNumber = "+1 555 0111",
            priority = 2,
            presence = ContactPresence.Online,
            locationLabel = "1.2 mi away",
        ),
        EmergencyContact(
            id = "c3",
            name = "Amara Okafor",
            relationship = "Roommate",
            phoneNumber = "+1 555 0122",
            priority = 3,
            presence = ContactPresence.Offline,
        ),
    )

    val codewords = listOf(
        Codeword("cw-safe", CodewordTier.Safe, "marshmallow"),
        Codeword("cw-caution", CodewordTier.Caution, "pineapple"),
        Codeword("cw-danger", CodewordTier.Danger, "yellow submarine"),
        Codeword("cw-emergency", CodewordTier.Emergency, "glitter"),
    )

    /** Away from home at night: the score has decayed from a safe-haven 100. */
    val movingSafetyScore = SafetyScore(
        score = 84,
        deltaPercent = -16,
        rationale = "Down 16% over 42 minutes away from your safe base.",
        factors = listOf(
            SafetyFactor("away", "Away from base", "42 mins", FactorDirection.Lowers),
            SafetyFactor("havens", "Safe nodes", "3 nearby", FactorDirection.Raises),
            SafetyFactor("light", "Illumination", "92% lit", FactorDirection.Raises),
            SafetyFactor("decay", "Time decay", "-16%", FactorDirection.Lowers),
        ),
        isLiveScanning = false,
    )

    /** Anchored at home: the baseline everything else is measured against. */
    val atHomeSafetyScore = SafetyScore(
        score = 100,
        deltaPercent = 0,
        rationale = "Anchored at Sweet Home. Perimeter intact, no threats detected.",
        factors = listOf(
            SafetyFactor("range", "Range", "0 m away", FactorDirection.Raises),
            SafetyFactor("perimeter", "Perimeter", "Anchored", FactorDirection.Raises),
            SafetyFactor("check", "Last check", "Just now", FactorDirection.Neutral),
            SafetyFactor("light", "Illumination", "Indoors", FactorDirection.Neutral),
        ),
        isLiveScanning = false,
    )

    val safeWalk = SafeWalk(
        destinationLabel = "742 Evergreen Terrace",
        estimatedDuration = 18.minutes,
        etaLabel = "10:45 PM",
    )

    val telemetry = GuardianTelemetry(batteryPercent = 94, gpsAccuracyMeters = 1.8)

    /** The walk the user is on once she has left her safe haven. */
    val journey = ActiveJourney(
        corridorLabel = "Shattuck Ave",
        destinationLabel = "742 Evergreen Terrace",
        illuminationPercent = 84,
        delayMinutes = 4,
        elapsedMinutes = 12,
    )

    fun transcript(now: Long = System.currentTimeMillis()) = listOf(
        TranscriptLine("You", "I'm heading down Market now.", now - 12_000),
        TranscriptLine("Unknown voice", "Hey — hold on a second.", now - 5_000, isFlagged = true),
        TranscriptLine("You", "No thanks, I'm good.", now - 1_000),
    )

    /** A snapshot in the given mode, with the rest of the state made consistent with it. */
    fun snapshot(mode: GuardianMode): GuardianSnapshot = GuardianSnapshot(
        userFirstName = "Maya",
        userAvatarUrl = null,
        mode = mode,
        telemetry = telemetry,
        safetyScore = if (mode == GuardianMode.Standby) atHomeSafetyScore else movingSafetyScore,
        codewords = codewords,
        contacts = contacts,
        safeWalk = safeWalk,
        activeSession = if (mode == GuardianMode.Recording) {
            RecordingSession(
                id = "sample-session",
                startedAtEpochMillis = System.currentTimeMillis() - 74_000,
                triggeredBy = CodewordTier.Danger,
                transcriptPreview = transcript(),
                contactsNotified = listOf("c1", "c2"),
            )
        } else {
            null
        },
        safeHavenLabel = if (mode == GuardianMode.Standby) "home" else null,
        safeNodeCount = 3,
        journey = if (mode == GuardianMode.Standby) null else journey,
    )
}
