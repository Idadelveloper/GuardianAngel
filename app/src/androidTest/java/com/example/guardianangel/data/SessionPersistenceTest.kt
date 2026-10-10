package com.example.guardianangel.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.guardianangel.data.local.CurrentUser
import com.example.guardianangel.data.local.FileTranscriptExporter
import com.example.guardianangel.data.local.GuardianDatabase
import com.example.guardianangel.data.local.RoomActivityRepository
import com.example.guardianangel.data.local.RoomSessionRecorder
import com.example.guardianangel.data.local.UserEntity
import com.example.guardianangel.domain.model.ActivityFilter
import com.example.guardianangel.domain.model.AnalyticsRange
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.DiarizedEntry
import com.example.guardianangel.domain.model.SessionKind
import com.example.guardianangel.domain.model.SpeakerKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A recording becomes a readable, exportable record.
 *
 * This is the chain the product rests on: audio is transcribed, written as it happens,
 * and readable afterwards in the Activity tab, in the analytics, and as a file. None of
 * it existed — the schema was complete and nothing ever wrote to it, while the Activity
 * screens served three invented incidents from `ActivitySamples`.
 */
@RunWith(AndroidJUnit4::class)
class SessionPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: GuardianDatabase
    private lateinit var recorder: RoomSessionRecorder
    private lateinit var activity: RoomActivityRepository

    private val userId = "test-user"

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, GuardianDatabase::class.java).build()
        database.userDao().upsert(
            UserEntity(
                id = userId,
                displayName = "Ida",
                email = null,
                phoneNumber = null,
                isAnonymous = true,
                authProvider = "local",
                createdAt = 0,
                updatedAt = 0,
            )
        )
        val currentUser = CurrentUser(database.userDao())
        recorder = RoomSessionRecorder(database.sessionDao(), currentUser)
        activity = RoomActivityRepository(
            dao = database.sessionDao(),
            currentUser = currentUser,
            exporter = FileTranscriptExporter(context),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun anAccountWithNoRecordingsShowsNothing() = runBlocking {
        // The old fake returned three invented incidents here. An app that shows
        // imaginary evidence teaches the user its records cannot be trusted.
        assertTrue(activity.observeSessions(ActivityFilter.All).first().isEmpty())

        val analytics = activity.observeAnalytics(AnalyticsRange.AllTime).first()
        assertEquals(0, analytics.guardedWalks)
        assertEquals(0, analytics.duressTriggers)
        assertTrue(analytics.diurnalBreakdown.isEmpty())
        assertNull("No insight should be invented for an empty account", analytics.insight)
    }

    @Test
    fun aRecordingIsReadableWhileItIsStillRunning() = runBlocking {
        recorder.begin(trigger = null, triggerLabel = "hey angel")
        recorder.addTranscript(line("I'm heading down Market now"))

        // Written as it happens, not buffered to the end: the moments worth recording
        // are exactly the moments something might kill the process.
        val live = activity.observeSessions(ActivityFilter.All).first().single()
        assertNull("A live session has no end time", live.endedAtEpochMillis)
        assertTrue(live.title.contains("hey angel"))

        val detail = activity.observeSession(live.id).first()
        assertEquals(1, detail?.entries?.size)
        assertEquals("I'm heading down Market now", detail?.entries?.first()?.text)
    }

    @Test
    fun aFinishedRecordingKeepsItsTimelineInOrder() = runBlocking {
        val id = recorder.begin(trigger = null, triggerLabel = "manual")
        recorder.addTranscript(line("hello", at = 1_000))
        recorder.addAudioEvent("Glass", decibels = 91, confidence = 0.8f, atMillis = 1_500, isDanger = true)
        recorder.addTranscript(line("what was that", at = 2_000))
        recorder.finish()

        val session = activity.observeSession(id).first()
        assertNotNull(session)
        assertNotNull("A finished session must have an end time", session!!.endedAtEpochMillis)

        // Speech and sounds interleaved by time. Splitting them apart loses the sequence
        // that gives "glass breaking between two sentences" its meaning.
        assertEquals(listOf("hello", "Glass", "what was that"), session.entries.map { it.text })
        assertEquals(91, session.peakDecibels)
    }

    @Test
    fun aCodewordMakesItAnIncidentAndIsLoggedInTheTimeline() = runBlocking {
        val id = recorder.begin(trigger = null, triggerLabel = "manual")
        recorder.addTranscript(line("please leave me alone"))
        recorder.noteCodeword(CodewordTier.Danger, "lighthouse", wasUserVoice = true)
        recorder.finish()

        val session = activity.observeSession(id).first()!!
        assertEquals(SessionKind.Incident, session.kind)
        assertTrue(
            "The trigger should appear in the timeline, not only in metadata",
            session.entries.any { it.text.contains("lighthouse") },
        )
        assertTrue(session.entries.any { it.speakerQualifier == "Voiceprint verified" })
    }

    @Test
    fun aSafeCodewordAfterADangerOneDoesNotRewriteHistory() = runBlocking {
        val id = recorder.begin(trigger = null, triggerLabel = "manual")
        recorder.noteCodeword(CodewordTier.Danger, "lighthouse", wasUserVoice = true)
        recorder.noteCodeword(CodewordTier.Safe, "sunflower", wasUserVoice = true)
        recorder.finish()

        // Cancelling the alert is right; erasing the record of the incident is not.
        assertEquals(SessionKind.Incident, activity.observeSession(id).first()!!.kind)
    }

    @Test
    fun recordingsCountTowardTheAnalytics() = runBlocking {
        recorder.begin(trigger = null, triggerLabel = "manual")
        recorder.addTranscript(line("all quiet", kind = SpeakerKind.You))
        recorder.noteSafetyScore(88)
        recorder.finish()

        recorder.begin(trigger = null, triggerLabel = "manual")
        recorder.addTranscript(line("who are you", kind = SpeakerKind.Unknown))
        recorder.addAudioEvent("Shouting", decibels = 95, confidence = 0.9f, atMillis = 10, isDanger = true)
        recorder.noteCodeword(CodewordTier.Danger, "lighthouse", wasUserVoice = true)
        recorder.noteSafetyScore(32)
        recorder.finish()

        val analytics = activity.observeAnalytics(AnalyticsRange.AllTime).first()
        assertEquals(2, analytics.guardedWalks)
        assertEquals(1, analytics.duressTriggers)
        assertEquals("One of two sessions escalated", 50, analytics.safeArrivalsPercent)
        // The average of the worst points, so one bad night is not smoothed away.
        assertEquals(60, analytics.overallRating)
        assertTrue(analytics.acousticBreakdown.any { it.label == "Shouting" })
        assertTrue(analytics.voiceBreakdown.any { it.label == "You" })
        assertTrue(analytics.voiceBreakdown.any { it.label == "Unrecognised" })
        assertNotNull("An incident is worth an insight line", analytics.insight)
    }

    @Test
    fun deletingASessionRemovesItFromTheAnalyticsToo() = runBlocking {
        val id = recorder.begin(trigger = null, triggerLabel = "manual")
        recorder.noteCodeword(CodewordTier.Danger, "lighthouse", wasUserVoice = true)
        recorder.finish()
        assertEquals(1, activity.observeAnalytics(AnalyticsRange.AllTime).first().duressTriggers)

        activity.deleteSession(id)

        // Derived on read rather than stored, so deleting really does stop it counting —
        // which is the whole point of being able to delete one.
        val after = activity.observeAnalytics(AnalyticsRange.AllTime).first()
        assertEquals(0, after.guardedWalks)
        assertEquals(0, after.duressTriggers)
        assertTrue(activity.observeSessions(ActivityFilter.All).first().isEmpty())
    }

    @Test
    fun deletingASessionTakesItsTranscriptWithIt() = runBlocking {
        val id = recorder.begin(trigger = null, triggerLabel = "manual")
        recorder.addTranscript(line("something private"))
        recorder.finish()

        activity.deleteSession(id)

        // A deleted recording must not leave its words behind in another table.
        assertTrue(database.sessionDao().findTranscript(id).isEmpty())
    }

    @Test
    fun theFilterSeparatesIncidentsFromConversations() = runBlocking {
        recorder.begin(trigger = null, triggerLabel = "manual")
        recorder.addTranscript(line("nice evening"))
        recorder.finish()

        recorder.begin(trigger = null, triggerLabel = "manual")
        recorder.noteCodeword(CodewordTier.Emergency, "firefly", wasUserVoice = true)
        recorder.finish()

        assertEquals(2, activity.observeSessions(ActivityFilter.All).first().size)
        assertEquals(1, activity.observeSessions(ActivityFilter.Incidents).first().size)
        assertEquals(1, activity.observeSessions(ActivityFilter.Conversations).first().size)
    }

    @Test
    fun exportWritesARealFileWithTheTranscriptAndItsProvenance() = runBlocking {
        val id = recorder.begin(trigger = null, triggerLabel = "manual")
        recorder.addTranscript(line("I'm being followed"))
        recorder.addLocation(37.7749, -122.4194, atMillis = 500, label = "Market St", safetyScore = 44)
        recorder.noteCodeword(CodewordTier.Danger, "lighthouse", wasUserVoice = true)
        recorder.finish()

        val path = activity.exportSession(id)
        val file = File(path)

        // The old implementation returned "guardian-angel-<id>.pdf" and wrote nothing.
        assertTrue("Export wrote no file: $path", file.exists())
        val text = file.readText()
        assertTrue("The transcript is missing", text.contains("I'm being followed"))
        assertTrue("The codeword is missing", text.contains("lighthouse"))
        assertTrue("The location trail is missing", text.contains("Market St"))
        // Anyone relying on this as evidence needs to know how it was made.
        assertTrue(
            "The file must state that it is machine-generated",
            text.contains("automatic model") && text.contains("not been reviewed"),
        )
        file.delete()
        Unit
    }

    @Test
    fun aSessionThatNeverEndedIsReportedHonestly() = runBlocking {
        val id = recorder.begin(trigger = null, triggerLabel = "manual")
        recorder.addTranscript(line("hello"))
        // No finish(): the process died, which is exactly when evidence matters most.

        val session = activity.observeSession(id).first()!!
        assertNull(session.endedAtEpochMillis)
        assertEquals("The line must still be there", 1, session.entries.size)

        val insight = activity.observeAnalytics(AnalyticsRange.AllTime).first().insight
        assertTrue(
            "An unfinished recording is worth telling her about: $insight",
            insight?.contains("without being stopped") == true,
        )
    }

    @Test
    fun aSessionTrailJoinsBreadcrumbsToWhatHappened() = runBlocking {
        val id = recorder.begin(trigger = null, triggerLabel = "manual")
        val t0 = System.currentTimeMillis()

        recorder.addLocation(37.8690, -122.2680, atMillis = t0, label = "Shattuck Ave", safetyScore = 92)
        recorder.addTranscript(line("all quiet so far", at = t0 + 2_000).copy(isFlagged = false))
        recorder.addLocation(37.8700, -122.2690, atMillis = t0 + 30_000, label = "Allston Way", safetyScore = 48)
        recorder.addTranscript(line("please leave me alone", at = t0 + 31_000).copy(isFlagged = true))
        recorder.finish()

        val trail = activity.observeTrail(id).first()

        assertEquals(2, trail.points.size)
        assertTrue("Two points is a path", trail.hasPath)
        assertEquals(48, trail.lowestScore)

        // The flagged line is pinned to the breadcrumb it happened at, and the 44-point
        // fall between them earns its own marker.
        val kinds = trail.incidents.map { it.kind }
        assertTrue("Expected a pin for the flagged line, got $kinds", trail.incidents.isNotEmpty())
        val flagged = trail.incidents.first()
        assertEquals(37.8700, flagged.latitude, 1e-6)
        assertTrue(flagged.detail.contains("leave me alone") || flagged.detail.contains("Dropped"))
    }

    @Test
    fun trailPathsForTheListComeBackInOneRead() = runBlocking {
        val first = recorder.begin(trigger = null, triggerLabel = "manual")
        recorder.addLocation(37.0, -122.0, atMillis = 1, label = null, safetyScore = null)
        recorder.finish()

        val second = recorder.begin(trigger = null, triggerLabel = "manual")
        recorder.addLocation(38.0, -123.0, atMillis = 2, label = null, safetyScore = null)
        recorder.addLocation(38.1, -123.1, atMillis = 3, label = null, safetyScore = null)
        recorder.finish()

        val paths = activity.observeTrailPaths().first()
        assertEquals(1, paths[first]?.size)
        assertEquals(2, paths[second]?.size)
    }

    @Test
    fun aSessionWithoutLocationHasAnEmptyTrail() = runBlocking {
        val id = recorder.begin(trigger = null, triggerLabel = "manual")
        recorder.addTranscript(line("recorded indoors"))
        recorder.finish()

        // Location off is a normal thing to have. The UI says so rather than drawing an
        // empty map.
        val trail = activity.observeTrail(id).first()
        assertTrue(trail.points.isEmpty())
        assertTrue(trail.incidents.isEmpty())
    }

    private fun line(
        text: String,
        at: Long = System.currentTimeMillis(),
        kind: SpeakerKind = SpeakerKind.You,
    ) = DiarizedEntry(
        id = "spk0",
        sessionId = "",
        atEpochMillis = at,
        speakerKind = kind,
        speakerLabel = if (kind == SpeakerKind.You) "You" else "Unfamiliar voice",
        speakerQualifier = null,
        text = text,
    )
}
