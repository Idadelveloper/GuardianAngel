package com.example.guardianangel.domain.repository

/**
 * Reads the permissions hands-free listening depends on.
 *
 * An interface, not a snapshot pushed in from the UI, because permission state has
 * exactly one owner — the platform — and anything that caches it will eventually be
 * wrong. It was: the listening repository used to start every process assuming nothing
 * was granted and wait for a screen to tell it otherwise, so the home screen urged the
 * user to grant permissions she had already granted during onboarding.
 *
 * Probing instead means the very first value the UI sees is the truth, and that revoking
 * a permission in system settings shows up on the next read rather than never.
 */
interface PermissionProbe {

    /** `RECORD_AUDIO`. Without it nothing can be captured at all. */
    fun hasMicrophone(): Boolean

    /** `POST_NOTIFICATIONS`, or true below Android 13 where it is not a runtime permission. */
    fun hasNotifications(): Boolean

    /** Fine location. Angel can listen without it; she just cannot say where you are. */
    fun hasLocation(): Boolean

    /**
     * `SEND_SMS`.
     *
     * Not required to run, but it is the difference between an alert that goes out on
     * its own and one waiting on a tap the user may never be able to give.
     */
    fun hasSendSms(): Boolean

    /** Battery optimisation exemption. Not required, but long walks get killed without it. */
    fun isBatteryExempt(): Boolean
}
