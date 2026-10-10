package com.example.guardianangel.domain.model

/**
 * One journey the user asked Angel to walk with her.
 *
 * A trip outlives the screen that started it. Walking state used to be `remember`ed
 * inside the map composable, so switching tabs — or taking a call — silently ended the
 * navigation she was relying on, and she had to notice and restart it. Nothing about
 * being somewhere else in the app means she stopped walking.
 */
data class Trip(
    val id: String,
    val destinationName: String,
    val destinationAddress: String,
    val destination: GeoPoint,
    /** The route she chose, so the same line is drawn when she comes back to the map. */
    val routeId: String?,
    val routeLabel: String?,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val outcome: TripOutcome = TripOutcome.InProgress,
    /** Straight-line metres from where she set off, filled in when the trip closes. */
    val distanceMeters: Int? = null,
    /** Guardian to tell when she arrives, if she asked for that. */
    val notifyContactId: String? = null,
    val notifyContactName: String? = null,
    /** What to say instead of the default arrival message. */
    val notifyMessage: String? = null,
    /** When the arrival text actually went out, or null if it did not. */
    val arrivalNotifiedAtEpochMillis: Long? = null,
    /** Why the arrival text did not go out, when it did not. */
    val arrivalNotifyError: String? = null,
) {
    val isActive: Boolean get() = outcome == TripOutcome.InProgress

    val durationMillis: Long?
        get() = endedAtEpochMillis?.let { it - startedAtEpochMillis }
}

enum class TripOutcome {
    /** Still walking. Exactly one trip may be in this state at a time. */
    InProgress,

    /** She reached the destination. */
    Arrived,

    /**
     * She stopped navigating before arriving.
     *
     * Not a failure to report back as one — plans change, and a journey log that shames
     * someone for changing her mind is a log she will turn off.
     */
    Ended,
}
