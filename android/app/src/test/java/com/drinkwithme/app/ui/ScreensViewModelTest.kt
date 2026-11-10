package com.drinkwithme.app.ui

import com.drinkwithme.app.FakeFeed
import com.drinkwithme.app.FakeScreenReader
import com.drinkwithme.app.FakeSpeaker
import com.drinkwithme.app.MutableClock
import com.drinkwithme.app.account.AccountController
import com.drinkwithme.app.data.AppSettings
import com.drinkwithme.app.data.ChangeSignal
import com.drinkwithme.app.data.InMemoryKeyValueStore
import com.drinkwithme.app.entry
import com.drinkwithme.app.friendEvent
import com.drinkwithme.app.profile
import com.drinkwithme.app.push.DistributorInfo
import com.drinkwithme.app.routing.AppRequest
import com.drinkwithme.app.social.OutgoingRequest
import com.drinkwithme.app.social.OutgoingRequestsService
import com.drinkwithme.app.speech.SpeakerStatus
import com.drinkwithme.app.supabase.AuthCallback
import com.drinkwithme.app.supabase.ErrorMessages
import com.drinkwithme.app.supabase.PushSubscription
import com.drinkwithme.app.supabase.SocialException
import com.drinkwithme.app.ui.common.MessageQueue
import com.drinkwithme.app.ui.common.RefreshSignal
import com.drinkwithme.app.ui.event.EventViewModel
import com.drinkwithme.app.ui.event.NotificationTextParser
import com.drinkwithme.app.ui.friends.FriendsViewModel
import com.drinkwithme.app.ui.friends.relativeTime
import com.drinkwithme.app.ui.history.HistoryViewModel
import com.drinkwithme.app.ui.navigation.AppSection
import com.drinkwithme.app.ui.onboarding.OnboardingViewModel
import com.drinkwithme.app.ui.settings.PushSettings
import com.drinkwithme.app.ui.settings.PushStatus
import com.drinkwithme.app.ui.settings.SettingsViewModel
import com.drinkwithme.core.bar.DefaultBarCatalog
import com.drinkwithme.core.bar.InMemoryBarRepository
import com.drinkwithme.core.bar.SetUpBarFromCatalogUseCase
import com.drinkwithme.core.log.DeleteEntryUseCase
import com.drinkwithme.core.log.InMemoryHistoryRepository
import com.drinkwithme.core.sharing.AlcoholSharingConsent
import com.drinkwithme.core.social.Friend
import com.drinkwithme.core.social.FriendRequest
import com.drinkwithme.core.social.FriendsService
import com.drinkwithme.core.social.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Konto-Fake für die Oberfläche. */
class FakeAccount(configured: Boolean = true, user: UserProfile? = null) : AccountController {
    override val isConfigured: Boolean = configured
    val user = MutableStateFlow(user)
    override val currentUser: StateFlow<UserProfile?> = this.user
    override val currentEmail: StateFlow<String?> = MutableStateFlow("me@example.org")
    var signInError: SocialException? = null
    var deleted = false
    var signedOut = false
    override suspend fun signInUrl(): String = "https://example.supabase.co/auth/v1/authorize"
    override suspend fun completeSignIn(callback: AuthCallback): UserProfile {
        signInError?.let { throw it }
        return profile("me-id", "Ich").also { user.value = it }
    }
    override suspend fun signOut() {
        signedOut = true
        user.value = null
    }
    override suspend fun deleteAccount() {
        deleted = true
        user.value = null
    }
}

/** Freunde-Fake mit Protokoll. */
class FakeFriends : FriendsService, OutgoingRequestsService {
    val calls = mutableListOf<String>()
    var friends = listOf(Friend(profile("anna-id", "Anna"), Instant.parse("2026-11-01T00:00:00Z")))
    var requests = listOf(
        FriendRequest("r1", profile("bob-id", "Bob"), FriendRequest.Direction.INCOMING, Instant.parse("2026-11-09T00:00:00Z")),
    )
    var outgoing = listOf(OutgoingRequest("r2", "c***@example.org", Instant.parse("2026-11-08T00:00:00Z")))
    var failure: SocialException? = null
    override suspend fun friends(): List<Friend> = friends.also { failure?.let { throw it } }
    override suspend fun requests(): List<FriendRequest> = requests
    override suspend fun outgoingRequests(): List<OutgoingRequest> = outgoing
    override suspend fun sendRequestByEmail(email: String) {
        failure?.let { throw it }
        calls += "email:$email"
    }
    override suspend fun respond(requestId: String, accept: Boolean) { calls += "respond:$requestId:$accept" }
    override suspend fun cancel(requestId: String) { calls += "cancel:$requestId" }
    override suspend fun createInviteLink(): String = "https://q0ochen.github.io/DrinkWithMe/einladung/AbCdEfGhIjKlMnOpQrStUv"
    override suspend fun acceptInvite(token: String): UserProfile {
        failure?.let { throw it }
        calls += "accept:$token"
        return profile("anna-id", "Anna")
    }
    override suspend fun removeFriend(userId: String) { calls += "remove:$userId" }
}

@OptIn(ExperimentalCoroutinesApi::class)
abstract class MainDispatcherTest {
    protected val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()
}

private class TestRevision : ChangeSignal {
    val flow = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = flow
}

class HistoryViewModelTest : MainDispatcherTest() {
    private val clock = MutableClock(Instant.parse("2026-11-10T20:00:00Z"))
    private val history = InMemoryHistoryRepository()
    private val revision = TestRevision()
    private val settings = AppSettings(InMemoryKeyValueStore())
    private val speaker = FakeSpeaker()
    private val messages = MessageQueue()

    private fun viewModel() = HistoryViewModel(
        history, revision, DeleteEntryUseCase(history), settings.showAlcoholBands, speaker, messages, clock, { ZoneOffset.UTC }, dispatcher,
    )

    @Test
    fun computesOverviewAndGroupsEntriesByDay() = runTest(dispatcher) {
        history.save(entry(id = "today", at = Instant.parse("2026-11-10T10:00:00Z")))
        history.save(entry(id = "yesterday", name = "Pils", alcohol = 4.9, sugar = 0.3, at = Instant.parse("2026-11-09T19:00:00Z")))
        val vm = viewModel()
        val state = vm.state.value
        assertFalse(state.loading)
        assertEquals(27.5, state.overview!!.today.sugarGrams)
        assertEquals(listOf("Heute", "Gestern"), state.days.map { it.heading })
        assertEquals(0, state.hiddenEntries)
    }

    @Test
    fun deleteNeedsConfirmationAndReloads() = runTest(dispatcher) {
        history.save(entry(id = "a"))
        val vm = viewModel()
        vm.onDeleteRequested(history.entry("a")!!)
        vm.onDeleteConfirmed(false)
        assertNotNull(history.entry("a"))
        vm.onDeleteRequested(history.entry("a")!!)
        vm.onDeleteConfirmed(true)
        assertNull(history.entry("a"))
        assertEquals(listOf("Apfelschorle gelöscht."), messages.texts)
    }

    @Test
    fun bandsToggleReadTodayAndSharePreview() = runTest(dispatcher) {
        history.save(entry(at = Instant.parse("2026-11-10T10:00:00Z")))
        val vm = viewModel()
        vm.onToggleBands()
        assertFalse(vm.state.value.showAlcoholBands)
        assertFalse(settings.showAlcoholBands.value.value)

        vm.onReadTodayAloud()
        assertTrue(speaker.spoken.single().startsWith("Heute aus Getränken: achtundzwanzig Gramm Zucker"))

        vm.onShareRequested()
        val card = assertNotNull(vm.state.value.sharePreview)
        assertFalse(card.showAlcoholBands)
        vm.onShareDismissed()
        assertNull(vm.state.value.sharePreview)
    }

    @Test
    fun longHistoriesShowTheNewestFirst() = runTest(dispatcher) {
        repeat(60) { history.save(entry(id = "e$it", at = Instant.parse("2026-11-01T00:00:00Z").plusSeconds(3600L * it))) }
        val vm = viewModel()
        assertEquals(10, vm.state.value.hiddenEntries)
        assertEquals("e59", vm.state.value.days.first().entries.first().id)
        vm.onShowAllEntries()
        assertEquals(0, vm.state.value.hiddenEntries)
        assertEquals(60, vm.state.value.days.sumOf { it.entries.size })
    }
}

class FriendsViewModelTest : MainDispatcherTest() {
    private val clock = MutableClock()
    private val account = FakeAccount(user = profile("me-id", "Ich"))
    private val friends = FakeFriends()
    private val feed = FakeFeed(listOf(friendEvent()))
    private val refresh = RefreshSignal()
    private val messages = MessageQueue()

    private fun viewModel() = FriendsViewModel(account, friends, friends, feed, refresh, messages, clock)

    @Test
    fun signedOutShowsOnlySignInCard() = runTest(dispatcher) {
        account.user.value = null
        val vm = viewModel()
        assertNull(vm.state.value.user)
        assertTrue(vm.state.value.configured)
        assertTrue(vm.state.value.feed.isEmpty())
    }

    @Test
    fun notConfiguredIsShown() = runTest(dispatcher) {
        val vm = FriendsViewModel(FakeAccount(configured = false), null, null, null, refresh, messages, clock)
        assertFalse(vm.state.value.configured)
    }

    @Test
    fun loadsFeedFriendsAndRequests() = runTest(dispatcher) {
        val vm = viewModel()
        val state = vm.state.value
        assertEquals(listOf("Anna"), state.friends.map { it.profile.displayName })
        assertEquals(listOf("r1"), state.incoming.map { it.id })
        assertEquals(listOf("r2"), state.outgoing.map { it.id })
        assertEquals(listOf("ev-1"), state.feed.map { it.id })
        assertEquals(clock.now, state.now)
    }

    @Test
    fun emailRequestsAreValidatedAndAnsweredNeutrally() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onOpenEmailDialog()
        vm.onSendEmailRequest("keine-adresse")
        assertEquals(listOf(FriendsViewModel.INVALID_EMAIL), messages.texts)
        assertTrue(vm.state.value.emailDialogOpen)

        vm.onSendEmailRequest(" anna@example.org ")
        assertEquals(listOf("email:anna@example.org"), friends.calls)
        assertFalse(vm.state.value.emailDialogOpen)
        assertEquals(FriendsViewModel.EMAIL_SENT, messages.texts.last())
    }

    @Test
    fun serverErrorsBecomeGermanMessages() = runTest(dispatcher) {
        val vm = viewModel()
        friends.failure = SocialException.Server("rate_limited", 400)
        vm.onSendEmailRequest("anna@example.org")
        assertEquals(ErrorMessages.RATE_LIMITED, messages.texts.single())
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun requestsAndRemovalCallTheService() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onRespond(friends.requests[0], accept = true)
        vm.onCancelRequest(friends.outgoing[0])
        vm.onRemoveRequested(friends.friends[0])
        vm.onRemoveConfirmed(true)
        assertEquals(listOf("respond:r1:true", "cancel:r2", "remove:anna-id"), friends.calls)
        assertEquals("Du bist jetzt mit Bob befreundet.", messages.texts.first())
        assertEquals("Anfrage an c***@example.org zurückgezogen.", messages.texts[1])
    }

    @Test
    fun outgoingRequestsShowOnlyTheMaskedAddress() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(listOf("Anfrage an c***@example.org"), vm.state.value.outgoing.map(FriendsViewModel::outgoingLabel))
    }

    @Test
    fun inviteLinkIsHandedToTheShareSheetOnce() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onCreateInvite()
        assertTrue(vm.state.value.inviteToShare!!.startsWith("https://q0ochen.github.io/DrinkWithMe/einladung/"))
        vm.onInviteShared()
        assertNull(vm.state.value.inviteToShare)
    }

    @Test
    fun loadErrorsAreShownInline() = runTest(dispatcher) {
        friends.failure = SocialException.Network(java.io.IOException())
        val vm = viewModel()
        assertEquals(ErrorMessages.NETWORK, vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun relativeTimesAreGerman() {
        val now = Instant.parse("2026-11-10T12:00:00Z")
        assertEquals("gerade eben", relativeTime(now.minusSeconds(30), now, ZoneOffset.UTC))
        assertEquals("vor 5 Minuten", relativeTime(now.minusSeconds(300), now, ZoneOffset.UTC))
        assertEquals("vor 1 Stunde", relativeTime(now.minusSeconds(3700), now, ZoneOffset.UTC))
        assertEquals("vor 3 Stunden", relativeTime(now.minusSeconds(3 * 3600 + 5), now, ZoneOffset.UTC))
        assertEquals("8.11. um 09:30 Uhr", relativeTime(Instant.parse("2026-11-08T09:30:00Z"), now, ZoneOffset.UTC))
    }
}

class EventViewModelTest : MainDispatcherTest() {
    private val clock = MutableClock(Instant.parse("2026-11-10T12:00:00Z"))
    private val speaker = FakeSpeaker()
    private val screenReader = FakeScreenReader()
    private var autoRead = false

    private fun viewModel(request: AppRequest.OpenEvent, found: Boolean = true) = EventViewModel(
        request,
        { id -> if (found) friendEvent(id, at = clock.now.minusSeconds(180)) else null },
        speaker,
        screenReader,
        { autoRead },
        clock,
        { ZoneOffset.UTC },
    )

    @Test
    fun showsBigSentenceAndReadsOnRequest() = runTest(dispatcher) {
        val vm = viewModel(AppRequest.OpenEvent("ev-1", null, null, autoRead = false))
        val state = vm.state.value
        assertEquals("Anna trinkt eine Apfelschorle.", state.headline)
        assertEquals("500 ml · vor 3 Minuten", state.detail)
        assertTrue(speaker.spoken.isEmpty(), "ohne Einstellung nicht automatisch")
        assertEquals(1, speaker.prepared)
        vm.onReadAloud()
        assertEquals("Anna trinkt eine Apfelschorle, fünfhundert Milliliter.", speaker.spoken.single())
    }

    @Test
    fun notificationActionReadsEvenWithScreenReader() = runTest(dispatcher) {
        screenReader.active = true
        viewModel(AppRequest.OpenEvent("ev-1", null, null, autoRead = true))
        assertEquals(1, speaker.spoken.size)
    }

    @Test
    fun autoReadSettingRespectsScreenReader() = runTest(dispatcher) {
        autoRead = true
        screenReader.active = true
        viewModel(AppRequest.OpenEvent("ev-1", null, null, autoRead = false))
        assertTrue(speaker.spoken.isEmpty())
        screenReader.active = false
        viewModel(AppRequest.OpenEvent("ev-2", null, null, autoRead = false))
        assertEquals(1, speaker.spoken.size)
    }

    @Test
    fun offlineFallsBackToNotificationTexts() = runTest(dispatcher) {
        val vm = viewModel(AppRequest.OpenEvent("ev-9", "Anna trinkt gerade", "Rotwein trocken (125 ml)", autoRead = true), found = false)
        assertEquals("Anna trinkt einen Rotwein trocken.", vm.state.value.headline)
        assertEquals("Anna trinkt einen Rotwein trocken, einhundertfünfundzwanzig Milliliter.", speaker.spoken.single())
    }

    @Test
    fun missingGermanVoiceShowsSetupHelpRightOnTheEventScreen() = runTest(dispatcher) {
        val speaker = FakeSpeaker(SpeakerStatus.NO_ENGINE)
        val vm = EventViewModel(
            AppRequest.OpenEvent("ev-1", null, null, autoRead = false),
            { id -> friendEvent(id, at = clock.now.minusSeconds(180)) },
            speaker, screenReader, { autoRead }, clock, { ZoneOffset.UTC },
        )
        assertEquals(SpeakerStatus.NO_ENGINE, vm.state.value.speakerStatus, "Karte „Vorlesen einrichten“")
        assertFalse(vm.state.value.speechHelpOpen)

        vm.onReadAloud()
        assertTrue(vm.state.value.speechHelpOpen, "Tippen auf „Vorlesen“ bleibt nicht folgenlos")
        vm.onSpeechHelp(false)
        assertFalse(vm.state.value.speechHelpOpen)

        speaker.statusFlow.value = SpeakerStatus.READY
        assertEquals(SpeakerStatus.READY, vm.state.value.speakerStatus)
        vm.onReadAloud()
        assertFalse(vm.state.value.speechHelpOpen)
        assertEquals(1, speaker.spoken.size)
    }

    @Test
    fun notificationActionWithoutVoiceOpensTheHelp() = runTest(dispatcher) {
        val vm = EventViewModel(
            AppRequest.OpenEvent("ev-1", null, null, autoRead = true),
            { id -> friendEvent(id) },
            FakeSpeaker(SpeakerStatus.MISSING_DATA), screenReader, { autoRead }, clock, { ZoneOffset.UTC },
        )
        assertTrue(vm.state.value.speechHelpOpen)
    }

    @Test
    fun notificationParserHandlesOddTexts() {
        assertEquals(NotificationTextParser.Parsed("Bob", "Mate", null), NotificationTextParser.parse("Bob trinkt gerade", "Mate"))
        assertNull(NotificationTextParser.parse("Neue Nachricht", "Mate (330 ml)"))
        assertNull(NotificationTextParser.parse(null, null))
    }
}

private class FakePush(override val available: Boolean = true) : PushSettings {
    override val endpoint = MutableStateFlow<PushSubscription?>(null)
    override val lastError = MutableStateFlow<String?>(null)
    var installed = listOf<DistributorInfo>()
    var chosen: String? = null
    override fun distributors(): List<DistributorInfo> = installed
    override fun selected(): String? = chosen
    override fun use(distributor: DistributorInfo): Boolean {
        chosen = distributor.packageName
        return true
    }
    override fun disable() {
        chosen = null
        endpoint.value = null
    }
}

class SettingsViewModelTest : MainDispatcherTest() {
    private val account = FakeAccount(user = profile("me-id", "Ich"))
    private val settings = AppSettings(InMemoryKeyValueStore())
    private val push = FakePush()
    private val messages = MessageQueue()

    private fun viewModel() = SettingsViewModel(account, settings, push, FakeSpeaker(), messages, dispatcher)

    @Test
    fun pushAssistantRecommendsNtfyAndTracksSetup() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(PushStatus.NoDistributor, vm.state.value.push)

        val ntfy = DistributorInfo("io.heckel.ntfy", "ntfy")
        push.installed = listOf(ntfy, DistributorInfo("org.example.other", "Andere"))
        vm.refreshPush()
        assertEquals(PushStatus.Choose(push.installed), vm.state.value.push)
        assertTrue(ntfy.isNtfy)

        vm.onUseDistributor(ntfy)
        assertEquals(PushStatus.Waiting(ntfy), vm.state.value.push)
        push.endpoint.value = PushSubscription("https://ntfy.sh/up", "p", "a")
        assertEquals(PushStatus.Active(ntfy), vm.state.value.push)

        vm.onDisablePush()
        assertEquals(PushStatus.Choose(push.installed), vm.state.value.push)
        assertEquals(SettingsViewModel.PUSH_DISABLED, messages.texts.last())
    }

    @Test
    fun withoutVapidOnlyPollingIsOffered() = runTest(dispatcher) {
        val vm = SettingsViewModel(account, settings, FakePush(available = false), FakeSpeaker(), messages, dispatcher)
        assertEquals(PushStatus.Unavailable, vm.state.value.push)
    }

    @Test
    fun consentAndDisplaySettingsAreSaved() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAlcoholSharingChanged(true)
        assertEquals(AlcoholSharingConsent.ALLOWED, vm.state.value.alcoholConsent)
        vm.onAlcoholSharingChanged(false)
        assertEquals(AlcoholSharingConsent.DENIED, settings.alcoholConsent.value)
        vm.onAutoReadEventsChanged(true)
        vm.onAnnounceLoggedChanged(false)
        assertTrue(vm.state.value.autoReadEvents)
        assertFalse(vm.state.value.announceLogged)
    }

    @Test
    fun accountDeletionNeedsConfirmation() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals("me@example.org", vm.state.value.email)
        vm.onDeleteAccountRequested()
        assertTrue(vm.state.value.confirmDeleteAccount)
        vm.onDeleteAccountConfirmed(false)
        assertFalse(account.deleted)
        vm.onDeleteAccountRequested()
        vm.onDeleteAccountConfirmed(true)
        assertTrue(account.deleted)
        assertNull(vm.state.value.user)
        assertEquals(SettingsViewModel.ACCOUNT_DELETED, messages.texts.last())
    }

    @Test
    fun testSpeechWithoutUsableVoiceOpensTheHelp() = runTest(dispatcher) {
        val speaker = FakeSpeaker(SpeakerStatus.MISSING_DATA)
        val vm = SettingsViewModel(account, settings, push, speaker, messages, dispatcher)
        vm.onTestSpeech()
        assertTrue(vm.state.value.speechHelpOpen, "auch wenn nur Sprachdaten fehlen")
        assertTrue(speaker.spoken.isEmpty())
    }

    @Test
    fun signOutAfterConfirmation() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onSignOutRequested()
        vm.onSignOutConfirmed(true)
        assertTrue(account.signedOut)
    }
}

class AppViewModelTest : MainDispatcherTest() {
    private val settings = AppSettings(InMemoryKeyValueStore())
    private val bar = InMemoryBarRepository()
    private val account = FakeAccount()
    private val friends = FakeFriends()
    private val refresh = RefreshSignal()
    private val messages = MessageQueue()

    private fun viewModel() = AppViewModel(settings, bar, account, friends, refresh, messages, dispatcher)

    @Test
    fun firstStartNeedsOnboardingUntilFinished() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(true, vm.state.value.needsOnboarding)
        vm.onOnboardingFinished()
        assertEquals(false, vm.state.value.needsOnboarding)
        assertTrue(settings.onboardingDone)
    }

    @Test
    fun existingBarSkipsOnboarding() = runTest(dispatcher) {
        bar.save(DefaultBarCatalog.item(DefaultBarCatalog.WASSER)!!)
        assertEquals(false, viewModel().state.value.needsOnboarding)
    }

    @Test
    fun inviteWhileSignedOutWaitsForSignIn() = runTest(dispatcher) {
        val vm = viewModel()
        vm.handle(AppRequest.AcceptInvite("AbCdEfGhIjKlMnOpQrStUv"))
        assertEquals(AppSection.FRIENDS, vm.state.value.section)
        assertNull(vm.state.value.inviteToConfirm)
        assertEquals(AppViewModel.SIGN_IN_FOR_INVITE, messages.texts.single())

        vm.handle(AppRequest.SignInCallback(AuthCallback.Code("code")))
        assertEquals("AbCdEfGhIjKlMnOpQrStUv", vm.state.value.inviteToConfirm, "nach der Anmeldung nachfragen")
        assertTrue(vm.state.value.askNotificationPermission)
        assertEquals("Angemeldet als Ich.", messages.texts.last())

        vm.onInviteAnswered(true)
        assertEquals(listOf("accept:AbCdEfGhIjKlMnOpQrStUv"), friends.calls)
        assertEquals("Du bist jetzt mit Anna befreundet.", messages.texts.last())
        assertEquals(2L, refresh.value.value, "Freunde neu laden: nach Anmeldung und nach Annahme")
        assertNull(settings.pendingInviteToken)
    }

    @Test
    fun declinedInviteDoesNothing() = runTest(dispatcher) {
        account.user.value = profile("me-id", "Ich")
        val vm = viewModel()
        vm.handle(AppRequest.AcceptInvite("AbCdEfGhIjKlMnOpQrStUv"))
        vm.onInviteAnswered(false)
        assertTrue(friends.calls.isEmpty())
    }

    @Test
    fun failedSignInIsExplained() = runTest(dispatcher) {
        account.signInError = SocialException.NotSignedIn(ErrorMessages.SIGN_IN_CANCELLED)
        val vm = viewModel()
        vm.handle(AppRequest.SignInCallback(AuthCallback.Failure("access_denied", null)))
        assertEquals(ErrorMessages.SIGN_IN_CANCELLED, messages.texts.single())
        assertFalse(vm.state.value.signingIn)
    }

    @Test
    fun notificationOpensEventScreen() = runTest(dispatcher) {
        val vm = viewModel()
        val request = AppRequest.OpenEvent("ev-1", "Anna trinkt gerade", "Tee (250 ml)", autoRead = true)
        vm.handle(request)
        assertEquals(request, vm.state.value.openEvent?.request)
        vm.onEventClosed()
        assertNull(vm.state.value.openEvent)
    }

    @Test
    fun everyOpeningOfAnEventIsANewScreen() = runTest(dispatcher) {
        // Zweimal dieselbe Benachrichtigungs-Aktion „Vorlesen“: Jedes Öffnen braucht ein eigenes ViewModel (das liest vor).
        val vm = viewModel()
        val request = AppRequest.OpenEvent("ev-1", null, null, autoRead = true)
        vm.handle(request)
        val first = vm.state.value.openEvent!!
        vm.onEventClosed()
        vm.handle(request)
        val second = vm.state.value.openEvent!!
        vm.handle(request)
        val third = vm.state.value.openEvent!!
        assertEquals(setOf(request), setOf(first.request, second.request, third.request))
        assertEquals(3, setOf(first.key, second.key, third.key).size)
    }

    @Test
    fun withoutServerInvitesExplainWhy() = runTest(dispatcher) {
        val vm = AppViewModel(settings, bar, FakeAccount(configured = false), null, refresh, messages, dispatcher)
        vm.handle(AppRequest.AcceptInvite("AbCdEfGhIjKlMnOpQrStUv"))
        assertEquals(ErrorMessages.NOT_CONFIGURED, messages.texts.single())
    }
}

class OnboardingViewModelTest : MainDispatcherTest() {
    @Test
    fun defaultSelectionIsPrecheckedAndSavedInCatalogOrder() = runTest(dispatcher) {
        val bar = InMemoryBarRepository()
        val vm = OnboardingViewModel(SetUpBarFromCatalogUseCase(bar), dispatcher)
        assertEquals(DefaultBarCatalog.defaultSelectionIds.toSet(), vm.state.value.selected)
        vm.onToggle(DefaultBarCatalog.ROTWEIN)
        vm.onToggle(DefaultBarCatalog.MINERALWASSER)
        vm.onFinish()
        assertTrue(vm.state.value.finished)
        assertEquals(
            listOf(DefaultBarCatalog.WASSER, DefaultBarCatalog.MINERALWASSER, DefaultBarCatalog.KAFFEE, DefaultBarCatalog.TEE,
                DefaultBarCatalog.COLA, DefaultBarCatalog.APFELSCHORLE, DefaultBarCatalog.PILS),
            bar.items().map { it.id },
        )
    }

    @Test
    fun cannotFinishWithEmptyBar() = runTest(dispatcher) {
        val bar = InMemoryBarRepository()
        val vm = OnboardingViewModel(SetUpBarFromCatalogUseCase(bar), dispatcher)
        DefaultBarCatalog.defaultSelectionIds.forEach(vm::onToggle)
        assertFalse(vm.state.value.canFinish)
        vm.onFinish()
        assertFalse(vm.state.value.finished)
        assertTrue(bar.items().isEmpty())
    }
}
