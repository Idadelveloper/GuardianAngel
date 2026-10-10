package com.example.guardianangel.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.guardianangel.audio.SentencePieceTokenizer
import com.example.guardianangel.data.local.CurrentUser
import com.example.guardianangel.data.local.GuardianDatabase
import com.example.guardianangel.data.local.RoomListeningRepository
import com.example.guardianangel.data.local.RoomWakeWordStore
import com.example.guardianangel.data.local.UserEntity
import com.example.guardianangel.domain.repository.PermissionProbe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The wake word survives being set.
 *
 * Tested against a real Room database and the real tokeniser rather than through the UI,
 * because this is the only link in hands-free activation whose failure is completely
 * silent: the phrase appears in the field, the screen says it is saved, and the spotter
 * has nothing to listen for. Driving the actual screen was tried first and proved to test
 * the emulator's keyboard more than the app.
 */
@RunWith(AndroidJUnit4::class)
class WakeWordPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: GuardianDatabase
    private lateinit var repository: RoomListeningRepository

    private val allGranted = object : PermissionProbe {
        override fun hasMicrophone() = true
        override fun hasNotifications() = true
        override fun hasLocation() = true
        override fun hasSendSms() = true
        override fun isBatteryExempt() = true
    }

    @Before
    fun setUp() = runBlocking {
        // In-memory, so the test cannot disturb the installed app's data.
        database = Room.inMemoryDatabaseBuilder(context, GuardianDatabase::class.java)
            .build()
        database.userDao().upsert(
            UserEntity(
                id = "test-user",
                displayName = "Ida",
                email = null,
                phoneNumber = null,
                isAnonymous = true,
                authProvider = "local",
                createdAt = 0,
                updatedAt = 0,
            )
        )

        val tokenizer = runCatching {
            SentencePieceTokenizer.fromAssets(
                context.assets,
                "sherpa-onnx-kws-zipformer-gigaspeech/bpe.model",
            )
        }.getOrNull()

        repository = RoomListeningRepository(
            wakeWordStore = RoomWakeWordStore(
                dao = database.wakeWordDao(),
                currentUser = CurrentUser(database.userDao()),
                tokenizer = tokenizer,
            ),
            permissions = allGranted,
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun settingAPhrasePersistsItWithTokens() = runBlocking {
        assertEquals("", repository.observeWakeWord().first().phrase)

        repository.setWakeWordPhrase("hey angel")

        val stored = repository.observeWakeWord().first()
        assertEquals("hey angel", stored.phrase)
        assertTrue("A stored phrase must count as enrolled", stored.isEnrolled)

        val row = database.wakeWordDao().find("test-user")
        assertEquals("hey angel", row?.phrase)
        assertTrue("Tokens were not cached", row?.tokens?.isNotBlank() == true)
        assertEquals(
            "The row must record which tokeniser produced its tokens",
            SentencePieceTokenizer.VERSION,
            row?.tokenizerVersion,
        )
    }

    @Test
    fun aPhraseIsTrimmedAndSurvivesBeingChanged() = runBlocking {
        repository.setWakeWordPhrase("  hey angel  ")
        assertEquals("hey angel", repository.observeWakeWord().first().phrase)

        repository.setWakeWordPhrase("watch over me")
        assertEquals("watch over me", repository.observeWakeWord().first().phrase)
    }

    @Test
    fun curatedPhrasesAreAllAccepted() = runBlocking {
        // These are the phrases the setup screen suggests. Suggesting one the model
        // cannot express would be the worst possible bug in this area.
        for (phrase in SentencePieceTokenizer.CURATED_PHRASES) {
            assertTrue(
                "Suggested phrase \"$phrase\" is not usable",
                repository.canUseWakePhrase(phrase),
            )
        }
    }

    @Test
    fun unrepresentablePhrasesAreRejected() = runBlocking {
        assertFalse("A blank phrase is not usable", repository.canUseWakePhrase(""))
        // Only meaningful when a vocabulary is installed; without one everything is
        // allowed by design, so this would be vacuously true and is skipped.
        if (repository.canUseWakePhrase("hey angel")) {
            assertFalse(
                "A phrase outside the vocabulary must be refused before it is saved",
                repository.canUseWakePhrase("ЖЖЖ ЩЩЩ"),
            )
        }
    }

    @Test
    fun statusUnblocksOnceAPhraseIsSet() = runBlocking {
        // Every permission is granted here, so the wake word is the only thing that can
        // block listening — the exact state the home screen reports as "ready".
        assertFalse(repository.observeStatus().first().canListen)

        repository.setWakeWordPhrase("hey angel")

        assertTrue(
            "With permissions granted and a phrase set, listening must be unblocked",
            repository.observeStatus().first().canListen,
        )
    }
}
