package com.drinkwithme.core.sharing

import com.drinkwithme.core.bar.BarItem
import com.drinkwithme.core.health.isAlcoholic
import com.drinkwithme.core.log.DrinkEntry
import com.drinkwithme.core.log.ShareDecider

/**
 * Teilen-Regel: Ein Eintrag wird mit Freunden geteilt, wenn angemeldet **und** das Getränk nicht als privat
 * markiert ist **und** (nicht alkoholisch **oder** der Nutzer hat dem Teilen alkoholischer Getränke einmalig
 * zugestimmt). Geteilt werden nur Name, Icon, Kategorie, Menge in ml und Zeitpunkt ([SharedDrink]) – Gramm,
 * Zucker und Statistiken nie.
 */
class SharingPolicy(private val state: SharingStateProvider) : ShareDecider {

    /**
     * Beim Eintragen: `shareOverride == false` markiert den Eintrag als privat. `true` hebt nur diese
     * Markierung auf und ersetzt **nicht** Anmeldung oder Alkohol-Zustimmung.
     */
    override fun shareWithFriends(item: BarItem, shareOverride: Boolean?): Boolean =
        shouldShare(state.current(), isAlcoholic = item.isAlcoholic, markedPrivate = shareOverride == false)

    /** Beim Veröffentlichen: erneute Prüfung, falls sich Anmeldung oder Zustimmung inzwischen geändert haben. */
    fun shouldPublish(entry: DrinkEntry): Boolean =
        entry.shareWithFriends && shouldShare(state.current(), isAlcoholic = entry.isAlcoholic, markedPrivate = false)

    /** Zurückziehen geteilter Ereignisse ist nur angemeldet möglich. */
    fun canUnpublish(): Boolean = state.current().isSignedIn

    /**
     * `true`, wenn die App vor dem Teilen einmal „Alkoholische Getränke mit Freunden teilen?“ fragen soll:
     * angemeldet, alkoholisches Getränk und noch keine Antwort.
     */
    fun needsAlcoholConsentQuestion(item: BarItem): Boolean {
        val current = state.current()
        return current.isSignedIn && item.isAlcoholic && current.alcoholConsent == AlcoholSharingConsent.UNKNOWN
    }

    companion object {
        /** Einmalige Frage beim ersten alkoholischen Getränk. */
        const val ALCOHOL_CONSENT_QUESTION = "Alkoholische Getränke mit Freunden teilen?"
        const val ALCOHOL_CONSENT_YES = "Ja"
        const val ALCOHOL_CONSENT_NO = "Nein"
        const val ALCOHOL_CONSENT_HINT = "Das kannst du später in den Einstellungen ändern."

        /** Die Regel als reine Funktion. */
        fun shouldShare(state: SharingState, isAlcoholic: Boolean, markedPrivate: Boolean): Boolean =
            state.isSignedIn && !markedPrivate &&
                (!isAlcoholic || state.alcoholConsent == AlcoholSharingConsent.ALLOWED)
    }
}
