package com.drinkwithme.app.push

/** Meldet das Push-Token dieses Geräts beim Server an bzw. ab – immer für den angemeldeten Nutzer. */
interface PushTokenRegistrar {
    /** Holt das aktuelle Token des Geräts und meldet es an. Ohne Anmeldung passiert nichts. */
    suspend fun registerCurrentToken()

    /** Meldet ein neues Token an (z. B. nach einem Token-Wechsel). Ohne Anmeldung passiert nichts. */
    suspend fun registerToken(token: String)

    /**
     * Startet das Löschen des Token-Dokuments beim angemeldeten Nutzer, **ohne** auf die Bestätigung des
     * Servers zu warten (offline würde das nie fertig). Vor dem Abmelden aufrufen – danach fehlt die Berechtigung.
     */
    suspend fun removeCurrentTokenDocument()

    /**
     * Macht das Push-Token dieses Geräts ungültig, damit es keine Nachrichten mehr bekommt – unabhängig davon,
     * ob das Löschen des Token-Dokuments schon beim Server angekommen ist. Ein neues Token entsteht bei Bedarf.
     */
    suspend fun invalidateToken()
}
