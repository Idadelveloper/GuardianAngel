package com.example.guardianangel.ui.map

import com.example.guardianangel.domain.model.GuardianLocationState

/**
 * What Angel says when the user taps her marker to ask "where am I?".
 *
 * Pure, so the wording can be tested without a map. The rule it enforces is that the
 * answer names a *place the user recognises* whenever one is known — "You're home",
 * "You're at Doe Library" — and falls back to an honest "I can't place this spot" rather
 * than inventing a neighbourhood from coordinates we have no gazetteer for.
 *
 * Accuracy is appended only when it is poor enough to matter. Telling someone standing in
 * their own kitchen that the fix is good to three metres is noise; telling her the fix is
 * good to eighty metres explains why the dot is across the street from her.
 */
object AngelWhereAmI {

    /** Above this, the fix is vague enough that the user deserves to be told. */
    const val VAGUE_ACCURACY_METERS = 40f

    data class Callout(
        val headline: String,
        val detail: String,
        /** True when the user is inside a place she has marked safe. */
        val isSanctuary: Boolean,
    )

    fun describe(
        locationState: GuardianLocationState,
        accuracyMeters: Float? = null,
    ): Callout {
        val base = when (locationState) {
            is GuardianLocationState.AtHome -> Callout(
                headline = "You're home",
                detail = "You're inside ${locationState.home.name}. I keep watch quietly " +
                    "here, and your score stays at 100%.",
                isSanctuary = true,
            )

            is GuardianLocationState.AtSafeLocation -> Callout(
                headline = "You're somewhere safe",
                detail = "You're at ${locationState.safeLocation.name}, one of the places " +
                    "you marked safe.",
                isSanctuary = true,
            )

            is GuardianLocationState.TravelingHome -> Callout(
                headline = "On your way home",
                detail = "You're heading back to ${locationState.home.name}. I'll tell you " +
                    "when you're inside.",
                isSanctuary = false,
            )

            is GuardianLocationState.Navigating -> Callout(
                headline = "On your way",
                detail = "You're heading to ${locationState.destination.name}. I'm watching " +
                    "the route with you.",
                isSanctuary = false,
            )

            GuardianLocationState.OutAndAbout -> Callout(
                headline = "You're out and about",
                detail = "This isn't one of your safe places, so I'm paying closer " +
                    "attention. Long-press the map to save this spot if it should be.",
                isSanctuary = false,
            )

            is GuardianLocationState.ActiveThreat -> Callout(
                headline = "I'm on alert",
                detail = locationState.reason,
                isSanctuary = false,
            )

            is GuardianLocationState.Emergency -> Callout(
                headline = "I'm on alert",
                detail = locationState.description,
                isSanctuary = false,
            )
        }

        if (accuracyMeters == null || accuracyMeters <= VAGUE_ACCURACY_METERS) return base
        return base.copy(
            detail = base.detail + " Your position is only accurate to about " +
                "${Math.round(accuracyMeters)} m right now, so the dot may sit a little off.",
        )
    }
}
