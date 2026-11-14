package com.drinkwithme.app.push

/** Was eine Benachrichtigung über ein Getränk eines Freundes zeigt. */
data class DrinkNotificationContent(
    val eventId: String,
    /** „Anna trinkt gerade“ */
    val title: String,
    /** „Apfelschorle (500 ml)“ */
    val body: String,
)

/** Zeigt Benachrichtigungen (unter Android im Kanal „drinks“). */
fun interface FriendDrinkNotifier {
    fun show(content: DrinkNotificationContent)
}

/**
 * Pro Ereignis eigene, stabile RequestCodes für die PendingIntents (Vertrag, Abschnitt 3): sonst würde Android
 * ältere Benachrichtigungen beim Antippen mit dem neuesten Ereignis öffnen.
 */
object NotificationRequestCodes {
    private fun base(eventId: String): Int = eventId.hashCode() and 0x3FFFFFFF

    /** Benachrichtigungs-ID und RequestCode für „antippen = Ereignis öffnen“ (gerade Zahl). */
    fun open(eventId: String): Int = base(eventId) * 2

    /** RequestCode für die Aktion „Vorlesen“ (ungerade Zahl, nie gleich [open]). */
    fun readAloud(eventId: String): Int = base(eventId) * 2 + 1
}
