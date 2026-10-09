package com.example.guardianangel.audio

/**
 * The seam between the listening service and whatever model does keyword spotting.
 *
 * ## Why this shape
 *
 * Guardian Angel lets the user choose her **own** wake phrase, which rules out the usual
 * approach of training one fixed-vocabulary classifier and shipping it. The workable
 * design for user-defined keywords is few-shot enrolment:
 *
 * 1. A frozen embedding model turns a ~1.5 s audio window into a small vector.
 * 2. At setup the user says her phrase a few times; the vectors are averaged into a
 *    [Voiceprint]-style template stored on device.
 * 3. At runtime every window is embedded and compared to the template by cosine
 *    similarity; a match above threshold fires.
 *
 * Nothing about that is model-specific, so the interface takes audio in and gives scores
 * out, and the model choice stays an implementation detail.
 *
 * ## Why the interface exists rather than calling LiteRT directly
 *
 * The service, the UI state machine, the enrolment flow and the thresholds are all
 * testable without a 1 MB binary blob or a device microphone. [StubKeywordSpotter] makes
 * the whole hands-free path exercisable on an emulator with no model installed.
 */
interface KeywordSpotter {

    /** True once a usable model is loaded. False means hands-free activation cannot fire. */
    val isReady: Boolean

    /** Audio window length the model expects, in samples. */
    val windowSamples: Int

    /** How far the window advances between evaluations, in samples. */
    val hopSamples: Int

    /**
     * Loads the model. Safe to call repeatedly.
     *
     * @return false when no model is installed in this build — callers should surface
     *   that rather than silently never triggering.
     */
    suspend fun load(): Boolean

    /**
     * Embeds one window of mono PCM in -1f..1f.
     *
     * @return the embedding, or null when the model is unavailable.
     */
    fun embed(window: FloatArray): FloatArray?

    /** Releases native resources. */
    fun close()
}

/**
 * An enrolled phrase: the averaged embedding of several takes, plus how tightly the
 * takes agreed.
 *
 * [selfSimilarity] is the mean pairwise similarity of the enrolment takes. It is the
 * honest measure of enrolment quality — takes that disagree with each other produce a
 * template that will either miss constantly or fire on everything — and it is what the
 * onboarding screen reports back to the user instead of a fabricated accuracy figure.
 */
data class KeywordTemplate(
    val phrase: String,
    val embedding: FloatArray,
    val takes: Int,
    val selfSimilarity: Float,
) {
    /** Data classes compare arrays by reference, which would be wrong here. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeywordTemplate) return false
        return phrase == other.phrase &&
            takes == other.takes &&
            selfSimilarity == other.selfSimilarity &&
            embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = phrase.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + takes
        result = 31 * result + selfSimilarity.hashCode()
        return result
    }
}

/**
 * Builds a template from enrolment takes.
 *
 * Averaging in embedding space is the standard few-shot approach: it is what GE2E-style
 * keyword spotting and speaker-verification enrolment both do, and it keeps enrolment
 * cheap enough to run on the phone during setup.
 */
fun buildTemplate(
    phrase: String,
    spotter: KeywordSpotter,
    takes: List<FloatArray>,
): KeywordTemplate? {
    val embeddings = takes.mapNotNull(spotter::embed)
    if (embeddings.isEmpty()) return null

    val dimensions = embeddings.first().size
    val mean = FloatArray(dimensions)
    embeddings.forEach { embedding ->
        for (i in 0 until dimensions) mean[i] += embedding[i]
    }
    for (i in 0 until dimensions) mean[i] /= embeddings.size

    // Mean pairwise agreement between takes.
    var total = 0f
    var pairs = 0
    for (i in embeddings.indices) {
        for (j in i + 1 until embeddings.size) {
            total += cosineSimilarity(embeddings[i], embeddings[j])
            pairs++
        }
    }
    val selfSimilarity = if (pairs == 0) 1f else total / pairs

    return KeywordTemplate(
        phrase = phrase,
        embedding = mean,
        takes = embeddings.size,
        selfSimilarity = selfSimilarity,
    )
}
