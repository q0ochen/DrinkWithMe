package com.drinkwithme.app.supabase

/**
 * Aufrufe im Namen des angemeldeten Nutzers (PostgREST `rest/v1/…`, Edge Functions `functions/v1/…`).
 * Holt das Access-Token, versucht es nach einer 401-Antwort genau einmal mit einem erneuerten Token und wirft
 * bei Fehlern [SocialException.Server] mit dem maschinenlesbaren Code.
 */
class SupabaseApi(
    private val http: SupabaseHttp,
    private val tokens: AccessTokenSource,
) {
    suspend fun get(path: String, query: List<Pair<String, String>> = emptyList()): HttpResponse =
        call(HttpMethod.GET, path, query, body = null)

    suspend fun post(
        path: String,
        body: String,
        query: List<Pair<String, String>> = emptyList(),
        prefer: String? = null,
    ): HttpResponse = call(HttpMethod.POST, path, query, body, prefer)

    suspend fun delete(path: String, query: List<Pair<String, String>>): HttpResponse =
        call(HttpMethod.DELETE, path, query, body = null)

    /** RPC `POST rest/v1/rpc/<name>` mit JSON-Parametern. */
    suspend fun rpc(name: String, parameters: String = "{}"): HttpResponse = post("rest/v1/rpc/$name", parameters)

    /** Edge Function `POST functions/v1/<name>`. */
    suspend fun function(name: String, body: String = "{}"): HttpResponse = post("functions/v1/$name", body)

    private suspend fun call(
        method: HttpMethod,
        path: String,
        query: List<Pair<String, String>>,
        body: String?,
        prefer: String? = null,
    ): HttpResponse {
        val url = http.url(path, query)
        val headers = prefer?.let { mapOf("Prefer" to it) } ?: emptyMap()
        val token = tokens.accessToken()
        var response = http.send(method, url, body, token, headers)
        if (response.status == 401) {
            val fresh = tokens.refreshAfterUnauthorized(token)
            response = http.send(method, url, body, fresh, headers)
        }
        return response.requireSuccess()
    }
}
