package com.drinkwithme.app.social.firebase

import com.drinkwithme.app.social.SocialServiceException
import com.drinkwithme.core.social.GroupMember
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GroupMapperTest {

    @Test
    fun mapsGroupDocumentInMemberOrder() {
        val group = GroupMapper.fromDocument(
            id = "g1",
            data = mapOf(
                "name" to "Stammtisch",
                "inviteCode" to "K7MX2QAB",
                "ownerId" to "u1",
                "memberIds" to listOf("u2", "u1"),
                "members" to mapOf("u1" to mapOf("displayName" to "Anna"), "u2" to mapOf("displayName" to "Ben")),
                "createdAt" to Any(),
            ),
        )!!

        assertEquals("g1", group.id)
        assertEquals("Stammtisch", group.name)
        assertEquals("K7MX2QAB", group.inviteCode.value)
        assertEquals("u1", group.ownerId)
        assertEquals(listOf(GroupMember("u2", "Ben"), GroupMember("u1", "Anna")), group.members)
    }

    @Test
    fun missingMemberNameBecomesUnknown() {
        val group = GroupMapper.fromDocument(
            "g1",
            mapOf("name" to "Runde", "inviteCode" to "K7MX2QAB", "ownerId" to "u1", "memberIds" to listOf("u1")),
        )!!
        assertEquals(listOf(GroupMember("u1", "Unbekannt")), group.members)
    }

    @Test
    fun documentWithoutValidCodeIsSkipped() {
        assertNull(GroupMapper.fromDocument("g1", mapOf("name" to "Runde", "inviteCode" to "0000")))
        assertNull(GroupMapper.fromDocument("g1", mapOf("name" to "Runde")))
    }

    @Test
    fun mapsCallableResult() {
        val result = mapOf(
            "group" to mapOf(
                "id" to "g1",
                "name" to "Stammtisch",
                "inviteCode" to "K7MX2QAB",
                "ownerId" to "u1",
                "members" to listOf(mapOf("userId" to "u1", "displayName" to "Anna"), mapOf("displayName" to "ohne ID")),
            ),
        )

        val group = GroupMapper.fromCallableResult(result)

        assertEquals("g1", group.id)
        assertEquals("K7MX2QAB", group.inviteCode.value)
        assertEquals(listOf(GroupMember("u1", "Anna")), group.members)
    }

    @Test
    fun malformedCallableResultIsReportedInGerman() {
        val error = assertFailsWith<SocialServiceException> { GroupMapper.fromCallableResult(mapOf("group" to null)) }
        assertEquals(true, error.message?.startsWith("Unerwartete Antwort vom Server"))
        assertFailsWith<SocialServiceException> { GroupMapper.fromCallableResult(null) }
        assertFailsWith<SocialServiceException> {
            GroupMapper.fromCallableResult(mapOf("group" to mapOf("id" to "g1", "inviteCode" to "falsch")))
        }
    }
}
