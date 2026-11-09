package com.drinkwithme.app.ui.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drinkwithme.core.social.FeedEntry
import com.drinkwithme.core.sharing.VolumeFormatter

/** Ein Getränk aus dem Feed: wer, was, wie viel, wann. */
@Composable
fun FeedEntryRow(
    entry: FeedEntry,
    isOwnEntry: Boolean,
    relativeTime: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(entry.category.emoji, style = MaterialTheme.typography.headlineMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isOwnEntry) "Du" else entry.author.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(entry.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${entry.category.displayName} · ${VolumeFormatter.format(entry.volumeInMilliliters)} · $relativeTime",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.note?.let {
                    Text("„$it“", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
