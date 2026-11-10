package com.drinkwithme.app.ui.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Kurze Meldungen für die Snackbar, der Reihe nach. Gemeinsam für alle Bildschirme. */
class MessageQueue {
    private val queue = MutableStateFlow<List<String>>(emptyList())

    val messages: StateFlow<List<String>> = queue.asStateFlow()

    fun show(message: String) = queue.update { it + message }

    /** Nach dem Anzeigen entfernen. */
    fun consume(message: String) = queue.update { current ->
        val index = current.indexOf(message)
        if (index < 0) current else current.toMutableList().apply { removeAt(index) }
    }
}

/** Zähler, der steigt, wenn sich Freundschaften geändert haben (z. B. Einladung angenommen). */
class RefreshSignal {
    private val counter = MutableStateFlow(0L)
    val value: StateFlow<Long> = counter.asStateFlow()
    fun trigger() = counter.update { it + 1 }
}
