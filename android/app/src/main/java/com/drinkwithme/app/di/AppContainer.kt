package com.drinkwithme.app.di

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.drinkwithme.app.account.AccountController
import com.drinkwithme.app.account.SupabaseAccountController
import com.drinkwithme.app.account.UnconfiguredAccountController
import com.drinkwithme.app.config.ServerConfig
import com.drinkwithme.app.config.serverConfigFromBuild
import com.drinkwithme.app.data.AppSettings
import com.drinkwithme.app.data.JsonFileBarRepository
import com.drinkwithme.app.data.JsonFileHistoryRepository
import com.drinkwithme.app.data.NotifiedEvents
import com.drinkwithme.app.data.SharedPreferencesKeyValueStore
import com.drinkwithme.app.platform.AndroidLinks
import com.drinkwithme.app.polling.FeedPoller
import com.drinkwithme.app.polling.ForegroundRefreshThrottle
import com.drinkwithme.app.polling.SocialBackground
import com.drinkwithme.app.publish.PendingShareQueue
import com.drinkwithme.app.publish.RetryingEventPublisher
import com.drinkwithme.app.publish.ShareRetryProcessor
import com.drinkwithme.app.push.AndroidFriendDrinkNotifier
import com.drinkwithme.app.push.DistributorInfo
import com.drinkwithme.app.push.DrinkNotificationChannel
import com.drinkwithme.app.push.PushDistributors
import com.drinkwithme.app.push.PushEndpointStore
import com.drinkwithme.app.push.PushMessageHandler
import com.drinkwithme.app.push.PushSubscriptionSync
import com.drinkwithme.app.push.UnifiedPushDistributors
import com.drinkwithme.app.routing.AppRequest
import com.drinkwithme.app.share.AndroidSharing
import com.drinkwithme.app.speech.AndroidScreenReaderDetector
import com.drinkwithme.app.speech.AndroidSpeaker
import com.drinkwithme.app.supabase.AndroidKeystoreTokenCipher
import com.drinkwithme.app.supabase.CurrentUserId
import com.drinkwithme.app.supabase.EncryptedSessionStore
import com.drinkwithme.app.supabase.ProfileLookup
import com.drinkwithme.app.supabase.PushSubscription
import com.drinkwithme.app.supabase.SupabaseAccountApi
import com.drinkwithme.app.supabase.SupabaseApi
import com.drinkwithme.app.supabase.SupabaseAuthApi
import com.drinkwithme.app.supabase.SupabaseEventPublisher
import com.drinkwithme.app.supabase.SupabaseFeedService
import com.drinkwithme.app.supabase.SupabaseFriendsService
import com.drinkwithme.app.supabase.SupabaseHttp
import com.drinkwithme.app.supabase.SupabasePushSubscriptions
import com.drinkwithme.app.supabase.SupabaseSession
import com.drinkwithme.app.ui.AppViewModel
import com.drinkwithme.app.ui.bar.BarViewModel
import com.drinkwithme.app.ui.bar.DrinkFormMapper
import com.drinkwithme.app.ui.common.MessageQueue
import com.drinkwithme.app.ui.common.ScopedViewModelStores
import com.drinkwithme.app.ui.common.RefreshSignal
import com.drinkwithme.app.ui.event.EventViewModel
import com.drinkwithme.app.ui.friends.FriendsViewModel
import com.drinkwithme.app.ui.history.HistoryViewModel
import com.drinkwithme.app.ui.onboarding.OnboardingViewModel
import com.drinkwithme.app.ui.settings.PushSettings
import com.drinkwithme.app.ui.settings.SettingsViewModel
import com.drinkwithme.app.voice.AndroidVoiceInputAvailability
import com.drinkwithme.core.RandomIdGenerator
import com.drinkwithme.core.bar.SaveBarItemUseCase
import com.drinkwithme.core.bar.SetUpBarFromCatalogUseCase
import com.drinkwithme.core.log.DeleteEntryUseCase
import com.drinkwithme.core.log.DrinkEntry
import com.drinkwithme.core.log.HistoryReader
import com.drinkwithme.core.log.HistoryRepository
import com.drinkwithme.core.log.LogDrinkFromBarUseCase
import com.drinkwithme.core.log.UndoLastEntryUseCase
import com.drinkwithme.core.sharing.SharingHistoryRepository
import com.drinkwithme.core.sharing.SharingPolicy
import com.drinkwithme.core.sharing.SharingState
import com.drinkwithme.core.social.EventPublisher
import com.drinkwithme.core.stats.StatisticsCalculator
import com.drinkwithme.core.voice.VoiceCommandMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.Clock
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Kompositionswurzel: erzeugt alle Abhängigkeiten genau einmal und verdrahtet sie. Kein anderer Teil der App
 * erzeugt Implementierungen selbst (Dependency Inversion); Tests ersetzen einzelne Teile über die Schnittstellen.
 */
class AppContainer(private val application: Application) {
    private val clock: Clock = Clock.systemUTC()
    private val io = Dispatchers.IO
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private fun zone(): ZoneId = ZoneId.systemDefault()

    /** `null` = „Server noch nicht eingerichtet“ (keine `supabase.properties`). */
    val server: ServerConfig? = serverConfigFromBuild()

    // ---------- Lokale Daten (nur auf dem Gerät) ----------
    private val settingsStore = SharedPreferencesKeyValueStore(prefs(SETTINGS_PREFS))
    private val sessionStore = SharedPreferencesKeyValueStore(prefs(SESSION_PREFS), synchronous = true)
    val settings = AppSettings(settingsStore)
    private val notifiedEvents = NotifiedEvents(settingsStore)
    private val pendingShares = PendingShareQueue(settingsStore)
    val pushEndpoints = PushEndpointStore(settingsStore)
    private val barRepository = JsonFileBarRepository(File(application.filesDir, BAR_FILE), ::logError)
    private val localHistory = JsonFileHistoryRepository(File(application.filesDir, HISTORY_FILE), ::logError)
    private val messages = MessageQueue()
    private val friendsChanged = RefreshSignal()
    private val scheduler = WorkManagerScheduler(application)

    // ---------- Vernetzung (nur mit eingerichtetem Server) ----------
    private val http: SupabaseHttp? = server?.let { SupabaseHttp(SupabaseHttp.defaultClient(), it) }
    private val session: SupabaseSession? = http?.let {
        SupabaseSession(SupabaseAuthApi(it, clock), EncryptedSessionStore(sessionStore, AndroidKeystoreTokenCipher()), sessionStore, clock)
    }
    private val currentUserId: CurrentUserId = session ?: CurrentUserId { null }
    private val api: SupabaseApi? = if (http != null && session != null) SupabaseApi(http, session) else null
    private val profiles: ProfileLookup? = api?.let(::ProfileLookup)
    private val friendsService: SupabaseFriendsService? = if (api != null && profiles != null && server != null) {
        SupabaseFriendsService(api, profiles, currentUserId, server.webAppUrl)
    } else {
        null
    }
    private val feedService: SupabaseFeedService? = if (api != null && profiles != null) SupabaseFeedService(api, profiles, currentUserId) else null
    private val remotePublisher: EventPublisher? = api?.let(::SupabaseEventPublisher)
    private val distributors: PushDistributors? = server?.vapidPublicKey?.let { UnifiedPushDistributors(application, it) }
    val pushSubscriptionSync: PushSubscriptionSync? = api?.let {
        PushSubscriptionSync(pushEndpoints, SupabasePushSubscriptions(it), currentUserId) { distributors?.renew() }
    }

    /** Push ersetzt den Abruf nur, wenn das Abo nachweislich für das angemeldete Konto beim Server liegt. */
    private fun pushConfirmed(): Boolean = pushEndpoints.isConfirmedFor(currentUserId.currentUserId())

    private val sharingPolicy = SharingPolicy {
        SharingState(isSignedIn = session?.isSignedIn == true, alcoholConsent = settings.alcoholConsent.value)
    }
    private val eventPublisher: EventPublisher = remotePublisher?.let { RetryingEventPublisher(it, pendingShares, scheduler) } ?: NoServerPublisher

    /** Historie mit Teilen im Hintergrund (lokal speichern wartet nie auf das Netz). */
    private val history: HistoryRepository = SharingHistoryRepository(localHistory, eventPublisher, sharingPolicy, applicationScope, ::logError)

    val shareRetryProcessor: ShareRetryProcessor? = remotePublisher?.let {
        ShareRetryProcessor(pendingShares, localHistory, sharingPolicy, it, clock)
    }

    val socialBackground = SocialBackground(
        serverConfigured = server != null,
        isSignedIn = { session?.isSignedIn == true },
        isPushActive = ::pushConfirmed,
        polling = scheduler,
        scheduleSubscriptionSync = scheduler::scheduleSync,
    )

    private val notifier = AndroidFriendDrinkNotifier(application)
    val pushMessageHandler = PushMessageHandler(currentUserId, notifiedEvents, notifier)
    val feedPoller: FeedPoller? = feedService?.let { FeedPoller(it, currentUserId, settings, notifiedEvents, notifier, clock) }

    private val account: AccountController = if (session != null && profiles != null && api != null && pushSubscriptionSync != null) {
        SupabaseAccountController(
            session = session,
            profiles = profiles,
            account = SupabaseAccountApi(api),
            pushSync = pushSubscriptionSync,
            clearAccountData = ::clearAccountData,
            onSessionChanged = socialBackground::refresh,
        )
    } else {
        UnconfiguredAccountController()
    }

    // ---------- Plattform ----------
    val speaker = AndroidSpeaker(application)
    private val screenReader = AndroidScreenReaderDetector(application)
    val sharing = AndroidSharing(application)
    val links = AndroidLinks()
    val accountController: AccountController get() = account
    val messageQueue: MessageQueue get() = messages

    // ---------- Anwendungsfälle aus dem Kern ----------
    private val logDrink = LogDrinkFromBarUseCase(history, sharingPolicy, clock)
    private val undoLast = UndoLastEntryUseCase(history, clock)
    private val deleteEntry = DeleteEntryUseCase(history)
    private val saveBarItem = SaveBarItemUseCase(barRepository)
    private val setUpBar = SetUpBarFromCatalogUseCase(barRepository)

    private val pushSettings: PushSettings = object : PushSettings {
        override val available: Boolean = server != null && distributors != null
        override val endpoint: StateFlow<PushSubscription?> = pushEndpoints.current
        override val lastError: StateFlow<String?> = pushEndpoints.lastError
        override fun distributors(): List<DistributorInfo> = distributors?.installed().orEmpty()
        override fun selected(): String? = distributors?.selected()
        override fun use(distributor: DistributorInfo): Boolean {
            pushEndpoints.setLastError(null)
            return distributors?.use(distributor.packageName) == true
        }
        override fun disable() {
            distributors?.disable()
            pushEndpoints.setCurrent(null)
            socialBackground.refresh()
        }
    }

    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { AppViewModel(settings, barRepository, account, friendsService, friendsChanged, messages, io) }
        initializer { OnboardingViewModel(setUpBar, io) }
        initializer {
            BarViewModel(
                bar = barRepository,
                barChanges = barRepository,
                logDrink = logDrink,
                undoLast = undoLast,
                saveItem = saveBarItem,
                policy = sharingPolicy,
                settings = settings,
                isSignedIn = { session?.isSignedIn == true },
                speaker = speaker,
                screenReader = screenReader,
                voiceMatcher = VoiceCommandMatcher,
                voiceAvailability = AndroidVoiceInputAvailability(application),
                todaySummary = { StatisticsCalculator(clock, zone()).today(allEntries()) },
                latestFriendEvent = { feedService?.recentEvents(since = null, limit = 1)?.firstOrNull() },
                formMapper = DrinkFormMapper(RandomIdGenerator),
                messages = messages,
                sdkInt = Build.VERSION.SDK_INT,
                io = io,
            )
        }
        initializer {
            HistoryViewModel(history, localHistory, deleteEntry, settings.showAlcoholBands, speaker, messages, clock, ::zone, io)
        }
        initializer { FriendsViewModel(account, friendsService, friendsService, feedService, friendsChanged, messages, clock) }
        initializer { SettingsViewModel(account, settings, pushSettings, speaker, messages, io) }
        initializer { ScopedViewModelStores() }
    }

    /** Eigene Fabrik je Ereignis (der Bildschirm bekommt die Daten aus der Benachrichtigung mit). */
    fun eventViewModelFactory(request: AppRequest.OpenEvent): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            EventViewModel(request, feedService, speaker, screenReader, { settings.autoReadEvents.value.value }, clock, ::zone)
        }
    }

    private val registeredThisProcess = AtomicBoolean(false)
    private val foregroundRefresh = ForegroundRefreshThrottle(clock)

    /** Beim Start des Prozesses (auch für Hintergrundarbeit und Push). */
    fun onProcessStarted() {
        DrinkNotificationChannel.create(application)
        // Die Sitzung (Keystore, Datei) nicht auf dem Hauptthread lesen; danach die Hintergrundarbeit anpassen.
        applicationScope.launch(io) {
            session?.warmUp()
            socialBackground.refresh()
        }
    }

    /**
     * Die App kommt wirklich in den Vordergrund (ProcessLifecycleOwner ON_START – nicht jede Rückkehr aus
     * Erlaubnis-Dialog, Spracherkennung, Teilen-Menü): einmal pro Prozess beim Push-Distributor neu registrieren
     * (vom Connector empfohlen, falls er neu installiert wurde) und – höchstens alle paar Minuten – Freunde neu laden
     * und ohne bestätigtes Push-Abo gleich nach neuen Getränken der Freunde schauen.
     */
    fun onAppForeground() {
        applicationScope.launch(io) {
            val signedIn = session?.isSignedIn == true
            if (signedIn && registeredThisProcess.compareAndSet(false, true)) distributors?.reRegister()
            if (!foregroundRefresh.tryAcquire()) return@launch
            if (server != null && signedIn && !pushConfirmed()) scheduler.pollNow()
            friendsChanged.trigger()
        }
    }

    /** Kontobezogene Merker löschen – beim Abmelden, beim Löschen des Kontos und wenn die Sitzung verloren geht. */
    private fun clearAccountData() {
        settings.clearAccountData()
        notifiedEvents.clear()
        pendingShares.clear()
    }

    private fun allEntries(): List<DrinkEntry> = history.entries(HistoryReader.EARLIEST, HistoryReader.LATEST)

    private fun prefs(name: String) = application.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun logError(error: Throwable) {
        Log.w(TAG, "Hintergrundfehler: ${error.javaClass.simpleName}", error)
    }

    /** Ohne Server wird nie geteilt (die Teilen-Regel verlangt eine Anmeldung); nur als Platzhalter. */
    private object NoServerPublisher : EventPublisher {
        override suspend fun publish(entry: DrinkEntry) = Unit
        override suspend fun unpublish(entryId: String) = Unit
    }

    private companion object {
        const val TAG = "DrinkWithMe"
        const val SETTINGS_PREFS = "drinkwithme_settings"

        /** Muss zu res/xml/backup_rules.xml und data_extraction_rules.xml passen (nicht gesichert). */
        const val SESSION_PREFS = "drinkwithme_session"
        const val BAR_FILE = "bar.json"
        const val HISTORY_FILE = "history.json"
    }
}
