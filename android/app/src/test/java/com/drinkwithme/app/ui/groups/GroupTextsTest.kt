package com.drinkwithme.app.ui.groups

import com.drinkwithme.core.social.DrinkGroup
import com.drinkwithme.core.social.GroupMember
import com.drinkwithme.core.social.InviteCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupTextsTest {
    private fun group(vararg members: GroupMember) =
        DrinkGroup("g1", "Stammtisch", InviteCode.parse("K7MX2Q")!!, "u1", members.toList())

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
        assertTrue("K7MX2Q" in message)
        assertTrue("#DrinkWithMe" in message)
    }
}
