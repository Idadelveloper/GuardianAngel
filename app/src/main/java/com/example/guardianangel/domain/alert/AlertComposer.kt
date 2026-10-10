package com.example.guardianangel.domain.alert

import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.DiarizedEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the message a guardian receives.
 *
 * This text may be the only thing that reaches another person, read on a lock screen,
 * possibly by someone who was asleep. Every decision here follows from that:
 *
 * - **Who, then where, then why.** A guardian's first two questions are "who is this
 *   about" and "where are they". The explanation can wait a line.
 * - **The location link is never buried.** It is the single most actionable item, so it
 *   gets its own line and is a plain `https://` maps URL — those open from any SMS
 *   client, on any phone, without the app installed.
 * - **Quotes, not paraphrase.** If something alarming was said, the guardian sees the
 *   words. A summary that softens "he grabbed my arm" into "an altercation" has edited
 *   the evidence.
 * - **It says what it does not know.** No location means the message says so, rather
 *   than silently omitting the line and leaving the reader to assume.
 *
 * Pure and time-injected, so every variant can be read in a test and judged as writing.
 */
object AlertComposer {

    data class Alert(
        val body: String,
        /**
         * True when the app should offer the user a one-tap call to emergency services.
         *
         * An *offer*, not an automatic dial. Placing a 911 call without a person
         * deciding to is a false report if the trigger was wrong, which is a criminal
         * offence in much of the US and would also tie up a line someone else needs. The
         * app surfaces the call; the human places it.
         */
        val promptEmergencyCall: Boolean,
    )

    fun compose(
        tier: CodewordTier,
        userName: String,
        latitude: Double?,
        longitude: Double?,
        placeLabel: String?,
        entries: List<DiarizedEntry>,
        now: Long = System.currentTimeMillis(),
    ): Alert {
        val name = userName.trim().ifBlank { "Someone you're a guardian for" }
        val summary = TranscriptSummariser.summarise(entries)

        val body = buildString {
            appendLine(headline(tier, name))
            appendLine()

            appendLine("Where: " + locationLine(latitude, longitude, placeLabel))
            appendLine("When: ${CLOCK.format(Date(now))}")
            appendLine()

            appendLine("What Angel heard: ${summary.detail}")
            summary.quote?.let {
                appendLine()
                appendLine("Last thing flagged: “$it”")
            }

            appendLine()
            append(footer(tier))
        }.trim()

        return Alert(
            body = body,
            promptEmergencyCall = tier == CodewordTier.Emergency,
        )
    }

    private fun headline(tier: CodewordTier, name: String): String = when (tier) {
        CodewordTier.Emergency ->
            "EMERGENCY — $name has triggered an emergency alert and may need help now."
        CodewordTier.Danger ->
            "$name has triggered a Guardian Angel alert and may need help."
        CodewordTier.Caution ->
            "$name has started recording and wanted you to know."
        CodewordTier.Safe ->
            "$name is safe — a previous alert has been stood down."
    }

    /**
     * The location line.
     *
     * `https://www.google.com/maps/search/?api=1&query=lat,lng` rather than a `geo:`
     * URI: a geo link is not clickable in most SMS clients and does nothing on a
     * desktop, while this opens in a browser or the Maps app on anything.
     */
    private fun locationLine(
        latitude: Double?,
        longitude: Double?,
        placeLabel: String?,
    ): String {
        if (latitude == null || longitude == null) {
            return "location unavailable — her phone could not get a fix."
        }
        val coords = "%.6f,%.6f".format(Locale.US, latitude, longitude)
        val link = "https://www.google.com/maps/search/?api=1&query=$coords"
        return buildString {
            placeLabel?.takeIf { it.isNotBlank() }?.let { append("$it\n") }
            append(link)
        }
    }

    private fun footer(tier: CodewordTier): String = when (tier) {
        // Does not claim emergency services have been called. The app prompts her to
        // call; it does not dial on her behalf. Telling a guardian help is already on
        // the way when it is not could stop them calling it themselves.
        CodewordTier.Emergency ->
            "Please call her, and call emergency services if you cannot reach her. " +
                "Sent automatically by Guardian Angel."
        CodewordTier.Danger ->
            "Please call or go to her. Sent automatically by Guardian Angel."
        CodewordTier.Caution ->
            "No action needed yet. Sent automatically by Guardian Angel."
        CodewordTier.Safe ->
            "No action needed. Sent automatically by Guardian Angel."
    }

    private val CLOCK = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())
}
