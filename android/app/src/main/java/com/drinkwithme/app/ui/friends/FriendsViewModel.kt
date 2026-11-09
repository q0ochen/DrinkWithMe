package com.drinkwithme.app.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drinkwithme.core.social.DrinkGroup
import com.drinkwithme.core.social.FeedEntry
import com.drinkwithme.core.social.usecase.ObserveFeedUseCase
import com.drinkwithme.core.social.usecase.ObserveMyGroupsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

data class FriendsUiState(
    /** Runden oder Feed werden noch geladen. */
    val isLoading: Boolean = true,
    val groups: List<DrinkGroup> = emptyList(),
    val selectedGroupId: String? = null,
    val entries: List<FeedEntry> = emptyList(),
    val errorMessage: String? = null,
) {
    val selectedGroup: DrinkGroup? get() = groups.firstOrNull { it.id == selectedGroupId }
}

/** Live-Feed der gewählten Trinkrunde – kennt nur die Use Cases aus dem Kern. */
@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModel(
    observeMyGroups: ObserveMyGroupsUseCase,
    private val observeFeed: ObserveFeedUseCase,
) : ViewModel() {

    private val requestedGroupId = MutableStateFlow<String?>(null)
    private val groupsFailed = MutableStateFlow(false)

    /**
     * Ohne Beobachter werden Runden und Feed nach [STOP_TIMEOUT_MILLIS] verworfen (`replayExpirationMillis = 0`)
     * statt zwischengespeichert – es bleiben keine Daten liegen, die später veraltet angezeigt würden.
     */
    private val sharing = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0)

    // Fehler werden vor shareIn/stateIn abgefangen – sonst würden sie den viewModelScope beenden.
    private val groups: Flow<List<DrinkGroup>> = observeMyGroups()
        .onStart { groupsFailed.value = false }
        .catch {
            groupsFailed.value = true
            emit(emptyList())
        }
        .shareIn(viewModelScope, sharing, replay = 1)

    /** Gewünschte Runde, sonst die erste – ändert sich nur, wenn sich die Auswahl wirklich ändert. */
    private val selectedGroupId: Flow<String?> = combine(groups, requestedGroupId) { groups, requested ->
        (groups.firstOrNull { it.id == requested } ?: groups.firstOrNull())?.id
    }.distinctUntilChanged()

    private val feed: Flow<FeedState> = selectedGroupId.flatMapLatest { groupId ->
        if (groupId == null) {
            flowOf(FeedState(groupId = null, entries = emptyList()))
        } else {
            observeFeed(groupId)
                .map { entries -> FeedState(groupId, entries) }
                .onStart { emit(FeedState(groupId, entries = null)) }
                .catch { emit(FeedState(groupId, entries = emptyList(), failed = true)) }
        }
    }

    val state: StateFlow<FriendsUiState> = combine(groups, feed, groupsFailed) { groups, feed, groupsFailed ->
        FriendsUiState(
            isLoading = feed.entries == null,
            groups = groups,
            selectedGroupId = feed.groupId,
            entries = feed.entries.orEmpty(),
            errorMessage = if (groupsFailed || feed.failed) LOAD_FAILED else null,
        )
    }.stateIn(viewModelScope, sharing, FriendsUiState())

    fun selectGroup(groupId: String) {
        requestedGroupId.value = groupId
    }

    /** `entries == null`: wird noch geladen. */
    private data class FeedState(val groupId: String?, val entries: List<FeedEntry>?, val failed: Boolean = false)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val LOAD_FAILED = "Die Getränke deiner Freunde konnten nicht geladen werden. Bitte versuche es später erneut."
    }
}
