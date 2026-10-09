package com.example.guardianangel.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.guardianangel.data.auth.LocalAuthRepository
import com.example.guardianangel.data.local.GuardianDatabase
import com.example.guardianangel.domain.repository.AuthResult
import com.example.guardianangel.domain.repository.AuthState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The auth gate: what the app opens on, and what signing out does.
 *
 * Instrumented rather than a unit test because it needs real Room and the real Keystore —
 * the password hash is encrypted with a hardware-backed key, and a fake would skip the
 * one part most likely to behave differently on a device.
 *
 * Each case here corresponds to a way the gate failed during this work:
 * launch creating an account and skipping the gate entirely, sign-out deleting the
 * account along with every guardian, and a login screen that accepted any password.
 */
@RunWith(AndroidJUnit4::class)
class AuthGateTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: GuardianDatabase
    private lateinit var auth: LocalAuthRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, GuardianDatabase::class.java).build()
        auth = LocalAuthRepository(database.userDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun freshInstallHasNoSession() = runBlocking {
        // The whole point of the gate. This failed: launch created an anonymous account
        // whenever the database was empty, so the app was always authenticated and the
        // sign-up screen could never be reached.
        assertTrue(auth.ensureSignedIn() is AuthResult.Failure)
        assertEquals(AuthState.SignedOut, auth.observeAuthState().first())
        assertEquals(0, database.userDao().accountCount())
    }

    @Test
    fun signingUpOpensASession() = runBlocking {
        val result = auth.signUpWithEmail("Ida", "ida@example.com", "a-good-passphrase")
        assertTrue("Sign-up failed: $result", result is AuthResult.Success)

        val state = auth.observeAuthState().first()
        assertTrue(state is AuthState.SignedIn)
        assertEquals("Ida", (state as AuthState.SignedIn).user.displayName)
        assertFalse("A signed-up account is not anonymous", state.user.isAnonymous)
    }

    @Test
    fun aResumedLaunchStaysSignedIn() = runBlocking {
        auth.signUpWithEmail("Ida", "ida@example.com", "a-good-passphrase")

        // What the next launch does. It must not ask her to log in again.
        assertTrue(auth.ensureSignedIn() is AuthResult.Success)
        assertTrue(auth.observeAuthState().first() is AuthState.SignedIn)
    }

    @Test
    fun theRightPasswordLetsHerBackIn() = runBlocking {
        auth.signUpWithEmail("Ida", "ida@example.com", "a-good-passphrase")
        auth.signOut()
        assertEquals(AuthState.SignedOut, auth.observeAuthState().first())

        val result = auth.signInWithEmail("ida@example.com", "a-good-passphrase")
        assertTrue("Correct password was rejected: $result", result is AuthResult.Success)
        assertTrue(auth.observeAuthState().first() is AuthState.SignedIn)
    }

    @Test
    fun theWrongPasswordDoesNot() = runBlocking {
        auth.signUpWithEmail("Ida", "ida@example.com", "a-good-passphrase")
        auth.signOut()

        // A login screen that accepts anything is worse than no login screen, because it
        // looks like protection.
        assertTrue(auth.signInWithEmail("ida@example.com", "wrong") is AuthResult.Failure)
        assertEquals(AuthState.SignedOut, auth.observeAuthState().first())
    }

    @Test
    fun anUnknownEmailFailsIdenticallyToAWrongPassword() = runBlocking {
        auth.signUpWithEmail("Ida", "ida@example.com", "a-good-passphrase")
        auth.signOut()

        val unknown = auth.signInWithEmail("someone@example.com", "a-good-passphrase")
        val wrong = auth.signInWithEmail("ida@example.com", "nope")

        // Identical messages, so the screen cannot be used to discover whether an address
        // has an account on this phone.
        assertEquals(
            (unknown as AuthResult.Failure).message,
            (wrong as AuthResult.Failure).message,
        )
    }

    @Test
    fun email_matching_ignoresCase() = runBlocking {
        auth.signUpWithEmail("Ida", "Ida@Example.com", "a-good-passphrase")
        auth.signOut()

        // Typing an address with different capitalisation is not a wrong password.
        assertTrue(
            auth.signInWithEmail("ida@example.com", "a-good-passphrase") is AuthResult.Success
        )
    }

    @Test
    fun signingOutKeepsTheAccountAndItsData() = runBlocking {
        val user = (auth.signUpWithEmail("Ida", "ida@example.com", "a-good-passphrase")
                as AuthResult.Success).user

        // Something that would be destroyed by a cascading delete.
        database.wakeWordDao().upsert(
            com.example.guardianangel.data.local.WakeWordEntity(
                userId = user.id,
                phrase = "hey angel",
                tokens = "x",
            )
        )

        auth.signOut()

        // Sign-out used to delete the user row, which cascaded through guardians,
        // codewords, sessions and the voiceprint. It is not a request to be forgotten.
        assertEquals(1, database.userDao().accountCount())
        assertNotNull("Signing out destroyed the wake word", database.wakeWordDao().find(user.id))
    }

    @Test
    fun signingOutThenInKeepsTheSameAccountId() = runBlocking {
        val first = (auth.signUpWithEmail("Ida", "ida@example.com", "a-good-passphrase")
                as AuthResult.Success).user
        auth.signOut()
        val second = (auth.signInWithEmail("ida@example.com", "a-good-passphrase")
                as AuthResult.Success).user

        // Every row is keyed by this id. A new one would orphan all of her setup.
        assertEquals(first.id, second.id)
    }

    @Test
    fun continuingWithoutAnAccountUpgradesInPlace() = runBlocking {
        val anonymous = (auth.signInAnonymously() as AuthResult.Success).user
        assertTrue(anonymous.isAnonymous)

        val upgraded = (auth.signUpWithEmail("Ida", "ida@example.com", "a-good-passphrase")
                as AuthResult.Success).user

        // Adding credentials later must keep the guardians and codewords already set up,
        // so the id has to survive the upgrade.
        assertEquals(anonymous.id, upgraded.id)
        assertFalse(upgraded.isAnonymous)
        assertEquals(1, database.userDao().accountCount())
    }

    @Test
    fun theSameEmailCannotBeUsedTwice() = runBlocking {
        auth.signUpWithEmail("Ida", "ida@example.com", "a-good-passphrase")
        auth.signOut()
        assertTrue(
            auth.signUpWithEmail("Someone", "ida@example.com", "another-passphrase")
                is AuthResult.Failure
        )
    }
}
