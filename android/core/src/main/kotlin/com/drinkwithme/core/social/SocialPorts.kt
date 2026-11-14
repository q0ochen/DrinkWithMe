package com.drinkwithme.core.social

import com.drinkwithme.core.log.DrinkEntry
import kotlinx.coroutines.flow.Flow
import java.time.Instant

// Ports der Vernetzung. Der Kern kennt nur diese kleinen Schnittstellen (Interface Segregation);
// die Supabase-Implementierungen liegen im App-Modul (Dependency Inversion).

/** Liefert den angemeldeten Nutzer oder `null` (nicht angemeldet). */
interface SessionProvider { val currentUser: Flow<UserProfile?> }

/** Freunde, Anfragen und Einladungs-Links. */
interface FriendsService {
    suspend fun friends(): List<Friend>
    suspend fun requests(): List<FriendRequest>
    suspend fun sendRequestByEmail(email: String)      // Antwort immer neutral (kein Hinweis, ob die Adresse existiert)
    suspend fun respond(requestId: String, accept: Boolean)
    suspend fun cancel(requestId: String)
    suspend fun createInviteLink(): String              // vollständige https-URL
    suspend fun acceptInvite(token: String): UserProfile
    suspend fun removeFriend(userId: String)
}

/** Geteilte Getränke der Freunde, neueste zuerst; `since` = nur neuere als dieser Zeitpunkt. */
interface FeedService { suspend fun recentEvents(since: Instant?, limit: Int = 100): List<FriendDrinkEvent> }

/**
 * Teilt eigene Einträge bzw. zieht sie zurück. Implementierungen senden nur die Felder aus
 * `com.drinkwithme.core.sharing.SharedDrink` (siehe `toSharedDrink()`).
 */
interface EventPublisher { suspend fun publish(entry: DrinkEntry); suspend fun unpublish(entryId: String) }
