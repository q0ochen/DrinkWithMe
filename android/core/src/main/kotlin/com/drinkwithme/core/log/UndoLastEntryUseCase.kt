package com.drinkwithme.core.log

import java.time.Clock
import java.time.Duration

/** Ergebnis von [UndoLastEntryUseCase]. */
sealed interface UndoResult {
    /** Der Eintrag wurde gelöscht. */
    data class Undone(val entry: DrinkEntry) : UndoResult

    /** Der letzte Eintrag ist älter als das Zeitfenster – erst nach Rückfrage löschen ([UndoLastEntryUseCase.confirm]). */
    data class ConfirmationRequired(val entry: DrinkEntry) : UndoResult

    /** Es gibt keinen (ausreichend neuen) Eintrag. */
    data object NothingToUndo : UndoResult
}

/**
 * „Rückgängig“: löscht den zuletzt eingetragenen Eintrag **ohne Rückfrage**, wenn er höchstens [window]
 * (Standard 10 s) alt ist. Ältere Einträge (bis [lookback], Standard 24 h) liefern
 * [UndoResult.ConfirmationRequired]; die App fragt dann nach und ruft [confirm] auf.
 */
class UndoLastEntryUseCase(
    private val history: HistoryRepository,
    private val clock: Clock,
    private val window: Duration = DEFAULT_WINDOW,
    private val lookback: Duration = DEFAULT_LOOKBACK,
) {
    operator fun invoke(): UndoResult {
        val now = clock.instant()
        val last = history.entries(now.minus(lookback), now.plus(FUTURE_TOLERANCE))
            .sortedBy { it.consumedAt }
            .lastOrNull()
            ?: return UndoResult.NothingToUndo
        val age = Duration.between(last.consumedAt, now)
        if (age > window) return UndoResult.ConfirmationRequired(last)
        history.delete(last.id)
        return UndoResult.Undone(last)
    }

    /** Löscht genau den Eintrag, dessen Löschung der Nutzer nach Rückfrage bestätigt hat. */
    fun confirm(entry: DrinkEntry): UndoResult.Undone {
        history.delete(entry.id)
        return UndoResult.Undone(entry)
    }

    companion object {
        /** Zeitfenster, in dem ohne Rückfrage rückgängig gemacht wird. */
        val DEFAULT_WINDOW: Duration = Duration.ofSeconds(10)

        /** Wie weit zurück überhaupt nach einem „letzten Eintrag“ gesucht wird. */
        val DEFAULT_LOOKBACK: Duration = Duration.ofHours(24)

        /** Schließt den aktuellen Zeitpunkt (und kleine Uhrabweichungen) in die Suche ein. */
        private val FUTURE_TOLERANCE: Duration = Duration.ofSeconds(1)
    }
}
