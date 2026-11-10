package com.drinkwithme.app.routing

import com.drinkwithme.app.supabase.AuthCallback
import com.drinkwithme.core.social.InviteLinks

/** Was ein eingehender Intent von der App will. */
sealed interface AppRequest {
    /** Rückkehr aus der Anmeldung im Browser. */
    data class SignInCallback(val callback: AuthCallback) : AppRequest

    /** Einladungs-Link `drinkwithme://einladung/<token>` – erst nach Bestätigung annehmen. */
    data class AcceptInvite(val token: String) : AppRequest

    /**
     * Tipp auf eine Benachrichtigung: Ereignis-Bildschirm. [title]/[body] stammen aus der Benachrichtigung und
     * werden gezeigt, falls das Ereignis offline nicht geladen werden kann. [autoRead] = Aktion „Vorlesen“.
     */
    data class OpenEvent(val eventId: String, val title: String?, val body: String?, val autoRead: Boolean) : AppRequest
}

/** Liest Extras eines Intents (unter Android `Intent.getStringExtra` / `getBooleanExtra`). */
interface IntentExtras {
    fun string(key: String): String?
    fun boolean(key: String): Boolean
}

/** Ordnet Intents den [AppRequest]s zu – reine Logik, ohne Android testbar. */
object IntentRouter {
    const val ACTION_VIEW = "android.intent.action.VIEW"
    const val ACTION_OPEN_EVENT = "com.drinkwithme.app.action.OPEN_EVENT"
    const val EXTRA_EVENT_ID = "com.drinkwithme.app.extra.EVENT_ID"
    const val EXTRA_TITLE = "com.drinkwithme.app.extra.TITLE"
    const val EXTRA_BODY = "com.drinkwithme.app.extra.BODY"
    const val EXTRA_AUTO_READ = "com.drinkwithme.app.extra.AUTO_READ"

    /**
     * Nicht exportierter Einstieg (activity-alias auf MainActivity im Manifest) nur für die eigenen Benachrichtigungen.
     * Andere Apps können ihn nicht starten; nur von dort kommende Intents gelten als vertrauenswürdig.
     */
    const val NOTIFICATION_ENTRY = "com.drinkwithme.app.NotificationEntry"

    /** Ob ein Intent über den nicht exportierten [NOTIFICATION_ENTRY] kam (Klassenname der Intent-Komponente). */
    fun isTrustedEntry(componentClassName: String?): Boolean = componentClassName == NOTIFICATION_ENTRY

    /**
     * Nur beim echten Start auswerten: nicht nach einer Drehung ([restored]) und nicht, wenn Android die Aufgabe aus
     * „Zuletzt verwendet“ mit ihrem ursprünglichen Intent neu startet ([launchedFromHistory]) – sonst kämen eine
     * abgelehnte Einladung oder ein schon vorgelesenes Getränk noch einmal.
     */
    fun shouldRoute(restored: Boolean, launchedFromHistory: Boolean): Boolean = !restored && !launchedFromHistory

    /**
     * @param trusted `true` nur für Intents über [NOTIFICATION_ENTRY]. Die exportierte MainActivity kann jede App
     *   starten: Dann zählt nur die Ereignis-ID; Titel, Text und „sofort vorlesen“ werden ignoriert (sonst könnte
     *   eine fremde App beliebige Sätze als Getränk eines Freundes zeigen und vorlesen lassen).
     */
    fun route(action: String?, data: String?, extras: IntentExtras, trusted: Boolean = false): AppRequest? = when (action) {
        ACTION_OPEN_EVENT -> extras.string(EXTRA_EVENT_ID)?.takeIf { it.isNotBlank() }?.let { id ->
            AppRequest.OpenEvent(
                eventId = id,
                title = if (trusted) extras.string(EXTRA_TITLE) else null,
                body = if (trusted) extras.string(EXTRA_BODY) else null,
                autoRead = trusted && extras.boolean(EXTRA_AUTO_READ),
            )
        }
        ACTION_VIEW -> data?.let(::routeLink)
        else -> null
    }

    /** `drinkwithme://auth-callback?…` oder `drinkwithme://einladung/<token>` (auch https-Links der Web-App). */
    fun routeLink(link: String): AppRequest? {
        AuthCallback.parse(link)?.let { return AppRequest.SignInCallback(it) }
        return InviteLinks.parseToken(link)?.takeIf { link.contains(InviteLinks.PATH_SEGMENT) }?.let { AppRequest.AcceptInvite(it) }
    }
}
