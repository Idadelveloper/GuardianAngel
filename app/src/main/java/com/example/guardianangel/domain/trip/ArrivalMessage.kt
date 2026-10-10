package com.example.guardianangel.domain.trip

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The "I'm here" text.
 *
 * Short on purpose. This arrives as an SMS on someone's lock screen, and the one thing
 * they need from it is that she is fine and when — a paragraph buries both. It carries a
 * timestamp because "arrived safely" with no time is ambiguous the moment the phone is
 * picked up an hour later.
 *
 * It never claims anything beyond what the phone observed. The app knows her device
 * reached the destination; it does not know she is safe, so the wording is "arrived",
 * and a custom message is **hers**, passed through untouched.
 */
object ArrivalMessage {

    fun compose(
        userName: String,
        destinationName: String,
        arrivedAtMillis: Long,
        customMessage: String? = null,
        locale: Locale = Locale.getDefault(),
    ): String {
        val who = userName.trim().substringBefore(' ').ifBlank { "Your contact" }
        val time = SimpleDateFormat("h:mm a", locale).format(Date(arrivedAtMillis))
        val place = destinationName.trim().ifBlank { "the destination" }

        val custom = customMessage?.trim()?.takeIf { it.isNotEmpty() }
        if (custom != null) {
            // Her words first, then the facts the app can vouch for. Appended rather
            // than merged so it is always clear which half a person wrote.
            return "$custom\n\n— sent by Guardian Angel when $who reached $place at $time."
        }

        return "$who has arrived at $place at $time. " +
            "Sent automatically by Guardian Angel."
    }
}
