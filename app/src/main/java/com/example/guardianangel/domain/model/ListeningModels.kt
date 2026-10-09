package com.example.guardianangel.domain.model

/**
 * Hands-free activation.
 *
 * Guardian Angel has **two** kinds of spoken trigger, and conflating them was the main
 * modelling mistake to avoid:
 *
 *  - A [WakeWord] wakes Angel out of standby and *starts* a recording. It has to be
 *    matched continuously, on battery, with the screen off, so the model behind it must
 *    be tiny — which is exactly why it is a single short phrase the user enrols by
 *    saying it a few times.
 *
 *  - A [Codeword] is said *while Angel is already recording* and chooses what she does
 *    next (check in, stay quiet, alert the circle, call for help). By then audio is
 *    already being transcribed, so these can be longer, more natural phrases matched
 *    against the transcript rather than against a 1 MB always-on model.
 *
 * Keeping them as separate types means a screen can never accidentally offer the wrong
 * one, and the two very different runtime budgets stay visible in the code.
 */

/**
 * The phrase that wakes Angel and starts recording.
 *
 * Typing the phrase is **all that is required**. The keyword spotter behind it is
 * open-vocabulary — it matches a phrase from its tokens, not from recordings of the user
 * saying it — so there is no enrolment step standing between setup and protection.
 *
 * [voiceSamples] is therefore optional, and serves a different purpose: it builds the
 * voiceprint that [requireVoiceMatch] checks, so somebody *else* saying the phrase
 * cannot start a recording on her phone. Without samples that check simply cannot run,
 * which is why it degrades to off rather than blocking.
 */
data class WakeWord(
    val phrase: String,
    /** Recordings captured to build the voiceprint. Optional; improves voice matching. */
    val voiceSamples: Int = 0,
    /** Only fire when the voice also matches the enrolled voiceprint. */
    val requireVoiceMatch: Boolean = true,
    /** How eagerly the spotter fires. Higher sensitivity catches whispers and more noise. */
    val sensitivity: ListeningSensitivity = ListeningSensitivity.Balanced,
) {
    /** Ready to listen. A phrase is enough. */
    val isEnrolled: Boolean get() = phrase.isNotBlank()

    /** Enough samples for the voiceprint check to be meaningful. */
    val canMatchVoice: Boolean get() = voiceSamples >= RECOMMENDED_SAMPLES

    /** True when the user asked for voice matching but has not recorded enough for it. */
    val voiceMatchUnavailable: Boolean get() = requireVoiceMatch && !canMatchVoice

    companion object {
        const val RECOMMENDED_SAMPLES = 3
    }
}

/** What the always-on listener is doing. */
enum class ListeningState {
    /** Not listening. Nothing is being captured. */
    Off,

    /** Starting up — permissions granted, service spinning up, model loading. */
    Arming,

    /** Capturing and matching the wake word. The mic is live. */
    Listening,

    /** The wake word fired; a recording session is starting. */
    Triggered,

    /** Cannot listen — see [ListeningStatus.blockedBy]. */
    Blocked,
}

/**
 * Something that must be true before Angel can listen hands-free.
 *
 * Modelled explicitly rather than as a pile of booleans so the UI can list exactly what
 * is missing, in order, and so the rules are testable without a device.
 */
enum class ListeningRequirement {
    /** `RECORD_AUDIO` runtime permission. */
    MicrophonePermission,

    /** `POST_NOTIFICATIONS` — Android 13+ needs it to show the listening notification. */
    NotificationPermission,

    /** No wake phrase has been set yet. */
    WakeWordEnrolled,

    /** Battery optimisation exemption. Not fatal, but long walks get killed without it. */
    BatteryExemption,
}

/**
 * Whether Angel can listen, and why not.
 *
 * @param detectorReady true once the keyword model is loaded. False means the build has
 *   no model asset installed and hands-free activation will not fire — see the audio
 *   package documentation.
 */
data class ListeningStatus(
    val state: ListeningState = ListeningState.Off,
    val blockedBy: List<ListeningRequirement> = emptyList(),
    val detectorReady: Boolean = false,
    /** Wall-clock seconds the current listening session has been armed. */
    val armedForSeconds: Long = 0,
) {
    /** True when everything required is in place, whether or not it is running yet. */
    val canListen: Boolean get() = blockedBy.none { it != ListeningRequirement.BatteryExemption }

    val isActive: Boolean
        get() = state == ListeningState.Listening || state == ListeningState.Triggered
}
