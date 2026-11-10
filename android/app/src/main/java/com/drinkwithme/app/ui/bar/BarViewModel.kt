package com.drinkwithme.app.ui.bar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drinkwithme.app.data.AppSettings
import com.drinkwithme.app.data.ChangeSignal
import com.drinkwithme.app.speech.AnnouncementPolicy
import com.drinkwithme.app.speech.ScreenReaderDetector
import com.drinkwithme.app.speech.Speaker
import com.drinkwithme.app.stats.HistoryTexts
import com.drinkwithme.app.ui.common.MessageQueue
import com.drinkwithme.app.voice.VoiceInputAvailability
import com.drinkwithme.app.voice.VoiceRequest
import com.drinkwithme.app.voice.VoiceRequestFactory
import com.drinkwithme.core.bar.BarItem
import com.drinkwithme.core.bar.BarRepository
import com.drinkwithme.core.bar.CatalogEntry
import com.drinkwithme.core.bar.DefaultBarCatalog
import com.drinkwithme.core.bar.SaveBarItemUseCase
import com.drinkwithme.core.log.DrinkEntry
import com.drinkwithme.core.log.LogDrinkFromBarUseCase
import com.drinkwithme.core.log.UndoLastEntryUseCase
import com.drinkwithme.core.log.UndoResult
import com.drinkwithme.core.sharing.AlcoholSharingConsent
import com.drinkwithme.core.sharing.SharingPolicy
import com.drinkwithme.core.social.FriendDrinkEvent
import com.drinkwithme.core.speech.EventTexts
import com.drinkwithme.core.speech.SpeechTexts
import com.drinkwithme.core.stats.TodaySummary
import com.drinkwithme.core.voice.VoiceCommand
import com.drinkwithme.core.voice.VoiceMatch
import com.drinkwithme.core.voice.VoiceMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bestätigung nach dem Eintragen, mit „Rückgängig“ für [BarViewModel.UNDO_VISIBLE_MILLIS]. */
data class LoggedConfirmation(val entryId: String, val text: String, val sharingHint: String?)

/** Eintragen wartet auf die einmalige Frage zum Teilen alkoholischer Getränke. */
data class PendingLog(val item: BarItem, val shareOverride: Boolean?)

/** Formular „Getränk bearbeiten/hinzufügen“. */
data class EditorState(val form: DrinkForm, val errors: List<String> = emptyList(), val confirmDelete: Boolean = false)

data class BarUiState(
    val items: List<BarItem> = emptyList(),
    val voiceAvailable: Boolean = false,
    val confirmation: LoggedConfirmation? = null,
    val ambiguity: VoiceMatch.Ambiguous? = null,
    val pendingConsent: PendingLog? = null,
    val confirmUndo: DrinkEntry? = null,
    val editor: EditorState? = null,
    /** Vorschläge aus dem Katalog, die noch nicht in der Bar sind; `null` = Auswahl geschlossen. */
    val catalogChoices: List<CatalogEntry>? = null,
)

/**
 * Bar: ein Tipp = getrunken (Zeitpunkt jetzt), Bestätigung per kurzer Ansage und großem „Rückgängig“ für 10 s,
 * einmalige Frage zum Teilen alkoholischer Getränke, Spracheingabe, Bar bearbeiten.
 */
class BarViewModel(
    private val bar: BarRepository,
    private val barChanges: ChangeSignal,
    private val logDrink: LogDrinkFromBarUseCase,
    private val undoLast: UndoLastEntryUseCase,
    private val saveItem: SaveBarItemUseCase,
    private val policy: SharingPolicy,
    private val settings: AppSettings,
    private val isSignedIn: () -> Boolean,
    private val speaker: Speaker,
    private val screenReader: ScreenReaderDetector,
    private val voiceMatcher: VoiceMatcher,
    private val voiceAvailability: VoiceInputAvailability,
    private val todaySummary: suspend () -> TodaySummary,
    private val latestFriendEvent: suspend () -> FriendDrinkEvent?,
    private val formMapper: DrinkFormMapper,
    private val messages: MessageQueue,
    private val sdkInt: Int,
    private val io: CoroutineDispatcher,
) : ViewModel() {

    private val mutableState = MutableStateFlow(BarUiState())
    val state: StateFlow<BarUiState> = mutableState.asStateFlow()
    private var confirmationJob: Job? = null

    init {
        viewModelScope.launch {
            barChanges.revision.collect { reload() }
        }
        viewModelScope.launch {
            val available = withContext(io) { voiceAvailability.isAvailable() }
            mutableState.update { it.copy(voiceAvailable = available) }
        }
    }

    // ---------- Eintragen ----------

    fun onDrinkTapped(item: BarItem) = requestLog(item, shareOverride = null)

    /** „Privat eintragen“: dieser Eintrag wird nicht geteilt. */
    fun onLogPrivately(item: BarItem) = requestLog(item, shareOverride = false)

    /** Antwort auf „Alkoholische Getränke mit Freunden teilen?“; `null` = ohne Antwort geschlossen (später erneut fragen). */
    fun onConsentAnswered(allow: Boolean?) {
        val pending = state.value.pendingConsent ?: return
        mutableState.update { it.copy(pendingConsent = null) }
        if (allow != null) {
            settings.setAlcoholConsent(if (allow) AlcoholSharingConsent.ALLOWED else AlcoholSharingConsent.DENIED)
        }
        log(pending.item, pending.shareOverride)
    }

    fun onUndo() {
        viewModelScope.launch {
            when (val result = withContext(io) { undoLast() }) {
                is UndoResult.Undone -> undone(result.entry)
                is UndoResult.ConfirmationRequired -> mutableState.update { it.copy(confirmUndo = result.entry) }
                UndoResult.NothingToUndo -> messages.show(NOTHING_TO_UNDO)
            }
        }
    }

    /** Rückfrage bei älteren Einträgen („Letzten Eintrag … löschen?“). */
    fun onConfirmUndo(confirmed: Boolean) {
        val entry = state.value.confirmUndo ?: return
        mutableState.update { it.copy(confirmUndo = null) }
        if (!confirmed) return
        viewModelScope.launch {
            withContext(io) { undoLast.confirm(entry) }
            undone(entry)
        }
    }

    fun onConfirmationTimeout() {
        mutableState.update { it.copy(confirmation = null) }
    }

    private fun requestLog(item: BarItem, shareOverride: Boolean?) {
        if (policy.needsAlcoholConsentQuestion(item) && shareOverride != false) {
            mutableState.update { it.copy(pendingConsent = PendingLog(item, shareOverride)) }
            return
        }
        log(item, shareOverride)
    }

    private fun log(item: BarItem, shareOverride: Boolean?) {
        viewModelScope.launch {
            val entry = try {
                withContext(io) { logDrink(item, shareOverride = shareOverride) }
            } catch (error: IllegalArgumentException) {
                messages.show(error.message ?: LOG_FAILED)
                return@launch
            }
            val text = SpeechTexts.logged(entry.name)
            showConfirmation(LoggedConfirmation(entry.id, text, sharingHint(entry)))
            if (settings.announceLogged.value.value) announce(text, explicit = false)
        }
    }

    private fun sharingHint(entry: DrinkEntry): String? = when {
        entry.shareWithFriends -> SHARED_HINT
        isSignedIn() -> PRIVATE_HINT
        else -> null
    }

    private fun showConfirmation(confirmation: LoggedConfirmation) {
        confirmationJob?.cancel()
        mutableState.update { it.copy(confirmation = confirmation) }
        confirmationJob = viewModelScope.launch {
            delay(UNDO_VISIBLE_MILLIS)
            mutableState.update { if (it.confirmation == confirmation) it.copy(confirmation = null) else it }
        }
    }

    private fun undone(entry: DrinkEntry) {
        confirmationJob?.cancel()
        mutableState.update { it.copy(confirmation = null) }
        val text = SpeechTexts.undone(entry.name)
        messages.show(text)
        announce(text, explicit = false)
    }

    // ---------- Sprache ----------

    fun voiceRequest(): VoiceRequest = VoiceRequestFactory.create(state.value.items, sdkInt)

    /** Ergebnisse der Spracherkennung; `null` oder leer = abgebrochen. */
    fun onVoiceResults(candidates: List<String>?) {
        if (candidates.isNullOrEmpty()) return
        when (val match = voiceMatcher.match(candidates, state.value.items)) {
            is VoiceMatch.Drink -> onDrinkTapped(match.item)
            is VoiceMatch.Ambiguous -> {
                mutableState.update { it.copy(ambiguity = match) }
                announce(SpeechTexts.whichDrink(match.first.name, match.second.name), explicit = false)
            }
            is VoiceMatch.Command -> onVoiceCommand(match.cmd)
            VoiceMatch.NoMatch -> {
                val text = SpeechTexts.notUnderstood()
                messages.show(text)
                announce(text, explicit = false)
            }
        }
    }

    /** Auswahl bei mehrdeutiger Spracheingabe; `null` = keins von beiden. */
    fun onAmbiguityResolved(item: BarItem?) {
        mutableState.update { it.copy(ambiguity = null) }
        item?.let(::onDrinkTapped)
    }

    private fun onVoiceCommand(command: VoiceCommand) {
        when (command) {
            VoiceCommand.UNDO -> onUndo()
            // Antworten auch als Text: ohne (deutsche) Sprachausgabe bliebe die Antwort sonst unsichtbar und stumm.
            VoiceCommand.TODAY -> viewModelScope.launch {
                val summary = withContext(io) { todaySummary() }
                messages.show(todayText(summary))
                announce(SpeechTexts.today(summary), explicit = true)
            }
            VoiceCommand.READ_ALOUD -> viewModelScope.launch {
                val event = try {
                    latestFriendEvent()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    null
                }
                messages.show(event?.let(EventTexts::headline) ?: NO_FRIEND_EVENT)
                announce(event?.let { SpeechTexts.drinkEvent(it) } ?: NO_FRIEND_EVENT, explicit = true)
            }
        }
    }

    private fun todayText(summary: TodaySummary): String {
        val lines = HistoryTexts.todayLines(summary)
        return if (summary.drinkCount == 0) lines.single() else "Heute: " + lines.take(TODAY_TEXT_LINES).joinToString(" · ")
    }

    private fun announce(text: String, explicit: Boolean) {
        if (AnnouncementPolicy.shouldSpeak(screenReader.isScreenReaderActive(), explicit)) speaker.speak(text)
    }

    // ---------- Bar bearbeiten ----------

    fun onEdit(item: BarItem) = mutableState.update { it.copy(editor = EditorState(formMapper.fromItem(item))) }

    fun onAddCustom() = mutableState.update {
        it.copy(catalogChoices = null, editor = EditorState(formMapper.newForm(nextSortOrder())))
    }

    fun onOpenCatalog() = mutableState.update { state ->
        val present = state.items.map { it.id }.toSet()
        state.copy(catalogChoices = DefaultBarCatalog.entries.filterNot { it.item.id in present })
    }

    fun onCloseCatalog() = mutableState.update { it.copy(catalogChoices = null) }

    fun onAddFromCatalog(entry: CatalogEntry) {
        mutableState.update { it.copy(catalogChoices = null) }
        viewModelScope.launch {
            withContext(io) { saveItem(entry.item.copy(sortOrder = nextSortOrder())) }
            messages.show("${entry.item.name} ist jetzt in deiner Bar.")
        }
    }

    fun onEditorChange(form: DrinkForm) = mutableState.update { state ->
        state.copy(editor = state.editor?.copy(form = form, errors = emptyList()))
    }

    fun onEditorSave() {
        val editor = state.value.editor ?: return
        when (val result = formMapper.toItem(editor.form)) {
            is FormResult.Invalid -> mutableState.update { it.copy(editor = editor.copy(errors = result.errors)) }
            is FormResult.Valid -> {
                mutableState.update { it.copy(editor = null) }
                viewModelScope.launch {
                    withContext(io) { saveItem(result.item) }
                    messages.show("${result.item.name} gespeichert.")
                }
            }
        }
    }

    fun onEditorDismiss() = mutableState.update { it.copy(editor = null) }

    fun onEditorDeleteRequested() = mutableState.update { state ->
        state.copy(editor = state.editor?.copy(confirmDelete = true))
    }

    fun onEditorDeleteConfirmed(confirmed: Boolean) {
        val editor = state.value.editor ?: return
        val original = editor.form.original
        if (!confirmed || original == null) {
            mutableState.update { it.copy(editor = editor.copy(confirmDelete = false)) }
            return
        }
        mutableState.update { it.copy(editor = null) }
        viewModelScope.launch {
            withContext(io) { bar.delete(original.id) }
            messages.show("${original.name} aus der Bar entfernt. Dein Verlauf bleibt erhalten.")
        }
    }

    /** Reihenfolge ändern: eine Position nach vorne ([towardsStart]) oder hinten. */
    fun onMove(item: BarItem, towardsStart: Boolean) {
        val ids = state.value.items.map { it.id }.toMutableList()
        val index = ids.indexOf(item.id)
        val target = if (towardsStart) index - 1 else index + 1
        if (index < 0 || target !in ids.indices) return
        ids.removeAt(index)
        ids.add(target, item.id)
        viewModelScope.launch { withContext(io) { bar.reorder(ids) } }
    }

    private fun nextSortOrder(): Int = (state.value.items.maxOfOrNull { it.sortOrder } ?: -1) + 1

    private suspend fun reload() {
        val items = withContext(io) { bar.items() }
        mutableState.update { it.copy(items = items) }
    }

    companion object {
        const val UNDO_VISIBLE_MILLIS = 10_000L
        const val NOTHING_TO_UNDO = "Es gibt nichts zum Rückgängigmachen."
        const val LOG_FAILED = "Das Getränk konnte nicht eingetragen werden."
        const val SHARED_HINT = "Mit deinen Freunden geteilt."
        const val PRIVATE_HINT = "Nicht geteilt."
        const val NO_FRIEND_EVENT = "Es gibt noch keine Getränke von Freunden zum Vorlesen."
        private const val TODAY_TEXT_LINES = 3
    }
}
