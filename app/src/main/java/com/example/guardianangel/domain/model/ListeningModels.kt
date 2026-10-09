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
 *
 * Note that [requireVoiceMatch] is a *request*, not the outcome. The listening service
 * gates on the voiceprint only when it is good enough to trust — see
 * `VoiceProfile.isUsable` — because gating on a bad voiceprint does not keep a stranger
 * out, it stops Angel waking for the person she belongs to.
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

    companion object {
        /**
         * Takes to aim for, for the progress dots during enrolment.
         *
         * A target, not a threshold. Whether voice matching can actually run is decided
         * by `VoiceProfile.isUsable` — how well the recorded speech *agreed with itself*,
         * which is the thing that determines whether the comparison means anything. A
         * count was the old rule and a bad one: three takes in a noisy kitchen make a
         * voiceprint that rejects its owner.
         */
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

    /**
     * Fine location.
     *
     * Blocking, by product decision: an alert that cannot say where she is leaves her
     * guardians with an emergency and no address. Angel can technically capture audio
     * without it, but a recording nobody can be sent to is half a feature, so recording
     * is gated on location the same way it is gated on the microphone.
     */
    LocationPermission,

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
