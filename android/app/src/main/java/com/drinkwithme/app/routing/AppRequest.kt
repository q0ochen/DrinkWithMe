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

    fun route(action: String?, data: String?, extras: IntentExtras): AppRequest? = when (action) {
        ACTION_OPEN_EVENT -> extras.string(EXTRA_EVENT_ID)?.takeIf { it.isNotBlank() }?.let { id ->
            AppRequest.OpenEvent(
                eventId = id,
                title = extras.string(EXTRA_TITLE),
                body = extras.string(EXTRA_BODY),
                autoRead = extras.boolean(EXTRA_AUTO_READ),
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
