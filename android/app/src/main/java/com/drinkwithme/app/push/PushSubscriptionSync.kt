package com.drinkwithme.app.push

import com.drinkwithme.app.data.KeyValueStore
import com.drinkwithme.app.data.nonBlankStringOrNull
import com.drinkwithme.app.supabase.CurrentUserId
import com.drinkwithme.app.supabase.PushSubscription
import com.drinkwithme.app.supabase.PushSubscriptionRemote
import com.drinkwithme.app.supabase.SocialException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONException
import org.json.JSONObject

/** Auf dem Server hinterlegtes Abo: welcher Endpunkt für welches Konto. */
data class UploadedSubscription(val endpoint: String, val userId: String)

/**
 * Merkt sich den aktuellen UnifiedPush-Endpunkt (vom Distributor, z. B. ntfy) und welches Abo schon beim Server
 * liegt. Beobachtbar, damit die Einstellungen den Stand zeigen können.
 */
class PushEndpointStore(private val store: KeyValueStore) {
    private val currentFlow = MutableStateFlow(readSubscription())

    /** Aktueller Endpunkt oder `null` (kein Distributor, abgemeldet, Registrierung fehlgeschlagen). */
    val current: StateFlow<PushSubscription?> = currentFlow.asStateFlow()

    private val errorFlow = MutableStateFlow(store.getString(KEY_LAST_ERROR))

    /** Letzter Fehler beim Hinterlegen (deutscher Text) oder `null`. */
    val lastError: StateFlow<String?> = errorFlow.asStateFlow()

    fun setCurrent(subscription: PushSubscription?) {
        store.putString(
            KEY_CURRENT,
            subscription?.let {
                JSONObject().put("endpoint", it.endpoint).put("p256dh", it.p256dh).put("auth", it.auth).toString()
            },
        )
        currentFlow.value = subscription
    }

    var uploaded: UploadedSubscription?
        get() = store.getString(KEY_UPLOADED)?.let { text ->
            try {
                val json = JSONObject(text)
                val endpoint = json.nonBlankStringOrNull("endpoint")
                val userId = json.nonBlankStringOrNull("userId")
                if (endpoint != null && userId != null) UploadedSubscription(endpoint, userId) else null
            } catch (error: JSONException) {
                null
            }
        }
        set(value) = store.putString(
            KEY_UPLOADED,
            value?.let { JSONObject().put("endpoint", it.endpoint).put("userId", it.userId).toString() },
        )

    fun setLastError(message: String?) {
        store.putString(KEY_LAST_ERROR, message)
        errorFlow.value = message
    }

    private fun readSubscription(): PushSubscription? = store.getString(KEY_CURRENT)?.let { text ->
        try {
            val json = JSONObject(text)
            PushSubscription(
                endpoint = json.nonBlankStringOrNull("endpoint") ?: return@let null,
                p256dh = json.nonBlankStringOrNull("p256dh") ?: return@let null,
                auth = json.nonBlankStringOrNull("auth") ?: return@let null,
            )
        } catch (error: JSONException) {
            null
        }
    }

    private companion object {
        const val KEY_CURRENT = "push_endpoint_current"
        const val KEY_UPLOADED = "push_endpoint_uploaded"
        const val KEY_LAST_ERROR = "push_endpoint_error"
    }
}

/** Ergebnis eines Abgleichs. */
enum class SyncOutcome { DONE, RETRY_LATER }

/**
 * Gleicht das Push-Abo mit dem Server ab: Ein veraltetes Abo (anderer Endpunkt, abgemeldet) wird gelöscht, das
 * aktuelle für den angemeldeten Nutzer angelegt. Läuft im Hintergrund (WorkManager, mit Netz).
 */
class PushSubscriptionSync(
    private val endpoints: PushEndpointStore,
    private val remote: PushSubscriptionRemote,
    private val currentUser: CurrentUserId,
) {
    suspend fun sync(): SyncOutcome {
        val me = currentUser.currentUserId()
        val current = endpoints.current.value
        return try {
            endpoints.uploaded?.let { uploaded ->
                val stale = uploaded.userId != me || uploaded.endpoint != current?.endpoint
                if (stale) {
                    // Fremde Abos (anderes Konto) kann nur dieses Konto löschen; der Server räumt tote Abos selbst auf.
                    if (uploaded.userId == me) remote.unregister(uploaded.endpoint)
                    endpoints.uploaded = null
                }
            }
            if (me != null && current != null && endpoints.uploaded == null) {
                remote.register(current)
                endpoints.uploaded = UploadedSubscription(current.endpoint, me)
            }
            endpoints.setLastError(null)
            SyncOutcome.DONE
        } catch (error: SocialException.Network) {
            SyncOutcome.RETRY_LATER
        } catch (error: SocialException) {
            endpoints.setLastError(error.userMessage)
            SyncOutcome.DONE
        }
    }

    /** Vor dem Abmelden (solange noch ein Token da ist): das Abo dieses Geräts beim Server entfernen. */
    suspend fun removeForSignOut() {
        val me = currentUser.currentUserId()
        val uploaded = endpoints.uploaded ?: return
        try {
            if (uploaded.userId == me) remote.unregister(uploaded.endpoint)
        } catch (error: SocialException) {
            // Best effort: Der Server verwirft Nachrichten ohnehin per recipientId-Prüfung auf dem Gerät.
        }
        endpoints.uploaded = null
    }
}
