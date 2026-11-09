package com.drinkwithme.app.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drinkwithme.core.domain.Drink
import com.drinkwithme.core.domain.DrinkCategory
import com.drinkwithme.core.domain.DrinkDraft
import com.drinkwithme.core.domain.DrinkValidationException
import com.drinkwithme.core.usecase.LogDrinkUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AddDrinkUiState(
    val name: String = "",
    val category: DrinkCategory = DrinkCategory.WATER,
    val volumeInMilliliters: Int = 250,
    val note: String = "",
    val errorMessage: String? = null,
)

/** Hält den Formularzustand und delegiert das Speichern an den Use Case. */
class AddDrinkViewModel(private val logDrink: LogDrinkUseCase) : ViewModel() {
    private val _state = MutableStateFlow(AddDrinkUiState())
    val state: StateFlow<AddDrinkUiState> = _state.asStateFlow()

    fun onNameChange(name: String) = _state.update { it.copy(name = name) }
    fun onCategoryChange(category: DrinkCategory) = _state.update { it.copy(category = category) }
    fun onNoteChange(note: String) = _state.update { it.copy(note = note) }
    fun onVolumeChange(volume: Int) = _state.update { it.copy(volumeInMilliliters = volume.coerceIn(10, 5_000)) }

    fun save(onSaved: (Drink) -> Unit) {
        val current = _state.value
        val draft = DrinkDraft(current.name, current.category, current.volumeInMilliliters, current.note)
        viewModelScope.launch {
            try {
                val drink = withContext(Dispatchers.IO) { logDrink(draft) }
                reset()
                onSaved(drink)
            } catch (error: DrinkValidationException) {
                _state.update { it.copy(errorMessage = error.message) }
            } catch (error: Exception) {
                _state.update { it.copy(errorMessage = "Speichern fehlgeschlagen: ${error.message}") }
            }
        }
    }

    fun reset() = _state.update { AddDrinkUiState() }

    companion object {
        val volumePresets = listOf(30, 100, 200, 250, 330, 500, 1_000)
    }
}
