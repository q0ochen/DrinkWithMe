package com.drinkwithme.app.supabase

import com.drinkwithme.app.FixedUser
import com.drinkwithme.app.social.OutgoingRequest
import com.drinkwithme.app.config.ServerConfig
import com.drinkwithme.app.entry
import com.drinkwithme.core.bar.DrinkCategory
import com.drinkwithme.core.bar.DrinkIcon
import com.drinkwithme.core.social.FriendRequest
import com.drinkwithme.core.social.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Token-Quelle, die beim Erneuern ein neues Token liefert und mitzählt. */
private class CountingTokens : AccessTokenSource {
    var current = "token-1"
    var refreshes = 0
    override suspend fun accessToken(): String = current
    override suspend fun refreshAfterUnauthorized(rejected: String): String {
        refreshes++
        current = "token-${refreshes + 1}"
        return current
    }
}

class SupabaseServicesTest {
    private val server = MockWebServer()
    private val tokens = CountingTokens()
    private val me = FixedUser("me-id")
    private lateinit var api: SupabaseApi
    private lateinit var friends: SupabaseFriendsService
    private lateinit var feed: SupabaseFeedService

    @Before
    fun setUp() {
        server.start()
        val config = ServerConfig(server.url("/").toString().trimEnd('/'), "anon-key", null, "https://q0ochen.github.io/DrinkWithMe")
        api = SupabaseApi(SupabaseHttp(OkHttpClient(), config, Dispatchers.IO), tokens)
        val profiles = ProfileLookup(api)
        friends = SupabaseFriendsService(api, profiles, me, config.webAppUrl)
        feed = SupabaseFeedService(api, profiles, me)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(body: String, code: Int = 200) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private fun take(): RecordedRequest = server.takeRequest(5, TimeUnit.SECONDS)!!

    @Test
    fun friendsLoadsFriendshipsThenProfiles() = runTest {
        json("""[{"user_a":"a-id","user_b":"me-id","created_at":"2026-11-01T10:00:00+00:00"},
                 {"user_a":"me-id","user_b":"z-id","created_at":"2026-11-02T10:00:00+00:00"}]""")
        json("""[{"id":"a-id","display_name":"Zora","avatar_url":null},{"id":"z-id","display_name":"Anna","avatar_url":"http://unsicher"}]""")

        val result = friends.friends()

        assertEquals(listOf("Anna", "Zora"), result.map { it.profile.displayName }, "alphabetisch")
        assertNull(result.first().profile.avatarUrl, "nur https-Bilder")
        assertEquals(Instant.parse("2026-11-02T10:00:00Z"), result.first().since)
        val first = take()
        assertEquals("GET", first.method)
        assertEquals("/rest/v1/friendships?select=user_a%2Cuser_b%2Ccreated_at", first.path)
        assertEquals("Bearer token-1", first.getHeader("Authorization"))
        assertEquals("anon-key", first.getHeader("apikey"))
        val second = take()
        assertEquals("id,display_name,avatar_url", second.requestUrl!!.queryParameter("select"))
        assertEquals("in.(a-id,z-id)", second.requestUrl!!.queryParameter("id"))
    }

    @Test
    fun incomingRequestsComeFromTheTableWithTheSendersProfile() = runTest {
        json("""[{"id":"r1","from_user":"x-id","created_at":"2026-11-09T10:00:00Z"}]""")
        json("""[{"id":"x-id","display_name":"Xaver"}]""")

        val result = friends.requests()

        assertEquals(1, result.size)
        assertEquals(FriendRequest.Direction.INCOMING, result[0].direction)
        assertEquals("Xaver", result[0].other.displayName)
        val request = take().requestUrl!!
        assertEquals("id,from_user,created_at", request.queryParameter("select"), "Absender lesen to_user nie")
        assertEquals("eq.me-id", request.queryParameter("to_user"))
        assertEquals("created_at.desc", request.queryParameter("order"))
    }

    @Test
    fun outgoingRequestsShowOnlyTheMaskedAddress() = runTest {
        json("""[{"id":"r2","email_hint":"b***@example.org","created_at":"2026-11-08T10:00:00Z"},
                 {"id":"kaputt"}]""")

        val result = friends.outgoingRequests()

        assertEquals(listOf(OutgoingRequest("r2", "b***@example.org", Instant.parse("2026-11-08T10:00:00Z"))), result)
        val request = take()
        assertEquals("POST", request.method)
        assertEquals("/rest/v1/rpc/list_outgoing_friend_requests", request.path)
        assertEquals(1, server.requestCount, "kein Profil-Abruf – der Server verrät nicht, wen die Anfrage erreicht")
    }

    @Test
    fun rpcsSendTheDocumentedParameters() = runTest {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(204)) }
        friends.sendRequestByEmail("  Anna@Example.org ")
        friends.respond("r1", accept = true)
        friends.removeFriend("f-id")

        val send = take()
        assertEquals("/rest/v1/rpc/send_friend_request", send.path)
        assertEquals("Anna@Example.org", JSONObject(send.body.readUtf8()).getString("email"))
        val respond = JSONObject(take().body.readUtf8())
        assertEquals("r1", respond.getString("request_id"))
        assertTrue(respond.getBoolean("accept"))
        val remove = take()
        assertEquals("/rest/v1/rpc/remove_friend", remove.path)
        assertEquals("f-id", JSONObject(remove.body.readUtf8()).getString("friend_id"))
    }

    @Test
    fun cancelUsesTheRpc() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        friends.cancel("r9")
        val request = take()
        assertEquals("POST", request.method)
        assertEquals("/rest/v1/rpc/cancel_friend_request", request.path)
        assertEquals("r9", JSONObject(request.body.readUtf8()).getString("request_id"))
    }

    @Test
    fun inviteLinkIsTheWebAppUrl() = runTest {
        json("\"AbCdEfGhIjKlMnOpQrStUvWxYz012345\"")
        assertEquals("https://q0ochen.github.io/DrinkWithMe/einladung/AbCdEfGhIjKlMnOpQrStUvWxYz012345", friends.createInviteLink())
        assertEquals("/rest/v1/rpc/create_invite_link", take().path)
    }

    @Test
    fun acceptInviteReturnsTheInviter() = runTest {
        json("""{"id":"anna-id","display_name":"Anna","avatar_url":null,"created_at":"2026-11-01T00:00:00Z"}""")
        assertEquals(UserProfile("anna-id", "Anna", null), friends.acceptInvite("token-xyz"))
        assertEquals("token-xyz", JSONObject(take().body.readUtf8()).getString("token"))
    }

    @Test
    fun rpcErrorsCarryMachineCodeAndGermanText() = runTest {
        json("""{"code":"P0001","details":null,"hint":null,"message":"invite_own"}""", code = 400)
        val error = assertFailsWith<SocialException.Server> { friends.acceptInvite("token-xyz") }
        assertEquals("invite_own", error.code)
        assertEquals(ErrorMessages.INVITE_OWN, error.userMessage)

        json("""{"code":"P0001","message":"rate_limited"}""", code = 400)
        val limited = assertFailsWith<SocialException.Server> { friends.sendRequestByEmail("a@b.de") }
        assertEquals(ErrorMessages.RATE_LIMITED, limited.userMessage)
    }

    @Test
    fun unauthorizedIsRetriedOnceWithAFreshToken() = runTest {
        json("""{"code":"PGRST301","message":"JWT expired"}""", code = 401)
        server.enqueue(MockResponse().setResponseCode(204))
        friends.removeFriend("f")
        assertEquals("Bearer token-1", take().getHeader("Authorization"))
        assertEquals("Bearer token-2", take().getHeader("Authorization"))
        assertEquals(1, tokens.refreshes)
    }

    @Test
    fun withoutUserNothingIsRequested() = runTest {
        me.id = null
        assertFailsWith<SocialException.NotSignedIn> { friends.friends() }
        assertFailsWith<SocialException.NotSignedIn> { feed.recentEvents(null) }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun feedExcludesOwnEventsAndUsesSince() = runTest {
        json("""[{"id":"e1","author_id":"anna-id","name":"Apfelschorle","icon":"apple_spritzer","category":"JUICE","volume_ml":500,"consumed_at":"2026-11-10T11:55:00Z"},
                 {"id":"e2","author_id":"bob-id","name":"Mate","icon":"mate","category":"NEW","volume_ml":330,"consumed_at":"2026-11-10T11:50:00Z"},
                 {"id":"kaputt","author_id":"bob-id"}]""")
        json("""[{"id":"anna-id","display_name":"Anna"}]""")

        val events = feed.recentEvents(Instant.parse("2026-11-10T10:00:00Z"), limit = 20)

        assertEquals(listOf("e1", "e2"), events.map { it.id })
        assertEquals("Anna", events[0].author.displayName)
        assertEquals(DrinkIcon.OTHER, events[1].icon)
        assertEquals(DrinkCategory.OTHER, events[1].category)
        val url = take().requestUrl!!
        assertEquals("neq.me-id", url.queryParameter("author_id"))
        assertEquals("gt.2026-11-10T10:00:00Z", url.queryParameter("consumed_at"))
        assertEquals("consumed_at.desc", url.queryParameter("order"))
        assertEquals("20", url.queryParameter("limit"))
        assertEquals("Apfelschorle", feed.event("e1")?.name, "aus dem Zwischenspeicher, ohne neue Anfrage")
        assertEquals(2, server.requestCount)
    }

    @Test
    fun publishInsertsOnlySharedFieldsThenNotifies() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))
        json("""{"recipients":2,"delivered":2,"failed":0,"removed":0}""")
        val drink = entry(id = "11111111-2222-3333-4444-555555555555", alcohol = 4.9, sugar = 0.3, at = Instant.parse("2026-11-10T11:59:00.250Z"))

        SupabaseEventPublisher(api).publish(drink)

        val insert = take()
        assertEquals("/rest/v1/drink_events", insert.path)
        assertEquals("return=minimal,resolution=ignore-duplicates", insert.getHeader("Prefer"))
        val body = JSONObject(insert.body.readUtf8())
        assertEquals(setOf("id", "name", "icon", "category", "volume_ml", "consumed_at"), body.keys().asSequence().toSet())
        assertEquals("apple_spritzer", body.getString("icon"))
        assertEquals("2026-11-10T11:59:00.250Z", body.getString("consumed_at"))
        val notify = take()
        assertEquals("/functions/v1/notify-friends", notify.path)
        assertEquals(drink.id, JSONObject(notify.body.readUtf8()).getString("eventId"))
    }

    @Test
    fun alreadyNotifiedOrRateLimitedIsNotAnError() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))
        json("""{"error":"event_not_found"}""", code = 404)
        SupabaseEventPublisher(api).publish(entry())
        server.enqueue(MockResponse().setResponseCode(201))
        json("""{"error":"rate_limited"}""", code = 429)
        SupabaseEventPublisher(api).publish(entry())
    }

    @Test
    fun otherNotifyErrorsAreReported() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))
        json("""{"error":"server_misconfigured"}""", code = 500)
        val error = assertFailsWith<SocialException.Server> { SupabaseEventPublisher(api).publish(entry()) }
        assertEquals(ErrorMessages.SERVER_MISCONFIGURED, error.userMessage)
    }

    @Test
    fun unpublishDeletesTheEvent() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        SupabaseEventPublisher(api).unpublish("e1")
        val request = take()
        assertEquals("DELETE", request.method)
        assertEquals("eq.e1", request.requestUrl!!.queryParameter("id"))
    }

    @Test
    fun pushSubscriptionIsRenewedByDeleteAndInsert() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(201))
        val endpoint = "https://ntfy.sh/upAbC123?up=1"
        SupabasePushSubscriptions(api).register(PushSubscription(endpoint, "p".repeat(87), "a".repeat(22)))

        val delete = take()
        assertEquals("DELETE", delete.method)
        assertEquals("eq.$endpoint", delete.requestUrl!!.queryParameter("endpoint"))
        val insert = JSONObject(take().body.readUtf8())
        assertEquals("unifiedpush", insert.getString("platform"))
        assertEquals(endpoint, insert.getString("endpoint"))
        assertFalse(insert.has("user_id"), "user_id setzt der Server")
    }

    @Test
    fun tooManySubscriptionsGetsAnExplanation() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        json("""{"code":"P0001","message":"too_many_subscriptions"}""", code = 400)
        val error = assertFailsWith<SocialException.Server> {
            SupabasePushSubscriptions(api).register(PushSubscription("https://ntfy.sh/x", "p".repeat(87), "a".repeat(22)))
        }
        assertEquals(ErrorMessages.TOO_MANY_SUBSCRIPTIONS, error.userMessage)
    }

    @Test
    fun deleteAccountCallsTheEdgeFunction() = runTest {
        json("""{"deleted":true}""")
        SupabaseAccountApi(api).deleteAccount()
        val request = take()
        assertEquals("POST", request.method)
        assertEquals("/functions/v1/delete-account", request.path)
    }
}

class ErrorMappingTest {
    @Test
    fun readsCodesFromAllServerKinds() {
        assertEquals("rate_limited", ErrorBody.code("""{"code":"P0001","message":"rate_limited"}"""))
        assertEquals("42501", ErrorBody.code("""{"code":"42501","message":"new row violates row-level security policy"}"""))
        assertEquals("event_not_found", ErrorBody.code("""{"error":"event_not_found"}"""))
        assertEquals("refresh_token_not_found", ErrorBody.code("""{"code":400,"error_code":"refresh_token_not_found","msg":"x"}"""))
        assertEquals("invalid_grant", ErrorBody.code("""{"error":"invalid_grant","error_description":"x"}"""))
        assertNull(ErrorBody.code("<html>Bad Gateway</html>"))
    }

    @Test
    fun everyDocumentedCodeHasAGermanText() {
        mapOf(
            "rate_limited" to ErrorMessages.RATE_LIMITED,
            "invite_invalid" to ErrorMessages.INVITE_INVALID,
            "invite_own" to ErrorMessages.INVITE_OWN,
            "request_not_found" to ErrorMessages.REQUEST_NOT_FOUND,
            "too_many_subscriptions" to ErrorMessages.TOO_MANY_SUBSCRIPTIONS,
            "too_many_events" to ErrorMessages.TOO_MANY_EVENTS,
            "not_authenticated" to ErrorMessages.SESSION_EXPIRED,
            "unauthorized" to ErrorMessages.SESSION_EXPIRED,
        ).forEach { (code, text) -> assertEquals(text, ErrorMessages.forCode(code, 400), code) }
        assertEquals(ErrorMessages.SERVER_UNAVAILABLE, ErrorMessages.forCode(null, 503))
        assertEquals(ErrorMessages.NETWORK, ErrorMessages.forThrowable(java.io.IOException()))
        assertEquals(ErrorMessages.GENERIC, ErrorMessages.forThrowable(IllegalStateException()))
    }
}

class SupabaseMappersTest {
    @Test
    fun eventNamesAreCleanedLikeTheServerExpects() {
        assertEquals("Apfelschorle", SupabaseMappers.eventName("  Apfelschorle \n"))
        assertEquals("A B", SupabaseMappers.eventName("A\u0007B"))
        val emoji = "🍺".repeat(45)
        val cut = SupabaseMappers.eventName(emoji)
        assertEquals(40, cut.codePointCount(0, cut.length), "nach Zeichen gezählt, kein halbes Emoji")
    }

    @Test
    fun rpcTextRejectsNonText() {
        assertEquals("abc", SupabaseMappers.rpcText("\"abc\""))
        assertFailsWith<SocialException.InvalidResponse> { SupabaseMappers.rpcText("null") }
        assertFailsWith<SocialException.InvalidResponse> { SupabaseMappers.array("{}") }
    }

    @Test
    fun invitingProfileAcceptsObjectOrList() {
        assertEquals("Anna", SupabaseMappers.invitingProfile("""[{"id":"a","display_name":"Anna"}]""").displayName)
        assertFailsWith<SocialException.InvalidResponse> { SupabaseMappers.invitingProfile("[]") }
    }
}
