package com.drinkwithme.app.config

/**
 * Öffentliche Server-Werte aus `android/app/supabase.properties` (über BuildConfig). Nichts davon ist geheim.
 *
 * @property vapidPublicKey öffentlicher VAPID-Schlüssel (base64url, 87 Zeichen) oder `null` – dann gibt es keine
 *   Push-Nachrichten, nur den Abruf alle 15 Minuten.
 */
data class ServerConfig(
    val supabaseUrl: String,
    val anonKey: String,
    val vapidPublicKey: String?,
    val webAppUrl: String,
) {
    companion object {
        /** Standard-Adresse der Web-App (Vertrag, Abschnitt 4), falls `webAppUrl` fehlt. */
        const val DEFAULT_WEB_APP_URL = "https://q0ochen.github.io/DrinkWithMe"

        private val VAPID_PATTERN = Regex("^[A-Za-z0-9_-]{87}$")

        /**
         * Liest die Werte; `null`, wenn der Server nicht eingerichtet ist (URL oder Schlüssel fehlen, oder die URL
         * ist kein https). Ein ungültiger VAPID-Schlüssel schaltet nur Push ab.
         */
        fun parse(supabaseUrl: String, anonKey: String, vapidPublicKey: String, webAppUrl: String): ServerConfig? {
            val url = supabaseUrl.trim().trimEnd('/')
            val key = anonKey.trim()
            if (url.isEmpty() || key.isEmpty() || !url.startsWith("https://", ignoreCase = true)) return null
            val vapid = vapidPublicKey.trim().trimEnd('=').takeIf { VAPID_PATTERN.matches(it) }
            val web = webAppUrl.trim().trimEnd('/').takeIf { it.startsWith("https://", ignoreCase = true) }
                ?: DEFAULT_WEB_APP_URL
            return ServerConfig(url, key, vapid, web)
        }
    }
}
