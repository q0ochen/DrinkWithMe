package com.drinkwithme.core.social

import java.net.URI
import java.net.URISyntaxException

/**
 * Einladungs-Links: `drinkwithme://einladung/<token>` (App) und `<WEB_APP_URL>/einladung/<token>` (Web).
 * Das Token ist base64url mit mindestens 128 Bit (≥ 22 Zeichen).
 */
object InviteLinks {
    const val APP_SCHEME = "drinkwithme"
    const val PATH_SEGMENT = "einladung"

    private val TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]{22,128}$")

    fun isValidToken(token: String): Boolean = TOKEN_PATTERN.matches(token)

    /** Link, der die App direkt öffnet. */
    fun appLink(token: String): String = "$APP_SCHEME://$PATH_SEGMENT/${requireToken(token)}"

    /** Link auf die Web-App, z. B. `https://q0ochen.github.io/DrinkWithMe/einladung/<token>`. */
    fun webLink(webAppUrl: String, token: String): String = "${webAppUrl.trimEnd('/')}/$PATH_SEGMENT/${requireToken(token)}"

    /**
     * Liest das Token aus einem App-Link, einem https-Link der Web-App oder einem nackten Token.
     * Gibt `null` zurück, wenn die Eingabe keine gültige Einladung ist (z. B. http statt https).
     */
    fun parseToken(input: String): String? {
        val text = input.trim()
        if (isValidToken(text)) return text
        val uri = try {
            URI(text)
        } catch (ignored: URISyntaxException) {
            return null
        }
        val segments = (uri.rawPath ?: "").split('/').filter { it.isNotEmpty() }
        val token = when (uri.scheme?.lowercase()) {
            APP_SCHEME -> segments.singleOrNull()?.takeIf { uri.host == PATH_SEGMENT }
            "https" -> segments.takeIf { it.size >= 2 && it[it.size - 2] == PATH_SEGMENT }?.last()
            else -> null
        }
        return token?.takeIf(::isValidToken)
    }

    private fun requireToken(token: String): String {
        require(isValidToken(token)) { "Ungültiges Einladungs-Token." }
        return token
    }
}
