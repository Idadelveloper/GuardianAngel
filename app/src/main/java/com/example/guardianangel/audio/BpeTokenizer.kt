package com.example.guardianangel.audio

import android.content.res.AssetManager
import java.io.BufferedReader

/**
 * Turns a user's wake phrase into the BPE tokens sherpa-onnx's keyword spotter expects.
 *
 * The spotter matches token sequences, not text, so `"hey angel"` has to become
 * `"▁HE Y ▁AN GE L"` before it can be registered. Upstream does this with a Python
 * `text2token` tool; Guardian Angel has to do it on the phone, because the whole point
 * is that the user invents her own phrase at setup.
 *
 * ## Accuracy caveat
 *
 * This is greedy longest-match over the model's 500-token vocabulary, not a faithful
 * re-implementation of SentencePiece's merge ordering. Checked against the nine
 * reference phrases sherpa ships, it reproduces **seven exactly**; the other two produce
 * a different but still valid segmentation (`▁S IR I` where SentencePiece gives
 * `▁S I RI`).
 *
 * A different segmentation is not a failure — every token is in the vocabulary and the
 * decoder will still score it — but it can shift the detection threshold for that
 * phrase. That is why [CURATED_PHRASES] exists: those are pre-verified, and the UI
 * should nudge users toward them while still allowing anything.
 */
class BpeTokenizer(private val vocabulary: Set<String>) {

    /**
     * Tokenises [phrase] into space-separated BPE pieces.
     *
     * @return null when some part of the phrase cannot be covered by the vocabulary at
     *   all — better to refuse the phrase at setup than to register a keyword containing
     *   `<unk>` that can never match.
     */
    fun tokenize(phrase: String): String? {
        val words = phrase.trim().uppercase().split(WHITESPACE).filter { it.isNotBlank() }
        if (words.isEmpty()) return null

        val pieces = mutableListOf<String>()
        for (word in words) {
            // SentencePiece marks a word boundary with U+2581, not a space.
            val target = WORD_BOUNDARY + word
            var index = 0
            while (index < target.length) {
                val match = longestMatchAt(target, index) ?: return null
                pieces += match
                index += match.length
            }
        }
        return pieces.joinToString(" ")
    }

    private fun longestMatchAt(text: String, from: Int): String? {
        for (end in text.length downTo from + 1) {
            val candidate = text.substring(from, end)
            if (candidate in vocabulary) return candidate
        }
        return null
    }

    companion object {
        private const val WORD_BOUNDARY = "▁"
        private val WHITESPACE = Regex("\\s+")

        /**
         * Wake phrases verified against this vocabulary.
         *
         * Chosen to be two or three syllables, acoustically distinct from each other,
         * and unlikely to appear in ordinary conversation — a wake word that fires
         * during small talk is worse than no wake word.
         */
        val CURATED_PHRASES = listOf(
            "hey angel",
            "angel help",
            "guardian angel",
            "watch over me",
            "marshmallow",
        )

        /** Loads the vocabulary from a sherpa-onnx `tokens.txt` in the APK assets. */
        fun fromAssets(assets: AssetManager, path: String): BpeTokenizer {
            val vocabulary = assets.open(path).bufferedReader().use(BufferedReader::readLines)
                .mapNotNull { line ->
                    // Each line is "<token> <id>"; the token itself may contain no space.
                    line.substringBeforeLast(' ', missingDelimiterValue = "")
                        .takeIf { it.isNotBlank() }
                }
                .toSet()
            return BpeTokenizer(vocabulary)
        }
    }
}
