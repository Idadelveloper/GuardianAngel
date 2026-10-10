package com.example.guardianangel.audio

import kotlinx.coroutines.flow.Flow

/**
 * The live analysis stack that runs **while Angel is recording**.
 *
 * ## Why this is a cascade rather than one model
 *
 * The temptation is to point one large multimodal model at the microphone and ask "is
 * she in danger". Measured numbers say otherwise:
 *
 *  - An MFCC + SVM distress classifier reaches ~95% detection at ~1% false alarm for
 *    3–5% battery over ten hours.
 *  - An on-device LLM costs 2–5 seconds to first token and ~1.2 GB of RAM at int4.
 *
 * Running the expensive thing continuously would flatten the battery during exactly the
 * walk home it exists to protect, and would answer slower than the cheap thing. So each
 * stage only runs when the stage below it says something is worth looking at:
 *
 * ```
 *  Tier 0  VAD                     always      ~free     is anyone speaking?
 *  Tier 1  wake word               armed       ~1 MB     should I start recording?
 *  ────────────────────────────── recording starts ───────────────────────────────
 *  Tier 2a streaming ASR           continuous  ~40 MB    what is being said
 *  Tier 2b audio tagging           continuous  ~4 MB     scream, glass, raised voices
 *  Tier 2c speaker diarization     continuous  ~8 MB     how many voices, whose
 *  Tier 3  reasoning               on suspicion          is this actually escalating?
 * ```
 *
 * Tier 3 is the only stage that needs to *understand* rather than *detect*, and it is
 * the only one that justifies a language model — see [ThreatAssessor].
 */

/** One incremental result from streaming recognition. */
data class TranscriptChunk(
    val text: String,
    /** False while the decoder may still revise this text. */
    val isFinal: Boolean,
    val startMillis: Long,
    val endMillis: Long,
    /** Diarization label when available, e.g. "spk0". Null when not diarizing. */
    val speakerTag: String? = null,
    /**
     * Whether *this* segment was the enrolled user.
     *
     * Per chunk, not per session. The session-level question — "has any stranger spoken
     * at all" — was being used to decide whether the user said a codeword, so one
     * stranger speaking once made every later line read as not-her, and before that a
     * stranger's words counted as hers. Codeword actions hang off this, so it has to be
     * about the words that were actually said.
     *
     * Null when it could not be judged: no voiceprint enrolled, no speaker model, or too
     * little speech in the segment to embed. Null means *unknown*, never *not her* — the
     * difference decides whether a codeword is acted on or quietly dropped.
     */
    val isEnrolledUser: Boolean? = null,
)

/** Who the speaker-identification tier thinks said a segment. */
data class SpeakerAttribution(
    /** Stable cluster label within the session, e.g. "spk0". */
    val tag: String?,
    /** True when it matched the enrolled voiceprint; null when undecidable. */
    val isEnrolledUser: Boolean?,
)

/**
 * Real-time speech-to-text.
 *
 * Streaming rather than batch: Guardian Angel needs partial text within a second so the
 * reasoning tier can react while something is still happening, not a perfect transcript
 * thirty seconds later.
 */
interface SpeechTranscriber {
    val isReady: Boolean

    suspend fun load(): Boolean

    /** Feeds mono PCM in -1f..1f. Results arrive on [transcript]. */
    fun accept(samples: FloatArray)

    val transcript: Flow<TranscriptChunk>

    /** Flushes any buffered audio and finalises the last chunk. */
    suspend fun finish()

    fun close()
}

/** An acoustic event the tagger recognised, e.g. a scream or breaking glass. */
data class AudioEvent(
    val label: String,
    /** 0f..1f. */
    val confidence: Float,
    val atMillis: Long,
    /** True for labels on the danger list rather than ambient ones. */
    val isDangerSignal: Boolean,
)

/**
 * Non-speech audio understanding.
 *
 * Carries signal that words never will: a scream, glass breaking, a car door, a sudden
 * jump in level. The brief calls these out explicitly, and they often precede or replace
 * anything intelligible being said.
 */
interface AudioTagger {
    val isReady: Boolean
    suspend fun load(): Boolean

    /** Classifies one window. Returns events above the tagger's own threshold. */
    fun tag(window: FloatArray, atMillis: Long): List<AudioEvent>

    fun close()
}

/** Everything the reasoning tier gets to look at. */
data class SituationSnapshot(
    /** Recent transcript, newest last. */
    val transcript: List<TranscriptChunk>,
    val events: List<AudioEvent>,
    /** Distinct voices heard in the window. */
    val speakerCount: Int,
    /** True when a voice other than the enrolled user is present. */
    val unknownVoicePresent: Boolean,
    /** Peak level in the window, dB SPL estimate. */
    val peakDecibels: Int?,
    /** Current location safety score, 0–100. */
    val locationScore: Int,
    /** Local hour, 0–23 — late at night changes how the same words should read. */
    val hourOfDay: Int,
)

/** What the reasoning tier concluded. */
data class ThreatAssessment(
    /** 0f (fine) .. 1f (acute). */
    val severity: Float,
    /** Plain sentence the UI and the transcript log can both show. */
    val rationale: String,
    /** True when the assessor believes contacts should be alerted now. */
    val recommendEscalation: Boolean,
    /** Which signals drove it, for the user to audit after the fact. */
    val contributingSignals: List<String> = emptyList(),
)

/**
 * Decides whether a situation is escalating.
 *
 * Deliberately takes a [SituationSnapshot] rather than audio: by this point the cheap
 * tiers have already turned sound into text, events and counts, and reasoning over that
 * summary is both far cheaper and far easier to explain to the user afterwards than
 * reasoning over a waveform.
 *
 * Two implementations are expected, and the app should be able to run with either:
 *
 *  - [HeuristicThreatAssessor] — transparent, instant, no model. Good enough for the
 *    obvious cases and the only thing that can run on every chunk.
 *  - An LLM-backed assessor for the genuinely ambiguous ones, invoked sparingly.
 */
interface ThreatAssessor {
    val isReady: Boolean
    suspend fun load(): Boolean
    suspend fun assess(snapshot: SituationSnapshot): ThreatAssessment
    fun close()
}
