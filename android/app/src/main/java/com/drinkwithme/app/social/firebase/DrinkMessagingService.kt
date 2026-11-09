package com.drinkwithme.app.social.firebase

import com.drinkwithme.app.DrinkWithMeApplication
import com.drinkwithme.app.push.PushMessageHandler
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Empfängt Firebase Cloud Messaging: neue Tokens und Nachrichten.
 *
 * Der Server sendet nur Daten-Nachrichten (ohne `notification`-Block). Sie landen deshalb immer hier –
 * im Vorder- wie im Hintergrund – und die App zeigt sie selbst an, aber nur, wenn sie für den gerade
 * angemeldeten Nutzer bestimmt sind (`recipientId`).
 */
class DrinkMessagingService : FirebaseMessagingService() {

    private val pushHandler: PushMessageHandler?
        get() = (application as DrinkWithMeApplication).container.pushHandler

    override fun onNewToken(token: String) {
        pushHandler?.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        pushHandler?.onDataMessage(message.data)
    }
}
