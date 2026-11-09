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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private var createResult: () -> DrinkGroup = { group("Neu") }
    private var joinResult: () -> DrinkGroup = { group("Beigetreten") }
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
    fun createClosesDialogAndShowsMessage() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.state.collect {} }
        var closed = false

        viewModel.create("Neu") { closed = true }

        assertTrue(closed)
        assertFalse(viewModel.state.value.isWorking)
        assertEquals(true, viewModel.state.value.message?.contains("„Neu“"))
        assertNull(viewModel.state.value.dialogErrorMessage)
    }

    @Test
    fun validationErrorStaysInDialog() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.state.collect {} }
        createResult = { throw SocialValidationException.EmptyGroupName }
        var closed = false

        viewModel.create(" ") { closed = true }

        assertFalse(closed)
        assertEquals("Bitte gib der Runde einen Namen.", viewModel.state.value.dialogErrorMessage)
    }

    @Test
    fun unknownCodeShowsGermanMessage() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.state.collect {} }
        joinResult = { throw GroupNotFoundException() }

        viewModel.join("K7MX2Q") {}

        assertEquals("Zu diesem Code gibt es keine Runde.", viewModel.state.value.dialogErrorMessage)
    }

    @Test
    fun technicalErrorsAreNotShownVerbatim() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.state.collect {} }
        joinResult = { throw IllegalStateException("socket closed") }

        viewModel.join("K7MX2Q") {}

        assertEquals(false, viewModel.state.value.dialogErrorMessage?.contains("socket"))
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
        DrinkGroup("id-$name", name, InviteCode.parse("K7MX2Q")!!, "u1", listOf(GroupMember("u1", "Anna")))
}
