package com.example.guardianangel.data.platform

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.guardianangel.domain.alert.GuardianNotifier
import com.example.guardianangel.domain.alert.NotifyOutcome
import com.example.guardianangel.domain.model.EmergencyContact
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
        val pending = mutableMapOf<String, SendReceipt>()

        guardians.forEach { guardian ->
            val number = guardian.phoneNumber.filter { it.isDigit() || it == '+' }
            if (number.length < MIN_NUMBER_LENGTH) {
                Log.w(TAG, "${guardian.name} has no usable number")
                failed += guardian.name
                return@forEach
            }
            val parts = runCatching { manager.divideMessage(message) }.getOrNull()
            if (parts.isNullOrEmpty()) {
                failed += guardian.name
                return@forEach
            }
            val receipt = SendReceipt(context, guardian.name, parts.size)
            runCatching {
                manager.sendMultipartTextMessage(number, null, parts, receipt.intents, null)
            }.fold(
                onSuccess = { pending[guardian.name] = receipt },
                onFailure = {
                    Log.w(TAG, "Could not text ${guardian.name}", it)
                    receipt.cancel()
                    failed += guardian.name
                },
            )
        }

        // Handing a message to `SmsManager` is not sending it. Without these receipts a
        // flight-mode phone, a dead SIM or a rejected message all reported success, and
        // the app told the user her guardians had been texted when nothing had left the
        // device. The wait is capped because an alert must not hang on a bad network —
        // whatever has not answered by then is reported as unconfirmed, not as sent.
        val unconfirmed = mutableListOf<String>()
        withTimeoutOrNull(CONFIRM_TIMEOUT_MILLIS) {
            pending.forEach { (name, receipt) ->
                if (receipt.await()) reached += name else failed += name
            }
        } ?: run {
            pending.forEach { (name, receipt) ->
                when (receipt.result) {
                    true -> if (name !in reached) reached += name
                    false -> if (name !in failed) failed += name
                    null -> unconfirmed += name
                }
            }
        }
        pending.values.forEach { it.cancel() }

        NotifyOutcome(reached = reached, failed = failed, unconfirmed = unconfirmed)
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

        // WhatsApp first when the circle is one person. A pre-filled chat is the same
        // single tap as the SMS composer but reaches them over wifi with no signal, and
        // it is the app most families actually watch. It cannot address several people
        // at once, though — so for a real circle the SMS composer wins, because one tap
        // for everyone beats three taps one at a time.
        val top = guardians.minByOrNull { it.priority }
        if (numbers.size == 1 && top != null) {
            WhatsAppHandoff.chatIntent(context, top.phoneNumber, message)?.let { intent ->
                val opened = runCatching { context.startActivity(intent) }.isSuccess
                if (opened) {
                    return NotifyOutcome.blocked(
                        "WhatsApp is open with the alert ready for ${top.name} — tap send. " +
                            "Allow Guardian Angel to send texts so this happens on its own."
                    )
                }
            }
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

        /**
         * How long to wait for the network to confirm, before reporting unconfirmed.
         *
         * Eight seconds: long enough that a normal send resolves, short enough that the
         * screen is not frozen on "sending" while someone needs to move. Nothing else
         * waits on this — the guardian call is placed in parallel.
         */
        const val CONFIRM_TIMEOUT_MILLIS = 8_000L
    }
}

/**
 * One guardian's send result, collected from the platform's sent-intent broadcasts.
 *
 * A multipart message produces one broadcast per part, and the message only counts as
 * sent if every part was. A unique action per receipt keeps two concurrent alerts from
 * reading each other's results.
 */
private class SendReceipt(
    private val context: Context,
    guardianName: String,
    private val partCount: Int,
) {
    private val action = "com.example.guardianangel.SMS_SENT.$guardianName.${System.nanoTime()}"
    private val outcome = CompletableDeferred<Boolean>()
    private var seen = 0
    private var allOk = true

    /** The result if it has already arrived, null while still waiting. */
    val result: Boolean? get() = if (outcome.isCompleted) outcome.getCompleted() else null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            seen++
            if (resultCode != Activity.RESULT_OK) {
                Log.w(TAG, "SMS part failed with result $resultCode")
                allOk = false
            }
            if (seen >= partCount) outcome.complete(allOk)
        }
    }

    val intents: ArrayList<PendingIntent> = ArrayList<PendingIntent>().apply {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(action),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        repeat(partCount) { part ->
            add(
                PendingIntent.getBroadcast(
                    context,
                    part,
                    Intent(action).setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
            )
        }
    }

    suspend fun await(): Boolean = outcome.await()

    fun cancel() {
        runCatching { context.unregisterReceiver(receiver) }
    }
}
