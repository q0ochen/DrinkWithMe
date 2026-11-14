package com.drinkwithme.app.data

import org.json.JSONArray
import org.json.JSONException

/**
 * Merkt sich, zu welchen Ereignissen schon eine Benachrichtigung gezeigt wurde – gemeinsam für Push und Abruf,
 * damit kein Ereignis doppelt erscheint. Begrenzt auf die [capacity] neuesten IDs.
 */
class NotifiedEvents(
    private val store: KeyValueStore,
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val lock = Any()

    /** `true`, wenn [eventId] neu war und jetzt vorgemerkt ist; `false`, wenn es schon bekannt war. */
    fun markIfNew(eventId: String): Boolean = synchronized(lock) {
        val ids = load()
        if (eventId in ids) return false
        ids.add(eventId)
        save(ids)
        true
    }

    fun contains(eventId: String): Boolean = synchronized(lock) { eventId in load() }

    fun clear() = synchronized(lock) { store.putString(KEY, null) }

    private fun load(): LinkedHashSet<String> {
        val text = store.getString(KEY) ?: return LinkedHashSet()
        return try {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNullTo(LinkedHashSet()) { array.opt(it) as? String }
        } catch (error: JSONException) {
            LinkedHashSet()
        }
    }

    private fun save(ids: LinkedHashSet<String>) {
        val newest = ids.toList().takeLast(capacity)
        store.putString(KEY, JSONArray().apply { newest.forEach { put(it) } }.toString())
    }

    private companion object {
        const val KEY = "notified_event_ids"
        const val DEFAULT_CAPACITY = 500
    }
}
