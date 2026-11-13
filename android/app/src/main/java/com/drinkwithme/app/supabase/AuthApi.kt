package com.drinkwithme.app.supabase

import com.drinkwithme.app.data.intOrNull
import com.drinkwithme.app.data.longOrNull
import com.drinkwithme.app.data.nonBlankStringOrNull
import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.time.Clock
import java.time.Instant

/** Angemeldeter Nutzer laut Supabase Auth (Name und Bild aus den Google-Metadaten). */
data class AuthUser(val id: String, val email: String?, val displayName: String?, val avatarUrl: String?)

/** Sitzung nach Anmeldung oder Erneuerung. Das Access-Token lebt nur im Speicher. */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
    val user: AuthUser,
)

/** Supabase Auth (GoTrue) – nur der Google-Anbieter, PKCE im Browser (Vertrag, Abschnitt 2.4). */
interface AuthApi {
    /** URL für den Browser (Custom Tab): `/auth/v1/authorize?provider=google&…&code_challenge_method=s256`. */
    fun authorizeUrl(codeChallenge: String): String

    /** `POST /auth/v1/token?grant_type=pkce` mit Code und Verifier. */
    suspend fun exchangeCode(authCode: String, codeVerifier: String): AuthTokens

    /** `POST /auth/v1/token?grant_type=refresh_token`. Das Refresh-Token wird dabei ersetzt (Rotation). */
    suspend fun refresh(refreshToken: String): AuthTokens

    /** `POST /auth/v1/logout?scope=local` – beendet nur diese Sitzung. */
    suspend fun signOut(accessToken: String)
}

class SupabaseAuthApi(
    private val http: SupabaseHttp,
    private val clock: Clock = Clock.systemUTC(),
) : AuthApi {

    override fun authorizeUrl(codeChallenge: String): String = http.url(
        "auth/v1/authorize",
        listOf(
            "provider" to "google",
            "redirect_to" to REDIRECT_URL,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "s256",
        ),
    ).toString()

    override suspend fun exchangeCode(authCode: String, codeVerifier: String): AuthTokens {
        val body = JSONObject().put("auth_code", authCode).put("code_verifier", codeVerifier).toString()
        val response = http.send(HttpMethod.POST, http.url("auth/v1/token", listOf("grant_type" to "pkce")), body)
        return parseTokens(response.requireSuccess().body)
    }

    override suspend fun refresh(refreshToken: String): AuthTokens {
        val body = JSONObject().put("refresh_token", refreshToken).toString()
        val response = http.send(HttpMethod.POST, http.url("auth/v1/token", listOf("grant_type" to "refresh_token")), body)
        return parseTokens(response.requireSuccess().body)
    }

    override suspend fun signOut(accessToken: String) {
        val response = http.send(
            HttpMethod.POST,
            http.url("auth/v1/logout", listOf("scope" to "local")),
            jsonBody = "{}",
            accessToken = accessToken,
        )
        // 401/403/404: Sitzung ist ohnehin schon ungültig – lokal abmelden reicht.
        if (!response.isSuccessful && response.status !in setOf(401, 403, 404)) response.requireSuccess()
    }

    internal fun parseTokens(body: String): AuthTokens = AuthResponses.parseTokens(body, clock.instant())

    companion object {
        /** In Supabase als erlaubte Redirect-URL eingetragen (Vertrag, Abschnitt 2.4). */
        const val REDIRECT_URL = "drinkwithme://auth-callback"
    }
}

/** Reine Parser für Antworten von Supabase Auth. */
object AuthResponses {
    fun parseTokens(body: String, now: Instant): AuthTokens {
        val json = try {
            JSONObject(body)
        } catch (error: JSONException) {
            throw SocialException.InvalidResponse("Token-Antwort ist kein JSON")
        }
        val accessToken = json.nonBlankStringOrNull("access_token")
            ?: throw SocialException.InvalidResponse("access_token fehlt")
        val refreshToken = json.nonBlankStringOrNull("refresh_token")
            ?: throw SocialException.InvalidResponse("refresh_token fehlt")
        val expiresAt = json.longOrNull("expires_at")?.let { Instant.ofEpochSecond(it) }
            ?: now.plusSeconds((json.intOrNull("expires_in") ?: DEFAULT_LIFETIME_SECONDS).toLong())
        val user = json.optJSONObject("user")?.let(::parseUser)
            ?: throw SocialException.InvalidResponse("user fehlt")
        return AuthTokens(accessToken, refreshToken, expiresAt, user)
    }

    fun parseUser(json: JSONObject): AuthUser {
        val id = json.nonBlankStringOrNull("id") ?: throw SocialException.InvalidResponse("user.id fehlt")
        val metadata = json.optJSONObject("user_metadata")
        val name = metadata?.nonBlankStringOrNull("full_name") ?: metadata?.nonBlankStringOrNull("name")
        val avatar = (metadata?.nonBlankStringOrNull("avatar_url") ?: metadata?.nonBlankStringOrNull("picture"))
            ?.takeIf { it.startsWith("https://") }
        return AuthUser(id = id, email = json.nonBlankStringOrNull("email"), displayName = name?.trim(), avatarUrl = avatar)
    }

    private const val DEFAULT_LIFETIME_SECONDS = 3600
}

/** Ergebnis der Rückkehr aus dem Browser (`drinkwithme://auth-callback?…`). */
sealed interface AuthCallback {
    data class Code(val code: String) : AuthCallback

    /** Supabase/Google meldet einen Fehler, z. B. `access_denied` (abgebrochen). */
    data class Failure(val error: String, val description: String?) : AuthCallback

    companion object {
        /** `null`, wenn [uri] kein Anmelde-Rücksprung ist. Fehler können in Query oder Fragment stehen. */
        fun parse(uri: String): AuthCallback? {
            val parsed = try {
                URI(uri)
            } catch (error: URISyntaxException) {
                return null
            }
            if (parsed.scheme?.lowercase() != "drinkwithme" || parsed.host?.lowercase() != "auth-callback") return null
            val params = parameters(parsed.rawQuery) + parameters(parsed.rawFragment)
            params["code"]?.takeIf { it.isNotBlank() }?.let { return Code(it) }
            val error = params["error"] ?: params["error_code"] ?: "unknown"
            return Failure(error, params["error_description"])
        }

        private fun parameters(raw: String?): Map<String, String> {
            if (raw.isNullOrEmpty()) return emptyMap()
            return raw.split('&').mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) return@mapNotNull null
                decode(part.substring(0, index)) to decode(part.substring(index + 1))
            }.toMap()
        }

        private fun decode(text: String): String = try {
            URLDecoder.decode(text, "UTF-8")
        } catch (error: IllegalArgumentException) {
            text
        }
    }
}
