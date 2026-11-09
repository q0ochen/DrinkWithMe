package com.drinkwithme.app.ui.account

import android.content.Context
import com.drinkwithme.app.social.AccountService
import com.drinkwithme.app.social.SocialServiceException
import com.drinkwithme.core.social.CurrentUserProvider
import com.drinkwithme.core.social.UserProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val user = MutableStateFlow<UserProfile?>(UserProfile("u1", "Anna"))
    private var deleteAccount: suspend () -> Unit = { user.value = null }
    private var signOutCalls = 0

    private val viewModel by lazy {
        AccountViewModel(
            currentUserProvider = object : CurrentUserProvider {
                override val currentUser = user
            },
            accountService = object : AccountService {
                override suspend fun signInWithGoogle(activityContext: Context): UserProfile = error("nicht benötigt")
                override suspend fun signOut() {
                    signOutCalls++
                }
                override suspend fun deleteAccount() = this@AccountViewModelTest.deleteAccount()
            },
        )
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun deletingTheAccountSignsOutAndShowsANotice() = runTest(dispatcher) {
        viewModel.deleteAccount()

        val state = viewModel.state.value
        assertNull(state.user)
        assertFalse(state.isDeletingAccount)
        assertNull(state.errorMessage)
        assertEquals(true, state.noticeMessage?.startsWith("Dein Konto wurde gelöscht."))
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
}
