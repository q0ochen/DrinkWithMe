package com.drinkwithme.core.speech

import com.drinkwithme.core.social.FriendDrinkEvent

/**
 * **Anzeige-Texte** zu Ereignissen von Freunden (mit Ziffern und Einheitenkürzeln) – gleich wie die vom Server
 * gesendeten Benachrichtigungen, damit der Abruf ohne Push dieselben Texte zeigt. Zum Vorlesen: [SpeechTexts].
 */
object EventTexts {
    /** Titel der Benachrichtigung: „Anna trinkt gerade“. */
    fun notificationTitle(authorName: String): String = "${authorName.trim()} trinkt gerade"

    /** Text der Benachrichtigung: „Apfelschorle (500 ml)“. */
    fun notificationBody(drinkName: String, volumeMilliliters: Int): String = "${drinkName.trim()} ($volumeMilliliters ml)"

    fun notificationTitle(event: FriendDrinkEvent): String = notificationTitle(event.author.displayName)

    fun notificationBody(event: FriendDrinkEvent): String = notificationBody(event.name, event.volumeMilliliters)

    /** Großer Satz auf dem Ereignis-Bildschirm: „Anna trinkt eine Apfelschorle.“ */
    fun headline(event: FriendDrinkEvent): String =
        "${event.author.displayName.trim()} trinkt ${GermanArticles.withIndefiniteArticle(event.name)}."
}
