package com.drinkwithme.app.ui.bar

import com.drinkwithme.app.APFELSAFT
import com.drinkwithme.app.APFELSCHORLE
import com.drinkwithme.app.FakeScreenReader
import com.drinkwithme.app.FakeSpeaker
import com.drinkwithme.app.MutableClock
import com.drinkwithme.app.PILS
import com.drinkwithme.app.barItem
import com.drinkwithme.app.data.AppSettings
import com.drinkwithme.app.data.ChangeSignal
import com.drinkwithme.app.data.InMemoryKeyValueStore
import com.drinkwithme.app.friendEvent
import com.drinkwithme.app.ui.common.MessageQueue
import com.drinkwithme.core.IdGenerator
import com.drinkwithme.core.bar.BarItem
import com.drinkwithme.core.bar.DefaultBarCatalog
import com.drinkwithme.core.bar.DrinkCategory
import com.drinkwithme.core.bar.DrinkIcon
import com.drinkwithme.core.bar.InMemoryBarRepository
import com.drinkwithme.core.bar.SaveBarItemUseCase
import com.drinkwithme.core.bar.ValueSource
import com.drinkwithme.core.log.HistoryReader
import com.drinkwithme.core.log.InMemoryHistoryRepository
import com.drinkwithme.core.log.LogDrinkFromBarUseCase
import com.drinkwithme.core.log.UndoLastEntryUseCase
import com.drinkwithme.core.sharing.AlcoholSharingConsent
import com.drinkwithme.core.sharing.SharingPolicy
import com.drinkwithme.core.sharing.SharingState
import com.drinkwithme.core.stats.TodaySummary
import com.drinkwithme.core.voice.VoiceCommandMatcher
import com.drinkwithme.core.voice.VoiceMatch
import com.drinkwithme.core.voice.VoiceMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrinkFormMapperTest {
    private val mapper = DrinkFormMapper(IdGenerator { "neu-1" })

    @Test
    fun acceptsGermanDecimalsAndAliases() {
        val form = mapper.newForm(sortOrder = 5).copy(
            name = "  Mate ",
            icon = DrinkIcon.ICED_TEA,
            category = DrinkCategory.SOFT_DRINK,
            servingText = "330",
            alcoholText = "0",
            sugarText = "4,5",
            aliasesText = "Club-Mate, ; Mate ,mate",
        )
        val item = (mapper.toItem(form) as FormResult.Valid).item
        assertEquals("neu-1", item.id)
        assertEquals("Mate", item.name)
        assertEquals(4.5, item.sugarGramsPer100Ml)
        assertEquals(listOf("Club-Mate"), item.aliases, "leere und doppelte (auch gleich dem Namen) entfernt")
        assertEquals(ValueSource.USER, item.valueSource)
        assertEquals(5, item.sortOrder)
    }

    @Test
    fun reportsParseAndValidationErrorsInGerman() {
        val parse = mapper.toItem(mapper.newForm(0).copy(name = "X", servingText = "viel", alcoholText = "1,2,3", sugarText = ""))
        assertEquals(3, (parse as FormResult.Invalid).errors.size)
        val invalid = mapper.toItem(mapper.newForm(0).copy(name = " ", servingText = "3000", alcoholText = "95"))
        val errors = (invalid as FormResult.Invalid).errors
        assertTrue(errors.contains("Bitte gib einen Namen ein."))
        assertTrue(errors.any { it.startsWith("Die Portion muss zwischen 1 und 2000 ml") })
        assertTrue(errors.any { it.startsWith("Der Alkoholgehalt muss zwischen 0 und 80") })
    }

    @Test
    fun keepsCatalogSourceUntilValuesChange() {
        val catalog = DefaultBarCatalog.item(DefaultBarCatalog.PILS)!!
        val renamed = mapper.toItem(mapper.fromItem(catalog).copy(name = "Mein Pils")) as FormResult.Valid
        assertEquals(ValueSource.DEFAULT, renamed.item.valueSource)
        assertEquals(catalog.id, renamed.item.id)
        val stronger = mapper.toItem(mapper.fromItem(catalog).copy(alcoholText = "5,2")) as FormResult.Valid
        assertEquals(ValueSource.USER, stronger.item.valueSource)
        assertEquals("4,9", mapper.fromItem(catalog).alcoholText)
    }
}

private class Revision(private val flow: MutableStateFlow<Long> = MutableStateFlow(0)) : ChangeSignal {
    override val revision: StateFlow<Long> = flow
    fun bump() {
        flow.value = flow.value + 1
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BarViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = MutableClock()
    private val bar = InMemoryBarRepository(listOf(barItem(sortOrder = 0), PILS, APFELSCHORLE, APFELSAFT))
    private val changes = Revision()
    private val history = InMemoryHistoryRepository()
    private val settings = AppSettings(InMemoryKeyValueStore())
    private var signedIn = true
    private val policy = SharingPolicy { SharingState(signedIn, settings.alcoholConsent.value) }
    private val speaker = FakeSpeaker()
    private val screenReader = FakeScreenReader()
    private val messages = MessageQueue()
    private val ids = AtomicInteger()
    private var friendEvent = friendEvent()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(matcher: VoiceMatcher = VoiceCommandMatcher) = BarViewModel(
        bar = bar,
        barChanges = changes,
        logDrink = LogDrinkFromBarUseCase(history, policy, clock, IdGenerator { "id-${ids.incrementAndGet()}" }),
        undoLast = UndoLastEntryUseCase(history, clock),
        saveItem = SaveBarItemUseCase(object : com.drinkwithme.core.bar.BarWriter by bar {
            override fun save(item: BarItem) {
                bar.save(item)
                changes.bump()
            }
        }),
        policy = policy,
        settings = settings,
        isSignedIn = { signedIn },
        speaker = speaker,
        screenReader = screenReader,
        voiceMatcher = matcher,
        voiceAvailability = { true },
        todaySummary = { TodaySummary(35.0, 0.0, 1, 0, 500, 1) },
        latestFriendEvent = { friendEvent },
        formMapper = DrinkFormMapper(IdGenerator { "custom-1" }),
        messages = messages,
        sdkInt = 34,
        io = dispatcher,
    )

    private fun all() = history.entries(HistoryReader.EARLIEST, HistoryReader.LATEST)

    @Test
    fun showsBarAndVoiceButton() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(listOf("wasser", "pils", "schorle", "saft"), vm.state.value.items.map { it.id })
        assertTrue(vm.state.value.voiceAvailable)
        assertEquals(listOf("Wasser", "Pils", "Bier", "Apfelschorle", "Schorle", "Apfelsaft"), vm.voiceRequest().biasingStrings)
    }

    @Test
    fun oneTapLogsWithConfirmationSpeechAndTenSecondUndo() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onDrinkTapped(APFELSCHORLE)

        val entry = all().single()
        assertEquals("Apfelschorle", entry.name)
        assertEquals(clock.now, entry.consumedAt, "Zeitpunkt immer jetzt")
        assertTrue(entry.shareWithFriends)
        val confirmation = assertNotNull(vm.state.value.confirmation)
        assertEquals("Apfelschorle eingetragen.", confirmation.text)
        assertEquals(BarViewModel.SHARED_HINT, confirmation.sharingHint)
        assertEquals(listOf("Apfelschorle eingetragen."), speaker.spoken)

        advanceTimeBy(BarViewModel.UNDO_VISIBLE_MILLIS + 1)
        assertNull(vm.state.value.confirmation, "Rückgängig-Knopf verschwindet nach 10 s")
    }

    @Test
    fun undoWithinTenSecondsRemovesTheEntry() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onDrinkTapped(APFELSCHORLE)
        clock.advance(Duration.ofSeconds(5))
        vm.onUndo()
        assertTrue(all().isEmpty())
        assertNull(vm.state.value.confirmation)
        assertEquals(listOf("Apfelschorle wieder entfernt."), messages.messages.value)
    }

    @Test
    fun olderEntriesNeedConfirmationToUndo() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onDrinkTapped(APFELSCHORLE)
        clock.advance(Duration.ofMinutes(5))
        vm.onUndo()
        assertNotNull(vm.state.value.confirmUndo)
        vm.onConfirmUndo(false)
        assertEquals(1, all().size)
        vm.onUndo()
        vm.onConfirmUndo(true)
        assertTrue(all().isEmpty())
    }

    @Test
    fun noSpeechWhenScreenReaderIsActiveOrDisabled() = runTest(dispatcher) {
        screenReader.active = true
        val vm = viewModel()
        vm.onDrinkTapped(APFELSCHORLE)
        screenReader.active = false
        settings.announceLogged.set(false)
        vm.onDrinkTapped(APFELSAFT)
        assertTrue(speaker.spoken.isEmpty())
        assertEquals(2, all().size)
    }

    @Test
    fun firstAlcoholicDrinkAsksOnceForConsent() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onDrinkTapped(PILS)
        assertEquals(PendingLog(PILS, null), vm.state.value.pendingConsent)
        assertTrue(all().isEmpty(), "erst nach der Antwort eintragen")

        vm.onConsentAnswered(true)
        assertNull(vm.state.value.pendingConsent)
        assertEquals(AlcoholSharingConsent.ALLOWED, settings.alcoholConsent.value)
        assertTrue(all().single().shareWithFriends)

        vm.onDrinkTapped(PILS)
        assertNull(vm.state.value.pendingConsent, "nur einmal fragen")
        assertEquals(2, all().size)
    }

    @Test
    fun consentDeniedOrDismissedKeepsAlcoholPrivate() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onDrinkTapped(PILS)
        vm.onConsentAnswered(null)
        assertEquals(AlcoholSharingConsent.UNKNOWN, settings.alcoholConsent.value, "ohne Antwort später erneut fragen")
        assertFalse(all().single().shareWithFriends)
        assertEquals(BarViewModel.PRIVATE_HINT, vm.state.value.confirmation?.sharingHint)

        vm.onDrinkTapped(PILS)
        vm.onConsentAnswered(false)
        assertEquals(AlcoholSharingConsent.DENIED, settings.alcoholConsent.value)
        assertTrue(all().none { it.shareWithFriends })
    }

    @Test
    fun noConsentQuestionWhenSignedOutOrPrivate() = runTest(dispatcher) {
        signedIn = false
        val vm = viewModel()
        vm.onDrinkTapped(PILS)
        assertNull(vm.state.value.pendingConsent)
        assertNull(vm.state.value.confirmation?.sharingHint, "ohne Anmeldung kein Hinweis zum Teilen")
        signedIn = true
        vm.onLogPrivately(PILS)
        assertNull(vm.state.value.pendingConsent)
        assertTrue(all().none { it.shareWithFriends })
    }

    @Test
    fun voiceDrinkLogsLikeATap() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onVoiceResults(listOf("ich trinke eine Schorle"))
        assertEquals("Apfelschorle", all().single().name)
        vm.onVoiceResults(null)
        vm.onVoiceResults(emptyList())
        assertEquals(1, all().size, "abgebrochen = nichts")
    }

    @Test
    fun ambiguousVoiceAsksWithTwoBigChoices() = runTest(dispatcher) {
        val vm = viewModel(matcher = { _, _ -> VoiceMatch.Ambiguous(APFELSCHORLE, APFELSAFT) })
        vm.onVoiceResults(listOf("apfel"))
        assertEquals(VoiceMatch.Ambiguous(APFELSCHORLE, APFELSAFT), vm.state.value.ambiguity)
        assertEquals("Meinst du Apfelschorle oder Apfelsaft?", speaker.spoken.last())
        assertTrue(all().isEmpty())
        vm.onAmbiguityResolved(APFELSAFT)
        assertNull(vm.state.value.ambiguity)
        assertEquals("Apfelsaft", all().single().name)
    }

    @Test
    fun ambiguityCanBeCancelled() = runTest(dispatcher) {
        val vm = viewModel(matcher = { _, _ -> VoiceMatch.Ambiguous(APFELSCHORLE, APFELSAFT) })
        vm.onVoiceResults(listOf("apfel"))
        vm.onAmbiguityResolved(null)
        assertNull(vm.state.value.ambiguity)
        assertTrue(all().isEmpty())
    }

    @Test
    fun undoWithoutEntriesSaysSo() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onVoiceResults(listOf("rückgängig"))
        assertEquals(listOf(BarViewModel.NOTHING_TO_UNDO), messages.messages.value)
    }

    @Test
    fun voiceCommandsUndoTodayAndReadAloud() = runTest(dispatcher) {
        screenReader.active = true
        val vm = viewModel()
        vm.onDrinkTapped(APFELSCHORLE)
        vm.onVoiceResults(listOf("rückgängig"))
        assertTrue(all().isEmpty())

        vm.onVoiceResults(listOf("wie viel heute"))
        assertEquals("Heute aus Getränken: fünfunddreißig Gramm Zucker. Insgesamt fünfhundert Milliliter.", speaker.spoken.last(),
            "ausdrücklich gefragt – auch mit Screenreader")
        vm.onVoiceResults(listOf("vorlesen"))
        assertEquals("Anna trinkt eine Apfelschorle.", speaker.spoken.last())
    }

    @Test
    fun unknownSpeechIsExplained() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onVoiceResults(listOf("Raumschiff"))
        assertTrue(messages.messages.value.single().startsWith("Das habe ich leider nicht verstanden"))
        assertTrue(all().isEmpty())
    }

    @Test
    fun editorValidatesAndSaves() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAddCustom()
        val form = vm.state.value.editor!!.form
        vm.onEditorChange(form.copy(name = ""))
        vm.onEditorSave()
        assertEquals(listOf("Bitte gib einen Namen ein."), vm.state.value.editor!!.errors)

        vm.onEditorChange(form.copy(name = "Mate", sugarText = "5"))
        vm.onEditorSave()
        assertNull(vm.state.value.editor)
        assertEquals("Mate", bar.item("custom-1")?.name)
        assertEquals("custom-1", vm.state.value.items.last().id, "neues Getränk hinten")
    }

    @Test
    fun catalogOffersOnlyMissingEntriesAndDeleteNeedsConfirmation() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onOpenCatalog()
        val choices = vm.state.value.catalogChoices!!
        assertEquals(DefaultBarCatalog.entries.size, choices.size, "Test-Bar nutzt keine Katalog-IDs")
        vm.onAddFromCatalog(choices.first { it.item.id == DefaultBarCatalog.MILCH })
        assertNotNull(bar.item(DefaultBarCatalog.MILCH))
        vm.onOpenCatalog()
        assertTrue(vm.state.value.catalogChoices!!.none { it.item.id == DefaultBarCatalog.MILCH })
        vm.onCloseCatalog()

        vm.onEdit(APFELSAFT)
        vm.onEditorDeleteRequested()
        assertTrue(vm.state.value.editor!!.confirmDelete)
        vm.onEditorDeleteConfirmed(true)
        assertNull(bar.item(APFELSAFT.id))
    }

    @Test
    fun moveChangesOrder() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onMove(APFELSCHORLE, towardsStart = true)
        assertEquals(listOf("wasser", "schorle", "pils", "saft"), bar.items().map { it.id })
        vm.onMove(barItem(sortOrder = 0), towardsStart = true)
        assertEquals("wasser", bar.items().first().id, "erstes bleibt erstes")
    }
}
