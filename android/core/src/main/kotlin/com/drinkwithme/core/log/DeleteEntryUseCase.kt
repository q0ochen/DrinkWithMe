package com.drinkwithme.core.log

/** Löscht einen Eintrag aus der Historie (z. B. aus der Liste im Verlauf). */
class DeleteEntryUseCase(private val history: HistoryWriter) {
    operator fun invoke(id: String) = history.delete(id)
}
