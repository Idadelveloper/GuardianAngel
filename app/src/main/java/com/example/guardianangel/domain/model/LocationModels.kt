package com.example.guardianangel.domain.model

/**
 * Kind of trusted safe location.
 *
 * [Home] is the designated sanctuary base, where the safety score is 100
 * and Angel adopts the Sanctuary mood.
 */
enum class SafeLocationKind {
    Home,
    SafeHaven,
    Custom,
}

/**
 * A user-configured safe location (Home, workplace, partner's flat, trusted sanctuary).
 */
data class SafeLocation(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val radiusMeters: Float = 80f,
    val kind: SafeLocationKind = SafeLocationKind.Custom,
    val address: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis(),
) {
    val isHome: Boolean get() = kind == SafeLocationKind.Home
}

/**
 * Centralized location & situational safety state.
 *
 * Drives Angel's mood, hero copy, map bubble, and route warnings consistently.
 */
sealed class GuardianLocationState {
    data class AtHome(val home: SafeLocation) : GuardianLocationState()
    data class AtSafeLocation(val safeLocation: SafeLocation) : GuardianLocationState()
    object OutAndAbout : GuardianLocationState()
    data class Navigating(val destination: Destination, val isArmed: Boolean) : GuardianLocationState()
    data class TravelingHome(val home: SafeLocation, val isArmed: Boolean) : GuardianLocationState()
    data class ActiveThreat(val reason: String) : GuardianLocationState()
    data class Emergency(val description: String) : GuardianLocationState()
}

/**
 * Live snapshot of the user's physical movement.
 */
data class UserLocationSnapshot(
    val point: GeoPoint,
    val accuracyMeters: Float? = null,
    val bearingDegrees: Float? = null,
    val bearingAccuracyDegrees: Float? = null,
    val speedMps: Float? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
)
