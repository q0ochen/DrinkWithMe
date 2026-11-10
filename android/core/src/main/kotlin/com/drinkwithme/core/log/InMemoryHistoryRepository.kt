package com.drinkwithme.core.log

import java.time.Instant

/** Flüchtige, thread-sichere Historie für Tests, Vorschauen und als Vorlage für echte Speicher. */
class InMemoryHistoryRepository(initial: List<DrinkEntry> = emptyList()) : HistoryRepository {
    private val lock = Any()
    private val entriesById = LinkedHashMap<String, DrinkEntry>().apply { initial.forEach { put(it.id, it) } }

    override fun entries(from: Instant, to: Instant): List<DrinkEntry> = synchronized(lock) {
        entriesById.values
            .filter { !it.consumedAt.isBefore(from) && it.consumedAt.isBefore(to) }
            .sortedBy { it.consumedAt }
    }

    override fun entry(id: String): DrinkEntry? = synchronized(lock) { entriesById[id] }

    override fun save(entry: DrinkEntry) {
        synchronized(lock) { entriesById[entry.id] = entry }
    }

    override fun delete(id: String) {
        synchronized(lock) { entriesById.remove(id) }
    }
}
