package com.drinkwithme.core.sharing

/** Einmalige Zustimmung zum Teilen alkoholischer Getränke (später in den Einstellungen änderbar). */
enum class AlcoholSharingConsent {
    /** Noch nicht gefragt – alkoholische Getränke werden nicht geteilt. */
    UNKNOWN,

    /** „Ja“: alkoholische Getränke werden wie alle anderen geteilt. */
    ALLOWED,

    /** „Nein“: alkoholische Getränke bleiben privat. */
    DENIED,
}

/** Momentaufnahme dessen, was die Teilen-Regel wissen muss. */
data class SharingState(
    val isSignedIn: Boolean,
    val alcoholConsent: AlcoholSharingConsent,
)

/** Liefert den aktuellen [SharingState] (Anmeldung aus der Sitzung, Zustimmung aus den Einstellungen). */
fun interface SharingStateProvider {
    fun current(): SharingState
}
