package com.drinkwithme.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drinkwithme.app.account.AccountController
import com.drinkwithme.app.data.AppSettings
import com.drinkwithme.app.routing.AppRequest
import com.drinkwithme.app.supabase.ErrorMessages
import com.drinkwithme.app.supabase.SocialException
import com.drinkwithme.app.ui.common.MessageQueue
import com.drinkwithme.app.ui.common.RefreshSignal
import com.drinkwithme.app.ui.navigation.AppSection
import com.drinkwithme.core.bar.BarReader
import com.drinkwithme.core.social.FriendsService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppUiState(
    /** `null` = wird noch geprüft. */
    val needsOnboarding: Boolean? = null,
    val section: AppSection = AppSection.BAR,
    val signingIn: Boolean = false,
    /** Einladung, deren Annahme der Nutzer noch bestätigen soll. */
    val inviteToConfirm: String? = null,
    val acceptingInvite: Boolean = false,
    val openEvent: AppRequest.OpenEvent? = null,
    /** Nach der Anmeldung einmal nach der Benachrichtigungs-Erlaubnis fragen (Android 13+). */
    val askNotificationPermission: Boolean = false,
)

/**
 * Wurzel der Oberfläche: erster Start, Bereich, eingehende Links (Anmeldung, Einladung) und Benachrichtigungen.
 */
class AppViewModel(
    private val settings: AppSettings,
    private val bar: BarReader,
    private val account: AccountController,
    private val friends: FriendsService?,
    private val friendsChanged: RefreshSignal,
    private val messages: MessageQueue,
    private val io: CoroutineDispatcher,
) : ViewModel() {

    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val needs = withContext(io) {
                if (!settings.onboardingDone && bar.items().isNotEmpty()) settings.onboardingDone = true
                !settings.onboardingDone
            }
            mutableState.update { it.copy(needsOnboarding = needs) }
        }
    }

    fun onOnboardingFinished() {
        settings.onboardingDone = true
        mutableState.update { it.copy(needsOnboarding = false, section = AppSection.BAR) }
    }

    fun onSectionSelected(section: AppSection) = mutableState.update { it.copy(section = section) }

    fun handle(request: AppRequest) {
        when (request) {
            is AppRequest.SignInCallback -> completeSignIn(request)
            is AppRequest.AcceptInvite -> {
                mutableState.update { it.copy(section = AppSection.FRIENDS) }
                when {
                    !account.isConfigured -> messages.show(ErrorMessages.NOT_CONFIGURED)
                    account.currentUser.value == null -> {
                        settings.pendingInviteToken = request.token
                        messages.show(SIGN_IN_FOR_INVITE)
                    }
                    else -> mutableState.update { it.copy(inviteToConfirm = request.token) }
                }
            }
            is AppRequest.OpenEvent -> mutableState.update { it.copy(openEvent = request) }
        }
    }

    fun onEventClosed() = mutableState.update { it.copy(openEvent = null) }

    fun onInviteAnswered(accept: Boolean) {
        val token = state.value.inviteToConfirm ?: return
        settings.pendingInviteToken = null
        mutableState.update { it.copy(inviteToConfirm = null) }
        val service = friends ?: return
        if (!accept) return
        viewModelScope.launch {
            mutableState.update { it.copy(acceptingInvite = true) }
            try {
                val inviter = service.acceptInvite(token)
                messages.show("Du bist jetzt mit ${inviter.displayName} befreundet.")
                friendsChanged.trigger()
            } catch (error: SocialException) {
                messages.show(error.userMessage)
            } finally {
                mutableState.update { it.copy(acceptingInvite = false) }
            }
        }
    }

    fun onNotificationPermissionHandled() = mutableState.update { it.copy(askNotificationPermission = false) }

    /** Nach dem Öffnen des Anmelde-Browsers: zeigt „Anmeldung läuft“, bis der Rücksprung kommt. */
    fun onSignInStarted() = mutableState.update { it.copy(signingIn = true) }

    /** Die App kam ohne Rücksprung zurück (Browser geschlossen). */
    fun onResumedWithoutCallback() = mutableState.update { it.copy(signingIn = false) }

    private fun completeSignIn(request: AppRequest.SignInCallback) {
        viewModelScope.launch {
            mutableState.update { it.copy(signingIn = true) }
            try {
                val user = account.completeSignIn(request.callback)
                messages.show("Angemeldet als ${user.displayName}.")
                friendsChanged.trigger()
                mutableState.update { it.copy(askNotificationPermission = true) }
                settings.pendingInviteToken?.let { token ->
                    mutableState.update { it.copy(inviteToConfirm = token, section = AppSection.FRIENDS) }
                }
            } catch (error: SocialException) {
                messages.show(error.userMessage)
            } finally {
                mutableState.update { it.copy(signingIn = false) }
            }
        }
    }

    companion object {
        const val SIGN_IN_FOR_INVITE = "Melde dich an, um die Einladung anzunehmen. Danach fragt die App noch einmal nach."
    }
}
