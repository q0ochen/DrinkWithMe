package com.drinkwithme.app.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drinkwithme.core.domain.DrinkCategory
import com.drinkwithme.core.sharing.VolumeFormatter

/** Zustandsloses Formular – erhält Zustand und Callbacks von außen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddDrinkForm(
    state: AddDrinkUiState,
    onNameChange: (String) -> Unit,
    onCategoryChange: (DrinkCategory) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Neues Getränk", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = { Text("Name, z. B. Cappuccino") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Kategorie", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrinkCategory.entries.forEach { category ->
                FilterChip(
                    selected = state.category == category,
                    onClick = { onCategoryChange(category) },
                    label = { Text("${category.emoji} ${category.displayName}") },
                )
            }
        }

        Text("Menge", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onVolumeChange(state.volumeInMilliliters - 10) }) {
                Icon(Icons.Filled.Remove, contentDescription = "Weniger")
            }
            Text(VolumeFormatter.format(state.volumeInMilliliters), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onVolumeChange(state.volumeInMilliliters + 10) }) {
                Icon(Icons.Filled.Add, contentDescription = "Mehr")
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddDrinkViewModel.volumePresets.forEach { preset ->
                FilterChip(
                    selected = state.volumeInMilliliters == preset,
                    onClick = { onVolumeChange(preset) },
                    label = { Text(VolumeFormatter.format(preset)) },
                )
            }
        }

        OutlinedTextField(
            value = state.note,
            onValueChange = onNoteChange,
            label = { Text("Notiz (optional)") },
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )

        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) { Text("Abbrechen") }
            Button(onClick = onSave) { Text("Speichern") }
        }
    }
}
