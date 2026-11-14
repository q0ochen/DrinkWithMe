package com.drinkwithme.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drinkwithme.core.bar.CatalogEntry
import com.drinkwithme.core.bar.DefaultBarCatalog
import com.drinkwithme.core.bar.SetUpBarFromCatalogUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OnboardingUiState(
    val entries: List<CatalogEntry> = DefaultBarCatalog.entries,
    /** Vorauswahl laut Vertrag: Wasser, Kaffee, Tee, Apfelschorle, Cola, Pils, Rotwein. */
    val selected: Set<String> = DefaultBarCatalog.defaultSelectionIds.toSet(),
    val saving: Boolean = false,
    /** Bar eingerichtet – die Wurzel-Oberfläche wechselt zur Bar. */
    val finished: Boolean = false,
) {
    val canFinish: Boolean get() = selected.isNotEmpty() && !saving
}

/** Erster Start: eigene Bar aus dem Katalog wählen. Anmeldung ist optional und kommt später. */
class OnboardingViewModel(
    private val setUpBar: SetUpBarFromCatalogUseCase,
    private val io: CoroutineDispatcher,
) : ViewModel() {

    private val mutableState = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    fun onToggle(id: String) = mutableState.update { state ->
        state.copy(selected = if (id in state.selected) state.selected - id else state.selected + id)
    }

    fun onFinish() {
        val current = state.value
        if (!current.canFinish) return
        mutableState.update { it.copy(saving = true) }
        viewModelScope.launch {
            // Reihenfolge wie im Katalog (gleiche Getränkearten stehen beieinander).
            val ids = current.entries.map { it.item.id }.filter { it in current.selected }
            withContext(io) { setUpBar(ids) }
            mutableState.update { it.copy(saving = false, finished = true) }
        }
    }
}
