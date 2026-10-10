package com.example.guardianangel.domain.codeword

import com.example.guardianangel.domain.model.Codeword
import com.example.guardianangel.domain.model.CodewordTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide whether a spoken codeword does anything.
 *
 * Every case here is a way the feature can hurt the user: alerting her circle dozens of
 * times for one word, letting a stranger cancel her alert, or refusing to act when she
 * says her danger word. The gate is pure so all of it is checked without a microphone.
 */
class CodewordGateTest {

    private var clock = 1_000_000L
    private val gate = CodewordGate(now = { clock })

    private val codewords = listOf(
        Codeword("1", CodewordTier.Safe, "sunflower"),
        Codeword("2", CodewordTier.Caution, "umbrella"),
        Codeword("3", CodewordTier.Danger, "lighthouse"),
        Codeword("4", CodewordTier.Emergency, "firefly"),
    )

    private fun say(text: String, isUser: Boolean? = true) =
        gate.evaluate(text, isUser, codewords)

    @Test
    fun `ordinary speech does nothing`() {
        assertEquals(CodewordGate.Decision.None, say("I'm walking home now"))
    }

    @Test
    fun `a codeword inside a sentence is heard`() {
        val decision = say("it's fine, lighthouse, really")
        assertTrue(decision is CodewordGate.Decision.Holding)
    }

    @Test
    fun `a word only partly matching does not fire`() {
        // "safely" contains no whole codeword. Substring matching would fire here.
        assertEquals(CodewordGate.Decision.None, say("I got home safely"))
    }

    @Test
    fun `the same phrase fires once, not on every chunk`() {
        // The matcher runs over a rolling window, so one utterance is seen repeatedly.
        // Without de-duplication this produced an alert per transcript chunk.
        assertTrue(say("lighthouse") is CodewordGate.Decision.Holding)
        clock += 500
        assertEquals(CodewordGate.Decision.None, say("lighthouse"))
        clock += 500
        assertEquals(CodewordGate.Decision.None, say("lighthouse"))
    }

    @Test
    fun `the same phrase is heard again much later`() {
        assertTrue(say("lighthouse") is CodewordGate.Decision.Holding)
        gate.consumePending()
        clock += CodewordGate.REPEAT_WINDOW_MILLIS + 1
        assertTrue("A codeword said a minute later must be heard", say("lighthouse")
            is CodewordGate.Decision.Holding)
    }

    @Test
    fun `the highest tier mentioned wins`() {
        // "I'm fine" plus a danger word is a danger situation, not a safe one.
        val decision = say("sunflower lighthouse")
        assertTrue(decision is CodewordGate.Decision.Holding)
        assertEquals(
            CodewordTier.Danger,
            (decision as CodewordGate.Decision.Holding).pending.tier,
        )
    }

    @Test
    fun `danger is held back so an accident can be taken back`() {
        val decision = say("lighthouse") as CodewordGate.Decision.Holding
        assertEquals(CodewordTier.Danger, decision.pending.tier)

        // Not due yet, so nothing has been dispatched.
        assertNull(gate.dueForDispatch())

        clock += CodewordGate.DANGER_HOLD_MILLIS + 1
        assertEquals(CodewordTier.Danger, gate.dueForDispatch()?.tier)
    }

    @Test
    fun `the safe word cancels a pending alert`() {
        say("lighthouse")
        clock += 2_000

        val decision = say("sunflower")
        assertEquals(CodewordGate.Decision.Cancelled(CodewordTier.Danger), decision)

        // And nothing fires afterwards, which is the point of the hold.
        clock += CodewordGate.DANGER_HOLD_MILLIS + 1
        assertNull("A cancelled alert must not dispatch later", gate.dueForDispatch())
    }

    @Test
    fun `a tap cancels a pending alert too`() {
        say("lighthouse")
        assertEquals(CodewordTier.Danger, gate.cancelPending())
        clock += CodewordGate.DANGER_HOLD_MILLIS + 1
        assertNull(gate.dueForDispatch())
    }

    @Test
    fun `escalating while holding replaces the pending tier`() {
        say("lighthouse")
        val decision = say("firefly") as CodewordGate.Decision.Holding
        assertEquals(CodewordTier.Emergency, decision.pending.tier)
    }

    @Test
    fun `de-escalating while holding does not defuse it`() {
        say("firefly")
        say("umbrella")
        // Saying a lesser word must not downgrade an emergency already in flight.
        assertEquals(CodewordTier.Emergency, gate.currentlyPending?.tier)
    }

    @Test
    fun `caution acts immediately because it only records`() {
        assertEquals(
            CodewordGate.Decision.Act(CodewordTier.Caution, "umbrella"),
            say("umbrella"),
        )
    }

    @Test
    fun `a different speaker cannot trigger anything`() {
        val danger = say("lighthouse", isUser = false)
        assertEquals(
            CodewordGate.Decision.Withheld(
                CodewordTier.Danger,
                "lighthouse",
                CodewordGate.WithheldReason.DifferentSpeaker,
            ),
            danger,
        )
        assertNull(gate.currentlyPending)
    }

    @Test
    fun `a different speaker cannot cancel her alert`() {
        say("lighthouse")
        // Someone holding her phone must not be able to call it off.
        val decision = say("sunflower", isUser = false)
        assertTrue(decision is CodewordGate.Decision.Withheld)
        assertEquals(
            "The alert must still be pending",
            CodewordTier.Danger,
            gate.currentlyPending?.tier,
        )
    }

    @Test
    fun `an unverifiable voice can still start recording`() {
        // Nobody enrolled a voiceprint, so nothing can be verified. Caution only gathers
        // evidence, and refusing it would make codewords useless for that user.
        assertEquals(
            CodewordGate.Decision.Act(CodewordTier.Caution, "umbrella"),
            say("umbrella", isUser = null),
        )
    }

    @Test
    fun `an unverifiable voice cannot alert anyone`() {
        val decision = say("lighthouse", isUser = null)
        assertEquals(
            CodewordGate.Decision.Withheld(
                CodewordTier.Danger,
                "lighthouse",
                CodewordGate.WithheldReason.UnverifiedSpeaker,
            ),
            decision,
        )
    }

    @Test
    fun `withholding is reported once, not on every chunk`() {
        assertTrue(say("lighthouse", isUser = false) is CodewordGate.Decision.Withheld)
        clock += 500
        assertEquals(CodewordGate.Decision.None, say("lighthouse", isUser = false))
    }

    @Test
    fun `a blank phrase never matches`() {
        val withBlank = listOf(Codeword("1", CodewordTier.Danger, "   "))
        assertEquals(CodewordGate.Decision.None, gate.evaluate("anything", true, withBlank))
    }

    @Test
    fun `reset forgets pending and history`() {
        say("lighthouse")
        gate.reset()
        assertNull(gate.currentlyPending)
        // And the repeat window is cleared, so the next session hears the word again.
        assertTrue(say("lighthouse") is CodewordGate.Decision.Holding)
    }

    // --- Imperfect transcripts -------------------------------------------------------
    //
    // The text being matched is a machine transcript of someone speaking, possibly
    // frightened and outdoors. Requiring an exact string means the codeword works in a
    // quiet room and fails in the situation it exists for.

    @Test
    fun `a one-word phrase split in two is still heard`() {
        // Far and away the most common transcription of "lighthouse".
        assertTrue(say("it's the light house on the corner") is CodewordGate.Decision.Holding)
    }

    @Test
    fun `a small misspelling is forgiven`() {
        assertTrue(say("lighthous") is CodewordGate.Decision.Holding)
    }

    @Test
    fun `a dropped letter is forgiven`() {
        assertTrue(say("lighthouse".replace("t", "")) is CodewordGate.Decision.Holding)
    }

    @Test
    fun `an unrelated word of similar length does not fire`() {
        // "warehouse" is four edits from "lighthouse" — well outside tolerance.
        assertEquals(CodewordGate.Decision.None, say("we walked past the warehouse"))
    }

    @Test
    fun `ordinary speech does not trip the fuzzy matcher`() {
        // The risk of fuzzy matching is firing on conversation. These are the kinds of
        // sentence the transcript is full of.
        listOf(
            "I'm nearly home now",
            "the weather is lovely tonight",
            "can you hear me okay",
            "sorry I missed your call earlier",
            "there's a light on upstairs",
            "my house is just around the corner",
        ).forEach { sentence ->
            assertEquals(
                "\"$sentence\" should not fire a codeword",
                CodewordGate.Decision.None,
                gate.evaluate(sentence, true, codewords),
            )
        }
    }

    @Test
    fun `short phrases get no fuzzy tolerance`() {
        // At four characters almost any word is one edit from any other, so a short
        // codeword is matched exactly or not at all.
        val shortWord = listOf(Codeword("1", CodewordTier.Danger, "fig"))
        assertEquals(CodewordGate.Decision.None, gate.evaluate("fog", true, shortWord))
        assertTrue(gate.evaluate("fig", true, shortWord) is CodewordGate.Decision.Holding)
    }

    @Test
    fun `a two-word phrase survives a wrong word ending`() {
        val two = listOf(Codeword("1", CodewordTier.Danger, "yellow submarine"))
        assertTrue(
            gate.evaluate("yellow submarines", true, two) is CodewordGate.Decision.Holding
        )
    }

    @Test
    fun `matching is case insensitive`() {
        assertTrue(say("LIGHTHOUSE") is CodewordGate.Decision.Holding)
    }
}
