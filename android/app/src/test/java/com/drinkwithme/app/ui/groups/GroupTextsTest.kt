package com.drinkwithme.app.ui.groups

import com.drinkwithme.core.social.DrinkGroup
import com.drinkwithme.core.social.GroupMember
import com.drinkwithme.core.social.InviteCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupTextsTest {
    private fun group(vararg members: GroupMember) =
        DrinkGroup("g1", "Stammtisch", InviteCode.parse("K7MX2QAB")!!, "u1", members.toList())

    @Test
    fun summaryListsOthersFirstAndMeAsDu() {
        val group = group(GroupMember("u1", "Anna"), GroupMember("u2", "Ben"), GroupMember("u3", "Cem"))
        assertEquals("3 Mitglieder: Ben, Cem, Du", GroupMemberSummary.format(group, currentUserId = "u1"))
    }

    @Test
    fun summaryUsesSingularForOneMember() {
        assertEquals("1 Mitglied: Du", GroupMemberSummary.format(group(GroupMember("u1", "Anna")), "u1"))
    }

    @Test
    fun inviteMessageContainsNameAndCode() {
        val message = GermanInviteMessageFormatter().message(group(GroupMember("u1", "Anna")))
        assertTrue("„Stammtisch“" in message)
        assertTrue("K7MX2QAB" in message)
        assertTrue("#DrinkWithMe" in message)
    }

    @Test
    fun codeIsDisplayedInTwoGroupsOfFour() {
        assertEquals("K7MX-2QAB", InviteCodeText.display(InviteCode.parse("K7MX2QAB")!!))
        assertEquals(InviteCode.parse("K7MX2QAB"), InviteCode.parse(InviteCodeText.EXAMPLE))
    }

    @Test
    fun inputIsUppercasedAndLimitedToEightCharacters() {
        assertEquals("K7MX2QAB", InviteCodeText.sanitizeInput("k7mx2qab"))
        assertEquals("K7MX-2QAB", InviteCodeText.sanitizeInput("k7mx-2qab"))
        assertEquals("K7MX2QAB", InviteCodeText.sanitizeInput("K7MX2QAB9"))
        assertEquals("K7MX-2QAB", InviteCodeText.sanitizeInput("K7MX-2QAB-"))
    }

    @Test
    fun inputDropsSeparatorsAndSymbols() {
        assertEquals("K7MX", InviteCodeText.sanitizeInput("-K7MX"))
        assertEquals("K7MX-2", InviteCodeText.sanitizeInput("K7MX--2"))
        assertEquals("K7MX2QAB", InviteCodeText.sanitizeInput("K7MX 2QAB"))
        assertEquals("K7MX2Q", InviteCodeText.sanitizeInput("K7MX#2Q!"))
    }

    @Test
    fun pastedInviteMessageYieldsTheCode() {
        val message = GermanInviteMessageFormatter().message(group(GroupMember("u1", "Anna")))
        assertEquals("K7MX2QAB", InviteCodeText.sanitizeInput(message))
        assertEquals("K7MX2QAB", InviteCodeText.sanitizeInput("Code: k7mx-2qab"))
    }

    @Test
    fun sanitizedInputIsAcceptedByTheParser() {
        listOf("k7mx2qab", "K7MX-2QAB", " K7MX 2QAB ").forEach { raw ->
            assertEquals("K7MX2QAB", InviteCode.parse(InviteCodeText.sanitizeInput(raw))?.value, raw)
        }
    }
}
