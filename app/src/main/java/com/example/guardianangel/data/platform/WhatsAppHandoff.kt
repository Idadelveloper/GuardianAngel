package com.example.guardianangel.data.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/**
 * Opens WhatsApp with an alert already typed out.
 *
 * ## Why this is a fallback and not the main channel
 *
 * WhatsApp has **no way for an app to send a message from the user's own account**. The
 * only programmatic sending Meta offers is the WhatsApp Business Cloud API, which sends
 * from a business number you own, needs a verified business and pre-approved template
 * messages, and only reaches people who opted in — none of which describes a woman
 * texting her mother at midnight. The unofficial libraries that claim otherwise drive
 * the WhatsApp Web protocol or an accessibility service and get accounts banned.
 *
 * So what is left is the documented `wa.me` deep link: it opens a chat with the text
 * pre-filled and the user presses send. That is **one tap, not zero**, and nothing in
 * this app may describe it as a sent message.
 *
 * It still earns its place as the fallback when `SEND_SMS` is unavailable: it reaches a
 * guardian over wifi with no mobile signal, shows the sender delivery and read receipts
 * that SMS does not, and for most people it is the app their family actually watches.
 */
object WhatsAppHandoff {

    private const val PACKAGE = "com.whatsapp"
    private const val BUSINESS_PACKAGE = "com.whatsapp.w4b"

    /** Whether either WhatsApp build is installed. */
    fun isAvailable(context: Context): Boolean =
        installedPackage(context) != null

    private fun installedPackage(context: Context): String? {
        val pm = context.packageManager
        return listOf(PACKAGE, BUSINESS_PACKAGE).firstOrNull { pkg ->
            runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
        }
    }

    /**
     * A chat with [phoneNumber], message pre-filled.
     *
     * `wa.me` needs the number in full international form with no punctuation and no
     * leading `+` or zeros — a number stored as "+1 (555) 010-0100" silently opens a
     * "phone number shared via url is invalid" page, which looks like the app failing.
     */
    fun chatIntent(context: Context, phoneNumber: String, message: String): Intent? {
        val digits = normalise(phoneNumber) ?: return null
        val pkg = installedPackage(context) ?: return null
        val url = "https://wa.me/$digits?text=" + URLEncoder.encode(message, "UTF-8")
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .takeIf { it.resolveActivity(context.packageManager) != null }
    }

    /** Digits only, or null if what is stored could not be a reachable number. */
    fun normalise(phoneNumber: String): String? =
        phoneNumber.filter(Char::isDigit)
            .trimStart('0')
            .takeIf { it.length >= MIN_DIGITS }

    /** Below this it is not an international number, whatever else it might be. */
    const val MIN_DIGITS = 8

}
