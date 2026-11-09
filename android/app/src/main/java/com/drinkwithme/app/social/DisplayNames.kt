package com.drinkwithme.app.social

/** Bereinigt Anzeigenamen so, wie die Sicherheitsregeln sie erwarten (1–50 Zeichen). */
object DisplayNames {
    const val MAXIMUM_LENGTH = 50
    const val UNKNOWN = "Unbekannt"

    fun sanitize(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return UNKNOWN
        if (trimmed.length <= MAXIMUM_LENGTH) return trimmed
        val cut = trimmed.take(MAXIMUM_LENGTH)
        // Kein halbes Emoji (Surrogatpaar) am Ende stehen lassen.
        val safe = if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut
        return safe.trimEnd().ifEmpty { UNKNOWN }
    }
}
