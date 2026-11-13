package com.drinkwithme.core.log

import java.time.Instant

/** Lesender Zugriff auf die Trinkhistorie (Interface Segregation). */
interface HistoryReader {
    /** Alle Einträge mit `from ≤ consumedAt < to`, aufsteigend nach [DrinkEntry.consumedAt] sortiert. */
    fun entries(from: Instant, to: Instant): List<DrinkEntry>

    /**
     * Ein Eintrag per ID oder `null`. Die Standardumsetzung durchsucht den gesamten Zeitraum;
     * Implementierungen mit Index dürfen das beschleunigen.
     */
    fun entry(id: String): DrinkEntry? = entries(EARLIEST, LATEST).firstOrNull { it.id == id }

    companion object {
        /** Frühester sinnvoller Zeitpunkt für „alle Einträge“. */
        val EARLIEST: Instant = Instant.EPOCH

        /** Spätester Zeitpunkt für „alle Einträge“ – bewusst nicht [Instant.MAX], damit er in Millisekunden passt. */
        val LATEST: Instant = Instant.parse("3000-01-01T00:00:00Z")
    }
}

/** Schreibender Zugriff auf die Trinkhistorie (Interface Segregation). */
interface HistoryWriter {
    /** Legt an oder ersetzt den Eintrag mit derselben ID. Wartet nie auf das Netz. */
    fun save(entry: DrinkEntry)

    /** Entfernt den Eintrag; unbekannte IDs werden ignoriert. */
    fun delete(id: String)
}

/** Die vollständige Trinkhistorie – liegt nur auf dem Gerät. */
interface HistoryRepository : HistoryReader, HistoryWriter
