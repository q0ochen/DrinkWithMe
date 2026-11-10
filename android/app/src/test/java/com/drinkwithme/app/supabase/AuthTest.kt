package com.drinkwithme.app.supabase

import com.drinkwithme.app.MutableClock
import com.drinkwithme.app.config.ServerConfig
import com.drinkwithme.app.data.InMemoryKeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PkceTest {
    @Test
    fun challengeMatchesRfc7636Example() {
        // RFC 7636, Anhang B.
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test
    fun verifiersAreLongRandomAndUrlSafe() {
        val random = SecureRandom()
        val first = Pkce.newVerifier(random)
        val second = Pkce.newVerifier(random)
        assertEquals(86, first.length)
        assertTrue(Pkce.isValidVerifier(first))
        assertNotEquals(first, second)
        assertTrue(Regex("^[A-Za-z0-9_-]{43}$").matches(Pkce.challenge(first)))
    }

    @Test
    fun rejectsInvalidVerifier() {
        assertFailsWith<IllegalArgumentException> { Pkce.challenge("zu kurz") }
    }
}

class AuthCallbackTest {
    @Test
    fun readsCodeFromQuery() {
        assertEquals(AuthCallback.Code("abc-123"), AuthCallback.parse("drinkwithme://auth-callback?code=abc-123"))
    }

    @Test
    fun readsErrorsFromQueryOrFragment() {
        assertEquals(
            AuthCallback.Failure("access_denied", "Der Nutzer hat abgebrochen"),
            AuthCallback.parse("drinkwithme://auth-callback?error=access_denied&error_description=Der+Nutzer+hat+abgebrochen"),
        )
        assertEquals(
            AuthCallback.Failure("server_error", null),
            AuthCallback.parse("drinkwithme://auth-callback#error=server_error"),
        )
    }

    @Test
    fun ignoresOtherLinks() {
        assertNull(AuthCallback.parse("drinkwithme://einladung/abc"))
        assertNull(AuthCallback.parse("https://example.org/auth-callback?code=1"))
        assertNull(AuthCallback.parse("::kaputt::"))
    }
}

class SupabaseAuthApiTest {
    private val server = MockWebServer()
    private lateinit var api: SupabaseAuthApi
    private val clock = MutableClock()

    @Before
    fun setUp() {
        server.start()
        val config = ServerConfig(server.url("/").toString().trimEnd('/'), "anon-key", null, ServerConfig.DEFAULT_WEB_APP_URL)
        api = SupabaseAuthApi(SupabaseHttp(OkHttpClient(), config, Dispatchers.IO), clock)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun authorizeUrlUsesGooglePkceAndAppRedirect() {
        val url = api.authorizeUrl("CHALLENGE").toHttpUrl()
        assertEquals("/auth/v1/authorize", url.encodedPath)
        assertEquals("google", url.queryParameter("provider"))
        assertEquals("drinkwithme://auth-callback", url.queryParameter("redirect_to"))
        assertEquals("CHALLENGE", url.queryParameter("code_challenge"))
        assertEquals("s256", url.queryParameter("code_challenge_method"))
    }

    @Test
    fun exchangesCodeWithVerifier() = runTest {
        server.enqueue(MockResponse().setBody(tokenJson(refresh = "r1", expiresAt = 1_794_400_000)))
        val tokens = api.exchangeCode("the-code", "the-verifier")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/auth/v1/token?grant_type=pkce", request.path)
        assertEquals("anon-key", request.getHeader("apikey"))
        assertNull(request.getHeader("Authorization"), "Auth-Aufrufe ohne Bearer-Token")
        val body = JSONObject(request.body.readUtf8())
        assertEquals("the-code", body.getString("auth_code"))
        assertEquals("the-verifier", body.getString("code_verifier"))

        assertEquals("access", tokens.accessToken)
        assertEquals("r1", tokens.refreshToken)
        assertEquals(Instant.ofEpochSecond(1_794_400_000), tokens.expiresAt)
        assertEquals(AuthUser("user-1", "anna@example.org", "Anna Beispiel", "https://img.example.org/a.png"), tokens.user)
    }

    @Test
    fun refreshUsesRefreshGrantAndFallsBackToExpiresIn() = runTest {
        server.enqueue(MockResponse().setBody(tokenJson(refresh = "r2", expiresAt = null)))
        val tokens = api.refresh("r1")
        val request = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=refresh_token", request.path)
        assertEquals("r1", JSONObject(request.body.readUtf8()).getString("refresh_token"))
        assertEquals(clock.now.plusSeconds(3600), tokens.expiresAt)
    }

    @Test
    fun goTrueErrorsBecomeServerExceptions() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":400,"error_code":"refresh_token_not_found","msg":"Invalid"}"""))
        val error = assertFailsWith<SocialException.Server> { api.refresh("alt") }
        assertEquals(400, error.status)
        assertEquals("refresh_token_not_found", error.code)
        assertEquals(ErrorMessages.SESSION_EXPIRED, error.userMessage)
    }

    @Test
    fun signOutIsLocalScopeAndToleratesExpiredSessions() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        api.signOut("token")
        val request = server.takeRequest()
        assertEquals("/auth/v1/logout?scope=local", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
    }

    @Test
    fun networkErrorsAreReportedAsNetwork() = runTest {
        server.shutdown()
        assertFailsWith<SocialException.Network> { api.refresh("r") }
    }

    private fun tokenJson(refresh: String, expiresAt: Long?): String = JSONObject()
        .put("access_token", "access")
        .put("token_type", "bearer")
        .put("expires_in", 3600)
        .apply { if (expiresAt != null) put("expires_at", expiresAt) }
        .put("refresh_token", refresh)
        .put(
            "user",
            JSONObject().put("id", "user-1").put("email", "anna@example.org").put(
                "user_metadata",
                JSONObject().put("full_name", "Anna Beispiel").put("picture", "https://img.example.org/a.png"),
            ),
        ).toString()
}

/** Auth-Fake: liefert Tokens nach Drehbuch und merkt sich die Aufrufe. */
private class FakeAuthApi(private val clock: MutableClock) : AuthApi {
    val refreshed = mutableListOf<String>()
    var refreshError: SocialException? = null
    var signedOut = mutableListOf<String>()
    private var counter = 0

    override fun authorizeUrl(codeChallenge: String): String = "https://example.supabase.co/auth/v1/authorize?code_challenge=$codeChallenge"

    override suspend fun exchangeCode(authCode: String, codeVerifier: String): AuthTokens {
        check(codeVerifier.isNotEmpty())
        return tokens()
    }

    override suspend fun refresh(refreshToken: String): AuthTokens {
        refreshed += refreshToken
        refreshError?.let { throw it }
        return tokens()
    }

    override suspend fun signOut(accessToken: String) {
        signedOut += accessToken
    }

    private fun tokens(): AuthTokens {
        counter++
        return AuthTokens("access-$counter", "refresh-$counter", clock.now.plus(Duration.ofHours(1)), AuthUser("user-1", "anna@example.org", "Anna", null))
    }
}

/** „Verschlüsselt“ sichtbar, damit der Test prüfen kann, dass nie Klartext gespeichert wird. */
private class ReversingCipher : TokenCipher {
    override fun encrypt(plain: String): String = "enc:" + plain.reversed()
    override fun decrypt(encrypted: String): String? = if (encrypted.startsWith("enc:")) encrypted.removePrefix("enc:").reversed() else null
}

class SupabaseSessionTest {
    private val clock = MutableClock()
    private val auth = FakeAuthApi(clock)
    private val kv = InMemoryKeyValueStore()
    private val store = EncryptedSessionStore(kv, ReversingCipher())

    private fun session() = SupabaseSession(auth, store, kv, clock)

    @Test
    fun signInStoresOnlyEncryptedRefreshTokenAndProfile() = runTest {
        val session = session()
        val url = session.beginSignIn()
        assertTrue(url.contains("code_challenge="))
        assertTrue(kv.getString("pkce_verifier")!!.length >= 43)

        val user = session.completeSignIn("code")
        assertEquals("Anna", user.displayName)
        assertEquals("user-1", session.currentUserId())
        assertNull(kv.getString("pkce_verifier"), "Verifier wird nach Gebrauch gelöscht")
        val stored = kv.getString("session")!!
        assertTrue(stored.contains("enc:"))
        assertTrue(!stored.contains("refresh-1"), "kein Klartext-Token")
        assertEquals("access-1", session.accessToken())
    }

    @Test
    fun completingWithoutStartedSignInFails() = runTest {
        val error = assertFailsWith<SocialException.NotSignedIn> { session().completeSignIn("code") }
        assertEquals(ErrorMessages.SIGN_IN_EXPIRED, error.userMessage)
    }

    @Test
    fun restoredSessionRefreshesAndRotatesTheRefreshToken() = runTest {
        session().apply { beginSignIn(); completeSignIn("code") }
        val restarted = session()
        assertEquals("user-1", restarted.currentUser.value?.id, "angemeldet ohne Netz")

        assertEquals("access-2", restarted.accessToken())
        assertEquals(listOf("refresh-1"), auth.refreshed)
        assertEquals("refresh-2", store.load()!!.refreshToken, "neues Refresh-Token sofort gespeichert")
        assertEquals("access-2", restarted.accessToken(), "noch gültig – kein weiterer Aufruf")

        clock.advance(Duration.ofMinutes(59).plusSeconds(30))
        assertEquals("access-3", restarted.accessToken(), "kurz vor Ablauf wird erneuert")
    }

    @Test
    fun rejectedRefreshTokenSignsOut() = runTest {
        session().apply { beginSignIn(); completeSignIn("code") }
        val restarted = session()
        auth.refreshError = SocialException.Server("refresh_token_not_found", 400)
        val error = assertFailsWith<SocialException.NotSignedIn> { restarted.accessToken() }
        assertEquals(ErrorMessages.SESSION_EXPIRED, error.userMessage)
        assertNull(restarted.currentUser.value)
        assertNull(store.load())
    }

    @Test
    fun networkProblemsKeepTheSession() = runTest {
        session().apply { beginSignIn(); completeSignIn("code") }
        val restarted = session()
        auth.refreshError = SocialException.Network(java.io.IOException("offline"))
        assertFailsWith<SocialException.Network> { restarted.accessToken() }
        assertEquals("user-1", restarted.currentUser.value?.id)
    }

    @Test
    fun refreshAfterUnauthorizedReusesAFreshToken() = runTest {
        val session = session().apply { beginSignIn(); completeSignIn("code") }
        assertEquals("access-2", session.refreshAfterUnauthorized("access-1"))
        assertEquals("access-2", session.refreshAfterUnauthorized("access-1"), "schon erneuert")
        assertEquals(1, auth.refreshed.size)
    }

    @Test
    fun profileFromTableReplacesGoogleName() = runTest {
        val session = session().apply { beginSignIn(); completeSignIn("code") }
        session.updateProfile(com.drinkwithme.core.social.UserProfile("user-1", "Anni", "https://x.example/a.png"))
        assertEquals("Anni", session().currentUser.value?.displayName)
        session.updateProfile(com.drinkwithme.core.social.UserProfile("fremd", "Bob", null))
        assertEquals("Anni", session.currentUser.value?.displayName)
    }

    @Test
    fun signOutCallsServerAndClearsEverything() = runTest {
        val session = session().apply { beginSignIn(); completeSignIn("code") }
        session.signOut()
        assertEquals(listOf("access-1"), auth.signedOut)
        assertNull(session.currentUser.value)
        assertNull(session.currentEmail.value)
        assertNull(store.load())
        assertIs<SocialException.NotSignedIn>(runCatching { session.accessToken() }.exceptionOrNull())
    }

    @Test
    fun signOutAfterRestartFetchesATokenFirst() = runTest {
        session().apply { beginSignIn(); completeSignIn("code") }
        val restarted = session()
        restarted.signOut()
        assertEquals(listOf("refresh-1"), auth.refreshed)
        assertEquals(listOf("access-2"), auth.signedOut)
        assertNull(store.load())
    }

    @Test
    fun signOutWorksOffline() = runTest {
        session().apply { beginSignIn(); completeSignIn("code") }
        val restarted = session()
        auth.refreshError = SocialException.Network(java.io.IOException("offline"))
        restarted.signOut()
        assertNull(restarted.currentUser.value)
        assertNull(store.load())
    }

    @Test
    fun undecryptableStoredSessionCountsAsSignedOut() {
        kv.putString("session", """{"refreshTokenEncrypted":"v1:fremder-schluessel","userId":"user-1"}""")
        assertNull(session().currentUser.value)
        assertNull(kv.getString("session"))
    }

    @Test
    fun keystoreFailureWhileLoadingNeverCrashesAndKeepsTheEntry() = runTest {
        session().apply { beginSignIn(); completeSignIn("code") }
        val stored = kv.getString("session")
        val busy = SupabaseSession(auth, EncryptedSessionStore(kv, ThrowingCipher(KeystoreUnavailableException(java.security.ProviderException("busy")))), kv, clock)
        assertNull(busy.currentUser.value, "diesmal abgemeldet")
        assertEquals(stored, kv.getString("session"), "Eintrag bleibt für den nächsten Start")

        val broken = SupabaseSession(auth, EncryptedSessionStore(kv, ThrowingCipher(IllegalStateException("kaputt"))), kv, clock)
        assertNull(broken.currentUser.value, "auch unerwartete Laufzeitfehler führen nicht zum Absturz")
    }

    @Test
    fun storedSessionIsReadLazilyAndStoreIoRunsOnTheIoDispatcher() = runTest {
        val threads = mutableListOf<String>()
        val recordingKv = RecordingKeyValueStore(kv, threads)
        val io = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "session-io") }.asCoroutineDispatcher()
        try {
            val session = SupabaseSession(auth, EncryptedSessionStore(recordingKv, ReversingCipher()), recordingKv, clock, io = io)
            assertTrue(threads.isEmpty(), "der Konstruktor (Application.onCreate) liest nichts")
            session.warmUp()
            assertEquals(listOf("session-io:get:session"), threads)

            session.beginSignIn()
            session.completeSignIn("code")
            session.accessToken()
            clock.advance(Duration.ofHours(2))
            session.accessToken()
            session.signOut()
            assertTrue(threads.size > 4)
            assertTrue(threads.all { it.startsWith("session-io:") }, threads.toString())
        } finally {
            io.close()
        }
    }

    @Test
    fun rejectedRefreshTokenReportsTheLostSession() = runTest {
        session().apply { beginSignIn(); completeSignIn("code") }
        val restarted = session()
        var lost = 0
        restarted.sessionLostListener = { lost++ }
        auth.refreshError = SocialException.Network(java.io.IOException("offline"))
        assertFailsWith<SocialException.Network> { restarted.accessToken() }
        assertEquals(0, lost, "Netzfehler beenden die Sitzung nicht")
        auth.refreshError = SocialException.Server("refresh_token_not_found", 400)
        assertFailsWith<SocialException.NotSignedIn> { restarted.accessToken() }
        assertEquals(1, lost)
        assertNull(restarted.currentUser.value)
    }
}

private class ThrowingCipher(private val failure: Exception) : TokenCipher {
    override fun encrypt(plain: String): String = "enc:$plain"
    override fun decrypt(encrypted: String): String? = throw failure
}

/** Merkt sich, auf welchem Thread gelesen und geschrieben wird. */
private class RecordingKeyValueStore(private val delegate: InMemoryKeyValueStore, private val threads: MutableList<String>) :
    com.drinkwithme.app.data.KeyValueStore {
    override fun getString(key: String): String? {
        record("get:$key")
        return delegate.getString(key)
    }

    override fun putString(key: String, value: String?) {
        record("put:$key")
        delegate.putString(key, value)
    }

    private fun record(what: String) = synchronized(threads) { threads += "${Thread.currentThread().name}:$what" }
}
