package com.drinkwithme.app.ui.groups

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drinkwithme.app.ui.account.AccountUiState
import com.drinkwithme.app.ui.account.SocialGate
import com.drinkwithme.core.social.UserProfile
import kotlinx.coroutines.launch

/**
 * Tab „Runden“: Runden erstellen, beitreten, Code teilen/kopieren, verlassen – und das Konto.
 * Zeigt ohne Server den Einrichtungs-Hinweis und ohne Anmeldung die Google-Anmeldung.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    account: AccountUiState?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    viewModelFactory: ViewModelProvider.Factory,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Runden") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        SocialGate(account = account, onSignIn = onSignIn, modifier = Modifier.padding(padding)) { user ->
            val viewModel: GroupsViewModel = viewModel(factory = viewModelFactory)
            GroupsContent(
                viewModel = viewModel,
                user = user,
                isSigningOut = account?.isSigningOut == true,
                accountErrorMessage = account?.errorMessage,
                onSignOut = onSignOut,
                onShare = onShare,
                snackbarHostState = snackbarHostState,
            )
        }
    }
}

@Composable
private fun GroupsContent(
    viewModel: GroupsViewModel,
    user: UserProfile,
    isSigningOut: Boolean,
    accountErrorMessage: String?,
    onSignOut: () -> Unit,
    onShare: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var showJoinDialog by rememberSaveable { mutableStateOf(false) }
    var groupIdToLeave by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GroupActions(
                onCreate = {
                    viewModel.clearDialogError()
                    showCreateDialog = true
                },
                onJoin = {
                    viewModel.clearDialogError()
                    showJoinDialog = true
                },
            )
        }
        when {
            state.isLoading -> item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.groups.isEmpty() -> item { NoGroupsYet() }
            else -> items(state.groups, key = { it.id }) { group ->
                GroupCard(
                    group = group,
                    currentUserId = user.id,
                    onCopyCode = {
                        clipboard.setText(AnnotatedString(group.inviteCode.value))
                        // Ab Android 13 bestätigt das System das Kopieren selbst.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            scope.launch { snackbarHostState.showSnackbar("Code ${group.inviteCode.value} kopiert") }
                        }
                    },
                    onShareCode = { onShare(viewModel.inviteMessage(group)) },
                    onLeave = { groupIdToLeave = group.id },
                )
            }
        }
        item {
            AccountSection(
                user = user,
                isSigningOut = isSigningOut,
                errorMessage = accountErrorMessage,
                onSignOut = onSignOut,
            )
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            isWorking = state.isWorking,
            errorMessage = state.dialogErrorMessage,
            onCreate = { name -> viewModel.create(name) { showCreateDialog = false } },
            onDismiss = { showCreateDialog = false },
        )
    }
    if (showJoinDialog) {
        JoinGroupDialog(
            isWorking = state.isWorking,
            errorMessage = state.dialogErrorMessage,
            onJoin = { code -> viewModel.join(code) { showJoinDialog = false } },
            onDismiss = { showJoinDialog = false },
        )
    }
    state.groups.firstOrNull { it.id == groupIdToLeave }?.let { group ->
        LeaveGroupDialog(
            group = group,
            onConfirm = {
                groupIdToLeave = null
                viewModel.leave(group)
            },
            onDismiss = { groupIdToLeave = null },
        )
    }
}

@Composable
private fun GroupActions(onCreate: () -> Unit, onJoin: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onCreate, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Erstellen")
        }
        OutlinedButton(onClick = onJoin, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.GroupAdd, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Beitreten")
        }
    }
}

@Composable
private fun NoGroupsYet() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("👯", style = MaterialTheme.typography.displaySmall)
        Text("Du bist noch in keiner Runde", style = MaterialTheme.typography.titleMedium)
        Text(
            "Erstelle eine Runde und teile den Code – oder tritt mit dem Code deiner Freunde bei.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccountSection(
    user: UserProfile,
    isSigningOut: Boolean,
    errorMessage: String?,
    onSignOut: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Konto", style = MaterialTheme.typography.titleMedium)
            Text("Angemeldet als ${user.displayName}", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onSignOut, enabled = !isSigningOut) {
                Text(if (isSigningOut) "Wird abgemeldet …" else "Abmelden")
            }
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
