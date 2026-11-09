package com.drinkwithme.app.social

import com.drinkwithme.core.social.CurrentUserProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Entfernt die vorgemerkten Getränke eines Nutzers aus seinen Runden – braucht dessen Anmeldung. */
fun interface PendingUnpublishRetrier {
    suspend fun retryPendingUnpublishes(userId: String)
}

/**
 * Hält fest, wer zuletzt angemeldet war (damit das abgemeldete Löschen eines Getränks mit unbekanntem Autor weiß,
 * für wen es vormerken muss), und holt vorgemerkte Löschungen eines Kontos nach, sobald genau dieses Konto angemeldet
 * ist – beim App-Start und nach jeder Anmeldung. Meldet sich der Nutzer währenddessen ab, wird abgebrochen; die
 * Einträge bleiben dann vorgemerkt.
 */
class PendingUnpublishSync(
    private val users: CurrentUserProvider,
    private val store: PendingUnpublishStore,
    private val retrier: PendingUnpublishRetrier,
    private val onError: (Throwable) -> Unit = {},
) {

    fun start(scope: CoroutineScope): Job = scope.launch {
        users.currentUser
            .map { it?.id }
            .distinctUntilChanged()
            .catch { error -> onError(error) }
            .collectLatest { userId ->
                if (userId == null) return@collectLatest
                // Alles abfangen: Ein Fehler im App-weiten Scope würde sonst die App beenden.
                try {
                    store.rememberSignedInUser(userId)
                    if (store.drinkIdsFor(userId).isNotEmpty()) retrier.retryPendingUnpublishes(userId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    onError(error)
                }
            }
    }
}
