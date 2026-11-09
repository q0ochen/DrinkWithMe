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

/** Die Dialoge, die eine Server-Aktion auslösen. */
enum class GroupDialog { CREATE, JOIN }

/** Fehlermeldung, die nur in dem Dialog erscheint, der die Aktion ausgelöst hat. */
data class GroupDialogError(val dialog: GroupDialog, val message: String)

data class GroupsUiState(
    val isLoading: Boolean = true,
    val groups: List<DrinkGroup> = emptyList(),
    /** Erstellen, Beitreten oder Verlassen läuft gerade. */
    val isWorking: Boolean = false,
    /** Fehler für den noch offenen Dialog, der die Aktion ausgelöst hat – siehe [dialogErrorFor]. */
    val dialogError: GroupDialogError? = null,
    /** Einmalige Meldung für die Snackbar. */
    val message: String? = null,
    /**
     * Erstellen bzw. Beitreten hat geklappt: Die Oberfläche schließt **diesen** Dialog selbst und bestätigt das mit
     * [GroupsViewModel.dialogClosed]. Als Zustand (statt Rückruf) übersteht das auch eine Drehung des Geräts.
     */
    val closeDialog: GroupDialog? = null,
) {
    fun dialogErrorFor(dialog: GroupDialog): String? = dialogError?.takeIf { it.dialog == dialog }?.message
}

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

    /** Ohne Beobachter werden die Runden nach 5 s verworfen (`replayExpirationMillis = 0`) statt zwischengespeichert. */
    val state: StateFlow<GroupsUiState> = combine(groups, actions) { groups, action ->
        GroupsUiState(
            isLoading = groups == null,
            groups = groups.orEmpty(),
            isWorking = action.isWorking,
            dialogError = action.dialogError,
            message = action.message,
            closeDialog = action.closeDialog,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0),
        GroupsUiState(),
    )

    /** Erstellt eine Runde aus dem Erstellen-Dialog; bei Erfolg wird [GroupsUiState.closeDialog] gesetzt. */
    fun create(name: String) = runDialogAction(GroupDialog.CREATE) {
        val group = createGroup(name)
        "Runde „${group.name}“ erstellt. Teile jetzt den Code ${InviteCodeText.display(group.inviteCode)} mit deinen Freunden."
    }

    /** Tritt aus dem Beitreten-Dialog per Code bei; bei Erfolg wird [GroupsUiState.closeDialog] gesetzt. */
    fun join(rawCode: String) = runDialogAction(GroupDialog.JOIN) {
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

    /** Die Oberfläche hat ihren Dialog nach [GroupsUiState.closeDialog] geschlossen. */
    fun dialogClosed() = actions.update { it.copy(closeDialog = null) }

    /** Ein Dialog wird geöffnet: Eine alte Fehlermeldung gehört nicht mehr dazu. */
    fun dialogOpened() = actions.update { it.copy(dialogError = null) }

    /**
     * Der Nutzer hat [dialog] geschlossen (Abbrechen, Zurück, daneben getippt). Das Ergebnis einer noch laufenden
     * Aktion dieses Dialogs schließt dann keinen anderen Dialog mehr; ein Fehler erscheint in der Snackbar.
     */
    fun dialogDismissed(dialog: GroupDialog) = actions.update { state ->
        state.copy(
            dialogOfRunningAction = state.dialogOfRunningAction.takeUnless { it == dialog },
            dialogError = state.dialogError.takeUnless { it?.dialog == dialog },
            closeDialog = state.closeDialog.takeUnless { it == dialog },
        )
    }

    private fun runDialogAction(dialog: GroupDialog, action: suspend () -> String) {
        if (actions.value.isWorking) return
        actions.update {
            it.copy(isWorking = true, dialogOfRunningAction = dialog, dialogError = null, closeDialog = null)
        }
        viewModelScope.launch {
            try {
                val message = action()
                actions.update { state ->
                    // Nur den auslösenden Dialog schließen – und nur, wenn er noch offen ist.
                    state.copy(
                        isWorking = false,
                        dialogOfRunningAction = null,
                        message = message,
                        closeDialog = state.dialogOfRunningAction,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                val errorMessage = error.userMessage(ACTION_FAILED)
                actions.update { state ->
                    val openDialog = state.dialogOfRunningAction
                    if (openDialog != null) {
                        state.copy(
                            isWorking = false,
                            dialogOfRunningAction = null,
                            dialogError = GroupDialogError(openDialog, errorMessage),
                        )
                    } else {
                        // Der Dialog ist schon zu: Die Meldung darf nicht verloren gehen.
                        state.copy(isWorking = false, message = errorMessage)
                    }
                }
            }
        }
    }

    private data class ActionState(
        val isWorking: Boolean = false,
        /** Dialog, der die laufende Aktion ausgelöst hat – `null`, sobald der Nutzer ihn geschlossen hat. */
        val dialogOfRunningAction: GroupDialog? = null,
        val dialogError: GroupDialogError? = null,
        val message: String? = null,
        val closeDialog: GroupDialog? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val LOAD_FAILED = "Deine Runden konnten nicht geladen werden. Bitte versuche es später erneut."
        const val ACTION_FAILED = "Das hat leider nicht geklappt. Bitte versuche es erneut."
    }
}
