package com.example.guardianangel.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.guardianangel.data.local.CurrentUser
import com.example.guardianangel.data.local.GuardianDatabase
import com.example.guardianangel.data.local.RoomCodewordRepository
import com.example.guardianangel.data.local.RoomContactsRepository
import com.example.guardianangel.data.local.UserEntity
import com.example.guardianangel.domain.model.Codeword
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.ContactPresence
import com.example.guardianangel.domain.model.EmergencyContact
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
 * Codewords and guardians survive being saved.
 *
 * Written after finding that the onboarding screen saved codewords under ids it invented
 * itself (`cw-safe`) while seeding used `cw-<userId>-safe` — so every phrase set during
 * setup was inserted as a *new* row and the account ended up holding eight codewords,
 * four of them the suggestions it was supposed to replace, all still live.
 */
@RunWith(AndroidJUnit4::class)
class CodewordPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: GuardianDatabase
    private lateinit var codewords: RoomCodewordRepository
    private lateinit var contacts: RoomContactsRepository

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
        codewords = RoomCodewordRepository(database.codewordDao(), currentUser)
        contacts = RoomContactsRepository(database.guardianDao(), currentUser)
        codewords.seedDefaults(userId)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun seedingProducesExactlyOneCodewordPerTier() = runBlocking {
        val all = codewords.observeCodewords().first()
        assertEquals(CodewordTier.entries.size, all.size)
        assertEquals(CodewordTier.entries.toSet(), all.map { it.tier }.toSet())
    }

    @Test
    fun seedingTwiceDoesNotDuplicate() = runBlocking {
        // seedDefaults runs on every authenticated launch.
        codewords.seedDefaults(userId)
        codewords.seedDefaults(userId)
        assertEquals(CodewordTier.entries.size, codewords.observeCodewords().first().size)
    }

    @Test
    fun seededPhrasesAreNotMarkedAsChosen() = runBlocking {
        // They are suggestions. The home screen must keep prompting until she replaces
        // them, which it decides from this flag.
        assertTrue(codewords.observeCodewords().first().none { it.isCustomised })
    }

    @Test
    fun savingAPhraseUpdatesTheTierInsteadOfAddingARow() = runBlocking {
        // Exactly what the onboarding screen does: an id it made up itself.
        codewords.updateCodeword(
            Codeword(id = "cw-danger", tier = CodewordTier.Danger, phrase = "umbrella")
        )

        val all = codewords.observeCodewords().first()
        assertEquals("A save added a row instead of updating one", 4, all.size)

        val danger = all.single { it.tier == CodewordTier.Danger }
        assertEquals("umbrella", danger.phrase)
        assertTrue("A saved phrase is a choice, not a suggestion", danger.isCustomised)
    }

    @Test
    fun savingOneTierLeavesTheOthersAlone() = runBlocking {
        codewords.updateCodeword(
            Codeword(id = "cw-safe", tier = CodewordTier.Safe, phrase = "sunflower")
        )

        val all = codewords.observeCodewords().first()
        assertEquals(1, all.count { it.isCustomised })
        assertEquals("sunflower", all.single { it.tier == CodewordTier.Safe }.phrase)
        assertFalse(all.single { it.tier == CodewordTier.Danger }.isCustomised)
    }

    @Test
    fun aSavedPhraseSurvivesReseeding() = runBlocking {
        codewords.updateCodeword(
            Codeword(id = "cw-safe", tier = CodewordTier.Safe, phrase = "sunflower")
        )
        // The next launch seeds again; it must not overwrite her choice.
        codewords.seedDefaults(userId)

        val safe = codewords.observeCodewords().first().single { it.tier == CodewordTier.Safe }
        assertEquals("sunflower", safe.phrase)
        assertTrue(safe.isCustomised)
    }

    @Test
    fun phrasesAreTrimmed() = runBlocking {
        codewords.updateCodeword(
            Codeword(id = "cw-safe", tier = CodewordTier.Safe, phrase = "  sunflower  ")
        )
        assertEquals(
            "sunflower",
            codewords.observeCodewords().first().single { it.tier == CodewordTier.Safe }.phrase,
        )
    }

    @Test
    fun guardiansArePersistedAndRankedInOrderAdded() = runBlocking {
        contacts.addContact(guardian("g1", "Sarah Jenkins"))
        contacts.addContact(guardian("g2", "David Chen"))

        val saved = contacts.observeContacts().first()
        assertEquals(2, saved.size)
        assertEquals(listOf("Sarah Jenkins", "David Chen"), saved.map { it.name })
        // Priority is positional, so the first guardian added is the first called.
        assertEquals(1, saved.first().priority)
        assertEquals(2, saved[1].priority)
    }

    @Test
    fun aGuardianCanBeUpdatedAndRemoved() = runBlocking {
        contacts.addContact(guardian("g1", "Sarah Jenkins"))

        val stored = contacts.observeContacts().first().single()
        contacts.updateContact(stored.copy(phoneNumber = "+1 555 9999"))
        assertEquals("+1 555 9999", contacts.observeContacts().first().single().phoneNumber)

        contacts.removeContact("g1")
        assertTrue(contacts.observeContacts().first().isEmpty())
    }

    private fun guardian(id: String, name: String) = EmergencyContact(
        id = id,
        name = name,
        relationship = "Friend",
        phoneNumber = "+1 555 0100",
        priority = 0,
        presence = ContactPresence.Unknown,
    )
}
