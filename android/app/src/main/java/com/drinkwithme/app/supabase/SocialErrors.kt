package com.drinkwithme.app.supabase

import java.io.IOException

/** Fehler der Vernetzung. [userMessage] ist ein deutscher Text für die Anzeige. */
sealed class SocialException(val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause) {
    /** `supabase.properties` fehlt oder ist unvollständig. */
    class NotConfigured : SocialException(ErrorMessages.NOT_CONFIGURED)

    /** Nicht (mehr) angemeldet. */
    class NotSignedIn(message: String = ErrorMessages.NOT_SIGNED_IN) : SocialException(message)

    /** Keine Verbindung (Zeitüberschreitung, kein Netz, DNS …). */
    class Network(cause: IOException) : SocialException(ErrorMessages.NETWORK, cause)

    /**
     * Antwort des Servers mit Fehler. [code] ist der maschinenlesbare Code (z. B. „rate_limited“ aus einer RPC,
     * „event_not_found“ aus einer Edge Function, „42501“ aus PostgREST), [status] der HTTP-Status.
     */
    class Server(val code: String?, val status: Int) : SocialException(ErrorMessages.forCode(code, status))

    /** Unerwartete Antwort (z. B. kaputtes JSON). */
    class InvalidResponse(val detail: String) : SocialException(ErrorMessages.GENERIC)
}

/** Deutsche Texte zu den Fehlercodes aus supabase/README.md. */
object ErrorMessages {
    const val NOT_CONFIGURED = "Server noch nicht eingerichtet. Bar, Verlauf, Statistik, Sprache und Vorlesen funktionieren trotzdem."
    const val NOT_SIGNED_IN = "Bitte melde dich an, um Freunde zu nutzen."
    const val SESSION_EXPIRED = "Deine Anmeldung ist abgelaufen. Bitte melde dich erneut an."
    const val NETWORK = "Keine Verbindung zum Server. Bitte prüfe deine Internetverbindung."
    const val GENERIC = "Das hat leider nicht geklappt. Bitte versuche es später noch einmal."
    const val RATE_LIMITED = "Zu viele Versuche in kurzer Zeit. Bitte versuche es später noch einmal."
    const val INVITE_INVALID = "Diese Einladung ist ungültig oder abgelaufen. Bitte um einen neuen Link."
    const val INVITE_OWN = "Das ist dein eigener Einladungs-Link. Teile ihn mit der Person, die du einladen möchtest."
    const val REQUEST_NOT_FOUND = "Diese Anfrage gibt es nicht mehr."
    const val TOO_MANY_SUBSCRIPTIONS =
        "Benachrichtigungen sind schon auf fünf Geräten eingerichtet. Melde dich auf einem alten Gerät ab und versuche es erneut."
    const val TOO_MANY_EVENTS =
        "Heute wurden schon sehr viele Getränke geteilt. Weitere Einträge bleiben vorerst nur auf diesem Gerät."
    const val SERVER_UNAVAILABLE = "Der Server ist gerade nicht erreichbar. Bitte versuche es später noch einmal."
    const val SERVER_MISCONFIGURED = "Der Server ist noch nicht vollständig eingerichtet."
    const val NOT_ALLOWED = "Das ist nicht erlaubt."
    const val SIGN_IN_FAILED = "Die Anmeldung hat nicht geklappt. Bitte versuche es noch einmal."
    const val SIGN_IN_CANCELLED = "Die Anmeldung wurde abgebrochen."
    const val SIGN_IN_EXPIRED = "Die Anmeldung ist abgelaufen. Bitte starte sie noch einmal."
    const val NO_BROWSER = "Für die Anmeldung wird ein Browser benötigt. Bitte installiere einen Browser und versuche es erneut."

    /** Text zu einem Fehlercode; unbekannte Codes fallen auf den HTTP-Status zurück. */
    fun forCode(code: String?, status: Int): String = when (code) {
        "rate_limited" -> RATE_LIMITED
        "invite_invalid" -> INVITE_INVALID
        "invite_own" -> INVITE_OWN
        "request_not_found" -> REQUEST_NOT_FOUND
        "too_many_subscriptions" -> TOO_MANY_SUBSCRIPTIONS
        "too_many_events" -> TOO_MANY_EVENTS
        "not_authenticated", "unauthorized", "invalid_grant", "refresh_token_not_found",
        "refresh_token_already_used", "session_not_found", "session_expired", "bad_jwt", "user_not_found",
        -> SESSION_EXPIRED
        "server_misconfigured" -> SERVER_MISCONFIGURED
        "42501" -> NOT_ALLOWED
        else -> forStatus(status)
    }

    private fun forStatus(status: Int): String = when {
        status == 401 -> SESSION_EXPIRED
        status == 403 -> NOT_ALLOWED
        status == 429 -> RATE_LIMITED
        status >= 500 -> SERVER_UNAVAILABLE
        else -> GENERIC
    }

    /** Anzeigetext für beliebige Fehler. */
    fun forThrowable(error: Throwable): String = when (error) {
        is SocialException -> error.userMessage
        is IOException -> NETWORK
        else -> GENERIC
    }
}
