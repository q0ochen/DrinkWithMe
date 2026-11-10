package com.drinkwithme.app.account

import com.drinkwithme.app.MutableClock
import com.drinkwithme.app.config.ServerConfig
import com.drinkwithme.app.data.InMemoryKeyValueStore
import com.drinkwithme.app.push.PushEndpointStore
import com.drinkwithme.app.push.PushSubscriptionSync
import com.drinkwithme.app.push.UploadedSubscription
import com.drinkwithme.app.supabase.AccountRemote
import com.drinkwithme.app.supabase.AuthCallback
import com.drinkwithme.app.supabase.EncryptedSessionStore
import com.drinkwithme.app.supabase.ErrorMessages
import com.drinkwithme.app.supabase.ProfileLookup
import com.drinkwithme.app.supabase.PushSubscription
import com.drinkwithme.app.supabase.PushSubscriptionRemote
import com.drinkwithme.app.supabase.SocialException
import com.drinkwithme.app.supabase.SupabaseApi
import com.drinkwithme.app.supabase.SupabaseAuthApi
import com.drinkwithme.app.supabase.SupabaseHttp
import com.drinkwithme.app.supabase.SupabaseSession
import com.drinkwithme.app.supabase.TokenCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object PlainCipher : TokenCipher {
    override fun encrypt(plain: String) = "x$plain"
    override fun decrypt(encrypted: String) = encrypted.removePrefix("x")
}

/** Zusammenspiel echter Teile (Auth über MockWebServer, Sitzung, Profil, Push-Abo) beim An- und Abmelden. */
class SupabaseAccountControllerTest {
    private val server = MockWebServer()
    private val events = mutableListOf<String>()
    private val endpoints = PushEndpointStore(InMemoryKeyValueStore())
    private lateinit var session: SupabaseSession
    private lateinit var controller: SupabaseAccountController
    private var deleteFailure: SocialException? = null

    private val remote = object : PushSubscriptionRemote {
        override suspend fun register(subscription: PushSubscription) { events += "register" }
        override suspend fun unregister(endpoint: String) { events += "unregister:${session.currentUserId()}" }
    }

    @Before
    fun setUp() {
        server.start()
        val config = ServerConfig(server.url("/").toString().trimEnd('/'), "anon", null, ServerConfig.DEFAULT_WEB_APP_URL)
        val http = SupabaseHttp(OkHttpClient(), config, Dispatchers.IO)
        val kv = InMemoryKeyValueStore()
        session = SupabaseSession(SupabaseAuthApi(http, MutableClock()), EncryptedSessionStore(kv, PlainCipher), kv, MutableClock())
        val api = SupabaseApi(http, session)
        controller = SupabaseAccountController(
            session = session,
            profiles = ProfileLookup(api),
            account = object : AccountRemote {
                override suspend fun deleteAccount() {
                    deleteFailure?.let { throw it }
                    events += "delete"
                }
            },
            pushSync = PushSubscriptionSync(endpoints, remote, session) {},
            clearAccountData = { events += "clear" },
            onSessionChanged = { events += "changed" },
        )
    }

    @After
    fun tearDown() = server.shutdown()

    private fun signInResponses() {
        server.enqueue(
            MockResponse().setBody(
                """{"access_token":"a1","refresh_token":"r1","expires_in":3600,
                    "user":{"id":"me-id","email":"me@example.org","user_metadata":{"name":"Google-Name"}}}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""[{"id":"me-id","display_name":"Profilname","avatar_url":null}]"""))
    }

    @Test
    fun signInLoadsProfileFromTable() = runTest {
        signInResponses()
        controller.signInUrl()
        val user = controller.completeSignIn(AuthCallback.Code("code"))
        assertEquals("Profilname", user.displayName)
        assertEquals("me@example.org", controller.currentEmail.value)
        assertEquals(listOf("changed"), events)
        server.takeRequest(5, TimeUnit.SECONDS)
        assertEquals("in.(me-id)", server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!.queryParameter("id"))
    }

    @Test
    fun lostSessionRunsTheSameCleanupAsSignOut() = runTest {
        signInResponses()
        controller.signInUrl()
        controller.completeSignIn(AuthCallback.Code("code"))
        server.takeRequest(5, TimeUnit.SECONDS)
        server.takeRequest(5, TimeUnit.SECONDS)
        // Das Refresh-Token wurde woanders widerrufen (z. B. Konto im Browser gelöscht).
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error_code":"refresh_token_not_found"}"""))

        assertFailsWith<SocialException.NotSignedIn> { session.refreshAfterUnauthorized("a1") }

        assertEquals(listOf("changed", "clear", "changed"), events)
        assertNull(controller.currentUser.value)
    }

    @Test
    fun cancelledSignInHasFriendlyMessage() = runTest {
        val error = assertFailsWith<SocialException.NotSignedIn> {
            controller.completeSignIn(AuthCallback.Failure("access_denied", null))
        }
        assertEquals(ErrorMessages.SIGN_IN_CANCELLED, error.userMessage)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun signOutRemovesPushSubscriptionWhileStillSignedIn() = runTest {
        signInResponses()
        controller.signInUrl()
        controller.completeSignIn(AuthCallback.Code("code"))
        endpoints.uploaded = UploadedSubscription("https://ntfy.sh/up", "me-id")
        server.enqueue(MockResponse().setResponseCode(204))

        controller.signOut()

        assertEquals(listOf("changed", "unregister:me-id", "clear", "changed"), events)
        assertNull(controller.currentUser.value)
        assertNull(endpoints.uploaded)
    }

    @Test
    fun deleteAccountSignsOutLocallyOnlyAfterSuccess() = runTest {
        signInResponses()
        controller.signInUrl()
        controller.completeSignIn(AuthCallback.Code("code"))
        deleteFailure = SocialException.Network(java.io.IOException())
        assertFailsWith<SocialException.Network> { controller.deleteAccount() }
        assertEquals("me-id", controller.currentUser.value?.id, "bei Fehler bleibt man angemeldet")

        deleteFailure = null
        controller.deleteAccount()
        assertNull(controller.currentUser.value)
        assertTrue(events.containsAll(listOf("delete", "clear")))
    }

    @Test
    fun unconfiguredControllerExplains() = runTest {
        val unconfigured = UnconfiguredAccountController()
        assertEquals(false, unconfigured.isConfigured)
        assertNull(unconfigured.currentUser.value)
        assertFailsWith<SocialException.NotConfigured> { unconfigured.signInUrl() }
        assertFailsWith<SocialException.NotConfigured> { unconfigured.deleteAccount() }
    }
}
