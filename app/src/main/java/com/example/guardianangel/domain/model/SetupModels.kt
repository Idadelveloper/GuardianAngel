package com.example.guardianangel.domain.model

/**
 * What Angel can and cannot currently do, and what is missing for each.
 *
 * Onboarding is skippable, which makes this necessary rather than nice: a user who
 * skipped a step has an app that *silently* cannot do part of its job. Nothing about a
 * missing codeword is visible until the moment it fails, and that moment is the worst
 * possible time to find out.
 *
 * So capability is modelled explicitly, named by what it does for her rather than by the
 * data it needs, and derived from live state rather than from a "completed setup" flag —
 * a flag would still say yes after she revoked location access in system settings.
 */
enum class GuardianCapability(
    /** What she gets. Phrased as the outcome, not the setting. */
    val summary: String,
    /** What is missing, and why it matters. Shown only when [GuardianSetup] says so. */
    val missingDetail: String,
    /** Label for the button that fixes it. */
    val fixLabel: String,
) {
    HandsFree(
        summary = "Wake me by saying your phrase",
        missingDetail = "Without a wake word I can only start recording when you tap. " +
            "Setting one takes a few seconds.",
        fixLabel = "Set a wake word",
    ),

    VoiceMatch(
        summary = "Only wake for your voice",
        missingDetail = "I don't know your voice yet, so anyone who learns your wake " +
            "word could start a recording on your phone.",
        fixLabel = "Record my voice",
    ),

    LocationSharing(
        summary = "Tell your guardians where you are",
        missingDetail = "Without location I can alert your guardians but can't tell " +
            "them where to go.",
        fixLabel = "Allow location",
    ),

    CodewordActions(
        summary = "Act on what you say while recording",
        missingDetail = "Codewords are how you tell me what to do once I'm recording — " +
            "check in, alert your circle, call for help. Without them I record, and " +
            "nothing else.",
        fixLabel = "Set codewords",
    ),

    GuardianAlerts(
        summary = "Reach someone when you need help",
        missingDetail = "You haven't added a guardian, so there is nobody for me to " +
            "alert. This is the one worth doing first.",
        fixLabel = "Add a guardian",
    ),
}

/**
 * Which capabilities are ready.
 *
 * @param ready every capability currently working end to end.
 */
data class GuardianSetup(
    val ready: Set<GuardianCapability> = emptySet(),
) {
    val missing: List<GuardianCapability>
        get() = GuardianCapability.entries.filter { it !in ready }

    val isComplete: Boolean get() = missing.isEmpty()

    /**
     * The one to raise first.
     *
     * Ordered by how badly its absence hurts, not by the order of setup: being unable to
     * reach anyone beats being unable to say where you are, which beats conveniences. The
     * home screen shows this one and keeps the rest behind a tap, because a list of five
     * warnings is read as decoration.
     */
    val mostImportantMissing: GuardianCapability?
        get() = PRIORITY.firstOrNull { it in missing }

    private companion object {
        val PRIORITY = listOf(
            GuardianCapability.GuardianAlerts,
            GuardianCapability.CodewordActions,
            GuardianCapability.HandsFree,
            GuardianCapability.LocationSharing,
            GuardianCapability.VoiceMatch,
        )
    }
}

/**
 * Works out what is ready from live state.
 *
 * Pure, so the rules can be tested without a device — and they are worth testing: every
 * one of these conditions is a claim the UI makes to the user about whether a safety
 * feature works.
 *
 * @param voiceprintUsable not merely "a voiceprint exists". A voiceprint built from takes
 *   that disagreed is not used for gating, so claiming voice matching works would be
 *   false — see `VoiceProfile.isUsable`.
 */
fun guardianSetup(
    hasWakeWord: Boolean,
    voiceprintUsable: Boolean,
    hasLocationPermission: Boolean,
    codewordCount: Int,
    guardianCount: Int,
): GuardianSetup = GuardianSetup(
    ready = buildSet {
        if (hasWakeWord) add(GuardianCapability.HandsFree)
        // Pointless without a wake word to gate, so it depends on both.
        if (hasWakeWord && voiceprintUsable) add(GuardianCapability.VoiceMatch)
        if (hasLocationPermission) add(GuardianCapability.LocationSharing)
        if (codewordCount > 0) add(GuardianCapability.CodewordActions)
        if (guardianCount > 0) add(GuardianCapability.GuardianAlerts)
    }
)
