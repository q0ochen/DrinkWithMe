package com.drinkwithme.app.ui.account

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.drinkwithme.app.social.AccountDeletionState
import com.drinkwithme.app.social.AccountService
import com.drinkwithme.app.social.ApplicationScopedAccountDeletion
import com.drinkwithme.app.social.SocialServiceException
import com.drinkwithme.core.social.CurrentUserProvider
import com.drinkwithme.core.social.UserProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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
class AccountViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val user = MutableStateFlow<UserProfile?>(UserProfile("u1", "Anna"))
    private var deleteAccount: suspend () -> Unit = { user.value = null }
    private var signOutCalls = 0

    private val accountService = object : AccountService {
        override suspend fun signInWithGoogle(activityContext: Context): UserProfile = error("nicht benötigt")
        override suspend fun signOut() {
            signOutCalls++
        }
        override suspend fun deleteAccount() = this@AccountViewModelTest.deleteAccount()
    }

    /** Lebt wie im Composition Root länger als jedes ViewModel. */
    private val accountDeletion = ApplicationScopedAccountDeletion(accountService, applicationScope)

    private val viewModel by lazy { newViewModel() }

    private fun newViewModel() = AccountViewModel(
        currentUserProvider = object : CurrentUserProvider {
            override val currentUser = user
        },
        accountService = accountService,
        accountDeletion = accountDeletion,
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() {
        applicationScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun deletingTheAccountSignsOutAndShowsANotice() = runTest(dispatcher) {
        viewModel.deleteAccount()

        val state = viewModel.state.value
        assertNull(state.user)
        assertFalse(state.isDeletingAccount)
        assertNull(state.errorMessage)
        assertEquals(true, state.noticeMessage?.startsWith("Dein Konto wurde gelöscht."))
        assertEquals(AccountDeletionState.Idle, accountDeletion.state.value, "Das Ergebnis ist quittiert.")
    }

    @Test
    fun failedDeletionShowsTheErrorAndKeepsTheUser() = runTest(dispatcher) {
        deleteAccount = { throw SocialServiceException("Der Server ist gerade nicht erreichbar.") }

        viewModel.deleteAccount()

        val state = viewModel.state.value
        assertEquals("u1", state.user?.id)
        assertFalse(state.isDeletingAccount)
        assertEquals("Der Server ist gerade nicht erreichbar.", state.errorMessage)
        assertNull(state.noticeMessage)
    }

    @Test
    fun noSignOutWhileTheAccountIsBeingDeleted() = runTest(dispatcher) {
        val pending = CompletableDeferred<Unit>()
        deleteAccount = { pending.await() }

        viewModel.deleteAccount()
        assertTrue(viewModel.state.value.isDeletingAccount)
        viewModel.signOut()
        viewModel.deleteAccount()

        assertEquals(0, signOutCalls)
        pending.complete(Unit)
        assertFalse(viewModel.state.value.isDeletingAccount)
    }

    @Test
    fun deletionFinishesEvenIfTheScreenIsClosedAndANewScreenShowsTheResult() = runTest(dispatcher) {
        val server = CompletableDeferred<Unit>()
        deleteAccount = {
            server.await()
            user.value = null // lokal aufgeräumt und abgemeldet
        }
        val closedScreen = newViewModel()
        closedScreen.deleteAccount()
        closedScreen.viewModelScope.cancel() // wie onCleared(): Activity beendet

        val newScreen = newViewModel()
        assertTrue(newScreen.state.value.isDeletingAccount, "Ein neuer Bildschirm zeigt die laufende Löschung.")
        server.complete(Unit)

        assertNull(user.value, "Abmelden und Aufräumen laufen trotz geschlossenem Bildschirm zu Ende.")
        val state = newScreen.state.value
        assertFalse(state.isDeletingAccount)
        assertEquals(true, state.noticeMessage?.startsWith("Dein Konto wurde gelöscht."))
    }
}
