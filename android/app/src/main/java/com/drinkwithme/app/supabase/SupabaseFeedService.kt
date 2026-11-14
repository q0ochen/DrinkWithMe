package com.drinkwithme.app.supabase

import com.drinkwithme.core.social.FeedService
import com.drinkwithme.core.social.FriendDrinkEvent
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Einzelnes Ereignis per ID (für den Ereignis-Bildschirm nach einem Tipp auf eine Benachrichtigung). */
fun interface EventLookup {
    suspend fun event(id: String): FriendDrinkEvent?
}

/**
 * Geteilte Getränke der Freunde (`drink_events`, RLS: Autor oder Freund des Autors), neueste zuerst, ohne die
 * eigenen. Merkt sich geladene Ereignisse kurz im Speicher, damit der Ereignis-Bildschirm sofort etwas zeigt.
 */
class SupabaseFeedService(
    private val api: SupabaseApi,
    private val profiles: ProfileLookup,
    private val currentUser: CurrentUserId,
) : FeedService, EventLookup {

    private val cache = ConcurrentHashMap<String, FriendDrinkEvent>()

    override suspend fun recentEvents(since: Instant?, limit: Int): List<FriendDrinkEvent> {
        val me = currentUser.currentUserId() ?: throw SocialException.NotSignedIn()
        val query = buildList {
            add("select" to COLUMNS)
            add("author_id" to "neq.$me")
            since?.let { add("consumed_at" to "gt.$it") }
            add("order" to "consumed_at.desc")
            add("limit" to limit.coerceIn(1, MAX_LIMIT).toString())
        }
        return withAuthors(SupabaseMappers.drinkEvents(api.get("rest/v1/drink_events", query).body))
    }

    override suspend fun event(id: String): FriendDrinkEvent? {
        cache[id]?.let { return it }
        val response = api.get("rest/v1/drink_events", listOf("select" to COLUMNS, "id" to "eq.$id", "limit" to "1"))
        return withAuthors(SupabaseMappers.drinkEvents(response.body)).firstOrNull()
    }

    private suspend fun withAuthors(rows: List<DrinkEventRow>): List<FriendDrinkEvent> {
        val authors = profiles.profiles(rows.map { it.authorId })
        return rows.map { row ->
            SupabaseMappers.toFriendDrinkEvent(row, authors[row.authorId]).also {
                if (cache.size >= MAX_CACHED) cache.clear()
                cache[it.id] = it
            }
        }
    }

    private companion object {
        const val COLUMNS = "id,author_id,name,icon,category,volume_ml,consumed_at"
        const val MAX_LIMIT = 200
        const val MAX_CACHED = 500
    }
}
