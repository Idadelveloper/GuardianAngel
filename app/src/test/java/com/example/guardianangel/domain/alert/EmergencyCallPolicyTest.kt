package com.example.guardianangel.domain.alert

import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.EmergencyContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyCallPolicyTest {

    private fun contact(id: String, priority: Int, phone: String = "+15550100") = EmergencyContact(
        id = id,
        name = id,
        relationship = "Friend",
        phoneNumber = phone,
        priority = priority,
    )

    private val guardians = listOf(
        contact("second", priority = 2),
        contact("first", priority = 1),
        contact("third", priority = 3),
    )

    @Test
    fun `emergency calls the highest priority guardian`() {
        val decision = EmergencyCallPolicy.decide(
            tier = CodewordTier.Emergency,
            guardians = guardians,
            enabled = true,
        )

        assertEquals("first", (decision as EmergencyCallPolicy.Decision.Call).contact.id)
    }

    @Test
    fun `danger texts rather than rings`() {
        val decision = EmergencyCallPolicy.decide(
            tier = CodewordTier.Danger,
            guardians = guardians,
            enabled = true,
        )

        assertTrue(decision is EmergencyCallPolicy.Decision.Skip)
    }

    @Test
    fun `caution and safe never ring`() {
        listOf(CodewordTier.Safe, CodewordTier.Caution).forEach { tier ->
            val decision = EmergencyCallPolicy.decide(tier, guardians, enabled = true)
            assertTrue("$tier should not call", decision is EmergencyCallPolicy.Decision.Skip)
        }
    }

    @Test
    fun `the setting wins over the tier`() {
        val decision = EmergencyCallPolicy.decide(
            tier = CodewordTier.Emergency,
            guardians = guardians,
            enabled = false,
        )

        assertTrue(decision is EmergencyCallPolicy.Decision.Skip)
    }

    @Test
    fun `a second emergency in the same session does not hang up the first call`() {
        val decision = EmergencyCallPolicy.decide(
            tier = CodewordTier.Emergency,
            guardians = guardians,
            enabled = true,
            alreadyCalledInSession = true,
        )

        assertTrue(decision is EmergencyCallPolicy.Decision.Skip)
    }

    @Test
    fun `a guardian with no number is skipped rather than dialled blank`() {
        val decision = EmergencyCallPolicy.decide(
            tier = CodewordTier.Emergency,
            guardians = listOf(contact("blank", priority = 1, phone = " "), contact("real", priority = 5)),
            enabled = true,
        )

        assertEquals("real", (decision as EmergencyCallPolicy.Decision.Call).contact.id)
    }

    @Test
    fun `no guardians at all is reported not crashed`() {
        val decision = EmergencyCallPolicy.decide(
            tier = CodewordTier.Emergency,
            guardians = emptyList(),
            enabled = true,
        )

        assertTrue((decision as EmergencyCallPolicy.Decision.Skip).reason.contains("phone number"))
    }
}
