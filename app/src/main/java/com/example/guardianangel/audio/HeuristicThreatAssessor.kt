package com.example.guardianangel.audio

import kotlin.math.min

/**
 * The always-on reasoning tier: transparent rules over the cheap signals.
 *
 * ## Why this exists even once an LLM is available
 *
 * Three reasons, in order of importance:
 *
 *  1. **It can run on every chunk.** A language model costs seconds and ~1 GB of RAM per
 *     call; this costs microseconds. Something has to look at *all* the audio and decide
 *     what is worth escalating to the expensive tier, and that something cannot itself
 *     be expensive.
 *  2. **It is auditable.** Every point of severity traces to a named signal the user can
 *     read back in the transcript log. "Angel thought so" is not an acceptable
 *     explanation for having called the police.
 *  3. **It still works when the model is missing, the device is low on memory, or
 *     inference fails.** A safety app must degrade to *something*.
 *
 * The weights below are a starting point, not a trained model. They encode what the
 * brief and the distress-detection literature agree are signals: repeated refusals,
 * unknown voices, raised volume, scream or glass events, late hours, and a low location
 * score. Real weights should come from labelled data — see the roadmap.
 */
class HeuristicThreatAssessor : ThreatAssessor {

    override val isReady: Boolean = true
    override suspend fun load(): Boolean = true
    override fun close() = Unit

    override suspend fun assess(snapshot: SituationSnapshot): ThreatAssessment {
        var severity = 0f
        val signals = mutableListOf<String>()

        val text = snapshot.transcript.joinToString(" ") { it.text }.lowercase()

        // Repeated refusal is the single most cited verbal distress marker, and the one
        // the product brief calls out by name.
        val refusals = REFUSAL_PATTERNS.sumOf { pattern ->
            Regex("\\b${Regex.escape(pattern)}\\b").findAll(text).count()
        }
        if (refusals > 0) {
            // A single refusal counts: on its own it is not enough to escalate, but it
            // should be enough to lift the window into the band the reasoning tier looks
            // at. Repeats compound, capped so a transcript full of "no" cannot alone
            // carry the decision.
            severity += min(REFUSAL_CAP, REFUSAL_WEIGHT * refusals)
            signals += if (refusals >= 2) {
                "You said no or stop $refusals times"
            } else {
                "You said no"
            }
        }

        if (DISTRESS_PHRASES.any { it in text }) {
            // An explicit call for help is the strongest verbal signal there is and was
            // previously weighted below a raised voice, which was plainly wrong.
            severity += DISTRESS_WEIGHT
            signals += "You asked for help or to be left alone"
        }

        // Acoustic events carry their own confidence; a confident scream outweighs a
        // hedged one.
        snapshot.events.filter { it.isDangerSignal }.forEach { event ->
            severity += DANGER_EVENT_WEIGHT * event.confidence
            signals += "${event.label} detected"
        }

        if (snapshot.unknownVoicePresent) {
            severity += 0.12f
            signals += "An unfamiliar voice is present"
        }
        if (snapshot.speakerCount >= 3) {
            severity += 0.08f
            signals += "${snapshot.speakerCount} voices around you"
        }

        snapshot.peakDecibels?.let { peak ->
            if (peak >= LOUD_DB) {
                severity += 0.12f
                signals += "Raised voices at $peak dB"
            }
        }

        // Context does not create danger on its own, so these only nudge — but the same
        // words at 2am on an unlit street do mean something different.
        if (snapshot.hourOfDay >= 22 || snapshot.hourOfDay <= 4) {
            severity += 0.05f
            signals += "Late at night"
        }
        if (snapshot.locationScore < 60) {
            severity += 0.08f
            signals += "Area scores ${snapshot.locationScore}%"
        }

        severity = severity.coerceIn(0f, 1f)

        return ThreatAssessment(
            severity = severity,
            rationale = describe(severity, signals),
            // Escalation needs corroboration: no single weak signal should be able to
            // call someone's emergency contacts on its own.
            recommendEscalation = severity >= ESCALATE_AT && signals.size >= 2,
            contributingSignals = signals,
        )
    }

    private fun describe(severity: Float, signals: List<String>): String = when {
        signals.isEmpty() -> "Nothing concerning so far."
        severity >= ESCALATE_AT ->
            "This looks like it's escalating — ${signals.first().replaceFirstChar { it.lowercase() }}."
        severity >= 0.3f ->
            "Keeping a close ear on this — ${signals.first().replaceFirstChar { it.lowercase() }}."
        else -> "Noted, but nothing that worries me yet."
    }

    /** True when the cheap tier is unsure enough that the expensive tier is worth it. */
    fun warrantsDeepAnalysis(assessment: ThreatAssessment): Boolean =
        assessment.severity in AMBIGUOUS_BAND

    private companion object {
        const val ESCALATE_AT = 0.6f
        const val LOUD_DB = 80

        // Weights are a starting point, not a trained model. They are ordered by how
        // directly each signal indicates danger: an explicit plea outranks a scream,
        // which outranks a stranger's voice, which outranks the time of day.
        const val DISTRESS_WEIGHT = 0.35f
        const val DANGER_EVENT_WEIGHT = 0.30f
        const val REFUSAL_WEIGHT = 0.10f
        const val REFUSAL_CAP = 0.35f

        /**
         * The band where the heuristic is genuinely uncertain. Below it nothing is
         * happening; above it the cheap signals already agree and waiting seconds for a
         * language model would only delay help.
         */
        val AMBIGUOUS_BAND = 0.30f..0.6f

        val REFUSAL_PATTERNS = listOf("no", "stop", "don't", "dont", "leave me alone", "get off")
        val DISTRESS_PHRASES = listOf(
            "help me", "call the police", "let go", "i'm scared", "im scared",
            "go away", "get away from me", "someone help",
        )
    }
}
