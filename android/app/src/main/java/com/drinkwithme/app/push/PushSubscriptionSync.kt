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

    /**
     * Endpunkt, den die App schon einmal ersetzen ließ, weil er beim Server noch unter einem anderen Konto lag.
     * Schutz vor einer Endlosschleife, falls der Distributor trotzdem wieder denselben liefert.
     */
    var replacedEndpoint: String?
        get() = store.getString(KEY_REPLACED)
        set(value) = store.putString(KEY_REPLACED, value)

    fun setLastError(message: String?) {
        store.putString(KEY_LAST_ERROR, message)
        errorFlow.value = message
    }

    /**
     * `true` nur, wenn das aktuelle Abo nachweislich für [userId] beim Server liegt. Sonst (kein Endpunkt, Hochladen
     * abgelehnt oder noch nicht erfolgt, anderes Konto) muss die App selbst regelmäßig abrufen.
     */
    fun isConfirmedFor(userId: String?): Boolean {
        val upload = uploaded ?: return false
        return userId != null && upload.userId == userId && upload.endpoint == current.value?.endpoint
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
        const val KEY_REPLACED = "push_endpoint_replaced"
    }
}

/** Ergebnis eines Abgleichs. */
enum class SyncOutcome { DONE, RETRY_LATER }

/**
 * Gleicht das Push-Abo mit dem Server ab: Ein veraltetes Abo (anderer Endpunkt) wird gelöscht, das aktuelle für den
 * angemeldeten Nutzer angelegt. Läuft im Hintergrund (WorkManager, mit Netz).
 *
 * Endpunkte sind beim Server global eindeutig, löschen darf ein Abo aber nur sein Konto. Liegt der aktuelle Endpunkt
 * noch unter einem anderen Konto (z. B. offline abgemeldet, Sitzung abgelaufen), fordert die App beim Distributor
 * über [requestNewEndpoint] einen neuen an, statt ihn unter dem neuen Konto vergeblich hochzuladen. Ob abgerufen
 * werden muss, entscheidet [PushEndpointStore.isConfirmedFor].
 */
class PushSubscriptionSync(
    private val endpoints: PushEndpointStore,
    private val remote: PushSubscriptionRemote,
    private val currentUser: CurrentUserId,
    private val requestNewEndpoint: () -> Unit,
) {
    suspend fun sync(): SyncOutcome {
        // Abgemeldet: nichts hochladen und den Merker behalten – er sagt dem nächsten Konto, wem das Abo gehört.
        val me = currentUser.currentUserId() ?: return SyncOutcome.DONE
        val current = endpoints.current.value
        return try {
            endpoints.uploaded?.let { uploaded ->
                when {
                    uploaded.userId == me && uploaded.endpoint == current?.endpoint -> Unit
                    uploaded.userId == me -> {
                        remote.unregister(uploaded.endpoint)
                        endpoints.uploaded = null
                    }
                    // Unser Endpunkt liegt noch unter einem anderen Konto; löschen kann nur jenes Konto.
                    uploaded.endpoint == current?.endpoint -> {
                        endpoints.uploaded = null
                        return replaceEndpoint(current)
                    }
                    // Abo eines anderen Kontos an einem alten Endpunkt: betrifft dieses Gerät nicht mehr.
                    else -> endpoints.uploaded = null
                }
            }
            if (current != null && endpoints.uploaded == null) {
                try {
                    remote.register(current)
                } catch (error: SocialException.Server) {
                    if (error.code == UNIQUE_VIOLATION) return replaceEndpoint(current)
                    throw error
                }
                endpoints.uploaded = UploadedSubscription(current.endpoint, me)
                endpoints.replacedEndpoint = null
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
        val uploaded = endpoints.uploaded ?: return
        if (uploaded.userId != currentUser.currentUserId()) return
        try {
            remote.unregister(uploaded.endpoint)
            endpoints.uploaded = null
        } catch (error: SocialException) {
            // Merker behalten: Das Abo liegt vermutlich noch beim Server. Meldet sich dasselbe Konto wieder an, gilt
            // es weiter; ein anderes Konto holt sich einen neuen Endpunkt (siehe sync).
        }
    }

    private fun replaceEndpoint(current: PushSubscription): SyncOutcome {
        if (endpoints.replacedEndpoint == current.endpoint) {
            // Der Distributor hat wieder denselben Endpunkt geliefert: erklären, die App ruft weiter selbst ab.
            endpoints.setLastError(PushTexts.ENDPOINT_TAKEN)
            return SyncOutcome.DONE
        }
        endpoints.replacedEndpoint = current.endpoint
        // Vor der Anfrage leeren: Der neue Endpunkt kann sofort (in einem anderen Thread) ankommen.
        endpoints.setCurrent(null)
        endpoints.setLastError(null)
        requestNewEndpoint()
        return SyncOutcome.DONE
    }

    private companion object {
        /** PostgreSQL `unique_violation`: Der Endpunkt ist schon (unter einem anderen Konto) hinterlegt. */
        const val UNIQUE_VIOLATION = "23505"
    }
}
