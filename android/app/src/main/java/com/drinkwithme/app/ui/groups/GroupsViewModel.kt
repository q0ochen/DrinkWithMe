package com.drinkwithme.app.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drinkwithme.app.ui.common.userMessage
import com.drinkwithme.core.social.DrinkGroup
import com.drinkwithme.core.social.usecase.CreateGroupUseCase
import com.drinkwithme.core.social.usecase.JoinGroupUseCase
import com.drinkwithme.core.social.usecase.LeaveGroupUseCase
import com.drinkwithme.core.social.usecase.ObserveMyGroupsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupsUiState(
    val isLoading: Boolean = true,
    val groups: List<DrinkGroup> = emptyList(),
    /** Erstellen, Beitreten oder Verlassen läuft gerade. */
    val isWorking: Boolean = false,
    /** Fehler im offenen Dialog (Erstellen/Beitreten). */
    val dialogErrorMessage: String? = null,
    /** Einmalige Meldung für die Snackbar. */
    val message: String? = null,
)

/** Eigene Trinkrunden verwalten – kennt nur die Use Cases aus dem Kern. */
class GroupsViewModel(
    observeMyGroups: ObserveMyGroupsUseCase,
    private val createGroup: CreateGroupUseCase,
    private val joinGroup: JoinGroupUseCase,
    private val leaveGroup: LeaveGroupUseCase,
    private val inviteMessages: InviteMessageFormatter,
) : ViewModel() {

    private val actions = MutableStateFlow(ActionState())

    /** `null`: wird noch geladen. Fehler werden vor stateIn abgefangen. */
    private val groups: Flow<List<DrinkGroup>?> = flow {
        emit(null)
        emitAll(observeMyGroups())
    }.catch {
        actions.update { it.copy(message = LOAD_FAILED) }
        emit(emptyList())
    }

    val state: StateFlow<GroupsUiState> = combine(groups, actions) { groups, action ->
        GroupsUiState(
            isLoading = groups == null,
            groups = groups.orEmpty(),
            isWorking = action.isWorking,
            dialogErrorMessage = action.dialogErrorMessage,
            message = action.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsUiState())

    /** Erstellt eine Runde; [onCreated] schließt z. B. den Dialog. */
    fun create(name: String, onCreated: () -> Unit) = runDialogAction(onCreated) {
        val group = createGroup(name)
        "Runde „${group.name}“ erstellt. Teile jetzt den Code ${group.inviteCode.value} mit deinen Freunden."
    }

    /** Tritt per Code bei; [onJoined] schließt z. B. den Dialog. */
    fun join(rawCode: String, onJoined: () -> Unit) = runDialogAction(onJoined) {
        val group = joinGroup(rawCode)
        "Willkommen in der Runde „${group.name}“!"
    }

    fun leave(group: DrinkGroup) {
        if (actions.value.isWorking) return
        actions.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val message = try {
                leaveGroup(group.id)
                "Du hast „${group.name}“ verlassen."
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                error.userMessage("„${group.name}“ konnte nicht verlassen werden. Bitte versuche es erneut.")
            }
            actions.update { it.copy(isWorking = false, message = message) }
        }
    }

    fun inviteMessage(group: DrinkGroup): String = inviteMessages.message(group)

    fun messageShown() = actions.update { it.copy(message = null) }

    fun clearDialogError() = actions.update { it.copy(dialogErrorMessage = null) }

    private fun runDialogAction(onSuccess: () -> Unit, action: suspend () -> String) {
        if (actions.value.isWorking) return
        actions.update { it.copy(isWorking = true, dialogErrorMessage = null) }
        viewModelScope.launch {
            try {
                val message = action()
                actions.update { it.copy(isWorking = false, message = message) }
                onSuccess()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                actions.update { it.copy(isWorking = false, dialogErrorMessage = error.userMessage(ACTION_FAILED)) }
            }
        }
    }

    private data class ActionState(
        val isWorking: Boolean = false,
        val dialogErrorMessage: String? = null,
        val message: String? = null,
    )

    private companion object {
        const val LOAD_FAILED = "Deine Runden konnten nicht geladen werden. Bitte versuche es später erneut."
        const val ACTION_FAILED = "Das hat leider nicht geklappt. Bitte versuche es erneut."
    }
}
