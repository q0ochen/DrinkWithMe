package com.drinkwithme.app.social

/** Lokale Daten, die zu einer Nutzer-ID gehören und beim Löschen des Kontos mit verschwinden. */
fun interface LocalUserDataEraser {
    fun eraseUser(userId: String)
}

/**
 * Vorgemerkte Löschungen geteilter Getränke (Nutzer-ID + Drink-ID).
 *
 * Wird ein geteiltes Getränk im abgemeldeten Zustand gelöscht, fehlt die Berechtigung, es aus den Runden
 * zu entfernen. Es wird dann für den zuletzt angemeldeten Nutzer vorgemerkt und beim nächsten Anmelden
 * **desselben** Nutzers nachgeholt (siehe [PendingUnpublishSync]).
 */
interface PendingUnpublishStore : LocalUserDataEraser {
    /** Zuletzt angemeldeter Nutzer – bleibt auch nach dem Abmelden bekannt. */
    fun lastSignedInUserId(): String?

    fun rememberSignedInUser(userId: String)

    fun add(userId: String, drinkId: String)

    /**
     * Merkt [drinkId] für den zuletzt angemeldeten Nutzer vor.
     * @return `false`, wenn noch nie jemand angemeldet war – dann wurde auch nichts geteilt.
     */
    fun addForLastSignedInUser(drinkId: String): Boolean

    fun drinkIdsFor(userId: String): List<String>

    fun remove(userId: String, drinkId: String)
}

/** Minimaler Schlüssel-Wert-Speicher (auf Android: SharedPreferences) – so bleibt die Logik ohne Android testbar. */
interface KeyValueStorage {
    fun getString(key: String): String?

    /** `null` entfernt den Eintrag. */
    fun putString(key: String, value: String?)
}

/**
 * [PendingUnpublishStore] in einem [KeyValueStorage]: eine Zeile je Eintrag, `Nutzer-ID<TAB>Drink-ID`.
 * Höchstens [maximumEntries] Einträge (die ältesten fallen weg), damit der Speicher nicht unbegrenzt wächst.
 */
class StoredPendingUnpublishStore(
    private val storage: KeyValueStorage,
    private val maximumEntries: Int = DEFAULT_MAXIMUM_ENTRIES,
) : PendingUnpublishStore {

    @Synchronized
    override fun lastSignedInUserId(): String? = storage.getString(KEY_LAST_USER)?.takeIf { isStorable(it) }

    @Synchronized
    override fun rememberSignedInUser(userId: String) {
        if (isStorable(userId) && lastSignedInUserId() != userId) storage.putString(KEY_LAST_USER, userId)
    }

    @Synchronized
    override fun add(userId: String, drinkId: String) {
        if (!isStorable(userId) || !isStorable(drinkId)) return
        val entry = Entry(userId, drinkId)
        val entries = readEntries()
        if (entry in entries) return
        writeEntries((entries + entry).takeLast(maximumEntries))
    }

    @Synchronized
    override fun addForLastSignedInUser(drinkId: String): Boolean {
        val userId = lastSignedInUserId() ?: return false
        add(userId, drinkId)
        return true
    }

    @Synchronized
    override fun drinkIdsFor(userId: String): List<String> =
        readEntries().filter { it.userId == userId }.map { it.drinkId }

    @Synchronized
    override fun remove(userId: String, drinkId: String) {
        val entries = readEntries()
        val remaining = entries - Entry(userId, drinkId)
        if (remaining.size != entries.size) writeEntries(remaining)
    }

    @Synchronized
    override fun eraseUser(userId: String) {
        val entries = readEntries()
        val remaining = entries.filterNot { it.userId == userId }
        if (remaining.size != entries.size) writeEntries(remaining)
        if (lastSignedInUserId() == userId) storage.putString(KEY_LAST_USER, null)
    }

    private fun readEntries(): List<Entry> =
        storage.getString(KEY_ENTRIES).orEmpty()
            .split(LINE_SEPARATOR)
            .mapNotNull { line ->
                val parts = line.split(FIELD_SEPARATOR)
                if (parts.size == 2 && parts.all { isStorable(it) }) Entry(parts[0], parts[1]) else null
            }

    private fun writeEntries(entries: List<Entry>) {
        val encoded = entries.joinToString(LINE_SEPARATOR) { "${it.userId}$FIELD_SEPARATOR${it.drinkId}" }
        storage.putString(KEY_ENTRIES, encoded.ifEmpty { null })
    }

    /** Firebase-UIDs und Drink-IDs (UUIDs) enthalten keine Trennzeichen – alles andere wird nicht gespeichert. */
    private fun isStorable(value: String): Boolean =
        value.isNotBlank() && FIELD_SEPARATOR !in value && LINE_SEPARATOR !in value

    private data class Entry(val userId: String, val drinkId: String)

    companion object {
        const val DEFAULT_MAXIMUM_ENTRIES = 200
        private const val KEY_LAST_USER = "lastSignedInUserId"
        private const val KEY_ENTRIES = "pendingUnpublishes"
        private const val FIELD_SEPARATOR = "\t"
        private const val LINE_SEPARATOR = "\n"
    }
}
