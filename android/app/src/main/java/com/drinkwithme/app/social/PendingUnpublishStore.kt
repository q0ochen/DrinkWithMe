package com.drinkwithme.app.social

/**
 * Vorgemerkte Löschungen geteilter Getränke (Nutzer-ID + Drink-ID).
 *
 * Aus den Runden entfernen darf ein geteiltes Getränk nur das Konto, das es geteilt hat. Wird es gelöscht,
 * während dieses Konto nicht angemeldet ist (abgemeldet oder ein anderes Konto angemeldet), wird es für dieses
 * Konto vorgemerkt und beim nächsten Anmelden **desselben** Kontos nachgeholt (siehe [PendingUnpublishSync]).
 * Ist das teilende Konto unbekannt, gilt das zuletzt angemeldete ([addForLastSignedInUser]).
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

/**
 * [PendingUnpublishStore] in einem [KeyValueStorage]: eine Zeile je Eintrag, `Nutzer-ID<TAB>Drink-ID`.
 * Höchstens [maximumEntries] Einträge (die ältesten fallen weg), damit der Speicher nicht unbegrenzt wächst.
 */
class StoredPendingUnpublishStore(
    private val storage: KeyValueStorage,
    private val maximumEntries: Int = DEFAULT_MAXIMUM_ENTRIES,
) : PendingUnpublishStore {

    /** Paare `Nutzer-ID to Drink-ID`. */
    private val entries = StoredPairList(storage, KEY_ENTRIES)

    @Synchronized
    override fun lastSignedInUserId(): String? =
        storage.getString(KEY_LAST_USER)?.takeIf { StoredPairList.isStorable(it) }

    @Synchronized
    override fun rememberSignedInUser(userId: String) {
        if (StoredPairList.isStorable(userId) && lastSignedInUserId() != userId) storage.putString(KEY_LAST_USER, userId)
    }

    @Synchronized
    override fun add(userId: String, drinkId: String) {
        if (!StoredPairList.isStorable(userId) || !StoredPairList.isStorable(drinkId)) return
        val entry = userId to drinkId
        val current = entries.read()
        if (entry in current) return
        entries.write((current + entry).takeLast(maximumEntries))
    }

    @Synchronized
    override fun addForLastSignedInUser(drinkId: String): Boolean {
        val userId = lastSignedInUserId() ?: return false
        add(userId, drinkId)
        return true
    }

    @Synchronized
    override fun drinkIdsFor(userId: String): List<String> =
        entries.read().filter { (entryUserId, _) -> entryUserId == userId }.map { (_, drinkId) -> drinkId }

    @Synchronized
    override fun remove(userId: String, drinkId: String) {
        val current = entries.read()
        val remaining = current - (userId to drinkId)
        if (remaining.size != current.size) entries.write(remaining)
    }

    @Synchronized
    override fun eraseUser(userId: String) {
        val current = entries.read()
        val remaining = current.filterNot { (entryUserId, _) -> entryUserId == userId }
        if (remaining.size != current.size) entries.write(remaining)
        if (lastSignedInUserId() == userId) storage.putString(KEY_LAST_USER, null)
    }

    companion object {
        const val DEFAULT_MAXIMUM_ENTRIES = 200
        private const val KEY_LAST_USER = "lastSignedInUserId"
        private const val KEY_ENTRIES = "pendingUnpublishes"
    }
}
