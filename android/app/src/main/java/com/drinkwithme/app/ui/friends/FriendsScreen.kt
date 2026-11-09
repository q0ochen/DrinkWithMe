package com.drinkwithme.app.ui.friends

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drinkwithme.app.ui.account.AccountUiState
import com.drinkwithme.app.ui.account.LoadingBox
import com.drinkwithme.app.ui.account.SocialGate
import com.drinkwithme.core.social.DrinkGroup
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Tab „Freunde“: Was trinken die anderen gerade? Zeigt ohne Server den Einrichtungs-Hinweis
 * und ohne Anmeldung die Google-Anmeldung.
 *
 * @param pendingGroupId Runde aus einer angetippten Benachrichtigung, die geöffnet werden soll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    account: AccountUiState?,
    onSignIn: () -> Unit,
    viewModelFactory: ViewModelProvider.Factory,
    onOpenGroups: () -> Unit,
    pendingGroupId: String?,
    onPendingGroupHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Freunde") }) },
    ) { padding ->
        SocialGate(account = account, onSignIn = onSignIn, modifier = Modifier.padding(padding)) { user ->
            val viewModel: FriendsViewModel = viewModel(factory = viewModelFactory)
            val state by viewModel.state.collectAsStateWithLifecycle()

            LaunchedEffect(pendingGroupId) {
                if (pendingGroupId != null) {
                    viewModel.selectGroup(pendingGroupId)
                    onPendingGroupHandled()
                }
            }

            FriendsFeed(
                state = state,
                currentUserId = user.id,
                onSelectGroup = viewModel::selectGroup,
                onOpenGroups = onOpenGroups,
            )
        }
    }
}

@Composable
private fun FriendsFeed(
    state: FriendsUiState,
    currentUserId: String,
    onSelectGroup: (String) -> Unit,
    onOpenGroups: () -> Unit,
) {
    // „vor 5 Min.“ soll mitlaufen, auch wenn keine neuen Getränke kommen.
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            delay(TIME_REFRESH_MILLIS)
            value = Instant.now()
        }
    }
    val timeFormatter = remember { GermanRelativeTimeFormatter() }

    when {
        state.errorMessage != null && state.groups.isEmpty() -> CenteredText(state.errorMessage)
        state.groups.isEmpty() && state.isLoading -> LoadingBox()
        state.groups.isEmpty() -> NoGroupsHint(onOpenGroups)
        else -> Column(modifier = Modifier.fillMaxSize()) {
            GroupHeader(groups = state.groups, selectedGroupId = state.selectedGroupId, onSelectGroup = onSelectGroup)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> LoadingBox()
                    state.errorMessage != null -> CenteredText(state.errorMessage)
                    state.entries.isEmpty() -> CenteredText(
                        "In „${state.selectedGroup?.name.orEmpty()}“ wurde noch nichts geteilt. " +
                            "Trag ein Getränk ein – deine Freunde sehen es dann hier.",
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.entries, key = { it.drinkId }) { entry ->
                            FeedEntryRow(
                                entry = entry,
                                isOwnEntry = entry.author.userId == currentUserId,
                                relativeTime = timeFormatter.format(entry.consumedAt, now),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Bei mehreren Runden eine Auswahl, bei einer Runde nur deren Name. */
@Composable
private fun GroupHeader(groups: List<DrinkGroup>, selectedGroupId: String?, onSelectGroup: (String) -> Unit) {
    if (groups.size == 1) {
        Text(
            groups.first().name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEach { group ->
            FilterChip(
                selected = group.id == selectedGroupId,
                onClick = { onSelectGroup(group.id) },
                label = { Text(group.name) },
            )
        }
    }
}

@Composable
private fun NoGroupsHint(onOpenGroups: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("👯", style = MaterialTheme.typography.displaySmall)
            Text("Noch keine Trinkrunde", style = MaterialTheme.typography.titleLarge)
            Text(
                "Erstelle eine Runde oder tritt mit dem Code deiner Freunde bei – dann siehst du hier, was sie trinken.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onOpenGroups) { Text("Zu den Runden") }
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val TIME_REFRESH_MILLIS = 30_000L
