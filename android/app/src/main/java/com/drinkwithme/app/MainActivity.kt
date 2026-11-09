package com.drinkwithme.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drinkwithme.app.ui.add.AddDrinkViewModel
import com.drinkwithme.app.ui.list.DrinkListScreen
import com.drinkwithme.app.ui.list.DrinkListViewModel
import com.drinkwithme.app.ui.theme.DrinkWithMeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DrinkWithMeApplication).container

        setContent {
            DrinkWithMeTheme {
                val listViewModel: DrinkListViewModel = viewModel(factory = container.viewModelFactory)
                val addViewModel: AddDrinkViewModel = viewModel(factory = container.viewModelFactory)
                DrinkListScreen(
                    listViewModel = listViewModel,
                    addDrinkViewModel = addViewModel,
                    onShare = { text -> container.drinkSharer.share(this, text) },
                )
            }
        }
    }
}
