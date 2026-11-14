package com.drinkwithme.core.social

import com.drinkwithme.core.bar.DrinkCategory
import com.drinkwithme.core.bar.DrinkIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SocialTest {
    private val token = "AbCdEfGhIjKlMnOpQrStUv" // 22 Zeichen = 132 Bit

    @Test
    fun parsesInviteTokensFromLinksAndRawTokens() {
        assertEquals(token, InviteLinks.parseToken(token))
        assertEquals(token, InviteLinks.parseToken(" drinkwithme://einladung/$token "))
        assertEquals(token, InviteLinks.parseToken("https://q0ochen.github.io/DrinkWithMe/einladung/$token"))
        assertEquals(token, InviteLinks.parseToken("https://q0ochen.github.io/DrinkWithMe/einladung/$token/"))
        assertEquals(token, InviteLinks.parseToken("https://example.org/einladung/$token?quelle=messenger#oben"))
        assertEquals("a-b_c".repeat(10), InviteLinks.parseToken("drinkwithme://einladung/${"a-b_c".repeat(10)}"))
    }

    @Test
    fun rejectsInvalidInvites() {
        listOf(
            "",
            "kurz",
            "http://q0ochen.github.io/DrinkWithMe/einladung/$token",
            "drinkwithme://freunde/$token",
            "drinkwithme://einladung/$token/mehr",
            "https://example.org/einladung/$token/mehr",
            "https://example.org/$token",
            "https://example.org/einladung/${token.dropLast(1)}",
            "https://example.org/einladung/${token.dropLast(1)}!",
            "nicht einmal ein Link mit Leerzeichen",
            "x".repeat(129),
        ).forEach { assertNull(InviteLinks.parseToken(it), it) }
    }

    @Test
    fun buildsInviteLinks() {
        assertEquals("drinkwithme://einladung/$token", InviteLinks.appLink(token))
        assertEquals(
            "https://q0ochen.github.io/DrinkWithMe/einladung/$token",
            InviteLinks.webLink("https://q0ochen.github.io/DrinkWithMe/", token),
        )
        assertFailsWith<IllegalArgumentException> { InviteLinks.appLink("kurz") }
        assertEquals(token, InviteLinks.parseToken(InviteLinks.webLink("https://q0ochen.github.io/DrinkWithMe", token)))
    }

    @Test
    fun modelsMatchTheContract() {
        val anna = UserProfile("u-1", "Anna", null)
        val request = FriendRequest("r-1", anna, FriendRequest.Direction.INCOMING, Instant.EPOCH)
        assertEquals(listOf("INCOMING", "OUTGOING"), FriendRequest.Direction.entries.map { it.name })
        assertEquals(request, request.copy())
        assertEquals(Friend(anna, Instant.EPOCH), Friend(anna.copy(), Instant.EPOCH))
        val event = FriendDrinkEvent("e-1", anna, "Tee", DrinkIcon.TEA, DrinkCategory.TEA, 250, Instant.EPOCH)
        assertEquals(250, event.volumeMilliliters)
    }

    @Test
    fun portsCanBeImplementedWithFakes() = runTest {
        val anna = UserProfile("u-1", "Anna", null)
        val session = object : SessionProvider {
            override val currentUser: Flow<UserProfile?> = MutableStateFlow(anna)
        }
        var requestedLimit = -1
        val feed = object : FeedService {
            override suspend fun recentEvents(since: Instant?, limit: Int): List<FriendDrinkEvent> {
                requestedLimit = limit
                return emptyList()
            }
        }
        assertEquals(anna, session.currentUser.first())
        feed.recentEvents(null)
        assertEquals(100, requestedLimit) // Standardlimit laut Vertrag
    }
}
