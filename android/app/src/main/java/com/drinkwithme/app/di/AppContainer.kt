package com.drinkwithme.app.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.drinkwithme.app.data.JsonFileDrinkRepository
import com.drinkwithme.app.sharing.DrinkSharer
import com.drinkwithme.app.sharing.IntentDrinkSharer
import com.drinkwithme.app.ui.add.AddDrinkViewModel
import com.drinkwithme.app.ui.list.DrinkListViewModel
import com.drinkwithme.core.domain.DefaultDrinkValidator
import com.drinkwithme.core.domain.DrinkRepository
import com.drinkwithme.core.sharing.GermanShareFormatter
import com.drinkwithme.core.usecase.DefaultDeleteDrinkUseCase
import com.drinkwithme.core.usecase.DefaultFetchDrinksUseCase
import com.drinkwithme.core.usecase.DefaultLogDrinkUseCase
import com.drinkwithme.core.usecase.DefaultShareDrinksUseCase
import java.io.File

/** Abhängigkeiten, die die UI benötigt – nur Abstraktionen (Dependency Inversion). */
interface AppContainer {
    val drinkSharer: DrinkSharer
    val viewModelFactory: ViewModelProvider.Factory
}

/** Composition Root: einziger Ort, an dem konkrete Klassen verdrahtet werden. */
class DefaultAppContainer(
    context: Context,
    repository: DrinkRepository = JsonFileDrinkRepository(File(context.filesDir, "drinks.json")),
) : AppContainer {
    private val formatter = GermanShareFormatter()
    private val logDrink = DefaultLogDrinkUseCase(DefaultDrinkValidator(), repository)
    private val fetchDrinks = DefaultFetchDrinksUseCase(repository)
    private val deleteDrink = DefaultDeleteDrinkUseCase(repository)
    private val shareDrinks = DefaultShareDrinksUseCase(formatter, formatter)

    override val drinkSharer: DrinkSharer = IntentDrinkSharer()

    override val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { DrinkListViewModel(fetchDrinks, deleteDrink, shareDrinks) }
        initializer { AddDrinkViewModel(logDrink) }
    }
}
