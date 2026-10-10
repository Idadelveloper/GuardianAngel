package com.example.guardianangel.agent

import com.example.guardianangel.domain.alert.GuardianNotifier
import com.example.guardianangel.domain.alert.NotifyOutcome
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.ContactPresence
import com.example.guardianangel.domain.model.EmergencyContact
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who gets woken, and how often.
 *
 * These decisions have a human cost on both sides. Alerting five people because a
 * recording started teaches them to ignore the next one; suppressing a genuine
 * escalation because something similar fired earlier is the feature failing silently.
 */
class GuardianNotificationAgentTest {

    private class RecordingNotifier(
        var outcome: (List<EmergencyContact>) -> NotifyOutcome = { guardians ->
            NotifyOutcome(reached = guardians.map { it.name }, failed = emptyList())
        },
    ) : GuardianNotifier {
        val sends = mutableListOf<Pair<List<String>, String>>()
        override val canSendSilently = true
        override suspend fun notify(guardians: List<EmergencyContact>, message: String):
            NotifyOutcome {
            sends += guardians.map { it.name } to message
            return outcome(guardians)
        }
    }

    private fun guardian(name: String, priority: Int) = EmergencyContact(
        id = name,
        name = name,
        relationship = "Friend",
        phoneNumber = "+1555010$priority",
        priority = priority,
        presence = ContactPresence.Unknown,
    )

    private val circle = listOf(
        guardian("Amara", 1),
        guardian("David", 2),
        guardian("Sarah", 3),
    )

    private suspend fun GuardianNotificationAgent.send(tier: CodewordTier) = dispatch(
        tier = tier,
        userName = "Ida",
        guardians = circle,
        entries = emptyList(),
        latitude = 37.0,
        longitude = -122.0,
        placeLabel = "Shattuck Ave",
    )

    @Test
    fun `caution reaches only the first guardian`() = runBlocking {
        val notifier = RecordingNotifier()
        GuardianNotificationAgent(notifier).send(CodewordTier.Caution)

        // Waking the whole circle because a recording started would train them to
        // ignore the one that matters.
        assertEquals(listOf("Amara"), notifier.sends.single().first)
    }

    @Test
    fun `danger reaches everyone, in priority order`() = runBlocking {
        val notifier = RecordingNotifier()
        GuardianNotificationAgent(notifier).send(CodewordTier.Danger)
        assertEquals(listOf("Amara", "David", "Sarah"), notifier.sends.single().first)
    }

    @Test
    fun `the same tier does not send twice in one session`() = runBlocking {
        val notifier = RecordingNotifier()
        val agent = GuardianNotificationAgent(notifier)
        agent.send(CodewordTier.Danger)
        val second = agent.send(CodewordTier.Danger)

        // A heuristic escalation and a spoken codeword arriving together must not
        // produce two texts about one incident.
        assertTrue(second.suppressed)
        assertEquals(1, notifier.sends.size)
    }

    @Test
    fun `escalating always gets through`() = runBlocking {
        val notifier = RecordingNotifier()
        val agent = GuardianNotificationAgent(notifier)
        agent.send(CodewordTier.Caution)
        val escalated = agent.send(CodewordTier.Emergency)

        assertFalse(escalated.suppressed)
        assertEquals(2, notifier.sends.size)
    }

    @Test
    fun `de-escalating does not send a weaker alert`() = runBlocking {
        val notifier = RecordingNotifier()
        val agent = GuardianNotificationAgent(notifier)
        agent.send(CodewordTier.Danger)
        assertTrue(agent.send(CodewordTier.Caution).suppressed)
    }

    @Test
    fun `the all-clear is never suppressed`() = runBlocking {
        val notifier = RecordingNotifier()
        val agent = GuardianNotificationAgent(notifier)
        agent.send(CodewordTier.Danger)
        agent.send(CodewordTier.Safe)
        agent.send(CodewordTier.Safe)

        // People who were woken are owed the follow-up more than they were owed the
        // original, and twice is better than not at all.
        assertEquals(3, notifier.sends.size)
    }

    @Test
    fun `a new session can alert at a tier the last one used`() = runBlocking {
        val notifier = RecordingNotifier()
        val agent = GuardianNotificationAgent(notifier)
        agent.send(CodewordTier.Danger)
        agent.reset()
        assertFalse(agent.send(CodewordTier.Danger).suppressed)
    }

    @Test
    fun `a failed send is not remembered as sent`() = runBlocking {
        val notifier = RecordingNotifier(
            outcome = { NotifyOutcome(emptyList(), it.map { g -> g.name }) }
        )
        val agent = GuardianNotificationAgent(notifier)
        agent.send(CodewordTier.Danger)

        // Otherwise one failure would silence every later attempt in that session.
        assertFalse(agent.send(CodewordTier.Danger).suppressed)
    }

    @Test
    fun `the outcome names who was and was not reached`() = runBlocking {
        val notifier = RecordingNotifier(
            outcome = { NotifyOutcome(reached = listOf("Amara"), failed = listOf("David")) }
        )
        val line = GuardianNotificationAgent(notifier).send(CodewordTier.Danger).summaryLine

        assertTrue(line, line.contains("Amara"))
        assertTrue(line, line.contains("could not reach David"))
    }

    @Test
    fun `nobody in the circle is reported, not silently ignored`() = runBlocking {
        val notifier = RecordingNotifier(
            outcome = { NotifyOutcome.blocked("There is nobody in your circle to alert yet.") }
        )
        val line = GuardianNotificationAgent(notifier).dispatch(
            tier = CodewordTier.Danger,
            userName = "Ida",
            guardians = emptyList(),
            entries = emptyList(),
            latitude = null,
            longitude = null,
            placeLabel = null,
        ).summaryLine

        assertTrue(line, line.contains("nobody in your circle"))
    }

    @Test
    fun `the message carries the location link`() = runBlocking {
        val notifier = RecordingNotifier()
        GuardianNotificationAgent(notifier).send(CodewordTier.Danger)
        assertTrue(notifier.sends.single().second.contains("google.com/maps"))
    }
}
