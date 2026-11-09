package com.drinkwithme.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drinkwithme.app.ui.account.AccountUiState
import com.drinkwithme.app.ui.account.AccountViewModel
import com.drinkwithme.app.ui.add.AddDrinkViewModel
import com.drinkwithme.app.ui.friends.FriendsScreen
import com.drinkwithme.app.ui.groups.GroupsScreen
import com.drinkwithme.app.ui.list.DrinkListScreen
import com.drinkwithme.app.ui.list.DrinkListViewModel

/** Die drei Bereiche der Navigation unten. */
enum class AppTab(val label: String, val icon: ImageVector) {
    MY_DRINKS("Meine Getränke", Icons.Filled.LocalDrink),
    FRIENDS("Freunde", Icons.Filled.People),
    GROUPS("Runden", Icons.Filled.Groups),
}

/**
 * Grundgerüst der App: Navigation unten, darüber der gewählte Bereich mit eigener TopAppBar.
 *
 * @param isSocialAvailable `false`, solange kein Server eingerichtet ist – dann gibt es keine
 *   Trinkrunden-ViewModels und „Freunde“/„Runden“ zeigen den Einrichtungs-Hinweis.
 * @param pendingGroupId Runde aus einer angetippten Benachrichtigung.
 */
@Composable
fun DrinkWithMeApp(
    viewModelFactory: ViewModelProvider.Factory,
    isSocialAvailable: Boolean,
    onShare: (String) -> Unit,
    pendingGroupId: String?,
    onPendingGroupHandled: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.MY_DRINKS) }
    val context = LocalContext.current

    val accountViewModel: AccountViewModel? =
        if (isSocialAvailable) viewModel(factory = viewModelFactory) else null
    val accountState: AccountUiState? = accountViewModel?.let { it.state.collectAsStateWithLifecycle().value }

    if (accountViewModel != null && accountState != null) {
        NotificationPermissionRequest(
            shouldRequest = accountState.requestNotificationPermission,
            onRequested = accountViewModel::onNotificationPermissionRequested,
        )
    }

    LaunchedEffect(pendingGroupId) {
        if (pendingGroupId != null) selectedTab = AppTab.FRIENDS
    }

    // Der Credential Manager braucht den Activity-Kontext für seinen Dialog.
    val signIn: () -> Unit = { accountViewModel?.signIn(context) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        // Die Bereiche haben eigene Scaffolds mit TopAppBar: Die Statusleiste behandeln sie selbst,
        // den unteren Rand (Navigationsleiste) übernimmt die NavigationBar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        val contentModifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
        when (selectedTab) {
            AppTab.MY_DRINKS -> MyDrinksTab(viewModelFactory, onShare, contentModifier)
            AppTab.FRIENDS -> FriendsScreen(
                account = accountState,
                onSignIn = signIn,
                viewModelFactory = viewModelFactory,
                onOpenGroups = { selectedTab = AppTab.GROUPS },
                pendingGroupId = pendingGroupId,
                onPendingGroupHandled = onPendingGroupHandled,
                modifier = contentModifier,
            )
            AppTab.GROUPS -> GroupsScreen(
                account = accountState,
                onSignIn = signIn,
                onSignOut = { accountViewModel?.signOut() },
                viewModelFactory = viewModelFactory,
                onShare = onShare,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun MyDrinksTab(
    viewModelFactory: ViewModelProvider.Factory,
    onShare: (String) -> Unit,
    modifier: Modifier,
) {
    val listViewModel: DrinkListViewModel = viewModel(factory = viewModelFactory)
    val addViewModel: AddDrinkViewModel = viewModel(factory = viewModelFactory)
    DrinkListScreen(
        listViewModel = listViewModel,
        addDrinkViewModel = addViewModel,
        onShare = onShare,
        modifier = modifier,
    )
}
