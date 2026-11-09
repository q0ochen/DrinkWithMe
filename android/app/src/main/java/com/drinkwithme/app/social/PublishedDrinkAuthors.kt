package com.drinkwithme.app.social

/**
 * Merkt sich auf dem Gerät, welches Konto ein Getränk in die Runden geteilt hat (Drink-ID → Nutzer-ID).
 *
 * „Meine Getränke“ gehören dem Gerät, nicht einem Konto: Nach einem Kontowechsel sieht (und löscht) man auch
 * Getränke, die ein anderes Konto geteilt hat. Aus den Runden entfernen darf sie nur dieses Konto – deshalb
 * muss das Löschen wissen, wer das Getränk geteilt hat (siehe [AuthorAwareDrinkPublisher]).
 */
interface PublishedDrinkAuthors : LocalUserDataEraser {
    /** Nutzer-ID des Kontos, das [drinkId] geteilt hat; `null`, wenn unbekannt (z. B. nie geteilt). */
    fun authorOf(drinkId: String): String?

    fun remember(drinkId: String, authorId: String)

    /** Vergisst [drinkId] – aber nur, wenn es von [authorId] geteilt wurde. */
    fun forget(drinkId: String, authorId: String)
}

/**
 * [PublishedDrinkAuthors] in einem [KeyValueStorage]: eine Zeile je Getränk, `Drink-ID<TAB>Nutzer-ID`.
 *
 * Höchstens [maximumEntries] Einträge – die ältesten fallen weg. Für sie gilt beim Löschen dann wieder das
 * angemeldete bzw. zuletzt angemeldete Konto, was ohne Kontowechsel ohnehin das richtige ist.
 */
class StoredPublishedDrinkAuthors(
    storage: KeyValueStorage,
    private val maximumEntries: Int = DEFAULT_MAXIMUM_ENTRIES,
) : PublishedDrinkAuthors {

    /** Paare `Drink-ID to Nutzer-ID`. */
    private val entries = StoredPairList(storage, KEY_AUTHORS)

    @Synchronized
    override fun authorOf(drinkId: String): String? =
        entries.read().lastOrNull { (entryDrinkId, _) -> entryDrinkId == drinkId }?.second

    @Synchronized
    override fun remember(drinkId: String, authorId: String) {
        if (!StoredPairList.isStorable(drinkId) || !StoredPairList.isStorable(authorId)) return
        val entry = drinkId to authorId
        val current = entries.read()
        if (entry in current) return
        // Eine Drink-ID gehört genau einem Konto: Ein älterer Eintrag dazu wird ersetzt.
        val others = current.filterNot { (entryDrinkId, _) -> entryDrinkId == drinkId }
        entries.write((others + entry).takeLast(maximumEntries))
    }

    @Synchronized
    override fun forget(drinkId: String, authorId: String) {
        val current = entries.read()
        val remaining = current - (drinkId to authorId)
        if (remaining.size != current.size) entries.write(remaining)
    }

    @Synchronized
    override fun eraseUser(userId: String) {
        val current = entries.read()
        val remaining = current.filterNot { (_, entryAuthorId) -> entryAuthorId == userId }
        if (remaining.size != current.size) entries.write(remaining)
    }

    companion object {
        const val DEFAULT_MAXIMUM_ENTRIES = 500
        private const val KEY_AUTHORS = "publishedDrinkAuthors"
    }
}
