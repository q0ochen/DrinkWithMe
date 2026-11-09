package com.drinkwithme.app.ui.groups

import com.drinkwithme.core.social.DrinkGroup
import com.drinkwithme.core.social.GroupMember
import com.drinkwithme.core.social.GroupNotFoundException
import com.drinkwithme.core.social.InviteCode
import com.drinkwithme.core.social.SocialValidationException
import com.drinkwithme.core.social.usecase.CreateGroupUseCase
import com.drinkwithme.core.social.usecase.JoinGroupUseCase
import com.drinkwithme.core.social.usecase.LeaveGroupUseCase
import com.drinkwithme.core.social.usecase.ObserveMyGroupsUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val groups = MutableStateFlow(listOf(group("Stammtisch")))
    private var createResult: suspend () -> DrinkGroup = { group("Neu") }
    private var joinResult: suspend () -> DrinkGroup = { group("Beigetreten") }
    private var leaveResult: () -> Unit = {}
    private val left = mutableListOf<String>()

    private val viewModel by lazy {
        GroupsViewModel(
            observeMyGroups = object : ObserveMyGroupsUseCase {
                override fun invoke(): Flow<List<DrinkGroup>> = groups
            },
            createGroup = object : CreateGroupUseCase {
                override suspend fun invoke(name: String): DrinkGroup = createResult()
            },
            joinGroup = object : JoinGroupUseCase {
                override suspend fun invoke(rawCode: String): DrinkGroup = joinResult()
            },
            leaveGroup = object : LeaveGroupUseCase {
                override suspend fun invoke(groupId: String) {
                    leaveResult()
                    left += groupId
                }
            },
            inviteMessages = GermanInviteMessageFormatter(),
        )
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun showsGroups() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.state.collect {} }
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf("Stammtisch"), viewModel.state.value.groups.map { it.name })
    }

    @Test
    fun createRequestsDialogCloseAndShowsMessage() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.state.collect {} }

        viewModel.create("Neu")

        assertEquals(GroupDialog.CREATE, viewModel.state.value.closeDialog)
        assertFalse(viewModel.state.value.isWorking)
        assertEquals(true, viewModel.state.value.message?.contains("„Neu“"))
        assertEquals(true, viewModel.state.value.message?.contains("K7MX-2QAB"))
        assertNull(viewModel.state.value.dialogError)

        viewModel.dialogClosed()
        assertNull(viewModel.state.value.closeDialog)
    }

    @Test
    fun closeRequestSurvivesUntilAnyScreenConsumesIt() = runTest(dispatcher) {
        // Simuliert eine Drehung: Während des Aufrufs gibt es keinen Beobachter, die neue Oberfläche
        // sammelt erst danach – und sieht trotzdem, dass sie ihren Dialog schließen soll.
        val pending = CompletableDeferred<DrinkGroup>()
        createResult = { pending.await() }
        val oldScreen = backgroundScope.launch { viewModel.state.collect {} }
        viewModel.create("Neu")
        assertTrue(viewModel.state.value.isWorking)
        oldScreen.cancel()

        pending.complete(group("Neu"))
        advanceTimeBy(10_000)
        backgroundScope.launch { viewModel.state.collect {} }

        assertEquals(GroupDialog.CREATE, viewModel.state.value.closeDialog)
    }

    @Test
    fun joinRequestsDialogClose() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.state.collect {} }

        viewModel.join("K7MX2QAB")

        assertEquals(GroupDialog.JOIN, viewModel.state.value.closeDialog)
        assertEquals("Willkommen in der Runde „Beigetreten“!", viewModel.state.value.message)
    }

    @Test
    fun validationErrorStaysInDialog() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.state.collect {} }
        createResult = { throw SocialValidationException.EmptyGroupName }

        viewModel.create(" ")

        assertNull(viewModel.state.value.closeDialog)
        assertEquals("Bitte gib der Runde einen Namen.", viewModel.state.value.dialogErrorFor(GroupDialog.CREATE))
        assertNull(viewModel.state.value.dialogErrorFor(GroupDialog.JOIN), "Nur im auslösenden Dialog.")
        assertNull(viewModel.state.value.message)
    }

    @Test
    fun unknownCodeShowsGermanMessage() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.state.collect {} }
        joinResult = { throw GroupNotFoundException() }

        viewModel.join("K7MX2QAB")

        assertNull(viewModel.state.value.closeDialog)
        assertEquals("Zu diesem Code gibt es keine Runde.", viewModel.state.value.dialogErrorFor(GroupDialog.JOIN))
        assertNull(viewModel.state.value.dialogErrorFor(GroupDialog.CREATE))
    }

    @Test
    fun technicalErrorsAreNotShownVerbatim() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.state.collect {} }
        joinResult = { throw IllegalStateException("socket closed") }

        viewModel.join("K7MX2QAB")

        assertEquals(false, viewModel.state.value.dialogErrorFor(GroupDialog.JOIN)?.contains("socket"))
    }

    @Test
    fun resultOfACreateStartedEarlierDoesNotCloseTheJoinDialog() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.state.collect {} }
        val pending = CompletableDeferred<DrinkGroup>()
        createResult = { pending.await() }

        viewModel.create("Neu")
        viewModel.dialogDismissed(GroupDialog.CREATE) // „Abbrechen“, während der Server noch arbeitet
        viewModel.dialogOpened() // Beitreten-Dialog geöffnet
        pending.complete(group("Neu"))

        assertNull(viewModel.state.value.closeDialog, "Der Beitreten-Dialog bleibt offen.")
        assertFalse(viewModel.state.value.isWorking)
        assertEquals(true, viewModel.state.value.message?.contains("„Neu“"))
    }

    @Test
    fun errorOfAClosedDialogGoesToTheSnackbar() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.state.collect {} }
        val pending = CompletableDeferred<DrinkGroup>()
        createResult = { pending.await() }

        viewModel.create("Neu")
        viewModel.dialogDismissed(GroupDialog.CREATE)
        viewModel.dialogOpened()
        pending.completeExceptionally(SocialValidationException.EmptyGroupName)

        assertNull(viewModel.state.value.dialogError, "Kein Fehler im Beitreten-Dialog.")
        assertEquals("Bitte gib der Runde einen Namen.", viewModel.state.value.message)
        assertNull(viewModel.state.value.closeDialog)
    }

    @Test
    fun dismissingADialogDropsItsErrorButNotThatOfTheOther() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.state.collect {} }
        joinResult = { throw GroupNotFoundException() }
        viewModel.join("K7MX2QAB")

        viewModel.dialogDismissed(GroupDialog.CREATE)
        assertEquals("Zu diesem Code gibt es keine Runde.", viewModel.state.value.dialogErrorFor(GroupDialog.JOIN))

        viewModel.dialogDismissed(GroupDialog.JOIN)
        assertNull(viewModel.state.value.dialogError)
    }

    @Test
    fun groupsAreDroppedWhenNobodyObserves() = runTest(dispatcher) {
        val screen = backgroundScope.launch { viewModel.state.collect {} }
        assertEquals(1, viewModel.state.value.groups.size)

        screen.cancel()
        advanceTimeBy(5_001)

        assertTrue(viewModel.state.value.groups.isEmpty())
        assertTrue(viewModel.state.value.isLoading)
    }

    @Test
    fun leaveShowsMessage() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.state.collect {} }
        val group = groups.value.first()

        viewModel.leave(group)

        assertEquals(listOf(group.id), left)
        assertEquals("Du hast „Stammtisch“ verlassen.", viewModel.state.value.message)
        viewModel.messageShown()
        assertNull(viewModel.state.value.message)
    }

    private fun group(name: String) =
        DrinkGroup("id-$name", name, InviteCode.parse("K7MX2QAB")!!, "u1", listOf(GroupMember("u1", "Anna")))
}
