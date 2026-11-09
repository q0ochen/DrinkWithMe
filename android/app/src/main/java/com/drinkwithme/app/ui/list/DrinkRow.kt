package com.drinkwithme.app.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drinkwithme.core.domain.Drink
import com.drinkwithme.core.domain.DrinkVisibility
import com.drinkwithme.core.sharing.VolumeFormatter
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("dd.MM. HH:mm")

/** Reine Darstellung eines Getränks – Aktionen werden nach oben gereicht. */
@Composable
fun DrinkRow(
    drink: Drink,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(drink.category.emoji, style = MaterialTheme.typography.headlineMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(drink.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${drink.category.displayName} · ${VolumeFormatter.format(drink.volumeInMilliliters)} · " +
                        drink.consumedAt.atZone(ZoneId.systemDefault()).format(timeFormatter) +
                        if (drink.visibility == DrinkVisibility.PRIVATE) " · privat" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                drink.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "${drink.name} teilen")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "${drink.name} löschen")
            }
        }
    }
}
