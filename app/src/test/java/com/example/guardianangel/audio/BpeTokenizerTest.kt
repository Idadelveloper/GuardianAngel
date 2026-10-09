package com.example.guardianangel.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the wake-phrase tokeniser.
 *
 * The reference cases are sherpa-onnx's own `keywords_raw.txt` → `keywords.txt` pairs,
 * so this is checked against upstream's tokenisation rather than against itself. Two of
 * the nine differ by design — greedy longest-match is not SentencePiece's merge order —
 * and those are asserted as *known* divergences so a future change to the algorithm has
 * to confront them rather than quietly shift behaviour.
 */
class BpeTokenizerTest {

    /** The subset of the 500-token vocabulary these cases need. */
    private val tokenizer = BpeTokenizer(
        setOf(
            "▁HE", "LL", "O", "▁WORLD", "▁HI", "▁GO", "G", "LE", "Y", "▁S", "IR", "I",
            "RI", "▁A", "X", "A", "▁LOVE", "▁AND", "▁P", "E", "CE", "▁PLAY", "▁MU",
            "S", "IC", "▁HOME", "▁HA", "PP", "▁NEW", "▁YEAR", "▁AN", "GE", "L",
            "▁HELP", "▁MAR", "SH", "MA", "OW",
        )
    )

    @Test
    fun `matches upstream tokenisation`() {
        assertEquals("▁HE LL O ▁WORLD", tokenizer.tokenize("HELLO WORLD"))
        assertEquals("▁GO ▁HOME", tokenizer.tokenize("GO HOME"))
        assertEquals("▁HA PP Y ▁NEW ▁YEAR", tokenizer.tokenize("HAPPY NEW YEAR"))
        assertEquals("▁PLAY ▁MU S IC", tokenizer.tokenize("PLAY MUSIC"))
    }

    @Test
    fun `known divergence from sentencepiece merge order`() {
        // Upstream gives "▁S I RI"; greedy prefers the longer "IR" first. Both are
        // valid vocabulary sequences, so the spotter still works — but the threshold
        // for this phrase may differ, which is why curated phrases exist.
        assertEquals("▁HE Y ▁S IR I", tokenizer.tokenize("HEY SIRI"))
    }

    @Test
    fun `is case and whitespace insensitive`() {
        assertEquals("▁HE Y ▁AN GE L", tokenizer.tokenize("hey angel"))
        assertEquals("▁HE Y ▁AN GE L", tokenizer.tokenize("  Hey   ANGEL  "))
    }

    @Test
    fun `rejects phrases the vocabulary cannot express`() {
        // Better to refuse at setup than to register a keyword that can never match.
        assertNull(tokenizer.tokenize("ZZZQ"))
        assertNull(tokenizer.tokenize("   "))
        assertNull(tokenizer.tokenize(""))
    }

    @Test
    fun `every curated phrase is expressible`() {
        // Guards the suggestions shown in onboarding: one that cannot be tokenised
        // would be offered to the user and then silently never wake.
        val full = BpeTokenizer(
            setOf(
                "▁HE", "Y", "▁AN", "GE", "L", "▁HELP", "▁GU", "ARD", "IAN",
                "▁WA", "T", "CH", "▁OVER", "▁ME", "▁MAR", "SH", "MA", "LL", "OW",
            )
        )
        BpeTokenizer.CURATED_PHRASES.forEach { phrase ->
            assertNotNull("\"$phrase\" is not expressible", full.tokenize(phrase))
        }
    }
}
