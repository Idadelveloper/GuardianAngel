package com.example.guardianangel.domain.alert

import com.example.guardianangel.domain.model.DiarizedEntry
import com.example.guardianangel.domain.model.SpeakerKind

/**
 * Turns a transcript into a sentence someone can act on.
 *
 * ## Why this is not an LLM
 *
 * It is the obvious thing to reach for, and it is the wrong tool at this point in the
 * pipeline. The output goes into an SMS that may be the only information a guardian
 * gets, and it is composed in the seconds after a danger codeword — when the phone may
 * be in a pocket, the network may be a single bar, and an on-device model would take
 * seconds and ~1 GB of RAM it does not have. A cloud call needs connectivity that
 * fails exactly where she needs this most.
 *
 * It is also a place where a confident paraphrase is dangerous. A model that renders
 * "he grabbed my arm" as "a minor altercation" has edited evidence. This picks real
 * lines and quotes them.
 *
 * [AiTranscriptSummariser] is the seam for a model-written version later; it should
 * enrich this, not replace it, and never on the dispatch path.
 */
object TranscriptSummariser {

    /** What the alert says, and what the session is titled. */
    data class Summary(
        /** A few words, for a session title. */
        val title: String,
        /** One or two sentences a guardian can read at a glance. */
        val detail: String,
        /** The most alarming thing actually said, quoted. Null when nothing stands out. */
        val quote: String?,
    )

    fun summarise(entries: List<DiarizedEntry>): Summary {
        val speech = entries.filter { it.speakerKind != SpeakerKind.SoundEvent }
        val sounds = entries.filter { it.speakerKind == SpeakerKind.SoundEvent }
        val flagged = speech.filter { it.isFlagged }
        val strangers = speech.filter { it.speakerKind == SpeakerKind.Unknown }

        if (speech.isEmpty() && sounds.isEmpty()) {
            return Summary(
                title = "Recording with no speech",
                detail = "Nothing was transcribed during this recording.",
                quote = null,
            )
        }

        // The quote is chosen from what was flagged, preferring a stranger's words:
        // "get in the car" from someone else is the single most useful line a guardian
        // can be handed, and it is the one a summary would most likely smooth away.
        val quote = (flagged.filter { it.speakerKind == SpeakerKind.Unknown } + flagged)
            .firstOrNull()
            ?.text
            ?.trim()
            ?.take(MAX_QUOTE)

        val dangerSounds = sounds.filter { it.isFlagged }.map { it.speakerLabel }.distinct()

        val detail = buildString {
            when {
                strangers.isNotEmpty() && flagged.isNotEmpty() ->
                    append("Another voice is present and the conversation is escalating")
                strangers.isNotEmpty() ->
                    append("Another voice is present")
                flagged.isNotEmpty() ->
                    append("Angel flagged what was being said")
                else ->
                    append("Recording in progress")
            }
            if (dangerSounds.isNotEmpty()) {
                append("; ")
                append(dangerSounds.take(2).joinToString(" and ") { it.lowercase() })
                append(" detected")
            }
            append(".")
        }

        return Summary(title = titleFor(strangers.isNotEmpty(), flagged, dangerSounds), detail = detail, quote = quote)
    }

    /**
     * A short title derived from what happened.
     *
     * Named after the situation rather than the trigger, because "Woken by hey angel"
     * tells someone scrolling a list nothing about which recording they want.
     */
    private fun titleFor(
        hasStranger: Boolean,
        flagged: List<DiarizedEntry>,
        dangerSounds: List<String>,
    ): String = when {
        dangerSounds.isNotEmpty() && hasStranger -> "Raised voices with someone else present"
        dangerSounds.isNotEmpty() -> "${dangerSounds.first()} during a recording"
        hasStranger && flagged.isNotEmpty() -> "A tense exchange with someone else"
        hasStranger -> "A conversation with someone else"
        flagged.isNotEmpty() -> "Something Angel flagged"
        else -> "A quiet recording"
    }

    private const val MAX_QUOTE = 90
}

/**
 * A model-written summary, for when one is available.
 *
 * Kept as an interface with no implementation on purpose. Firebase AI Logic needs a
 * Firebase project, which this app does not require and may never have, and a summariser
 * that silently fails when offline would be worse than the deterministic one. When a
 * model is wired in it should run *after* the alert has gone out and enrich the stored
 * session, never block the dispatch path.
 */
interface AiTranscriptSummariser {
    suspend fun summarise(entries: List<DiarizedEntry>): TranscriptSummariser.Summary?
}
