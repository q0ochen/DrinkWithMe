package com.drinkwithme.app.social.firebase

import com.drinkwithme.app.DrinkWithMeApplication
import com.drinkwithme.app.push.FriendDrinkNotification
import com.drinkwithme.app.push.PushMessageHandler
import com.drinkwithme.app.push.PushPayload
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Empfängt Firebase Cloud Messaging: neue Tokens und Nachrichten.
 *
 * Läuft die App im Hintergrund, zeigt Android Benachrichtigungen selbst an (Kanal „drinks“).
 * Nur im Vordergrund landet die Nachricht hier und wird selbst angezeigt.
 */
class DrinkMessagingService : FirebaseMessagingService() {

    private val pushHandler: PushMessageHandler?
        get() = (application as DrinkWithMeApplication).container.pushHandler

    override fun onNewToken(token: String) {
        pushHandler?.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        pushHandler?.onFriendDrink(
            FriendDrinkNotification(
                title = notification.title.orEmpty(),
                body = notification.body.orEmpty(),
                tag = notification.tag ?: message.data[PushPayload.KEY_DRINK_ID],
                groupId = message.data[PushPayload.KEY_GROUP_ID],
            ),
        )
    }
}
