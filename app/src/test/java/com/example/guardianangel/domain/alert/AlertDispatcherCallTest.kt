package com.example.guardianangel.domain.alert

import com.example.guardianangel.agent.GuardianNotificationAgent
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.DiarizedEntry
import com.example.guardianangel.domain.model.EmergencyContact
import com.example.guardianangel.domain.model.GuardianSnapshot
import com.example.guardianangel.domain.model.SessionKind
import com.example.guardianangel.domain.repository.ContactsRepository
import com.example.guardianangel.domain.repository.GuardianRepository
import com.example.guardianangel.domain.repository.SessionRecorder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ringing phone, end to end through the one dispatch path.
 *
 * [EmergencyCallPolicyTest] covers the rule; this covers the wiring, which is where the
 * two failures that matter live: a call placed for a tier that should only text, and a
 * session that records a call as having happened when nothing was ever dialled.
 */
class AlertDispatcherCallTest {

    private fun guardian(name: String, priority: Int) = EmergencyContact(
        id = name,
        name = name,
        relationship = "Friend",
        phoneNumber = "+1555010$priority",
        priority = priority,
    )

    private val circle = listOf(guardian("Amara", 1), guardian("David", 2))

    private class FakeCaller(
        var outcome: (EmergencyContact) -> GuardianCaller.Outcome = {
            GuardianCaller.Outcome.Dialling(it)
        },
    ) : GuardianCaller {
        val dialled = mutableListOf<String>()
        override fun canCall() = true
        override suspend fun call(contact: EmergencyContact): GuardianCaller.Outcome {
            dialled += contact.name
            return outcome(contact)
        }
    }

    private class FakeNotifier : GuardianNotifier {
        override val canSendSilently = true
        override suspend fun notify(guardians: List<EmergencyContact>, message: String) =
            NotifyOutcome(reached = guardians.map { it.name }, failed = emptyList())
    }

    private class FakeRecorder : SessionRecorder {
        val events = mutableListOf<String>()
        override val activeSessionId: String? = "s1"
        override suspend fun begin(trigger: CodewordTier?, triggerLabel: String) = "s1"
        override suspend fun addTranscript(entry: DiarizedEntry) = Unit
        override suspend fun addAudioEvent(
            label: String,
            decibels: Int?,
            confidence: Float,
            atMillis: Long,
            isDanger: Boolean,
        ) {
            events += label
        }
        override suspend fun addLocation(
            latitude: Double,
            longitude: Double,
            atMillis: Long,
            label: String?,
            safetyScore: Int?,
        ) = Unit
        override suspend fun noteSafetyScore(score: Int) = Unit
        override suspend fun noteCodeword(tier: CodewordTier, phrase: String, wasUserVoice: Boolean) = Unit
        override suspend fun noteGuardiansNotified(names: List<String>) = Unit
        override suspend fun finish(summary: String?, kind: SessionKind?) = Unit
    }

    private class FakeContacts(private val contacts: List<EmergencyContact>) : ContactsRepository {
        override fun observeContacts(): Flow<List<EmergencyContact>> = flowOf(contacts)
        override suspend fun addContact(contact: EmergencyContact) = Unit
        override suspend fun updateContact(contact: EmergencyContact) = Unit
        override suspend fun removeContact(contactId: String) = Unit
        override suspend fun sendTestPing(contactId: String) = Unit
    }

    private class FakeGuardian : GuardianRepository {
        override fun observeSnapshot(): Flow<GuardianSnapshot> = flowOf()
        override suspend fun armGuardian() = Unit
        override suspend fun disarmGuardian() = Unit
        override suspend fun startRecording(trigger: CodewordTier?) = Unit
        override suspend fun stopRecording() = Unit
        override suspend fun dispatchAlert(tier: CodewordTier) = Unit
        override suspend fun rescanArea() = Unit
    }

    private fun dispatcher(
        caller: GuardianCaller?,
        recorder: FakeRecorder = FakeRecorder(),
        enabled: Boolean = true,
        guardians: List<EmergencyContact> = circle,
    ) = AlertDispatcher(
        notificationAgent = GuardianNotificationAgent(FakeNotifier()),
        contacts = FakeContacts(guardians),
        guardianRepository = FakeGuardian(),
        recorder = recorder,
        profileName = { "Ida" },
        currentLocation = { null },
        sessionEntries = { emptyList() },
        caller = caller,
        callingEnabled = { enabled },
    ) to recorder

    @Test
    fun `a repeated emergency does not hang up the call it just placed`() = runBlocking {
        val caller = FakeCaller()
        val (dispatcher, _) = dispatcher(caller)

        dispatcher.dispatch(CodewordTier.Emergency)
        dispatcher.dispatch(CodewordTier.Emergency)

        assertEquals(listOf("Amara"), caller.dialled)
    }

    @Test
    fun `a new session may ring again`() = runBlocking {
        val caller = FakeCaller()
        val (dispatcher, _) = dispatcher(caller)

        dispatcher.dispatch(CodewordTier.Emergency)
        dispatcher.resetForNewSession()
        dispatcher.dispatch(CodewordTier.Emergency)

        assertEquals(listOf("Amara", "Amara"), caller.dialled)
    }

    @Test
    fun `an emergency alert rings the first guardian`() = runBlocking {
        val caller = FakeCaller()
        val (dispatcher, recorder) = dispatcher(caller)

        dispatcher.dispatch(CodewordTier.Emergency)

        assertEquals(listOf("Amara"), caller.dialled)
        assertTrue(recorder.events.any { it.contains("Calling Amara") })
    }

    @Test
    fun `a danger alert texts without ringing anyone`() = runBlocking {
        val caller = FakeCaller()
        val (dispatcher, _) = dispatcher(caller)

        dispatcher.dispatch(CodewordTier.Danger)

        assertEquals(emptyList<String>(), caller.dialled)
    }

    @Test
    fun `the setting off means no phone rings at any tier`() = runBlocking {
        val caller = FakeCaller()
        val (dispatcher, _) = dispatcher(caller, enabled = false)

        dispatcher.dispatch(CodewordTier.Emergency)

        assertEquals(emptyList<String>(), caller.dialled)
    }

    @Test
    fun `a notification the user has not tapped is never recorded as a placed call`() = runBlocking {
        val caller = FakeCaller(outcome = { GuardianCaller.Outcome.AwaitingTap(it) })
        val (dispatcher, recorder) = dispatcher(caller)

        dispatcher.dispatch(CodewordTier.Emergency)

        // The agent's own summary line names her too, and the two are written
        // concurrently now, so the note is picked by content rather than by position.
        val note = recorder.events.single { it.contains("Amara") && !it.contains("alert") }
        assertTrue("got: $note", note.contains("tap"))
        assertTrue("must not claim a call", !note.startsWith("Calling"))
    }

    @Test
    fun `an untapped notification does not stop the next emergency from trying again`() = runBlocking {
        val caller = FakeCaller(outcome = { GuardianCaller.Outcome.AwaitingTap(it) })
        val (dispatcher, _) = dispatcher(caller)

        dispatcher.dispatch(CodewordTier.Emergency)
        dispatcher.dispatch(CodewordTier.Emergency)

        assertEquals(listOf("Amara", "Amara"), caller.dialled)
    }

    @Test
    fun `a dialler failure is recorded and does not throw`() = runBlocking {
        val caller = FakeCaller(outcome = { GuardianCaller.Outcome.Failed("No SIM in this phone") })
        val (dispatcher, recorder) = dispatcher(caller)

        dispatcher.dispatch(CodewordTier.Emergency)

        assertTrue(recorder.events.any { it == "No SIM in this phone" })
    }

    @Test
    fun `a build with no dialler still texts`() = runBlocking {
        val (dispatcher, recorder) = dispatcher(caller = null)

        val result = dispatcher.dispatch(CodewordTier.Emergency)

        assertTrue(result!!.outcome.reached.contains("Amara"))
        assertTrue(recorder.events.none { it.contains("Calling") })
    }
}
