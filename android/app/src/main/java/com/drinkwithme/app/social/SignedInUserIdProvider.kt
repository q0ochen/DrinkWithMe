package com.drinkwithme.app.social

/**
 * Liefert die ID des gerade angemeldeten Nutzers sofort (ohne Flow) – z. B. für eingehende
 * Push-Nachrichten, die synchron geprüft werden müssen. `null`, wenn niemand angemeldet ist.
 */
fun interface SignedInUserIdProvider {
    fun signedInUserId(): String?
}
