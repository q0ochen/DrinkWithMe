package com.drinkwithme.app.push

import com.drinkwithme.app.social.SignedInUserIdProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Schlüssel der Daten-Nachricht `{ type, groupId, drinkId, docId, recipientId, title, body }`
 * (siehe docs/TRINKRUNDEN_ARCHITEKTUR.md). Der Server sendet nur Daten-Nachrichten ohne `notification`-Block,
 * damit die App jede Nachricht selbst prüfen kann.
 */
object PushPayload {
    const val KEY_TYPE = "type"
    const val KEY_GROUP_ID = "groupId"
    const val KEY_DRINK_ID = "drinkId"
    const val KEY_DOC_ID = "docId"
    const val KEY_RECIPIENT_ID = "recipientId"
    const val KEY_TITLE = "title"
    const val KEY_BODY = "body"

    const val TYPE_DRINK = "drink"
}

/** Inhalt einer Benachrichtigung „Anna trinkt gerade …“. */
data class FriendDrinkNotification(
    val title: String,
    val body: String,
    /** Dasselbe Getränk aus mehreren gemeinsamen Runden ersetzt sich über denselben Tag (Drink-ID + Autor). */
    val tag: String?,
    /** Runde, deren Feed beim Antippen geöffnet wird. */
    val groupId: String?,
)

/** Zeigt eine Benachrichtigung an. */
fun interface FriendDrinkNotifier {
    fun show(notification: FriendDrinkNotification)
}

/** Liest eine Daten-Nachricht „Freund trinkt gerade“ – reine Logik, ohne Android testbar. */
object FriendDrinkPushParser {

    /**
     * @param signedInUserId angemeldeter Nutzer dieses Geräts (`null`: abgemeldet).
     * @return `null`, wenn die Nachricht nicht angezeigt werden soll: falscher Typ, unvollständig oder nicht
     *   für den angemeldeten Nutzer bestimmt (z. B. altes Token nach dem Abmelden oder nach einem Kontowechsel).
     */
    fun parse(data: Map<String, String>, signedInUserId: String?): FriendDrinkNotification? {
        if (data[PushPayload.KEY_TYPE] != PushPayload.TYPE_DRINK) return null
        val recipientId = data.nonBlank(PushPayload.KEY_RECIPIENT_ID) ?: return null
        if (signedInUserId == null || recipientId != signedInUserId) return null
        val title = data.nonBlank(PushPayload.KEY_TITLE) ?: return null
        return FriendDrinkNotification(
            title = title,
            body = data[PushPayload.KEY_BODY].orEmpty(),
            // docId = "{authorId}_{drinkId}" – in allen Runden gleich, also genau „Drink-ID + Autor“.
            tag = data.nonBlank(PushPayload.KEY_DOC_ID) ?: data.nonBlank(PushPayload.KEY_DRINK_ID),
            groupId = data.nonBlank(PushPayload.KEY_GROUP_ID),
        )
    }

    private fun Map<String, String>.nonBlank(key: String): String? = this[key]?.takeIf { it.isNotBlank() }
}

/** Reagiert auf Ereignisse des Push-Dienstes – unabhängig vom konkreten Anbieter. */
interface PushMessageHandler {
    /** Beim App-Start: Token des angemeldeten Nutzers auffrischen. */
    fun onAppStarted()

    fun onNewToken(token: String)

    /**
     * Eine Daten-Nachricht kam an (Vorder- oder Hintergrund). Angezeigt wird sie nur, wenn sie für den
     * gerade angemeldeten Nutzer bestimmt ist (`recipientId`).
     */
    fun onDataMessage(data: Map<String, String>)
}

class DefaultPushMessageHandler(
    private val registrar: PushTokenRegistrar,
    private val notifier: FriendDrinkNotifier,
    private val signedInUser: SignedInUserIdProvider,
    private val scope: CoroutineScope,
    private val onError: (Throwable) -> Unit = {},
) : PushMessageHandler {

    override fun onAppStarted() = launchSafely { registrar.registerCurrentToken() }

    override fun onNewToken(token: String) = launchSafely { registrar.registerToken(token) }

    override fun onDataMessage(data: Map<String, String>) {
        FriendDrinkPushParser.parse(data, signedInUser.signedInUserId())?.let(notifier::show)
    }

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
