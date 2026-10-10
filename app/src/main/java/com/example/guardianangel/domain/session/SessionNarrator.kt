package com.example.guardianangel.domain.session

import com.example.guardianangel.domain.model.DiarizedEntry
import com.example.guardianangel.domain.model.SpeakerKind
import kotlin.math.roundToInt

/**
 * Turns a finished recording into something a person can read.
 *
 * A session arrives as a few dozen timestamped fragments — half-heard sentences, YAMNet
 * class names like "Slap, smack", a codeword, an alert. That is evidence, not an
 * account. Nobody scrolls a hundred lines to find out whether the walk home was fine,
 * and the one time it was not fine is the one time she will be least able to.
 *
 * So this produces three things: a **title** that says what happened, a **summary** of a
 * few sentences, and the **key moments** worth jumping to. It is deliberately pure and
 * deterministic — see `SessionSummaryAgent` for why this is not a language model.
 *
 * ## The rule it will not break
 *
 * It describes; it does not diagnose. "A sound like a slap or impact" is what the
 * classifier can support. "You were assaulted" is not, however likely it looks, because
 * this text ends up in an export that someone may hand to a police officer.
 */
object SessionNarrator {

    enum class Severity { Routine, Notable, Alarming }

    data class KeyMoment(
        val atMillis: Long,
        /** Milliseconds from the start of the recording, for a "02:14" label. */
        val offsetMillis: Long,
        val label: String,
        val detail: String,
        val severity: Severity,
    )

    data class Narrative(
        val title: String,
        val summary: String,
        val keyMoments: List<KeyMoment>,
    )

    /**
     * @param locationLabel where it happened, if a fix was ever taken.
     * @param triggeredTierName the codeword tier that opened or escalated the session.
     * @param guardiansNotified who was told, by name.
     */
    fun narrate(
        startedAtMillis: Long,
        endedAtMillis: Long?,
        entries: List<DiarizedEntry>,
        locationLabel: String? = null,
        triggeredTierName: String? = null,
        guardiansNotified: List<String> = emptyList(),
        lowestSafetyScore: Int? = null,
    ): Narrative {
        val ordered = entries.sortedBy { it.atEpochMillis }
        val speech = ordered.filter { it.speakerKind != SpeakerKind.SoundEvent }
        val sounds = ordered.filter { it.speakerKind == SpeakerKind.SoundEvent }
        val strangers = speech.filter { it.speakerKind == SpeakerKind.Unknown }
        val flagged = ordered.filter { it.isFlagged }
        val alarmingSounds = sounds.filter { interpretSound(it.text).severity == Severity.Alarming }

        val moments = keyMoments(startedAtMillis, ordered)
        val minutes = durationMinutes(startedAtMillis, endedAtMillis)
        val place = locationLabel?.takeIf { it.isNotBlank() && it !in PLACEHOLDER_LABELS }

        return Narrative(
            title = title(
                triggeredTierName = triggeredTierName,
                alarmingSoundCount = alarmingSounds.size,
                strangerCount = strangers.size,
                flaggedCount = flagged.size,
                place = place,
                minutes = minutes,
            ),
            summary = summary(
                minutes = minutes,
                place = place,
                speechCount = speech.size,
                strangers = strangers,
                sounds = sounds,
                flagged = flagged,
                triggeredTierName = triggeredTierName,
                guardiansNotified = guardiansNotified,
                lowestSafetyScore = lowestSafetyScore,
            ),
            keyMoments = moments,
        )
    }

    // -- Title ------------------------------------------------------------------------

    /**
     * Named for the most consequential thing that happened.
     *
     * Checked in descending order of seriousness and stopping at the first match, so a
     * session with a codeword *and* a stranger is titled by the codeword. A title that
     * averaged the two would describe neither.
     */
    private fun title(
        triggeredTierName: String?,
        alarmingSoundCount: Int,
        strangerCount: Int,
        flaggedCount: Int,
        place: String?,
        minutes: Long,
    ): String {
        val where = place?.let { " · $it" }.orEmpty()
        return when {
            triggeredTierName == "Emergency" -> "Emergency codeword$where"
            triggeredTierName == "Danger" -> "Danger codeword$where"
            alarmingSoundCount > 0 -> "Sounds of a struggle$where"
            strangerCount > 0 && flaggedCount > 0 -> "Someone approached you$where"
            triggeredTierName == "Caution" -> "You flagged something$where"
            strangerCount > 0 -> "A conversation with someone else$where"
            flaggedCount > 0 -> "Something you said stood out$where"
            minutes >= 1L -> "Quiet recording · ${minutes} min"
            else -> "Short recording$where"
        }
    }

    // -- Summary ----------------------------------------------------------------------

    private fun summary(
        minutes: Long,
        place: String?,
        speechCount: Int,
        strangers: List<DiarizedEntry>,
        sounds: List<DiarizedEntry>,
        flagged: List<DiarizedEntry>,
        triggeredTierName: String?,
        guardiansNotified: List<String>,
        lowestSafetyScore: Int?,
    ): String = buildList {
        add(
            buildString {
                append(if (minutes >= 1L) "A $minutes-minute recording" else "A short recording")
                place?.let { append(" around $it") }
                append(".")
            }
        )

        if (speechCount == 0 && sounds.isEmpty()) {
            add("Nothing was picked up — no speech and no notable sounds.")
        }

        if (strangers.isNotEmpty()) {
            add(
                "${strangers.size} line${plural(strangers.size)} came from a voice that " +
                    "isn't yours."
            )
        } else if (speechCount > 0) {
            add("Everything spoken matched your voice.")
        }

        // Grouped and counted rather than listed one by one: "three sounds like an
        // impact" is a pattern, while eight separate lines saying "Slap, smack" is a
        // wall of text that hides it.
        val interpreted = sounds
            .map { interpretSound(it.text) }
            .filter { it.severity != Severity.Routine }
            .groupingBy { it.phrase }
            .eachCount()
        if (interpreted.isNotEmpty()) {
            add(
                "I heard " + interpreted.entries.joinToString(", ") { (phrase, count) ->
                    if (count > 1) "$phrase (×$count)" else phrase
                } + "."
            )
        }

        // Quoted, not paraphrased. A summary that softens what was actually said has
        // edited the evidence, and this text can end up in an export.
        flagged.firstOrNull { it.speakerKind != SpeakerKind.SoundEvent && it.text.isNotBlank() }
            ?.let { add("One line stood out: “${it.text.trim()}”") }

        triggeredTierName?.let {
            add("Your $it codeword fired during this recording.")
        }

        if (guardiansNotified.isNotEmpty()) {
            add("${guardiansNotified.joinToString(" and ")} ${wasWere(guardiansNotified.size)} alerted.")
        }

        lowestSafetyScore?.takeIf { it < 70 }?.let {
            add("Your safety score fell to $it% at its lowest.")
        }
    }.joinToString(" ")

    // -- Key moments ------------------------------------------------------------------

    /**
     * The points worth jumping to.
     *
     * Capped, because a list of thirty moments is the transcript again. They are ranked
     * by severity and then thinned by time, so three shouts ten seconds apart become one
     * moment rather than crowding out the codeword a minute later.
     */
    private fun keyMoments(startedAt: Long, ordered: List<DiarizedEntry>): List<KeyMoment> {
        val candidates = ordered.mapNotNull { entry ->
            val offset = (entry.atEpochMillis - startedAt).coerceAtLeast(0L)
            when {
                entry.speakerKind == SpeakerKind.SystemAction -> KeyMoment(
                    atMillis = entry.atEpochMillis,
                    offsetMillis = offset,
                    label = "Angel acted",
                    detail = entry.text,
                    severity = Severity.Alarming,
                )

                entry.speakerKind == SpeakerKind.SoundEvent -> {
                    val sound = interpretSound(entry.text)
                    if (sound.severity == Severity.Routine) {
                        null
                    } else {
                        KeyMoment(
                            atMillis = entry.atEpochMillis,
                            offsetMillis = offset,
                            label = sound.phrase.replaceFirstChar(Char::uppercase),
                            detail = "Recognised from the sound alone as “${entry.text}”.",
                            severity = sound.severity,
                        )
                    }
                }

                entry.isFlagged -> KeyMoment(
                    atMillis = entry.atEpochMillis,
                    offsetMillis = offset,
                    label = "${entry.speakerLabel} said something concerning",
                    detail = entry.text,
                    severity = Severity.Notable,
                )

                else -> null
            }
        }

        val kept = mutableListOf<KeyMoment>()
        candidates
            .sortedWith(compareByDescending<KeyMoment> { it.severity.ordinal }.thenBy { it.atMillis })
            .forEach { candidate ->
                val crowded = kept.any {
                    it.label == candidate.label &&
                        kotlin.math.abs(it.atMillis - candidate.atMillis) < THIN_WINDOW_MILLIS
                }
                if (!crowded && kept.size < MAX_MOMENTS) kept += candidate
            }
        return kept.sortedBy { it.atMillis }
    }

    // -- Sound interpretation ---------------------------------------------------------

    data class Interpreted(val phrase: String, val severity: Severity)

    /**
     * What a raw AudioSet class name means in plain words.
     *
     * YAMNet's labels are a taxonomy, not language: "Slap, smack" and "Crying, sobbing"
     * are useful to a classifier and useless in a sentence someone reads at 1 a.m. The
     * hedged phrasing is deliberate — the model heard an acoustic pattern, not an event,
     * and "a sound like a slap or impact" is the strongest claim the evidence supports.
     */
    fun interpretSound(label: String): Interpreted = when (label.lowercase()) {
        "screaming", "yell", "shriek" -> Interpreted("a scream", Severity.Alarming)
        "shout", "children shouting", "bellow" -> Interpreted("shouting", Severity.Alarming)
        "slap, smack", "smash, crash", "thump, thud", "whack, thwack" ->
            Interpreted("a sound like a slap or impact", Severity.Alarming)
        "glass", "breaking", "shatter" -> Interpreted("breaking glass", Severity.Alarming)
        "gunshot, gunfire", "explosion" -> Interpreted("a bang like a gunshot", Severity.Alarming)
        "crying, sobbing", "whimper", "sobbing" -> Interpreted("crying", Severity.Notable)
        "groan", "grunt", "gasp", "pant" -> Interpreted("strained breathing or a groan", Severity.Notable)
        "run", "running", "footsteps" -> Interpreted("running footsteps", Severity.Notable)
        "siren", "emergency vehicle", "police car (siren)", "ambulance (siren)" ->
            Interpreted("a siren nearby", Severity.Notable)
        "vehicle", "car", "motor vehicle (road)", "traffic noise, roadway noise" ->
            Interpreted("traffic", Severity.Routine)
        else -> Interpreted(label.lowercase(), Severity.Routine)
    }

    // -- Helpers ----------------------------------------------------------------------

    private fun durationMinutes(startedAt: Long, endedAt: Long?): Long =
        endedAt?.let { ((it - startedAt) / 60_000.0).roundToInt().toLong() } ?: 0L

    private fun plural(count: Int) = if (count == 1) "" else "s"
    private fun wasWere(count: Int) = if (count == 1) "was" else "were"

    /** Labels the recorder writes before a real fix arrives. Not places. */
    private val PLACEHOLDER_LABELS = setOf("Locating…", "Location not recorded", "Unknown")

    /** Two identical moments closer together than this are the same event. */
    private const val THIN_WINDOW_MILLIS = 15_000L
    private const val MAX_MOMENTS = 8
}
