package com.drinkwithme.app.data

import com.drinkwithme.app.polling.PollingState
import com.drinkwithme.core.sharing.AlcoholSharingConsent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/** Ein Ja/Nein-Schalter mit beobachtbarem Wert. */
class BooleanSetting(private val store: KeyValueStore, private val key: String, default: Boolean) {
    private val flow = MutableStateFlow(store.getBoolean(key, default))
    val value: StateFlow<Boolean> = flow.asStateFlow()

    fun set(enabled: Boolean) {
        store.putBoolean(key, enabled)
        flow.value = enabled
    }
}

/**
 * Lokale Einstellungen und Merker (SharedPreferences). Nichts davon verlässt das Gerät.
 */
class AppSettings(private val store: KeyValueStore) : PollingState {

    private val consentFlow = MutableStateFlow(readConsent())

    /** Einmalige Zustimmung zum Teilen alkoholischer Getränke (später hier änderbar). */
    val alcoholConsent: StateFlow<AlcoholSharingConsent> = consentFlow.asStateFlow()

    fun setAlcoholConsent(consent: AlcoholSharingConsent) {
        store.putString(KEY_ALCOHOL_CONSENT, consent.name)
        consentFlow.value = consent
    }

    /** Erster Start abgeschlossen (Bar eingerichtet). */
    var onboardingDone: Boolean
        get() = store.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = store.putBoolean(KEY_ONBOARDING_DONE, value)

    /** DGE-Bänder im Alkohol-Diagramm anzeigen (ausblendbar). */
    val showAlcoholBands = BooleanSetting(store, KEY_SHOW_ALCOHOL_BANDS, true)

    /** Kurze Ansage nach dem Eintragen („Apfelschorle eingetragen.“). */
    val announceLogged = BooleanSetting(store, KEY_ANNOUNCE_LOGGED, true)

    /** Ereignisse beim Öffnen automatisch vorlesen (nicht, wenn ein Screenreader aktiv ist). */
    val autoReadEvents = BooleanSetting(store, KEY_AUTO_READ_EVENTS, false)

    /** Zeitpunkt des neuesten bereits gesehenen Ereignisses eines Freundes (für den Abruf ohne Push). */
    override var lastSeenEventAt: Instant?
        get() = store.getString(KEY_LAST_SEEN_EVENT_AT)?.let(::parseInstantOrNull)
        set(value) = store.putString(KEY_LAST_SEEN_EVENT_AT, value?.toString())

    /** Einladung, die nach der Anmeldung angenommen werden soll. */
    var pendingInviteToken: String?
        get() = store.getString(KEY_PENDING_INVITE)
        set(value) = store.putString(KEY_PENDING_INVITE, value)

    /** Nach dem Abmelden: kontobezogene Merker zurücksetzen (die Zustimmung zum Teilen bleibt eine Geräte-Einstellung). */
    fun clearAccountData() {
        lastSeenEventAt = null
        pendingInviteToken = null
    }

    private fun readConsent(): AlcoholSharingConsent =
        AlcoholSharingConsent.entries.firstOrNull { it.name == store.getString(KEY_ALCOHOL_CONSENT) }
            ?: AlcoholSharingConsent.UNKNOWN

    private companion object {
        const val KEY_ALCOHOL_CONSENT = "alcohol_sharing_consent"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_SHOW_ALCOHOL_BANDS = "show_alcohol_bands"
        const val KEY_ANNOUNCE_LOGGED = "announce_logged"
        const val KEY_AUTO_READ_EVENTS = "auto_read_events"
        const val KEY_LAST_SEEN_EVENT_AT = "last_seen_event_at"
        const val KEY_PENDING_INVITE = "pending_invite_token"
    }
}
