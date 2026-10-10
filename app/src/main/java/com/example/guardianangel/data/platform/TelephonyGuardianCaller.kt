package com.example.guardianangel.data.platform

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.guardianangel.R
import com.example.guardianangel.domain.alert.GuardianCaller
import com.example.guardianangel.domain.model.EmergencyContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "GuardianCaller"

/**
 * Rings a guardian's phone.
 *
 * ## Why this is not one line of code
 *
 * `Intent.ACTION_CALL` dials immediately and needs only `CALL_PHONE`. The catch is that
 * dialling means *starting an activity*, and since Android 10 an app with no visible
 * window is not allowed to start one. The emergency this exists for is precisely the case
 * where the phone is locked in a pocket and the app has no window — so the obvious
 * implementation works every time you test it with the app open and fails on the night it
 * matters, silently, with no exception to catch.
 *
 * Android's documented list of exceptions to that rule includes activities started from a
 * `PendingIntent` the system sends on the user's behalf — a notification tap. So:
 *
 * - **App visible** (she pressed SOS, or said the codeword with the app open): dial
 *   directly. Nothing to tap, which is the whole point when hands are not free.
 * - **App in the background**: post a maximum-priority call notification whose tap dials.
 *   A full-screen intent is attached when the system will honour one, which puts the
 *   "Call Mum" button on the lock screen itself. Since Android 14 that permission is
 *   granted automatically only to dialler and alarm apps, so
 *   `canUseFullScreenIntent()` is checked rather than assumed, and the notification still
 *   works as a heads-up banner without it.
 *
 * The two cases return different outcomes, and the difference is never flattened. Telling
 * the user a guardian was called when a notification is sitting unread on her lock screen
 * would be the one lie this app cannot afford.
 *
 * ## Not emergency services
 *
 * This dials a number the user saved herself. It never dials an emergency service, and
 * nothing in the app claims one has been dialled.
 */
class TelephonyGuardianCaller(private val context: Context) : GuardianCaller {

    override fun canCall(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    override suspend fun call(contact: EmergencyContact): GuardianCaller.Outcome =
        withContext(Dispatchers.Main) {
            val number = contact.phoneNumber.trim()
            if (number.isBlank()) {
                return@withContext GuardianCaller.Outcome.Failed(
                    "${contact.name} has no phone number saved"
                )
            }
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                return@withContext GuardianCaller.Outcome.Failed(
                    "This device can't place phone calls"
                )
            }

            // Posted first, and whatever happens next. If the direct dial is refused the
            // notification is the fallback; if it succeeds the notification is the record
            // of who was called, which a user coming back to a finished call wants.
            val posted = postCallNotification(contact, number)

            if (isAppVisible() && canCall()) {
                val dialled = runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                if (dialled.isSuccess) {
                    Log.i(TAG, "Dialling ${contact.name}")
                    return@withContext GuardianCaller.Outcome.Dialling(contact)
                }
                Log.w(TAG, "Direct dial refused; falling back to the notification", dialled.exceptionOrNull())
            }

            if (posted) {
                GuardianCaller.Outcome.AwaitingTap(contact)
            } else {
                GuardianCaller.Outcome.Failed(
                    "Couldn't reach the dialler or post a call reminder"
                )
            }
        }

    /**
     * Whether any of the app's windows is on screen.
     *
     * The process lifecycle is the honest answer to "will Android let me start an
     * activity". A flag maintained by the activity would drift the moment a crash or a
     * process restart skipped its `onStop`.
     */
    private fun isAppVisible(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    /** @return true if the notification was posted. */
    private fun postCallNotification(contact: EmergencyContact, number: String): Boolean {
        val manager = NotificationManagerCompat.from(context)
        createChannel(manager)

        // ACTION_CALL needs CALL_PHONE; ACTION_DIAL needs nothing and opens the dialler
        // with the number ready. Falling back to DIAL keeps the notification useful on a
        // phone where the permission was never granted or was later revoked.
        val callIntent = if (canCall()) {
            Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null))
        } else {
            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val pending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.call_guardian_title, contact.name))
            .setContentText(context.getString(R.string.call_guardian_body))
            .setContentIntent(pending)
            .addAction(0, context.getString(R.string.call_guardian_action, contact.name), pending)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            // Shown in full on the lock screen: hiding the name would make the one tap
            // that matters require an unlock first.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOngoing(false)

        if (canUseFullScreenIntent(manager)) {
            builder.setFullScreenIntent(pending, true)
        }

        // Checked rather than caught. A refused POST_NOTIFICATIONS makes `notify` a
        // silent no-op on some versions and a SecurityException on others, and either way
        // the caller must learn that no call button exists so it can report Failed rather
        // than AwaitingTap.
        val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!notificationsAllowed || !manager.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are off; cannot post a call button")
            return false
        }

        return runCatching {
            manager.notify(NOTIFICATION_ID, builder.build())
            true
        }.getOrElse {
            Log.w(TAG, "Could not post the call notification", it)
            false
        }
    }

    /**
     * Whether the system will honour a full-screen intent from this app.
     *
     * Android 14 restricted the permission to apps whose core function is calling or
     * alarms; everything else has to be granted it by the user in Settings. Asking the
     * manager is the only reliable answer — the permission being in the manifest says
     * nothing about whether it is live.
     */
    private fun canUseFullScreenIntent(manager: NotificationManagerCompat): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { manager.canUseFullScreenIntent() }.getOrDefault(false)
        } else {
            true
        }

    private fun createChannel(manager: NotificationManagerCompat) {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            // High, unlike the listening channel: this one is meant to interrupt.
            NotificationManagerCompat.IMPORTANCE_HIGH,
        )
            .setName(context.getString(R.string.call_guardian_channel_name))
            .setDescription(context.getString(R.string.call_guardian_channel_description))
            .build()
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "guardian_call"
        const val NOTIFICATION_ID = 1002
    }
}
