package com.example.guardianangel.data

import com.example.guardianangel.domain.repository.PermissionProbe

/**
 * A [PermissionProbe] with fixed answers, for previews and tests.
 *
 * Mutable so a test can flip a permission mid-flow and assert the UI follows, which is
 * the behaviour that matters: the point of probing is that the answer can change without
 * anything in app state changing.
 */
class GrantedPermissions(
    var microphone: Boolean = true,
    var notifications: Boolean = true,
    var location: Boolean = true,
    var sendSms: Boolean = true,
    var batteryExempt: Boolean = true,
) : PermissionProbe {
    override fun hasMicrophone(): Boolean = microphone
    override fun hasNotifications(): Boolean = notifications
    override fun hasLocation(): Boolean = location
    override fun hasSendSms(): Boolean = sendSms
    override fun isBatteryExempt(): Boolean = batteryExempt
}
