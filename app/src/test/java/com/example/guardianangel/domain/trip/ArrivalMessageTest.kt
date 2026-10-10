package com.example.guardianangel.domain.trip

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalMessageTest {

    private val arrivedAt = Calendar.getInstance(TimeZone.getDefault()).apply {
        set(2026, Calendar.OCTOBER, 10, 22, 45, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `the default names who, where and when`() {
        val message = ArrivalMessage.compose("Ida Delphine", "Doe Library", arrivedAt, locale = Locale.US)

        assertTrue(message.contains("Ida"))
        assertTrue(message.contains("Doe Library"))
        assertTrue(message.contains("10:45 PM"))
    }

    @Test
    fun `a custom message is passed through untouched`() {
        val message = ArrivalMessage.compose(
            userName = "Ida",
            destinationName = "Doe Library",
            arrivedAtMillis = arrivedAt,
            customMessage = "Made it, don't wait up x",
            locale = Locale.US,
        )

        assertTrue(message.startsWith("Made it, don't wait up x"))
        assertTrue("the facts still travel with it", message.contains("10:45 PM"))
    }

    @Test
    fun `it never claims to know she is safe, only that she arrived`() {
        val message = ArrivalMessage.compose("Ida", "Doe Library", arrivedAt, locale = Locale.US)

        assertFalse(message.lowercase().contains("safe"))
        assertTrue(message.contains("arrived"))
    }

    @Test
    fun `a blank name does not produce a sentence starting with nothing`() {
        val message = ArrivalMessage.compose("  ", "Doe Library", arrivedAt, locale = Locale.US)

        assertTrue(message.startsWith("Your contact has arrived"))
    }

    @Test
    fun `a blank custom message falls back to the default`() {
        val message = ArrivalMessage.compose(
            userName = "Ida",
            destinationName = "Doe Library",
            arrivedAtMillis = arrivedAt,
            customMessage = "   ",
            locale = Locale.US,
        )

        assertTrue(message.startsWith("Ida has arrived"))
    }

    @Test
    fun `an unnamed destination still reads as a sentence`() {
        val message = ArrivalMessage.compose("Ida", "", arrivedAt, locale = Locale.US)

        assertTrue(message.contains("the destination"))
    }
}
