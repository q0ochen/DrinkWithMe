package com.drinkwithme.app.supabase

import com.drinkwithme.app.config.ServerConfig
import com.drinkwithme.app.data.intOrNull
import com.drinkwithme.app.data.nonBlankStringOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Antwort eines HTTP-Aufrufs (Körper bereits gelesen). */
data class HttpResponse(val status: Int, val body: String) {
    val isSuccessful: Boolean get() = status in 200..299
}

/** HTTP-Methoden, die die App gegenüber Supabase nutzt. */
enum class HttpMethod { GET, POST, PATCH, DELETE }

/**
 * Dünne Schicht über OkHttp für alle Supabase-Aufrufe (Auth, PostgREST, Edge Functions): baut URLs und Header
 * (`apikey`, optional `Authorization: Bearer <Nutzer-JWT>`) und liefert Status und Körper.
 * Netzwerkfehler werden zu [SocialException.Network].
 */
class SupabaseHttp(
    private val client: OkHttpClient,
    config: ServerConfig,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val anonKey = config.anonKey
    val baseUrl: HttpUrl = config.supabaseUrl.toHttpUrl()

    /** URL zu einem Pfad wie `rest/v1/profiles` mit Query-Parametern (werden korrekt kodiert). */
    fun url(path: String, query: List<Pair<String, String>> = emptyList()): HttpUrl =
        baseUrl.newBuilder()
            .addPathSegments(path.trim('/'))
            .apply { query.forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()

    suspend fun send(
        method: HttpMethod,
        url: HttpUrl,
        jsonBody: String? = null,
        accessToken: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse {
        val builder = Request.Builder()
            .url(url)
            .header("apikey", anonKey)
            .header("Accept", "application/json")
        accessToken?.let { builder.header("Authorization", "Bearer $it") }
        headers.forEach { (name, value) -> builder.header(name, value) }
        val body = (jsonBody ?: if (method == HttpMethod.GET || method == HttpMethod.DELETE) null else "{}")
            ?.toRequestBody(JSON)
        builder.method(method.name, body)
        val request = builder.build()
        return withContext(ioDispatcher) {
            try {
                client.newCall(request).execute().use { response ->
                    HttpResponse(response.code, response.body?.string().orEmpty())
                }
            } catch (error: IOException) {
                throw SocialException.Network(error)
            }
        }
    }

    companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        /** OkHttp mit kurzen Zeitlimits – die App wartet nie lange auf das Netz. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

/** Liest den maschinenlesbaren Fehlercode aus den Fehlerkörpern von PostgREST, Edge Functions und GoTrue. */
object ErrorBody {
    /**
     * - PostgREST/RPC: `{ "code": "P0001", "message": "rate_limited" }` → „rate_limited“; andere PostgREST-Fehler →
     *   ihr `code` (z. B. „42501“).
     * - Edge Functions: `{ "error": "event_not_found" }`.
     * - GoTrue (Auth): `{ "error_code": "refresh_token_not_found", … }` oder `{ "error": "invalid_grant", … }`.
     */
    fun code(body: String): String? {
        val json = try {
            JSONObject(body)
        } catch (error: JSONException) {
            return null
        }
        json.nonBlankStringOrNull("error_code")?.let { return it }
        val code = json.nonBlankStringOrNull("code") ?: json.intOrNull("code")?.toString()
        if (code == RAISED_EXCEPTION) return json.nonBlankStringOrNull("message")
        json.nonBlankStringOrNull("error")?.let { return it }
        return code
    }

    /** SQLSTATE für `raise exception … using errcode = 'P0001'` in den RPCs. */
    const val RAISED_EXCEPTION = "P0001"
}

/** Wirft [SocialException.Server], wenn die Antwort kein Erfolg ist. */
fun HttpResponse.requireSuccess(): HttpResponse {
    if (!isSuccessful) throw SocialException.Server(ErrorBody.code(body), status)
    return this
}
