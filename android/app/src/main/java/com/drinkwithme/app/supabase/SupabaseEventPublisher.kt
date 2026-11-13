package com.drinkwithme.app.supabase

import com.drinkwithme.core.log.DrinkEntry
import com.drinkwithme.core.sharing.toSharedDrink
import com.drinkwithme.core.social.EventPublisher
import org.json.JSONObject

/**
 * Teilt Einträge als `drink_events` und benachrichtigt die Freunde:
 * 1. `POST rest/v1/drink_events` mit `Prefer: resolution=ignore-duplicates` (wiederholtes Senden ist harmlos),
 *    nur mit den Feldern aus [com.drinkwithme.core.sharing.SharedDrink];
 * 2. Edge Function `notify-friends { eventId }`.
 *
 * `event_not_found` (schon benachrichtigt oder älter als 10 Minuten) und `rate_limited` beim Benachrichtigen
 * sind kein Fehler: Das Ereignis ist geteilt, Freunde sehen es beim nächsten Abruf.
 */
class SupabaseEventPublisher(private val api: SupabaseApi) : EventPublisher {

    override suspend fun publish(entry: DrinkEntry) {
        val drink = entry.toSharedDrink()
        api.post("rest/v1/drink_events", SupabaseMappers.drinkEventInsert(drink), prefer = PREFER_INSERT)
        try {
            api.function("notify-friends", JSONObject().put("eventId", drink.id).toString())
        } catch (error: SocialException.Server) {
            if (error.code !in IGNORED_NOTIFY_ERRORS) throw error
        }
    }

    override suspend fun unpublish(entryId: String) {
        api.delete("rest/v1/drink_events", listOf("id" to "eq.$entryId"))
    }

    companion object {
        const val PREFER_INSERT = "return=minimal,resolution=ignore-duplicates"
        private val IGNORED_NOTIFY_ERRORS = setOf("event_not_found", "rate_limited")
    }
}
