package com.example.guardianangel.domain.model

import kotlin.time.Duration

/**
 * Domain model for Guardian Angel.
 *
 * Everything the home screen renders is described here, with no Android or Compose types,
 * so the same shapes survive being loaded from Room, Firestore or a network API later.
 * UI-only concerns (colours, icons, formatted strings) deliberately live in the UI layer.
 */

/** What the guardian is currently doing. Drives which home screen state is shown. */
enum class GuardianMode {
    /** Mic dormant. Codewords are configured but not being listened for. */
    Standby,

    /** Ambient listening is on; codewords are armed. */
    Listening,

    /** A codeword fired or the user held the trigger: recording and transcribing. */
    Recording,
}

/** The four escalation tiers a codeword can occupy. */
enum class CodewordTier {
    /** Tells chosen contacts the user is safe — cancels a false alarm. */
    Safe,

    /** Starts transcribing quietly without alerting anyone. */
    Caution,

    /** Alerts the trusted circle with live location. */
    Danger,

    /** Calls emergency services and alerts contacts. */
    Emergency,
}

/**
 * A spoken phrase that triggers a tier.
 *
 * @param notifyContactIds who to alert; empty means "everyone", matching the onboarding
 *   flow's "all contacts" option. [CodewordTier.Caution] never notifies anyone.
 */
data class Codeword(
    val id: String,
    val tier: CodewordTier,
    val phrase: String,
    val notifyContactIds: List<String> = emptyList(),
    val isArmed: Boolean = true,
)

/** How reachable a guardian is right now. */
enum class ContactPresence { Online, Offline, Unknown }

/**
 * Someone the user has nominated to be alerted.
 *
 * @param priority 1 is the top-priority contact. The onboarding flow lets the user rank
 *   up to six.
 */
data class EmergencyContact(
    val id: String,
    val name: String,
    val relationship: String,
    val phoneNumber: String,
    val priority: Int,
    val presence: ContactPresence = ContactPresence.Unknown,
    /** Free-text locality shown next to the name, e.g. "Home" or "1.2 mi away". */
    val locationLabel: String? = null,
    val avatarUrl: String? = null,
) {
    /** Initials fallback for when there is no avatar to show. */
    val initials: String
        get() = name.split(' ')
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
}

/** One input into the safety score, shown to the user so the number is never a black box. */
data class SafetyFactor(
    val id: String,
    /** What the factor measures, e.g. "Away from Base". */
    val label: String,
    /** Its current reading, e.g. "42 mins". */
    val value: String,
    /** Which way it is pushing the score. */
    val direction: FactorDirection = FactorDirection.Neutral,
)

enum class FactorDirection { Raises, Neutral, Lowers }

/**
 * The location safety score.
 *
 * @param score 0–100, where 100 is a known safe haven.
 * @param deltaPercent change since the last evaluation; negative means conditions worsened.
 * @param rationale one plain sentence explaining the number, because an unexplained score
 *   is not actionable when someone is deciding whether to keep walking.
 */
data class SafetyScore(
    val score: Int,
    val deltaPercent: Int,
    val rationale: String,
    val factors: List<SafetyFactor>,
    val isLiveScanning: Boolean,
)

/** A monitored journey with an expected arrival the app can check against. */
data class SafeWalk(
    val destinationLabel: String,
    val estimatedDuration: Duration,
    val etaLabel: String,
    val isActive: Boolean = false,
)

/** Device/runtime readings surfaced on the hero card so the user can trust the guardian. */
data class GuardianTelemetry(
    val batteryPercent: Int,
    val gpsAccuracyMeters: Double?,
    val isEncrypted: Boolean = true,
    val isGeofenceActive: Boolean = true,
)

/** An in-progress recording session started by a codeword or the duress trigger. */
data class RecordingSession(
    val id: String,
    val startedAtEpochMillis: Long,
    val triggeredBy: CodewordTier?,
    /** Live transcript lines, newest last. */
    val transcriptPreview: List<TranscriptLine> = emptyList(),
    val contactsNotified: List<String> = emptyList(),
    val isPoliceDispatched: Boolean = false,
)

/** One attributed line of transcript. */
data class TranscriptLine(
    val speakerLabel: String,
    val text: String,
    val atEpochMillis: Long,
    /** True when the AI flagged this line as indicating danger. */
    val isFlagged: Boolean = false,
)

/**
 * An in-progress guarded walk.
 *
 * Present only while the user is out; its absence is what makes the home screen render
 * its sanctuary state rather than its active-journey state.
 */
data class ActiveJourney(
    /** The street or corridor being guarded, e.g. "Shattuck Ave". */
    val corridorLabel: String,
    val destinationLabel: String?,
    /** 0-100 street lighting along the current stretch. */
    val illuminationPercent: Int,
    /** Minutes behind the original estimate; 0 when on time. */
    val delayMinutes: Int,
    val elapsedMinutes: Int,
)

/** Everything the home screen needs, in one snapshot. */
data class GuardianSnapshot(
    val userFirstName: String,
    val userAvatarUrl: String?,
    val mode: GuardianMode,
    val telemetry: GuardianTelemetry,
    val safetyScore: SafetyScore,
    val codewords: List<Codeword>,
    val contacts: List<EmergencyContact>,
    val safeWalk: SafeWalk?,
    val activeSession: RecordingSession?,
    /** Name of the place the user is currently anchored to, if any. */
    val safeHavenLabel: String? = null,
    val safeNodeCount: Int = 0,
    /** Non-null while a guarded walk is under way. */
    val journey: ActiveJourney? = null,
) {
    /** True when the user is anchored inside a verified safe haven. */
    val isAtSafeHaven: Boolean get() = safeHavenLabel != null && journey == null
}
