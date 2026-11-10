package com.drinkwithme.core.bar

/** Lesender Zugriff auf die eigene Bar (Interface Segregation). */
interface BarReader {
    /** Alle Getränke der Bar, aufsteigend nach [BarItem.sortOrder]. */
    fun items(): List<BarItem>

    /** Ein Getränk per ID. Implementierungen dürfen das beschleunigen. */
    fun item(id: String): BarItem? = items().firstOrNull { it.id == id }
}

/** Schreibender Zugriff auf die eigene Bar (Interface Segregation). */
interface BarWriter {
    /** Legt an oder ersetzt das Getränk mit derselben ID. */
    fun save(item: BarItem)

    /** Entfernt das Getränk; unbekannte IDs werden ignoriert. Die Historie bleibt unverändert (Snapshot). */
    fun delete(id: String)

    /**
     * Neue Reihenfolge: Die genannten IDs erhalten [BarItem.sortOrder] 0, 1, 2 …; nicht genannte Getränke
     * folgen dahinter in ihrer bisherigen Reihenfolge. Unbekannte IDs werden ignoriert.
     */
    fun reorder(ids: List<String>)
}

/** Die eigene Bar – liegt nur auf dem Gerät. Implementierungen sind austauschbar (Dependency Inversion). */
interface BarRepository : BarReader, BarWriter
