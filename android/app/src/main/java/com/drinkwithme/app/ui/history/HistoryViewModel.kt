package com.drinkwithme.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drinkwithme.app.data.BooleanSetting
import com.drinkwithme.app.data.ChangeSignal
import com.drinkwithme.app.share.StatisticsCard
import com.drinkwithme.app.share.StatisticsCardFactory
import com.drinkwithme.app.speech.Speaker
import com.drinkwithme.app.stats.HistoryOverview
import com.drinkwithme.app.stats.HistoryTexts
import com.drinkwithme.app.ui.common.MessageQueue
import com.drinkwithme.core.log.DeleteEntryUseCase
import com.drinkwithme.core.log.DrinkEntry
import com.drinkwithme.core.log.HistoryReader
import com.drinkwithme.core.speech.SpeechTexts
import com.drinkwithme.core.stats.StatisticsCalculator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.ZoneId

/** Ein Tag in der Eintragsliste (neueste zuerst). */
data class EntryDay(val heading: String, val entries: List<DrinkEntry>)

data class HistoryUiState(
    val loading: Boolean = true,
    val overview: HistoryOverview? = null,
    val days: List<EntryDay> = emptyList(),
    val hiddenEntries: Int = 0,
    val showAllEntries: Boolean = false,
    val showAlcoholBands: Boolean = true,
    val zone: ZoneId = ZoneId.systemDefault(),
    val confirmDelete: DrinkEntry? = null,
    /** Vorschau vor dem Teilen als Bild; `null` = geschlossen. */
    val sharePreview: StatisticsCard? = null,
)

/**
 * Verlauf: Diagramme, Kennzahlen und Einträge aus der lokalen Historie (alles nur auf dem Gerät). Gerechnet wird
 * beim Anzeigen; die Zeitzone ist die aktuelle des Geräts.
 */
class HistoryViewModel(
    private val history: HistoryReader,
    private val historyChanges: ChangeSignal,
    private val deleteEntry: DeleteEntryUseCase,
    private val showAlcoholBands: BooleanSetting,
    private val speaker: Speaker,
    private val messages: MessageQueue,
    private val clock: Clock,
    private val zone: () -> ZoneId,
    private val io: CoroutineDispatcher,
) : ViewModel() {

    private val mutableState = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = mutableState.asStateFlow()
    private var allEntries: List<DrinkEntry> = emptyList()

    init {
        viewModelScope.launch {
            combine(historyChanges.revision, showAlcoholBands.value) { _, bands -> bands }.collect { bands ->
                load(bands)
            }
        }
    }

    /** Neu berechnen (z. B. nach Mitternacht oder Zeitzonenwechsel). */
    fun refresh() {
        viewModelScope.launch { load(showAlcoholBands.value.value) }
    }

    fun onToggleBands() = showAlcoholBands.set(!showAlcoholBands.value.value)

    fun onShowAllEntries() {
        mutableState.update { it.copy(showAllEntries = true) }
        rebuildDays()
    }

    fun onDeleteRequested(entry: DrinkEntry) = mutableState.update { it.copy(confirmDelete = entry) }

    fun onDeleteConfirmed(confirmed: Boolean) {
        val entry = state.value.confirmDelete ?: return
        mutableState.update { it.copy(confirmDelete = null) }
        if (!confirmed) return
        viewModelScope.launch {
            withContext(io) { deleteEntry(entry.id) }
            messages.show("${entry.name} gelöscht.")
        }
    }

    /** „Heute vorlesen“ – ausdrücklich gewünscht, daher auch mit Screenreader. */
    fun onReadTodayAloud() {
        val overview = state.value.overview ?: return
        speaker.speak(SpeechTexts.today(overview.today))
    }

    fun onShareRequested() {
        val overview = state.value.overview ?: return
        mutableState.update { it.copy(sharePreview = StatisticsCardFactory.create(overview, it.showAlcoholBands)) }
    }

    fun onShareDismissed() = mutableState.update { it.copy(sharePreview = null) }

    private suspend fun load(bands: Boolean) {
        val currentZone = zone()
        val (entries, overview) = withContext(io) {
            val all = history.entries(HistoryReader.EARLIEST, HistoryReader.LATEST)
            val today = clock.instant().atZone(currentZone).toLocalDate()
            all to HistoryOverview.from(StatisticsCalculator(clock, currentZone), all, today)
        }
        allEntries = entries
        mutableState.update { it.copy(loading = false, overview = overview, showAlcoholBands = bands, zone = currentZone) }
        rebuildDays()
    }

    private fun rebuildDays() {
        val current = state.value
        val overview = current.overview ?: return
        val newestFirst = allEntries.sortedByDescending { it.consumedAt }
        val visible = if (current.showAllEntries) newestFirst else newestFirst.take(INITIAL_ENTRIES)
        val days = visible
            .groupBy { it.consumedAt.atZone(current.zone).toLocalDate() }
            .map { (date, entries) -> EntryDay(HistoryTexts.dayHeading(date, overview.todayDate), entries) }
        mutableState.update { it.copy(days = days, hiddenEntries = newestFirst.size - visible.size) }
    }

    companion object {
        const val INITIAL_ENTRIES = 50
    }
}
