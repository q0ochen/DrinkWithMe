package com.drinkwithme.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drinkwithme.app.stats.GermanFormat
import com.drinkwithme.app.ui.common.BigButton
import com.drinkwithme.app.ui.common.DrinkIconImage
import com.drinkwithme.app.ui.common.SectionCard
import com.drinkwithme.app.ui.common.SectionTitle
import com.drinkwithme.app.ui.common.focusFrame
import com.drinkwithme.core.bar.CatalogEntry
import com.drinkwithme.core.health.HealthReferences

/** Erster Start: Getränke für die eigene Bar wählen (Vorauswahl ist angehakt). Anmeldung ist optional. */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 280.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Willkommen bei Drink With Me")
                Text(
                    "Wähle die Getränke für deine Bar. Später reicht ein Tipp auf ein Getränk, um es einzutragen. " +
                        "Alles lässt sich jederzeit ändern, eigene Getränke sind möglich.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                SectionCard("Anmeldung ist freiwillig") {
                    Text(
                        "Bar, Verlauf, Statistik, Sprache und Vorlesen funktionieren ohne Konto. Ein Google-Konto brauchst du " +
                            "nur, wenn du dich mit Freunden verbinden möchtest – das geht später unter „Freunde“.",
                    )
                }
                Text(HealthReferences.INFO_TEXT, style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(state.entries, key = { it.item.id }) { entry ->
            CatalogRow(entry, checked = entry.item.id in state.selected, onToggle = { viewModel.onToggle(entry.item.id) })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text("${state.selected.size} Getränke ausgewählt", style = MaterialTheme.typography.bodyMedium)
                BigButton(
                    "Bar einrichten",
                    viewModel::onFinish,
                    enabled = state.canFinish,
                    minHeight = 64.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CatalogRow(entry: CatalogEntry, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .focusFrame()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = 64.dp)
            .padding(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        DrinkIconImage(entry.item.icon, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.item.name, style = MaterialTheme.typography.titleMedium)
            val detail = listOfNotNull(GermanFormat.milliliters(entry.item.servingMilliliters), entry.note).joinToString(" · ")
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
