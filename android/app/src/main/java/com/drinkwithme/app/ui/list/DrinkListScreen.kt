package com.drinkwithme.app.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drinkwithme.app.ui.add.AddDrinkForm
import com.drinkwithme.app.ui.add.AddDrinkViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrinkListScreen(
    listViewModel: DrinkListViewModel,
    addDrinkViewModel: AddDrinkViewModel,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by listViewModel.state.collectAsStateWithLifecycle()
    val addState by addDrinkViewModel.state.collectAsStateWithLifecycle()
    var isAddingDrink by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            listViewModel.dismissError()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Drink With Me") },
                actions = {
                    IconButton(onClick = { onShare(listViewModel.todaySummary()) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Tag teilen")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { isAddingDrink = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Getränk") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.drinks.isEmpty()) {
            EmptyDrinks(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.drinks, key = { it.id }) { drink ->
                    DrinkRow(
                        drink = drink,
                        onShare = { onShare(listViewModel.shareMessage(drink)) },
                        onDelete = { listViewModel.delete(drink) },
                    )
                }
            }
        }
    }

    if (isAddingDrink) {
        val closeSheet = {
            addDrinkViewModel.reset()
            isAddingDrink = false
        }
        ModalBottomSheet(
            onDismissRequest = closeSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            AddDrinkForm(
                state = addState,
                onNameChange = addDrinkViewModel::onNameChange,
                onCategoryChange = addDrinkViewModel::onCategoryChange,
                onVolumeChange = addDrinkViewModel::onVolumeChange,
                onNoteChange = addDrinkViewModel::onNoteChange,
                onPrivateChange = addDrinkViewModel::onPrivateChange,
                onSave = {
                    addDrinkViewModel.save {
                        isAddingDrink = false
                        listViewModel.refresh()
                    }
                },
                onCancel = closeSheet,
            )
        }
    }
}

@Composable
private fun EmptyDrinks(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🥂", style = MaterialTheme.typography.displayLarge)
            Text("Noch keine Getränke", style = MaterialTheme.typography.titleLarge)
            Text(
                "Tippe auf „Getränk“ und teile, was du gerade trinkst.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
