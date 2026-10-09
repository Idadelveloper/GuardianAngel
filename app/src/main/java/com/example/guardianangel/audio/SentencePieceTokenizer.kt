package com.example.guardianangel.audio

import android.content.res.AssetManager
import android.util.Log
import java.io.InputStream

private const val TAG = "SentencePiece"

/**
 * Turns a wake phrase into the subword tokens sherpa-onnx's keyword spotter expects.
 *
 * ## Why this is Viterbi and not greedy matching
 *
 * The first version of this did greedy longest-match over the vocabulary. It reproduced
 * seven of sherpa's nine reference tokenisations, which looked close enough — and it was
 * not. An instrumented test feeding real recorded speech showed the wake word **never
 * firing**: greedy turned "light up" into `▁LI G H T ▁UP` where the model expects
 * `▁ L IGHT ▁UP`, and a different-but-valid segmentation does not match at all. The
 * failure mode was total, not marginal.
 *
 * `bpe.model` is, despite the name, a SentencePiece **unigram** model: each piece
 * carries a log-probability, and the correct segmentation is the one maximising the sum
 * of those scores over the whole word. That is a shortest-path problem, solved here with
 * Viterbi over character positions. It reproduces all nine references exactly.
 *
 * Pairwise BPE merging was tried too and scored worse (four of nine) — the 500-piece
 * vocabulary is pruned, so the intermediate pieces a merge chain would need are missing.
 */
class SentencePieceTokenizer(private val scores: Map<String, Float>) {

    /**
     * Segments [phrase] into space-separated pieces.
     *
     * @return null when some word cannot be covered by the vocabulary at all — better to
     *   refuse the phrase at setup than register a keyword that can never match.
     */
    fun tokenize(phrase: String): String? {
        val words = phrase.trim().uppercase().split(WHITESPACE).filter { it.isNotBlank() }
        if (words.isEmpty()) return null

        val pieces = mutableListOf<String>()
        for (word in words) {
            val segmented = segment(WORD_BOUNDARY + word) ?: return null
            pieces += segmented
        }
        return pieces.joinToString(" ")
    }

    /** True when [phrase] can be represented, without registering it. */
    fun canRepresent(phrase: String): Boolean = tokenize(phrase) != null

    /**
     * Highest-scoring segmentation of [text], by Viterbi.
     *
     * `best[i]` is the best total score for the first `i` characters; `back[i]` is where
     * the piece ending at `i` started. Characters, not bytes: the word-boundary marker is
     * multi-byte in UTF-8 but one character here.
     */
    private fun segment(text: String): List<String>? {
        val n = text.length
        val best = FloatArray(n + 1) { NEGATIVE_INFINITY }
        val back = IntArray(n + 1) { -1 }
        best[0] = 0f

        for (end in 1..n) {
            for (start in 0 until end) {
                if (best[start] == NEGATIVE_INFINITY) continue
                val score = scores[text.substring(start, end)] ?: continue
                val candidate = best[start] + score
                if (candidate > best[end]) {
                    best[end] = candidate
                    back[end] = start
                }
            }
        }
        if (best[n] == NEGATIVE_INFINITY) return null

        val pieces = ArrayDeque<String>()
        var index = n
        while (index > 0) {
            val start = back[index]
            pieces.addFirst(text.substring(start, index))
            index = start
        }
        return pieces.toList()
    }

    companion object {
        private const val WORD_BOUNDARY = "▁"
        private val WHITESPACE = Regex("\\s+")
        private const val NEGATIVE_INFINITY = -1e30f

        /**
         * Wake phrases verified against this vocabulary.
         *
         * Two or three syllables, acoustically distinct from each other, and unlikely in
         * ordinary conversation — a wake word that fires during small talk is worse than
         * no wake word.
         */
        val CURATED_PHRASES = listOf(
            "hey angel",
            "angel help",
            "guardian angel",
            "watch over me",
            "marshmallow",
        )

        /** Bumped whenever segmentation changes, so stored tokens can be regenerated. */
        const val VERSION = 2

        /** Loads the vocabulary and its scores from a SentencePiece model in assets. */
        fun fromAssets(assets: AssetManager, path: String): SentencePieceTokenizer =
            assets.open(path).use { SentencePieceTokenizer(parseModel(it)) }

        /**
         * Minimal reader for SentencePiece's `ModelProto`.
         *
         * Only two fields matter: each `pieces` entry's `piece` (field 1, string) and
         * `score` (field 2, float). Everything else is skipped by wire type. Hand-rolled
         * rather than pulling in protobuf — this is forty lines against a stable,
         * published schema, and the alternative is a dependency for one message.
         */
        private fun parseModel(input: InputStream): Map<String, Float> {
            val bytes = input.readBytes()
            val scores = HashMap<String, Float>(1024)
            val reader = ProtoReader(bytes)

            while (!reader.exhausted) {
                val (field, wireType) = reader.readTag() ?: break
                if (field == FIELD_PIECES && wireType == WIRE_LENGTH_DELIMITED) {
                    val piece = ProtoReader(reader.readLengthDelimited())
                    var name: String? = null
                    var score = 0f
                    while (!piece.exhausted) {
                        val (f, w) = piece.readTag() ?: break
                        when {
                            f == FIELD_PIECE_NAME && w == WIRE_LENGTH_DELIMITED ->
                                name = String(piece.readLengthDelimited(), Charsets.UTF_8)
                            f == FIELD_PIECE_SCORE && w == WIRE_FIXED32 ->
                                score = Float.fromBits(piece.readFixed32())
                            else -> piece.skip(w)
                        }
                    }
                    name?.let { scores[it] = score }
                } else {
                    reader.skip(wireType)
                }
            }
            Log.i(TAG, "Loaded ${scores.size} vocabulary pieces")
            return scores
        }

        private const val FIELD_PIECES = 1
        private const val FIELD_PIECE_NAME = 1
        private const val FIELD_PIECE_SCORE = 2
        private const val WIRE_VARINT = 0
        private const val WIRE_FIXED64 = 1
        private const val WIRE_LENGTH_DELIMITED = 2
        private const val WIRE_FIXED32 = 5
    }
}

/** Just enough protobuf to walk a `ModelProto`. */
private class ProtoReader(private val bytes: ByteArray) {
    private var index = 0

    val exhausted: Boolean get() = index >= bytes.size

    /** @return field number and wire type, or null at the end. */
    fun readTag(): Pair<Int, Int>? {
        if (exhausted) return null
        val key = readVarint()
        return (key ushr 3).toInt() to (key and 7L).toInt()
    }

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (index < bytes.size) {
            val b = bytes[index++].toInt()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        return result
    }

    fun readLengthDelimited(): ByteArray {
        val length = readVarint().toInt()
        val end = (index + length).coerceAtMost(bytes.size)
        val slice = bytes.copyOfRange(index, end)
        index = end
        return slice
    }

    fun readFixed32(): Int {
        var value = 0
        repeat(4) { i -> value = value or ((bytes[index + i].toInt() and 0xFF) shl (i * 8)) }
        index += 4
        return value
    }

    /** Advances past a field whose contents are not needed. */
    fun skip(wireType: Int) {
        when (wireType) {
            0 -> readVarint()
            1 -> index += 8
            2 -> readLengthDelimited()
            5 -> index += 4
            else -> index = bytes.size
        }
    }
}
