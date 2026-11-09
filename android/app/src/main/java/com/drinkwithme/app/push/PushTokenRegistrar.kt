package com.drinkwithme.app.push

/** Meldet das Push-Token dieses Geräts beim Server an bzw. ab – immer für den angemeldeten Nutzer. */
interface PushTokenRegistrar {
    /** Holt das aktuelle Token des Geräts und meldet es an. Ohne Anmeldung passiert nichts. */
    suspend fun registerCurrentToken()

    /** Meldet ein neues Token an (z. B. nach einem Token-Wechsel). Ohne Anmeldung passiert nichts. */
    suspend fun registerToken(token: String)

    /** Entfernt das Token des Geräts beim angemeldeten Nutzer – vor dem Abmelden aufrufen. */
    suspend fun unregisterCurrentToken()
}
