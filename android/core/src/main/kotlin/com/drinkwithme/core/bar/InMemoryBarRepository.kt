package com.drinkwithme.core.bar

/** Flüchtige, thread-sichere Bar für Tests, Vorschauen und als Vorlage für echte Speicher. */
class InMemoryBarRepository(initial: List<BarItem> = emptyList()) : BarRepository {
    private val lock = Any()
    private val itemsById = LinkedHashMap<String, BarItem>().apply { initial.forEach { put(it.id, it) } }

    override fun items(): List<BarItem> = synchronized(lock) { itemsById.values.sortedBy { it.sortOrder } }

    override fun item(id: String): BarItem? = synchronized(lock) { itemsById[id] }

    override fun save(item: BarItem) {
        synchronized(lock) { itemsById[item.id] = item }
    }

    override fun delete(id: String) {
        synchronized(lock) { itemsById.remove(id) }
    }

    override fun reorder(ids: List<String>) {
        synchronized(lock) {
            val current = itemsById.values.sortedBy { it.sortOrder }
            val requested = ids.distinct().mapNotNull { itemsById[it] }
            val requestedIds = requested.map { it.id }.toSet()
            val ordered = requested + current.filterNot { it.id in requestedIds }
            ordered.forEachIndexed { index, item -> itemsById[item.id] = item.copy(sortOrder = index) }
        }
    }
}
