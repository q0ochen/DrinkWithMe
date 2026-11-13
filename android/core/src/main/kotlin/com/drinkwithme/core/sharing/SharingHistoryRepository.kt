package com.drinkwithme.core.sharing

import com.drinkwithme.core.log.DrinkEntry
import com.drinkwithme.core.log.HistoryRepository
import com.drinkwithme.core.social.EventPublisher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decorator um die lokale Historie: speichert und löscht **zuerst lokal und sofort** und teilt danach nach
 * der [SharingPolicy] im Hintergrund mit Freunden.
 *
 * - Lokales Speichern wartet nie auf das Netz; Netzwerkfehler gehen an [onError] und blockieren nichts.
 * - Netzwerkaufträge laufen nacheinander in Aufrufreihenfolge, damit z. B. ein schnelles „Rückgängig“
 *   das Veröffentlichen sicher wieder zurückzieht.
 * - Zurückgezogen wird nur, was geteilt war (keine unnötigen Serveraufrufe für private Einträge).
 *
 * Die Use Cases bleiben unverändert (Open/Closed); der Decorator ist überall austauschbar (Liskov).
 */
class SharingHistoryRepository(
    private val local: HistoryRepository,
    private val publisher: EventPublisher,
    private val policy: SharingPolicy,
    private val scope: CoroutineScope,
    private val onError: (Throwable) -> Unit = {},
) : HistoryRepository {

    private val pending = ConcurrentLinkedQueue<suspend () -> Unit>()
    private val draining = AtomicBoolean(false)

    override fun entries(from: Instant, to: Instant): List<DrinkEntry> = local.entries(from, to)

    override fun entry(id: String): DrinkEntry? = local.entry(id)

    override fun save(entry: DrinkEntry) {
        val publish = policy.shouldPublish(entry)
        val wasShared = !publish && local.entry(entry.id)?.shareWithFriends == true
        local.save(entry)
        when {
            publish -> enqueue { publisher.publish(entry) }
            wasShared && policy.canUnpublish() -> enqueue { publisher.unpublish(entry.id) }
        }
    }

    override fun delete(id: String) {
        val wasShared = local.entry(id)?.shareWithFriends == true
        local.delete(id)
        if (wasShared && policy.canUnpublish()) enqueue { publisher.unpublish(id) }
    }

    private fun enqueue(operation: suspend () -> Unit) {
        pending.add(operation)
        if (draining.compareAndSet(false, true)) {
            scope.launch { drain() }.invokeOnCompletion { cause -> if (cause != null) draining.set(false) }
        }
    }

    /** Arbeitet die Warteschlange ab; endet, sobald sie leer ist (kein dauerhaft laufender Job). */
    private suspend fun drain() {
        while (true) {
            val operation = pending.poll()
            if (operation == null) {
                draining.set(false)
                // Ein Auftrag kann zwischen poll() und set(false) hinzugekommen sein.
                if (pending.isEmpty() || !draining.compareAndSet(false, true)) return
                continue
            }
            try {
                operation()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                report(error)
            }
        }
    }

    private fun report(error: Exception) {
        try {
            onError(error)
        } catch (ignored: Exception) {
            // Ein fehlerhafter Fehler-Callback darf das Teilen nachfolgender Einträge nicht stoppen.
        }
    }
}
