package com.drinkwithme.app.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drinkwithme.app.account.AccountController
import com.drinkwithme.app.supabase.ErrorMessages
import com.drinkwithme.app.social.OutgoingRequest
import com.drinkwithme.app.social.OutgoingRequestsService
import com.drinkwithme.app.supabase.SocialException
import com.drinkwithme.app.ui.common.MessageQueue
import com.drinkwithme.app.ui.common.RefreshSignal
import com.drinkwithme.core.social.FeedService
import com.drinkwithme.core.social.Friend
import com.drinkwithme.core.social.FriendDrinkEvent
import com.drinkwithme.core.social.FriendRequest
import com.drinkwithme.core.social.FriendsService
import com.drinkwithme.core.social.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

data class FriendsUiState(
    val configured: Boolean = false,
    val user: UserProfile? = null,
    val loading: Boolean = false,
    val feed: List<FriendDrinkEvent> = emptyList(),
    val friends: List<Friend> = emptyList(),
    val incoming: List<FriendRequest> = emptyList(),
    /** Eigene Anfragen – nur mit maskierter Adresse, nie mit Namen (der Server verrät nicht, wen sie erreichen). */
    val outgoing: List<OutgoingRequest> = emptyList(),
    val error: String? = null,
    val emailDialogOpen: Boolean = false,
    val busy: Boolean = false,
    val confirmRemove: Friend? = null,
    /** Einladungs-Link, der gerade geteilt werden soll (einmalig; danach [FriendsViewModel.onInviteShared]). */
    val inviteToShare: String? = null,
    val now: Instant = Instant.EPOCH,
)

/**
 * Freunde: Feed, Freundesliste, Anfragen, Einladen per Link und per E-Mail. Ohne Anmeldung nur die Anmelde-Karte
 * (Anmeldung ist optional).
 */
class FriendsViewModel(
    private val account: AccountController,
    private val friendsService: FriendsService?,
    private val outgoingService: OutgoingRequestsService?,
    private val feedService: FeedService?,
    private val refreshSignal: RefreshSignal,
    private val messages: MessageQueue,
    private val clock: Clock,
) : ViewModel() {

    private val mutableState = MutableStateFlow(FriendsUiState(configured = account.isConfigured))
    val state: StateFlow<FriendsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(account.currentUser, refreshSignal.value) { user, _ -> user }.collect { user ->
                mutableState.update { it.copy(user = user) }
                if (user != null) load() else mutableState.update { FriendsUiState(configured = account.isConfigured) }
            }
        }
    }

    fun refresh() {
        if (state.value.user != null) viewModelScope.launch { load() }
    }

    fun onOpenEmailDialog() = mutableState.update { it.copy(emailDialogOpen = true) }

    fun onCloseEmailDialog() = mutableState.update { it.copy(emailDialogOpen = false) }

    /** Antwort immer neutral – kein Hinweis, ob es die Adresse gibt. */
    fun onSendEmailRequest(email: String) {
        val service = friendsService ?: return
        val clean = email.trim()
        if (!EMAIL_PATTERN.matches(clean)) {
            messages.show(INVALID_EMAIL)
            return
        }
        action {
            service.sendRequestByEmail(clean)
            mutableState.update { it.copy(emailDialogOpen = false) }
            messages.show(EMAIL_SENT)
        }
    }

    fun onRespond(request: FriendRequest, accept: Boolean) {
        val service = friendsService ?: return
        action {
            service.respond(request.id, accept)
            messages.show(if (accept) "Du bist jetzt mit ${request.other.displayName} befreundet." else "Anfrage abgelehnt.")
        }
    }

    fun onCancelRequest(request: OutgoingRequest) {
        val service = friendsService ?: return
        action {
            service.cancel(request.id)
            messages.show("${outgoingLabel(request)} zurückgezogen.")
        }
    }

    fun onCreateInvite() {
        val service = friendsService ?: return
        action(reload = false) {
            val link = service.createInviteLink()
            mutableState.update { it.copy(inviteToShare = link) }
        }
    }

    fun onInviteShared() = mutableState.update { it.copy(inviteToShare = null) }

    fun onRemoveRequested(friend: Friend) = mutableState.update { it.copy(confirmRemove = friend) }

    fun onRemoveConfirmed(confirmed: Boolean) {
        val friend = state.value.confirmRemove ?: return
        mutableState.update { it.copy(confirmRemove = null) }
        val service = friendsService ?: return
        if (!confirmed) return
        action {
            service.removeFriend(friend.profile.id)
            messages.show("${friend.profile.displayName} ist nicht mehr in deiner Freundesliste.")
        }
    }

    private fun action(reload: Boolean = true, block: suspend () -> Unit) {
        if (state.value.busy) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            try {
                block()
                if (reload) load()
            } catch (error: SocialException) {
                messages.show(error.userMessage)
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun load() {
        val friendsApi = friendsService ?: return
        val feedApi = feedService ?: return
        mutableState.update { it.copy(loading = true, error = null) }
        try {
            val friends = friendsApi.friends()
            val requests = friendsApi.requests()
            val outgoing = outgoingService?.outgoingRequests().orEmpty()
            val feed = feedApi.recentEvents(since = null, limit = FEED_LIMIT)
            mutableState.update {
                it.copy(
                    loading = false,
                    friends = friends,
                    incoming = requests.filter { request -> request.direction == FriendRequest.Direction.INCOMING },
                    outgoing = outgoing,
                    feed = feed,
                    now = clock.instant(),
                )
            }
        } catch (error: SocialException) {
            mutableState.update { it.copy(loading = false, error = error.userMessage, now = clock.instant()) }
        }
    }

    companion object {
        const val FEED_LIMIT = 50
        const val INVALID_EMAIL = "Bitte gib eine gültige E-Mail-Adresse ein."
        const val EMAIL_SENT = "Danke! Falls es zu dieser Adresse ein Konto bei Drink With Me gibt, bekommt die Person " +
            "jetzt eine Anfrage."
        private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        /** Anzeige, wenn der Server nicht eingerichtet ist. */
        const val NOT_CONFIGURED = ErrorMessages.NOT_CONFIGURED

        /** „Anfrage an b***@example.org“ – ohne Namen. */
        fun outgoingLabel(request: OutgoingRequest): String = "Anfrage an ${request.emailHint}"
    }
}
