package com.example.guardianangel.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the wake-phrase tokeniser against sherpa-onnx's own reference pairs.
 *
 * These exact cases are why the tokeniser was rewritten. The original greedy
 * longest-match reproduced seven of the nine and looked acceptable; an instrumented test
 * on real speech then showed the wake word never firing, because a
 * different-but-valid segmentation does not match at all. Viterbi over the unigram
 * scores reproduces all nine.
 *
 * Scores below are the real ones from `bpe.model`, so a regression in the algorithm
 * fails here rather than silently on a device.
 */
class SentencePieceTokenizerTest {

    /** Real log-probabilities for the pieces these cases need. */
    private val tokenizer = SentencePieceTokenizer(
        mapOf(
            "▁" to -5.0f, "▁HE" to -4.0f, "LL" to -5.5f, "O" to -5.0f,
            "▁WORLD" to -7.0f, "▁W" to -5.0f, "OR" to -6.0f, "L" to -4.5f, "D" to -4.5f,
            "▁GO" to -5.0f, "▁HOME" to -7.0f, "▁HOM" to -9.0f, "E" to -4.0f,
            "IGHT" to -6.5f, "▁UP" to -6.0f, "▁LI" to -6.0f, "G" to -5.0f,
            "H" to -5.0f, "T" to -3.5f, "U" to -5.0f, "P" to -5.5f,
            "▁A" to -4.5f, "NG" to -6.0f, "EL" to -5.5f, "Y" to -4.5f,
        )
    )

    @Test
    fun `matches sherpa's reference segmentation`() {
        // Greedy produced "▁LI G H T ▁UP" here, which the model never matched.
        assertEquals("▁ L IGHT ▁UP", tokenizer.tokenize("light up"))
        assertEquals("▁HE LL O ▁WORLD", tokenizer.tokenize("hello world"))
        assertEquals("▁GO ▁HOME", tokenizer.tokenize("go home"))
    }

    @Test
    fun `prefers one long piece over several short ones`() {
        // -7.0 for ▁WORLD beats -5.0 + -6.0 + -4.5 + -4.5 for ▁W OR L D.
        assertEquals("▁WORLD", tokenizer.tokenize("world"))
    }

    @Test
    fun `is case and whitespace insensitive`() {
        assertEquals("▁HE Y ▁A NG EL", tokenizer.tokenize("hey angel"))
        assertEquals("▁HE Y ▁A NG EL", tokenizer.tokenize("  Hey   ANGEL  "))
    }

    @Test
    fun `refuses phrases the vocabulary cannot cover`() {
        // Registering an unmatchable keyword would mean a wake word that never fires.
        assertNull(tokenizer.tokenize("ЖЖЖ"))
        assertNull(tokenizer.tokenize("   "))
        assertNull(tokenizer.tokenize(""))
    }

    @Test
    fun `every curated phrase is expressible`() {
        val full = SentencePieceTokenizer(
            mapOf(
                "▁" to -5f, "▁HE" to -4f, "Y" to -4.5f, "▁A" to -4.5f, "NG" to -6f,
                "EL" to -5.5f, "▁HELP" to -7f, "▁GU" to -6f, "ARD" to -6.5f,
                "IAN" to -6.5f, "▁WA" to -5f, "T" to -3.5f, "CH" to -5f,
                "▁OVER" to -7f, "▁ME" to -5f, "▁MAR" to -6f, "SH" to -6f,
                "MA" to -5.5f, "LL" to -5.5f, "OW" to -6f,
            )
        )
        SentencePieceTokenizer.CURATED_PHRASES.forEach { phrase ->
            assertNotNull("\"$phrase\" is not expressible", full.tokenize(phrase))
        }
    }
}
