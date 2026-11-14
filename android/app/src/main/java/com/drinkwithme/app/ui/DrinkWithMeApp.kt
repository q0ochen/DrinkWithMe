package com.drinkwithme.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drinkwithme.app.BuildConfig
import com.drinkwithme.app.di.AppContainer
import com.drinkwithme.app.routing.AppRequest
import com.drinkwithme.app.share.ShareImageFiles
import com.drinkwithme.app.share.StatisticsCard
import com.drinkwithme.app.share.StatisticsImageRenderer
import com.drinkwithme.app.supabase.ErrorMessages
import com.drinkwithme.app.supabase.SocialException
import com.drinkwithme.app.ui.bar.BarScreen
import com.drinkwithme.app.ui.bar.BarViewModel
import com.drinkwithme.app.ui.common.ConfirmDialog
import com.drinkwithme.app.ui.common.ScopedViewModelStores
import com.drinkwithme.app.ui.common.ScopedViewModels
import com.drinkwithme.app.ui.common.SpeechHelpActions
import com.drinkwithme.app.ui.event.EventScreen
import com.drinkwithme.app.ui.event.EventViewModel
import com.drinkwithme.app.ui.friends.FriendsScreen
import com.drinkwithme.app.ui.friends.FriendsViewModel
import com.drinkwithme.app.ui.history.HistoryScreen
import com.drinkwithme.app.ui.history.HistoryViewModel
import com.drinkwithme.app.ui.navigation.AdaptiveScaffold
import com.drinkwithme.app.ui.navigation.AppSection
import com.drinkwithme.app.ui.navigation.PlainScreen
import com.drinkwithme.app.ui.onboarding.OnboardingScreen
import com.drinkwithme.app.ui.onboarding.OnboardingViewModel
import com.drinkwithme.app.ui.settings.SettingsActions
import com.drinkwithme.app.ui.settings.SettingsScreen
import com.drinkwithme.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Wurzel der Oberfläche: erster Start, vier Bereiche, Ereignis-Bildschirm, Meldungen. */
@Composable
fun DrinkWithMeApp(container: AppContainer, appViewModel: AppViewModel) {
    val state by appViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = container.messageQueue
    val snackbar = remember { SnackbarHostState() }
    val queued by messages.messages.collectAsStateWithLifecycle()

    // Nach der ID, nicht nach dem Text: Zwei gleiche Meldungen hintereinander dürfen die Reihe nicht anhalten.
    val head = queued.firstOrNull()
    LaunchedEffect(head?.id) {
        val message = head ?: return@LaunchedEffect
        snackbar.showSnackbar(message.text, duration = if (message.text.length > 60) SnackbarDuration.Long else SnackbarDuration.Short)
        messages.consume(message.id)
    }

    var notificationsAllowed by remember { mutableStateOf(notificationsAllowed(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted || notificationsAllowed(context)
    }
    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            container.links.openNotificationSettings(context)
        }
    }
    LaunchedEffect(state.askNotificationPermission) {
        if (state.askNotificationPermission) {
            if (!notificationsAllowed(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            appViewModel.onNotificationPermissionHandled()
        }
    }

    val signIn: () -> Unit = {
        scope.launch {
            try {
                val url = container.accountController.signInUrl()
                if (container.links.openSignIn(context, url)) appViewModel.onSignInStarted() else messages.show(ErrorMessages.NO_BROWSER)
            } catch (error: SocialException) {
                messages.show(error.userMessage)
            }
        }
    }

    val speechHelp = SpeechHelpActions(
        openUrl = { url -> if (!container.links.openInBrowser(context, url)) messages.show(ErrorMessages.NO_BROWSER) },
        installSpeechData = { if (!container.links.installSpeechData(context)) messages.show(NO_SPEECH_DATA_INSTALLER) },
        openSpeechSettings = { if (!container.links.openSpeechSettings(context)) messages.show(NO_SPEECH_SETTINGS) },
    )

    // Je geöffnetem Ereignis ein eigener ViewModel-Speicher (übersteht Drehungen, wird beim Schließen geleert).
    val eventStores: ScopedViewModelStores = viewModel(factory = container.viewModelFactory)
    LaunchedEffect(state.openEvent?.key) { eventStores.retainOnly(state.openEvent?.key) }

    Surface(Modifier.fillMaxSize()) {
        when (state.needsOnboarding) {
            null -> Unit
            true -> PlainScreen {
                val onboarding: OnboardingViewModel = viewModel(factory = container.viewModelFactory)
                val onboardingState by onboarding.state.collectAsStateWithLifecycle()
                LaunchedEffect(onboardingState.finished) {
                    if (onboardingState.finished) appViewModel.onOnboardingFinished()
                }
                OnboardingScreen(onboarding)
            }
            false -> {
                val openEvent = state.openEvent
                if (openEvent != null) {
                    BackHandler(onBack = appViewModel::onEventClosed)
                    PlainScreen {
                        ScopedViewModels(eventStores, openEvent.key) {
                            val eventViewModel: EventViewModel = viewModel(factory = container.eventViewModelFactory(openEvent.request))
                            EventScreen(eventViewModel, onClose = appViewModel::onEventClosed, speechHelp = speechHelp)
                        }
                    }
                } else {
                    BackHandler(enabled = state.section != AppSection.BAR) { appViewModel.onSectionSelected(AppSection.BAR) }
                    AdaptiveScaffold(state.section, appViewModel::onSectionSelected, snackbar) { wide ->
                        SectionContent(
                            section = state.section,
                            wide = wide,
                            container = container,
                            signingIn = state.signingIn,
                            onSignIn = signIn,
                            notificationsAllowed = notificationsAllowed,
                            requestNotificationPermission = requestNotificationPermission,
                            onOpenEvent = { event ->
                                appViewModel.handle(AppRequest.OpenEvent(event.id, null, null, autoRead = false))
                            },
                            shareImage = { card ->
                                scope.launch {
                                    val file = withContext(Dispatchers.IO) {
                                        container.sharing.saveImage(StatisticsImageRenderer().render(card), LocalDate.now())
                                    }
                                    when {
                                        file == null -> messages.show(ShareImageFiles.SAVE_FAILED)
                                        !container.sharing.shareImage(context, file, "Statistik teilen") -> messages.show(NOTHING_TO_SHARE_WITH)
                                    }
                                }
                            },
                            speechHelp = speechHelp,
                        )
                    }
                }
            }
        }
    }

    state.inviteToConfirm?.let {
        ConfirmDialog(
            title = "Einladung annehmen?",
            text = "Ihr seid danach sofort befreundet und seht gegenseitig, was ihr teilt. Das lässt sich jederzeit beenden.",
            confirmLabel = "Annehmen",
            dismissLabel = "Ablehnen",
            onConfirm = { appViewModel.onInviteAnswered(true) },
            onDismiss = { appViewModel.onInviteAnswered(false) },
        )
    }
}

@Composable
private fun SectionContent(
    section: AppSection,
    wide: Boolean,
    container: AppContainer,
    signingIn: Boolean,
    onSignIn: () -> Unit,
    notificationsAllowed: Boolean,
    requestNotificationPermission: () -> Unit,
    onOpenEvent: (com.drinkwithme.core.social.FriendDrinkEvent) -> Unit,
    shareImage: (StatisticsCard) -> Unit,
    speechHelp: SpeechHelpActions,
) {
    val context = LocalContext.current
    when (section) {
        AppSection.BAR -> {
            val viewModel: BarViewModel = viewModel(factory = container.viewModelFactory)
            BarScreen(viewModel, onMessage = container.messageQueue::show)
        }
        AppSection.HISTORY -> {
            val viewModel: HistoryViewModel = viewModel(factory = container.viewModelFactory)
            LaunchedEffect(Unit) { viewModel.refresh() }
            HistoryScreen(
                viewModel = viewModel,
                wide = wide,
                renderPreview = { card -> StatisticsImageRenderer().render(card).asImageBitmap() },
                onShareImage = shareImage,
            )
        }
        AppSection.FRIENDS -> {
            val viewModel: FriendsViewModel = viewModel(factory = container.viewModelFactory)
            FriendsScreen(
                viewModel = viewModel,
                wide = wide,
                signingIn = signingIn,
                onSignIn = onSignIn,
                onShareInvite = { link ->
                    val text = "Trink mit mir! Öffne den Link, um dich mit mir in Drink With Me zu verbinden: $link"
                    if (!container.sharing.shareText(context, text, "Freund einladen")) container.messageQueue.show(NOTHING_TO_SHARE_WITH)
                },
                onOpenEvent = onOpenEvent,
            )
        }
        AppSection.SETTINGS -> {
            val viewModel: SettingsViewModel = viewModel(factory = container.viewModelFactory)
            SettingsScreen(
                viewModel = viewModel,
                signingIn = signingIn,
                actions = SettingsActions(
                    onSignIn = onSignIn,
                    openUrl = speechHelp.openUrl,
                    speechHelp = speechHelp,
                    notificationsAllowed = notificationsAllowed,
                    requestNotificationPermission = requestNotificationPermission,
                    versionName = BuildConfig.VERSION_NAME,
                ),
            )
        }
    }
}

private fun notificationsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private const val NOTHING_TO_SHARE_WITH = "Auf diesem Gerät gibt es keine App zum Teilen."
private const val NO_SPEECH_DATA_INSTALLER = "Die Sprachausgabe bietet keine Installation von Sprachdaten an."
private const val NO_SPEECH_SETTINGS = "Die Einstellungen der Sprachausgabe lassen sich hier nicht öffnen."
