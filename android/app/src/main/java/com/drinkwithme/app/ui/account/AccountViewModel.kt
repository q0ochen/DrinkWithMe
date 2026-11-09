package com.drinkwithme.app.ui.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drinkwithme.app.social.AccountDeletion
import com.drinkwithme.app.social.AccountDeletionState
import com.drinkwithme.app.social.AccountService
import com.drinkwithme.app.social.SignInCancelledException
import com.drinkwithme.app.ui.common.userMessage
import com.drinkwithme.core.social.CurrentUserProvider
import com.drinkwithme.core.social.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    val user: UserProfile? = null,
    /** Bis der Anmeldestatus bekannt ist – verhindert kurzes Aufblitzen der Anmeldekarte. */
    val isCheckingUser: Boolean = true,
    val isSigningIn: Boolean = false,
    val isSigningOut: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val errorMessage: String? = null,
    /** Hinweis ohne Fehler, z. B. nach dem Löschen des Kontos (wird auf der Anmeldekarte angezeigt). */
    val noticeMessage: String? = null,
    /** Nach erfolgreicher Anmeldung einmalig nach der Benachrichtigungs-Berechtigung fragen. */
    val requestNotificationPermission: Boolean = false,
) {
    /** Abmelden oder Konto löschen läuft – dann keine weitere Konto-Aktion starten. */
    val isAccountActionRunning: Boolean get() = isSigningOut || isDeletingAccount
}

/**
 * Anmeldung und Konto – kennt nur den Port [CurrentUserProvider], den [AccountService] und die [AccountDeletion].
 *
 * Die Kontolöschung läuft App-weit (nicht im [viewModelScope]): Sie wird auch dann zu Ende geführt, wenn dieser
 * Bildschirm schließt. Das ViewModel startet sie nur und zeigt ihren Stand an.
 */
class AccountViewModel(
    currentUserProvider: CurrentUserProvider,
    private val accountService: AccountService,
    private val accountDeletion: AccountDeletion,
) : ViewModel() {
    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            currentUserProvider.currentUser
                .catch { emit(null) }
                .collect { user -> _state.update { it.copy(user = user, isCheckingUser = false) } }
        }
        viewModelScope.launch {
            accountDeletion.state.collect { deletion -> showDeletion(deletion) }
        }
    }

    /**
     * Startet die Google-Anmeldung. [activityContext] wird nur für die Dauer des
     * Kontoauswahl-Dialogs verwendet (der Credential Manager braucht eine Activity).
     */
    fun signIn(activityContext: Context) {
        if (_state.value.isSigningIn) return
        _state.update { it.copy(isSigningIn = true, errorMessage = null, noticeMessage = null) }
        viewModelScope.launch {
            try {
                accountService.signInWithGoogle(activityContext)
                _state.update { it.copy(isSigningIn = false, requestNotificationPermission = true) }
            } catch (cancelled: SignInCancelledException) {
                _state.update { it.copy(isSigningIn = false) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _state.update { it.copy(isSigningIn = false, errorMessage = error.userMessage(SIGN_IN_FAILED)) }
            }
        }
    }

    fun signOut() {
        if (_state.value.isAccountActionRunning) return
        _state.update { it.copy(isSigningOut = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                accountService.signOut()
                _state.update { it.copy(isSigningOut = false) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _state.update { it.copy(isSigningOut = false, errorMessage = error.userMessage(SIGN_OUT_FAILED)) }
            }
        }
    }

    /** Löscht das Konto auf dem Server und meldet ab; bei einem Fehler bleibt man angemeldet und sieht die Meldung. */
    fun deleteAccount() {
        if (_state.value.isAccountActionRunning) return
        // Sofort sperren (nicht erst, wenn der Stand ankommt), damit kein Abmelden dazwischenkommt.
        _state.update { it.copy(isDeletingAccount = true, errorMessage = null) }
        accountDeletion.start()
    }

    /** Übernimmt den Stand der App-weiten Löschung; ein Ergebnis wird einmalig angezeigt und dann quittiert. */
    private fun showDeletion(deletion: AccountDeletionState) {
        when (deletion) {
            AccountDeletionState.Idle -> _state.update { it.copy(isDeletingAccount = false) }
            AccountDeletionState.Running -> _state.update { it.copy(isDeletingAccount = true) }
            AccountDeletionState.Deleted -> {
                _state.update { it.copy(isDeletingAccount = false, errorMessage = null, noticeMessage = ACCOUNT_DELETED) }
                accountDeletion.resultShown()
            }
            is AccountDeletionState.Failed -> {
                _state.update {
                    it.copy(isDeletingAccount = false, errorMessage = deletion.error.userMessage(DELETE_FAILED))
                }
                accountDeletion.resultShown()
            }
        }
    }

    fun onNotificationPermissionRequested() = _state.update { it.copy(requestNotificationPermission = false) }

    private companion object {
        const val SIGN_IN_FAILED = "Die Anmeldung ist fehlgeschlagen. Bitte versuche es erneut."
        const val SIGN_OUT_FAILED = "Die Abmeldung ist fehlgeschlagen. Bitte versuche es erneut."
        const val DELETE_FAILED = "Dein Konto konnte nicht gelöscht werden. Bitte versuche es erneut."
        const val ACCOUNT_DELETED =
            "Dein Konto wurde gelöscht. Deine Getränke unter „Meine Getränke“ sind weiterhin auf diesem Handy."
    }
}
