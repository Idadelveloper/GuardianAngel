package com.example.guardianangel.domain

import com.example.guardianangel.domain.model.initialsOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the blank-name cases.
 *
 * A fresh anonymous account has no display name until the user types one, which crashed
 * the Settings screen with `NoSuchElementException: Char sequence is empty`. The fakes
 * always supplied a name, so nothing caught it until the database was real.
 */
class InitialsTest {

    @Test
    fun `takes up to two initials`() {
        assertEquals("ID", initialsOf("Ida Delphine"))
        assertEquals("I", initialsOf("Ida"))
        assertEquals("AB", initialsOf("Ada Beatrice Carter"))
    }

    @Test
    fun `survives names a form can actually produce`() {
        assertEquals("?", initialsOf(""))
        assertEquals("?", initialsOf("   "))
        assertEquals("ID", initialsOf("  ida   delphine  "))
    }

    @Test
    fun `fallback is caller's choice`() {
        assertEquals("GA", initialsOf("", fallback = "GA"))
    }
}
