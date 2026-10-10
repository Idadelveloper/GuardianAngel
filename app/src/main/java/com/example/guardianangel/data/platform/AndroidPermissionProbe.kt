package com.example.guardianangel.data.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.guardianangel.domain.repository.PermissionProbe

/** [PermissionProbe] backed by the real platform. */
class AndroidPermissionProbe(private val context: Context) : PermissionProbe {

    override fun hasMicrophone(): Boolean = granted(Manifest.permission.RECORD_AUDIO)

    /** Notifications only became a runtime permission in Android 13. */
    override fun hasNotifications(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

    override fun hasLocation(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION)

    override fun hasSendSms(): Boolean = granted(Manifest.permission.SEND_SMS)

    override fun isBatteryExempt(): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
