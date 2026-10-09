package com.example.guardianangel.domain.model

/**
 * Account, onboarding and device-permission state.
 *
 * Everything a future `users` / `device_settings` table would hold. Secrets are
 * represented by their *status*, never their value: [VoiceProfile] carries a quality
 * score rather than the voiceprint, and [DisarmPin] carries only whether a PIN is set.
 * The real vectors belong in the hardware keystore, and keeping them out of the domain
 * model means they can never accidentally reach a log, a screenshot or a backup.
 */

/** The signed-in user. */
data class UserProfile(
    val id: String,
    val fullName: String,
    val phoneNumber: String,
    val isPhoneVerified: Boolean = false,
    val shieldActive: Boolean = true,
) {
    val firstName: String get() = fullName.substringBefore(' ')
}

/** Runtime permissions Angel needs in order to work at all. */
data class PermissionState(
    val locationGranted: Boolean = false,
    val microphoneGranted: Boolean = false,
) {
    val allGranted: Boolean get() = locationGranted && microphoneGranted
}

/** Result of voice enrolment. The vector itself never leaves the secure enclave. */
data class VoiceProfile(
    val isCalibrated: Boolean = false,
    /** 0–100 acoustic clarity of the enrolled sample. */
    val clarityPercent: Int = 0,
    val calibratedOnEpochMillis: Long? = null,
    val whisperDetection: Boolean = true,
    val noiseCancellation: Boolean = true,
    val sensitivity: ListeningSensitivity = ListeningSensitivity.Balanced,
)

/** How eagerly the on-device keyword spotter fires. */
enum class ListeningSensitivity { Low, Balanced, Whisper }

/**
 * The disarm PIN pair.
 *
 * [hasDecoyPin] enables the reverse-entry safety mechanism: typing the PIN backwards
 * appears to stand the alert down while silently escalating instead.
 */
data class DisarmPin(
    val isSet: Boolean = false,
    val hasDecoyPin: Boolean = true,
    val length: Int = 4,
)

/** Which onboarding step the user has reached. Drives resume-where-you-left-off. */
enum class OnboardingStep(val index: Int, val totalSteps: Int = 4) {
    SignUp(0),
    Permissions(1),
    VoiceCalibration(2),
    Guardians(3),
    Codewords(4),
    Complete(5);

    /** Progress through the four-step wizard, 0f..1f. Sign-up sits outside the bar. */
    val progress: Float
        get() = (index.coerceIn(0, totalSteps)).toFloat() / totalSteps
}

/** Everything the account/settings surfaces read. */
data class AccountSnapshot(
    val profile: UserProfile?,
    val permissions: PermissionState,
    val voiceProfile: VoiceProfile,
    val disarmPin: DisarmPin,
    val onboardingStep: OnboardingStep,
)
