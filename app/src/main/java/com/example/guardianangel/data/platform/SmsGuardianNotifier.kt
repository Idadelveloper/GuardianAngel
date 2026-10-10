package com.example.guardianangel.data.platform

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.guardianangel.domain.alert.GuardianNotifier
import com.example.guardianangel.domain.alert.NotifyOutcome
import com.example.guardianangel.domain.model.EmergencyContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SmsNotifier"

/**
 * Sends the alert by SMS.
 *
 * ## Why SMS, and why this is the hard part
 *
 * An alert has to arrive when the situation is at its worst, which is exactly when data
 * is least reliable. SMS works on one bar with no data plan, needs no account on the
 * receiving end, and lands on the lock screen of any phone. A push notification would
 * need every guardian to install this app; an email is read tomorrow.
 *
 * `SEND_SMS` is a restricted permission on Google Play. The policy lists
 * **"Physical safety/emergency alerts to send SMS"** as an eligible exception, declared
 * through the Permissions Declaration Form in Play Console. That declaration has to be
 * made before release, and it is reviewed — which is why this class is written to work
 * either way rather than assuming approval.
 *
 * ## The fallback is not silent, and says so
 *
 * Without the permission, the alert opens the user's messaging app with the recipients
 * and text already filled in. That needs no permission at all, but it needs a tap — so
 * it is a genuinely weaker promise, and [canSendSilently] exists so the UI can tell her
 * which one she has rather than implying a silent alert she is not going to get.
 *
 * Messages are sent with `sendMultipartTextMessage`: an alert with a maps link and a
 * quote runs past the 160-character limit, and a single-part send would truncate the
 * part carrying the location.
 */
class SmsGuardianNotifier(private val context: Context) : GuardianNotifier {

    override val canSendSilently: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun notify(
        guardians: List<EmergencyContact>,
        message: String,
    ): NotifyOutcome = withContext(Dispatchers.IO) {
        if (guardians.isEmpty()) {
            return@withContext NotifyOutcome.blocked(
                "There is nobody in your circle to alert yet."
            )
        }
        if (!canSendSilently) {
            return@withContext handOffToMessagingApp(guardians, message)
        }

        val manager = runCatching { context.getSystemService(SmsManager::class.java) }.getOrNull()
            ?: return@withContext NotifyOutcome.blocked(
                "This device cannot send SMS."
            )

        val reached = mutableListOf<String>()
        val failed = mutableListOf<String>()

        guardians.forEach { guardian ->
            val number = guardian.phoneNumber.filter { it.isDigit() || it == '+' }
            if (number.length < MIN_NUMBER_LENGTH) {
                failed += guardian.name
                return@forEach
            }
            runCatching {
                val parts = manager.divideMessage(message)
                manager.sendMultipartTextMessage(number, null, parts, null, null)
            }.fold(
                onSuccess = { reached += guardian.name },
                onFailure = {
                    Log.w(TAG, "Could not text ${guardian.name}", it)
                    failed += guardian.name
                },
            )
        }

        NotifyOutcome(reached = reached, failed = failed)
    }

    /**
     * Opens the messaging app with everything filled in.
     *
     * One composer addressed to everyone rather than one per guardian: a queue of
     * message screens to dismiss is the last thing someone in trouble should face.
     */
    private fun handOffToMessagingApp(
        guardians: List<EmergencyContact>,
        message: String,
    ): NotifyOutcome {
        val numbers = guardians
            .map { it.phoneNumber.filter { c -> c.isDigit() || c == '+' } }
            .filter { it.length >= MIN_NUMBER_LENGTH }

        if (numbers.isEmpty()) {
            return NotifyOutcome.blocked("No usable phone numbers in your circle.")
        }

        val intent = Intent(
            Intent.ACTION_SENDTO,
            Uri.parse("smsto:${numbers.joinToString(";")}"),
        ).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return runCatching { context.startActivity(intent) }.fold(
            onSuccess = {
                NotifyOutcome.blocked(
                    "Your messaging app is open with the alert ready — tap send. " +
                        "Allow Guardian Angel to send texts so this happens on its own."
                )
            },
            onFailure = {
                Log.w(TAG, "No messaging app could be opened", it)
                NotifyOutcome.blocked("No messaging app is available to send the alert.")
            },
        )
    }

    private companion object {
        /** Shorter than this is not a dialable number, not even a short code. */
        const val MIN_NUMBER_LENGTH = 5
    }
}
