package com.drinkwithme.app.social

import com.drinkwithme.core.domain.Drink
import com.drinkwithme.core.domain.DrinkVisibility
import com.drinkwithme.core.social.CurrentUserProvider
import com.drinkwithme.core.social.DrinkPublisher
import com.drinkwithme.core.social.UserProfile
import kotlinx.coroutines.flow.first

/**
 * Server-Zugriff auf die geteilten Kopien eigener Getränke in den Runden eines Kontos. Alle Aufrufe brauchen
 * die Anmeldung genau dieses Kontos (die Sicherheitsregeln prüfen die Nutzer-ID).
 */
interface GroupDrinkGateway {
    /** Runden, in denen [userId] gerade Mitglied ist – vom Server, damit eine gerade beigetretene Runde nicht fehlt. */
    suspend fun groupIdsOf(userId: String): List<String>

    /** Kehrt erst zurück, wenn der Server das Schreiben bestätigt hat (offline bleibt es bis dahin eingereiht). */
    suspend fun share(drink: Drink, author: UserProfile, groupIds: List<String>)

    /** Löscht die Kopie von [drinkId] des Kontos [authorId] in [groupIds]; fehlende Kopien sind kein Fehler. */
    suspend fun remove(authorId: String, drinkId: String, groupIds: List<String>)
}

/**
 * Teilt eigene Getränke in alle Runden des angemeldeten Kontos und merkt sich dabei, **welches Konto** es geteilt
 * hat ([authors]). Beim Löschen wird das Getränk aus den Runden genau dieses Kontos entfernt:
 * - Ist es angemeldet, sofort (schlägt das fehl, wird es vorgemerkt).
 * - Sonst (abgemeldet oder ein anderes Konto angemeldet) wird es für dieses Konto vorgemerkt ([pending]) und beim
 *   nächsten Anmelden desselben Kontos nachgeholt ([retryPendingUnpublishes], ausgelöst von [PendingUnpublishSync]).
 * - Ist das teilende Konto unbekannt (z. B. vor diesem Update geteilt), gilt wie bisher das angemeldete bzw.
 *   zuletzt angemeldete Konto.
 */
class AuthorAwareDrinkPublisher(
    private val users: CurrentUserProvider,
    private val groupDrinks: GroupDrinkGateway,
    private val authors: PublishedDrinkAuthors,
    private val pending: PendingUnpublishStore,
) : DrinkPublisher, PendingUnpublishRetrier {

    override suspend fun publish(drink: Drink) {
        if (drink.visibility != DrinkVisibility.FRIENDS) return
        val author = users.currentUser.first() ?: return
        val groupIds = groupDrinks.groupIdsOf(author.id)
        if (groupIds.isEmpty()) return
        // Vor dem Schreiben merken: Offline reiht Firestore den Batch ein und schreibt ihn später – auch dann, wenn
        // die App vorher beendet wird. Das Löschen muss den Autor trotzdem kennen.
        authors.remember(drink.id, author.id)
        groupDrinks.share(drink, author, groupIds)
    }

    override suspend fun unpublish(drinkId: String) {
        val signedInUserId = users.currentUser.first()?.id
        val authorId = authors.authorOf(drinkId) ?: signedInUserId
        when {
            // Autor unbekannt und niemand angemeldet: für das zuletzt angemeldete Konto vormerken (wie bisher).
            authorId == null -> pending.addForLastSignedInUser(drinkId)
            authorId == signedInUserId -> removeNow(authorId, drinkId)
            // Abgemeldet oder ein anderes Konto angemeldet: Löschen darf nur der Autor – bis zu seiner Anmeldung vormerken.
            else -> pending.add(authorId, drinkId)
        }
    }

    override suspend fun retryPendingUnpublishes(userId: String) {
        val drinkIds = pending.drinkIdsFor(userId)
        if (drinkIds.isEmpty()) return
        val groupIds = groupDrinks.groupIdsOf(userId)
        drinkIds.forEach { drinkId ->
            groupDrinks.remove(userId, drinkId, groupIds)
            pending.remove(userId, drinkId)
            authors.forget(drinkId, userId)
        }
    }

    private suspend fun removeNow(authorId: String, drinkId: String) {
        try {
            groupDrinks.remove(authorId, drinkId, groupDrinks.groupIdsOf(authorId))
        } catch (error: Exception) {
            // z. B. kein Netz oder abgebrochen: beim nächsten Start bzw. Anmelden erneut versuchen.
            pending.add(authorId, drinkId)
            throw error
        }
        authors.forget(drinkId, authorId)
    }
}
