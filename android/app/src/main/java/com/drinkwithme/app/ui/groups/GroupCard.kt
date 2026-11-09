package com.drinkwithme.app.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drinkwithme.core.social.DrinkGroup

/** Eine Trinkrunde mit Mitgliedern und Einladungscode – Aktionen werden nach oben gereicht. */
@Composable
fun GroupCard(
    group: DrinkGroup,
    currentUserId: String,
    onCopyCode: () -> Unit,
    onShareCode: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(group.name, style = MaterialTheme.typography.titleLarge)
            Text(
                GroupMemberSummary.format(group, currentUserId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Einladungscode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        InviteCodeText.display(group.inviteCode),
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp,
                    )
                }
                IconButton(onClick = onCopyCode) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Code kopieren")
                }
                IconButton(onClick = onShareCode) {
                    Icon(Icons.Filled.Share, contentDescription = "Code teilen")
                }
            }
            TextButton(onClick = onLeave, modifier = Modifier.align(Alignment.End)) {
                Text("Verlassen")
            }
        }
    }
}
