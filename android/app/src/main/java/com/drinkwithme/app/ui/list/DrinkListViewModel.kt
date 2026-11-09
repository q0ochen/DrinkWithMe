package com.drinkwithme.app.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drinkwithme.core.domain.Drink
import com.drinkwithme.core.usecase.DeleteDrinkUseCase
import com.drinkwithme.core.usecase.FetchDrinksUseCase
import com.drinkwithme.core.usecase.ShareDrinksUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DrinkListUiState(
    val drinks: List<Drink> = emptyList(),
    val errorMessage: String? = null,
)

/** Präsentationslogik der Getränkeliste – kennt nur Use-Case-Abstraktionen. */
class DrinkListViewModel(
    private val fetchDrinks: FetchDrinksUseCase,
    private val deleteDrink: DeleteDrinkUseCase,
    private val shareDrinks: ShareDrinksUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(DrinkListUiState())
    val state: StateFlow<DrinkListUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { fetchDrinks() } }
                .onSuccess { drinks -> _state.update { it.copy(drinks = drinks) } }
                .onFailure { showError("Getränke konnten nicht geladen werden: ${it.message}") }
        }
    }

    fun delete(drink: Drink) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { deleteDrink(drink.id) } }
                .onFailure { showError("Getränk konnte nicht gelöscht werden: ${it.message}") }
            refresh()
        }
    }

    fun shareMessage(drink: Drink): String = shareDrinks.message(drink)

    fun todaySummary(): String = shareDrinks.todaySummary(_state.value.drinks)

    fun dismissError() = _state.update { it.copy(errorMessage = null) }

    private fun showError(message: String) = _state.update { it.copy(errorMessage = message) }
}
