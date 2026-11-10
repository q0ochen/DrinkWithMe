package com.drinkwithme.app.data

import com.drinkwithme.core.bar.BarItem
import com.drinkwithme.core.bar.BarRepository
import com.drinkwithme.core.log.DrinkEntry
import com.drinkwithme.core.log.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.time.Instant

/** Meldet Änderungen an lokalen Daten, damit Bildschirme neu laden (Zähler steigt bei jedem Schreiben). */
interface ChangeSignal {
    val revision: StateFlow<Long>
}

/**
 * Liste in einer JSON-Datei mit Zwischenspeicher im Arbeitsspeicher. Gelesen wird einmal (tolerant), danach
 * schreibt jede Änderung die ganze Datei atomar. Thread-sicher; Aufrufer sollten außerhalb des UI-Threads
 * arbeiten (Dateizugriff).
 */
open class JsonFileList<T>(
    file: File,
    private val codec: LocalListJson<T>,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val onError: (Throwable) -> Unit = {},
) : ChangeSignal {
    private val store = AtomicTextFile(file)
    private val lock = Any()
    private var cache: List<T>? = null
    private val revisionFlow = MutableStateFlow(0L)

    override val revision: StateFlow<Long> = revisionFlow.asStateFlow()

    protected fun snapshot(): List<T> = synchronized(lock) { loaded() }

    /** Ändert die Liste und speichert sie. Schlägt das Speichern fehl, bleibt die Änderung im Speicher. */
    protected fun update(transform: (List<T>) -> List<T>) {
        synchronized(lock) {
            val next = transform(loaded())
            cache = next
            try {
                store.write(codec.encode(next))
            } catch (error: IOException) {
                onError(error)
            }
        }
        revisionFlow.value = revisionFlow.value + 1
    }

    private fun loaded(): List<T> {
        cache?.let { return it }
        val text = store.readOrNull()
        val items = when (val result = text?.let(codec::decode)) {
            null -> emptyList()
            is DecodeResult.Decoded -> result.items
            DecodeResult.Corrupt -> {
                store.quarantine(nowMillis())
                emptyList()
            }
        }
        cache = items
        return items
    }
}

/** Die eigene Bar in `bar.json`. */
class JsonFileBarRepository(
    file: File,
    onError: (Throwable) -> Unit = {},
) : JsonFileList<BarItem>(file, BarJson, onError = onError), BarRepository {

    override fun items(): List<BarItem> = snapshot().sortedBy { it.sortOrder }

    override fun item(id: String): BarItem? = snapshot().firstOrNull { it.id == id }

    override fun save(item: BarItem) = update { items -> items.filterNot { it.id == item.id } + item }

    override fun delete(id: String) = update { items -> items.filterNot { it.id == id } }

    override fun reorder(ids: List<String>) = update { items ->
        val current = items.sortedBy { it.sortOrder }
        val byId = current.associateBy { it.id }
        val requested = ids.distinct().mapNotNull { byId[it] }
        val requestedIds = requested.map { it.id }.toSet()
        (requested + current.filterNot { it.id in requestedIds }).mapIndexed { index, item -> item.copy(sortOrder = index) }
    }
}

/** Die vollständige Trinkhistorie in `history.json` – nur auf dem Gerät. */
class JsonFileHistoryRepository(
    file: File,
    onError: (Throwable) -> Unit = {},
) : JsonFileList<DrinkEntry>(file, HistoryJson, onError = onError), HistoryRepository {

    override fun entries(from: Instant, to: Instant): List<DrinkEntry> = snapshot()
        .filter { !it.consumedAt.isBefore(from) && it.consumedAt.isBefore(to) }
        .sortedBy { it.consumedAt }

    override fun entry(id: String): DrinkEntry? = snapshot().firstOrNull { it.id == id }

    override fun save(entry: DrinkEntry) = update { entries -> entries.filterNot { it.id == entry.id } + entry }

    override fun delete(id: String) = update { entries -> entries.filterNot { it.id == id } }
}
