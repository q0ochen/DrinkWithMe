package com.drinkwithme.app.ui.friends

import com.drinkwithme.core.domain.DrinkCategory
import com.drinkwithme.core.social.DrinkGroup
import com.drinkwithme.core.social.FeedEntry
import com.drinkwithme.core.social.GroupMember
import com.drinkwithme.core.social.InviteCode
import com.drinkwithme.core.social.usecase.ObserveFeedUseCase
import com.drinkwithme.core.social.usecase.ObserveMyGroupsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val groups = MutableSharedFlow<List<DrinkGroup>>(replay = 1)
    private val feeds = mutableMapOf<String, Flow<List<FeedEntry>>>()

    private val observeMyGroups = object : ObserveMyGroupsUseCase {
        override fun invoke(): Flow<List<DrinkGroup>> = groups
    }
    private val observeFeed = object : ObserveFeedUseCase {
        override fun invoke(groupId: String): Flow<List<FeedEntry>> = feeds.getOrPut(groupId) { MutableStateFlow(emptyList()) }
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun isLoadingUntilGroupsArrive() = runTest(dispatcher) {
        val viewModel = FriendsViewModel(observeMyGroups, observeFeed)
        backgroundScope.launch { viewModel.state.collect {} }

        assertTrue(viewModel.state.value.isLoading)

        groups.emit(emptyList())
        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.groups.isEmpty())
    }

    @Test
    fun showsFeedOfFirstGroupAndSwitchesOnSelection() = runTest(dispatcher) {
        feeds["a"] = MutableStateFlow(listOf(entry("a", "Pils")))
        feeds["b"] = MutableStateFlow(listOf(entry("b", "Rotwein")))
        val viewModel = FriendsViewModel(observeMyGroups, observeFeed)
        backgroundScope.launch { viewModel.state.collect {} }

        groups.emit(listOf(group("a"), group("b")))
        assertEquals("a", viewModel.state.value.selectedGroupId)
        assertEquals(listOf("Pils"), viewModel.state.value.entries.map { it.name })

        viewModel.selectGroup("b")
        assertEquals("b", viewModel.state.value.selectedGroupId)
        assertEquals(listOf("Rotwein"), viewModel.state.value.entries.map { it.name })
    }

    @Test
    fun fallsBackToFirstGroupWhenSelectedGroupDisappears() = runTest(dispatcher) {
        val viewModel = FriendsViewModel(observeMyGroups, observeFeed)
        backgroundScope.launch { viewModel.state.collect {} }
        groups.emit(listOf(group("a"), group("b")))
        viewModel.selectGroup("b")

        groups.emit(listOf(group("a")))

        assertEquals("a", viewModel.state.value.selectedGroupId)
    }

    @Test
    fun feedErrorIsShownAsMessage() = runTest(dispatcher) {
        feeds["a"] = flow { throw IllegalStateException("kaputt") }
        val viewModel = FriendsViewModel(observeMyGroups, observeFeed)
        backgroundScope.launch { viewModel.state.collect {} }

        groups.emit(listOf(group("a")))

        assertNotNull(viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.entries.isEmpty())
    }

    @Test
    fun noErrorWithoutFailure() = runTest(dispatcher) {
        val viewModel = FriendsViewModel(observeMyGroups, observeFeed)
        backgroundScope.launch { viewModel.state.collect {} }
        groups.emit(listOf(group("a")))
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun cachedDataIsDroppedWhenNobodyObserves() = runTest(dispatcher) {
        feeds["a"] = MutableStateFlow(listOf(entry("a", "Pils")))
        val viewModel = FriendsViewModel(observeMyGroups, observeFeed)
        val screen = backgroundScope.launch { viewModel.state.collect {} }
        groups.emit(listOf(group("a")))
        assertEquals(listOf("Pils"), viewModel.state.value.entries.map { it.name })

        screen.cancel()
        advanceTimeBy(5_001)

        // Nichts bleibt zwischengespeichert, was später (z. B. nach einem Kontowechsel) veraltet erscheinen könnte.
        assertEquals(FriendsUiState(), viewModel.state.value)
    }

    @Test
    fun shortInterruptionsKeepTheData() = runTest(dispatcher) {
        val viewModel = FriendsViewModel(observeMyGroups, observeFeed)
        val screen = backgroundScope.launch { viewModel.state.collect {} }
        groups.emit(listOf(group("a")))

        screen.cancel()
        advanceTimeBy(1_000)

        assertEquals(listOf("a"), viewModel.state.value.groups.map { it.id })
    }

    private fun group(id: String) =
        DrinkGroup(id, "Runde $id", InviteCode.parse("K7MX2QAB")!!, "u1", listOf(GroupMember("u1", "Anna")))

    private fun entry(groupId: String, name: String) = FeedEntry(
        drinkId = "$groupId-$name",
        groupId = groupId,
        author = GroupMember("u2", "Ben"),
        name = name,
        category = DrinkCategory.OTHER,
        volumeInMilliliters = 250,
        note = null,
        consumedAt = Instant.parse("2026-11-09T14:30:00Z"),
    )
}
