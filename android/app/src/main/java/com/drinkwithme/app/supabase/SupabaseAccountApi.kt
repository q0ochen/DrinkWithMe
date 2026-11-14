package com.drinkwithme.app.supabase

import org.json.JSONObject

/** Konto-Funktionen auf dem Server. */
interface AccountRemote {
    /** Edge Function `delete-account`: löscht das Konto; alles andere folgt per `ON DELETE CASCADE`. Idempotent. */
    suspend fun deleteAccount()
}

class SupabaseAccountApi(private val api: SupabaseApi) : AccountRemote {
    override suspend fun deleteAccount() {
        api.function("delete-account")
    }
}

/** Ein Push-Abo (UnifiedPush-Endpunkt mit den Web-Push-Schlüsseln des Geräts). */
data class PushSubscription(val endpoint: String, val p256dh: String, val auth: String)

/** Tabelle `push_subscriptions` (nur eigene Abos, höchstens 5 pro Nutzer, kein UPDATE). */
interface PushSubscriptionRemote {
    /** Legt das Abo an; ein gleicher Endpunkt wird vorher entfernt (Erneuern = löschen und neu anlegen). */
    suspend fun register(subscription: PushSubscription)

    suspend fun unregister(endpoint: String)
}

class SupabasePushSubscriptions(private val api: SupabaseApi) : PushSubscriptionRemote {

    override suspend fun register(subscription: PushSubscription) {
        unregister(subscription.endpoint)
        val body = JSONObject()
            .put("endpoint", subscription.endpoint)
            .put("p256dh", subscription.p256dh)
            .put("auth", subscription.auth)
            .put("platform", PLATFORM)
            .toString()
        api.post("rest/v1/push_subscriptions", body, prefer = "return=minimal")
    }

    override suspend fun unregister(endpoint: String) {
        api.delete("rest/v1/push_subscriptions", listOf("endpoint" to "eq.$endpoint"))
    }

    companion object {
        const val PLATFORM = "unifiedpush"
    }
}
