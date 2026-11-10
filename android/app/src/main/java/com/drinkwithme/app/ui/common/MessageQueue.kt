package com.drinkwithme.app.ui.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/** Eine Meldung in der Warteschlange. [id] ist eindeutig, auch wenn derselbe Text mehrmals hintereinander kommt. */
data class QueuedMessage(val id: Long, val text: String)

/**
 * Kurze Meldungen für die Snackbar, der Reihe nach. Gemeinsam für alle Bildschirme. Die Snackbar richtet sich nach
 * der [QueuedMessage.id] (nicht nach dem Text): Zwei gleiche Meldungen hintereinander dürfen sie nicht anhalten.
 */
class MessageQueue {
    private val nextId = AtomicLong()
    private val queue = MutableStateFlow<List<QueuedMessage>>(emptyList())

    val messages: StateFlow<List<QueuedMessage>> = queue.asStateFlow()

    /** Nur die Texte der wartenden Meldungen (z. B. für Tests). */
    val texts: List<String> get() = queue.value.map { it.text }

    fun show(message: String) {
        val queued = QueuedMessage(nextId.incrementAndGet(), message)
        queue.update { it + queued }
    }

    /** Nach dem Anzeigen entfernen. */
    fun consume(id: Long) = queue.update { current -> current.filterNot { it.id == id } }
}

/** Zähler, der steigt, wenn sich Freundschaften geändert haben (z. B. Einladung angenommen). */
class RefreshSignal {
    private val counter = MutableStateFlow(0L)
    val value: StateFlow<Long> = counter.asStateFlow()
    fun trigger() = counter.update { it + 1 }
}
