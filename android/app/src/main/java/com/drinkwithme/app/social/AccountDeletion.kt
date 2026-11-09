package com.drinkwithme.app.social

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Stand der Kontolöschung. */
sealed interface AccountDeletionState {
    data object Idle : AccountDeletionState

    data object Running : AccountDeletionState

    /** Konto gelöscht, lokal aufgeräumt und abgemeldet. */
    data object Deleted : AccountDeletionState

    /** Löschen fehlgeschlagen – der Nutzer ist weiterhin angemeldet. */
    data class Failed(val error: Throwable) : AccountDeletionState
}

/**
 * Kontolöschung, die unabhängig von der Oberfläche zu Ende läuft: Server-Aufruf, lokales Aufräumen und Abmelden
 * dürfen nicht abbrechen, nur weil der Bildschirm (ViewModel/Activity) geschlossen wird – sonst bliebe die App mit
 * einem auf dem Server schon gelöschten Konto angemeldet. Die Oberfläche startet sie nur und beobachtet [state].
 */
interface AccountDeletion {
    val state: StateFlow<AccountDeletionState>

    /** Startet die Löschung – nicht, solange schon eine läuft. */
    fun start()

    /** Die Oberfläche hat das Ergebnis ([AccountDeletionState.Deleted] bzw. [AccountDeletionState.Failed]) übernommen. */
    fun resultShown()
}

/**
 * Führt [AccountService.deleteAccount] im App-weiten [scope] aus, der so lange lebt wie die App. Das Ergebnis
 * bleibt stehen, bis eine Oberfläche es übernommen hat – auch ein nach dem Start neu erstellter Bildschirm sieht es.
 */
class ApplicationScopedAccountDeletion(
    private val accountService: AccountService,
    private val scope: CoroutineScope,
) : AccountDeletion {

    private val _state = MutableStateFlow<AccountDeletionState>(AccountDeletionState.Idle)
    override val state: StateFlow<AccountDeletionState> = _state.asStateFlow()

    override fun start() {
        val previous = _state.getAndUpdate { current ->
            if (current == AccountDeletionState.Running) current else AccountDeletionState.Running
        }
        if (previous == AccountDeletionState.Running) return

        scope.launch {
            // Alles abfangen: Ein Fehler im App-weiten Scope würde sonst die App beenden.
            _state.value = try {
                accountService.deleteAccount()
                AccountDeletionState.Deleted
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                AccountDeletionState.Failed(error)
            }
        }.invokeOnCompletion { cause ->
            // Nur wenn der App-weite Scope selbst beendet wurde: nicht für immer „läuft“ anzeigen.
            if (cause != null) _state.compareAndSet(AccountDeletionState.Running, AccountDeletionState.Idle)
        }
    }

    override fun resultShown() = _state.update { current ->
        if (current == AccountDeletionState.Running) current else AccountDeletionState.Idle
    }
}
