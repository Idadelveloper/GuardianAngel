package com.example.guardianangel.domain

import com.example.guardianangel.data.berkeley.BerkeleySafetyDataSource
import com.example.guardianangel.domain.model.DataConfidence
import com.example.guardianangel.domain.model.Destination
import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.domain.model.GuardianLocationState
import com.example.guardianangel.domain.model.SafeLocation
import com.example.guardianangel.domain.model.StatusCategory
import com.example.guardianangel.ui.mascot.AngelMood

data class ResolvedSafetyState(
    val locationState: GuardianLocationState,
    val score: Int,
    val mood: AngelMood,
    val heroHeadline: String,
    val heroSubtitle: String,
    val statusBubbleMessage: String,
    val statusCategory: StatusCategory,
    val isSanctuary: Boolean,
)

/**
 * Central state resolver for Guardian Angel.
 *
 * Synchronizes:
 * - Location state (AtHome, TravelingHome, Navigating, OutAndAbout, Emergency)
 * - Safety score (exactly 100 at Home unless exceptional active threat)
 * - Angel mascot mood
 * - Hero copy on Home and Map
 * - Floating status bubble text
 *
 * Invariants:
 * 1. Outside Home, never claim any route is "guaranteed safe".
 * 2. Geofence hysteresis prevents GPS noise from flip-flopping Home state.
 * 3. Never calculates safety states independently across screens.
 */
object GuardianSafetyStateResolver {

    const val DEFAULT_EXIT_HYSTERESIS_METERS = 25f

    fun resolve(
        currentLocation: GeoPoint?,
        home: SafeLocation?,
        safeLocations: List<SafeLocation> = emptyList(),
        isArmed: Boolean = true,
        isRecording: Boolean = false,
        inDuress: Boolean = false,
        activeNavigationDestination: Destination? = null,
        environmentalScore: Int? = null,
        dataConfidence: DataConfidence = DataConfidence.High,
        exceptionalThreatReason: String? = null,
        previousState: GuardianLocationState? = null,
        exitHysteresisMeters: Float = DEFAULT_EXIT_HYSTERESIS_METERS,
    ): ResolvedSafetyState {

        // 1. Check exceptional override first (duress, emergency codeword, active hazard)
        if (inDuress || exceptionalThreatReason != null) {
            val reason = exceptionalThreatReason ?: "Emergency alert triggered"
            val state = GuardianLocationState.Emergency(reason)
            val score = 20
            val mood = AngelMood.Critical
            return ResolvedSafetyState(
                locationState = state,
                score = score,
                mood = mood,
                heroHeadline = "Angel is alert. Help is ready.",
                heroSubtitle = reason,
                statusBubbleMessage = "Angel is alert. Help is ready.",
                statusCategory = StatusCategory.EmergencyStatus,
                isSanctuary = false,
            )
        }

        // 2. Check Home geofence with hysteresis
        val isCurrentlyAtHome = previousState is GuardianLocationState.AtHome
        val isWithinHomeGeofence = if (home != null && currentLocation != null) {
            val dist = BerkeleySafetyDataSource.distanceMeters(currentLocation, home.point)
            if (isCurrentlyAtHome) {
                // Must exceed radius + hysteresis to exit
                dist <= (home.radiusMeters + exitHysteresisMeters)
            } else {
                // Must be within radius to enter
                dist <= home.radiusMeters
            }
        } else {
            false
        }

        if (isWithinHomeGeofence && home != null) {
            val state = GuardianLocationState.AtHome(home)
            val score = 100 // Invariant: score is exactly 100 at Home
            val mood = AngelMood.fromScore(score = score, atSafeHaven = true, isArmed = isArmed, inDuress = false)
            return ResolvedSafetyState(
                locationState = state,
                score = score,
                mood = mood,
                heroHeadline = "You're home. Angel is keeping watch quietly.",
                heroSubtitle = "Your score stays at 100% inside your sanctuary zone.",
                statusBubbleMessage = "You're back in your safe area.",
                statusCategory = StatusCategory.HomeStatus,
                isSanctuary = true,
            )
        }

        // 3. Check additional Safe Locations (campus havens, partner safe stops)
        val matchedSafeLocation = if (currentLocation != null) {
            safeLocations.firstOrNull { loc ->
                !loc.isHome && BerkeleySafetyDataSource.distanceMeters(currentLocation, loc.point) <= loc.radiusMeters
            }
        } else null

        if (matchedSafeLocation != null) {
            val state = GuardianLocationState.AtSafeLocation(matchedSafeLocation)
            val score = 98
            val mood = AngelMood.fromScore(score = score, atSafeHaven = true, isArmed = isArmed, inDuress = false)
            return ResolvedSafetyState(
                locationState = state,
                score = score,
                mood = mood,
                heroHeadline = "Resting at ${matchedSafeLocation.name}.",
                heroSubtitle = "Verified safe location perimeter active.",
                statusBubbleMessage = "Resting in safe area.",
                statusCategory = StatusCategory.LocationStatus,
                isSanctuary = true,
            )
        }

        // 4. Check active navigation to Home vs other destination
        if (activeNavigationDestination != null) {
            val isNavigatingHome = (home != null && activeNavigationDestination.id == home.id) ||
                activeNavigationDestination.name.equals("Home", ignoreCase = true)

            val baseScore = environmentalScore ?: if (isNavigatingHome) 85 else 78
            val state = if (isNavigatingHome && home != null) {
                GuardianLocationState.TravelingHome(home, isArmed)
            } else {
                GuardianLocationState.Navigating(activeNavigationDestination, isArmed)
            }
            val mood = AngelMood.fromScore(score = baseScore, atSafeHaven = false, isArmed = isArmed, inDuress = false)

            val (headline, bubble) = if (isNavigatingHome) {
                "Angel found a lower-exposure route home." to "Your route home has been planned."
            } else {
                "Angel is guiding your walk." to "Walking with Angel."
            }

            return ResolvedSafetyState(
                locationState = state,
                score = baseScore,
                mood = mood,
                heroHeadline = headline,
                heroSubtitle = if (dataConfidence == DataConfidence.Limited) {
                    "Angel is watching, but safety data is limited here."
                } else {
                    "Monitoring corridor illumination and reporting exposure."
                },
                statusBubbleMessage = bubble,
                statusCategory = StatusCategory.NavigationStatus,
                isSanctuary = false,
            )
        }

        // 5. Out and About (Standard transit / walking)
        val state = GuardianLocationState.OutAndAbout
        val resolvedScore = environmentalScore ?: 80
        val mood = AngelMood.fromScore(score = resolvedScore, atSafeHaven = false, isArmed = isArmed, inDuress = false)

        val (headline, subtitle, bubble, category) = when {
            isRecording -> {
                Quadruple(
                    "Angel is listening quietly.",
                    "Transcribing and interpreting situation privately on-device.",
                    "Angel is listening privately.",
                    StatusCategory.ListeningStatus,
                )
            }
            isArmed -> {
                val sub = if (dataConfidence == DataConfidence.Limited) {
                    "Angel is watching, but safety data is limited here."
                } else {
                    "Monitoring environmental signals quietly."
                }
                Quadruple(
                    "You're out. Angel is watching with you.",
                    sub,
                    "Listening quietly.",
                    StatusCategory.ListeningStatus,
                )
            }
            else -> {
                Quadruple(
                    "Angel is on standby.",
                    "Arm before you set off for hands-free listening and routing.",
                    "I'm here with you.",
                    StatusCategory.LocationStatus,
                )
            }
        }

        return ResolvedSafetyState(
            locationState = state,
            score = resolvedScore,
            mood = mood,
            heroHeadline = headline,
            heroSubtitle = subtitle,
            statusBubbleMessage = bubble,
            statusCategory = category,
            isSanctuary = false,
        )
    }

    private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
