package com.drinkwithme.app.social

/** Minimaler Schlüssel-Wert-Speicher (auf Android: SharedPreferences) – so bleibt die Logik ohne Android testbar. */
interface KeyValueStorage {
    fun getString(key: String): String?

    /** `null` entfernt den Eintrag. */
    fun putString(key: String, value: String?)
}

/**
 * Liste von Wertepaaren unter einem Schlüssel eines [KeyValueStorage]: eine Zeile je Paar, `erster<TAB>zweiter`.
 * Leere Werte und Werte mit Trennzeichen werden nicht gespeichert, beschädigte Zeilen beim Lesen übersprungen.
 * Nicht threadsicher – die Aufrufer synchronisieren selbst.
 */
internal class StoredPairList(private val storage: KeyValueStorage, private val key: String) {

    fun read(): List<Pair<String, String>> =
        storage.getString(key).orEmpty()
            .split(LINE_SEPARATOR)
            .mapNotNull { line ->
                val parts = line.split(FIELD_SEPARATOR)
                if (parts.size == 2 && parts.all { isStorable(it) }) parts[0] to parts[1] else null
            }

    /** Eine leere Liste entfernt den Schlüssel ganz. */
    fun write(pairs: List<Pair<String, String>>) {
        val encoded = pairs.joinToString(LINE_SEPARATOR) { (first, second) -> "$first$FIELD_SEPARATOR$second" }
        storage.putString(key, encoded.ifEmpty { null })
    }

    companion object {
        private const val FIELD_SEPARATOR = "\t"
        private const val LINE_SEPARATOR = "\n"

        /** Firebase-UIDs und Drink-IDs (UUIDs) enthalten keine Trennzeichen – alles andere wird nicht gespeichert. */
        fun isStorable(value: String): Boolean =
            value.isNotBlank() && FIELD_SEPARATOR !in value && LINE_SEPARATOR !in value
    }
}
