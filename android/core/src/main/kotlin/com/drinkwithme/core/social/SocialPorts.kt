package com.drinkwithme.core.social

import com.drinkwithme.core.domain.Drink
import kotlinx.coroutines.flow.Flow

// Schnittstellen („Ports“) der Trinkrunden. Der Kern kennt nur diese Abstraktionen;
// die Firebase-Implementierungen liegen im App-Modul (Dependency Inversion).
// Die Schnittstellen sind bewusst klein gehalten (Interface Segregation).

/** Liefert den aktuell angemeldeten Nutzer (oder `null`). */
interface CurrentUserProvider {
    val currentUser: Flow<UserProfile?>
}

/** Liest die Trinkrunden des angemeldeten Nutzers (live). */
interface GroupReader {
    fun observeMyGroups(): Flow<List<DrinkGroup>>
}

/** Erstellen, Beitreten und Verlassen von Trinkrunden. */
interface GroupMembershipService {
    suspend fun createGroup(name: String): DrinkGroup

    /** @throws GroupNotFoundException wenn es keine Runde mit diesem Code gibt. */
    suspend fun joinGroup(code: InviteCode): DrinkGroup

    suspend fun leaveGroup(groupId: String)
}

/** Liest die geteilten Getränke einer Trinkrunde (live, neueste zuerst). */
interface FeedReader {
    fun observeFeed(groupId: String, limit: Int): Flow<List<FeedEntry>>
}

/** Teilt eigene Getränke mit allen Trinkrunden des angemeldeten Nutzers. */
interface DrinkPublisher {
    /** Ohne Anmeldung oder ohne Trinkrunde passiert nichts. */
    suspend fun publish(drink: Drink)

    /** Entfernt das Getränk wieder aus allen Trinkrunden des Nutzers. */
    suspend fun unpublish(drinkId: String)
}
