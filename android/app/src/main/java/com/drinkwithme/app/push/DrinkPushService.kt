package com.drinkwithme.app.push

import com.drinkwithme.app.DrinkWithMeApplication
import com.drinkwithme.app.supabase.PushSubscription
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * Empfänger des UnifiedPush-Connectors (im Manifest mit der Aktion `org.unifiedpush.android.connector.PUSH_EVENT`).
 * Der Connector entschlüsselt Web Push (RFC 8291) selbst; nicht entschlüsselte Nachrichten verwirft der
 * [PushMessageHandler].
 */
class DrinkPushService : PushService() {

    private val container get() = (application as DrinkWithMeApplication).container

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val keys = endpoint.pubKeySet
        val usable = keys != null && endpoint.url.startsWith("https://", ignoreCase = true)
        container.pushEndpoints.setCurrent(
            if (usable) PushSubscription(endpoint.url, keys.pubKey, keys.auth) else null,
        )
        if (!usable) container.pushEndpoints.setLastError(PushTexts.UNUSABLE_ENDPOINT)
        container.socialBackground.refresh()
    }

    override fun onMessage(message: PushMessage, instance: String) {
        container.pushMessageHandler.handle(message.content, message.decrypted)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        container.pushEndpoints.setCurrent(null)
        container.pushEndpoints.setLastError(PushTexts.registrationFailed(reason.name))
        container.socialBackground.refresh()
    }

    override fun onUnregistered(instance: String) {
        container.pushEndpoints.setCurrent(null)
        container.socialBackground.refresh()
    }
}

/** Deutsche Texte rund um die Einrichtung von Push. */
object PushTexts {
    const val NTFY_PACKAGE = "io.heckel.ntfy"
    const val NTFY_FDROID_URL = "https://f-droid.org/packages/io.heckel.ntfy/"
    const val UNUSABLE_ENDPOINT =
        "Der Push-Dienst hat keine sichere (https) Adresse geliefert. Die App prüft deshalb weiter etwa alle 15 Minuten."

    const val ENDPOINT_TAKEN =
        "Der Push-Dienst liefert eine Adresse, die noch zu einem anderen Konto gehört. Schalte die sofortigen " +
            "Benachrichtigungen aus und wieder ein. Bis dahin prüft die App etwa alle 15 Minuten."

    fun registrationFailed(reason: String): String = when (reason) {
        "NETWORK" -> "Der Push-Dienst ist gerade nicht erreichbar. Die App prüft so lange etwa alle 15 Minuten."
        "ACTION_REQUIRED" -> "Bitte öffne die Push-App (z. B. ntfy) und bestätige dort Drink With Me."
        else -> "Die Einrichtung der Benachrichtigungen hat nicht geklappt. Die App prüft weiter etwa alle 15 Minuten."
    }
}
