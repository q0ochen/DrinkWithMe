package com.drinkwithme.app.push

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Schlüssel im `data`-Teil der Push-Nachricht (siehe docs/TRINKRUNDEN_ARCHITEKTUR.md). */
object PushPayload {
    const val KEY_TYPE = "type"
    const val KEY_GROUP_ID = "groupId"
    const val KEY_DRINK_ID = "drinkId"
}

/** Inhalt einer Benachrichtigung „Anna trinkt gerade …“. */
data class FriendDrinkNotification(
    val title: String,
    val body: String,
    /** Gleiche Getränke aus mehreren Runden ersetzen sich über denselben Tag. */
    val tag: String?,
    /** Runde, deren Feed beim Antippen geöffnet wird. */
    val groupId: String?,
)

/** Zeigt eine Benachrichtigung an. */
fun interface FriendDrinkNotifier {
    fun show(notification: FriendDrinkNotification)
}

/** Reagiert auf Ereignisse des Push-Dienstes – unabhängig vom konkreten Anbieter. */
interface PushMessageHandler {
    /** Beim App-Start: Token des angemeldeten Nutzers auffrischen. */
    fun onAppStarted()

    fun onNewToken(token: String)

    /** Eine Nachricht kam an, während die App im Vordergrund ist – das System zeigt sie dann nicht selbst an. */
    fun onFriendDrink(notification: FriendDrinkNotification)
}

class DefaultPushMessageHandler(
    private val registrar: PushTokenRegistrar,
    private val notifier: FriendDrinkNotifier,
    private val scope: CoroutineScope,
    private val onError: (Throwable) -> Unit = {},
) : PushMessageHandler {

    override fun onAppStarted() = launchSafely { registrar.registerCurrentToken() }

    override fun onNewToken(token: String) = launchSafely { registrar.registerToken(token) }

    override fun onFriendDrink(notification: FriendDrinkNotification) = notifier.show(notification)

    private fun launchSafely(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                onError(error)
            }
        }
    }
}
