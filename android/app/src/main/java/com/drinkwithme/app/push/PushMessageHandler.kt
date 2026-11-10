package com.drinkwithme.app.push

import com.drinkwithme.app.data.NotifiedEvents
import com.drinkwithme.app.supabase.CurrentUserId

/** Was mit einer Push-Nachricht geschah (für Tests und Protokoll). */
enum class PushOutcome {
    SHOWN,

    /** Der Connector konnte die Nachricht nicht entschlüsseln (`decrypted == false`) – verworfen. */
    NOT_DECRYPTED,
    INVALID,
    SIGNED_OUT,

    /** Für ein anderes (früheres) Konto auf diesem Gerät – verworfen. */
    OTHER_RECIPIENT,
    OWN_EVENT,
    DUPLICATE,
}

/**
 * Prüft eingehende Push-Nachrichten: nur entschlüsselte, gültige `drink`-Nachrichten, deren `recipientId` dem
 * angemeldeten Nutzer entspricht, und jedes Ereignis nur einmal (gemeinsam mit dem Abruf ohne Push).
 */
class PushMessageHandler(
    private val currentUser: CurrentUserId,
    private val notified: NotifiedEvents,
    private val notifier: FriendDrinkNotifier,
) {
    fun handle(content: ByteArray, decrypted: Boolean): PushOutcome {
        if (!decrypted) return PushOutcome.NOT_DECRYPTED
        val message = DrinkPushParser.parse(content) ?: return PushOutcome.INVALID
        val me = currentUser.currentUserId() ?: return PushOutcome.SIGNED_OUT
        if (message.recipientId != me) return PushOutcome.OTHER_RECIPIENT
        if (message.authorId == me) return PushOutcome.OWN_EVENT
        if (!notified.markIfNew(message.eventId)) return PushOutcome.DUPLICATE
        notifier.show(DrinkNotificationContent(message.eventId, message.title, message.body))
        return PushOutcome.SHOWN
    }
}
