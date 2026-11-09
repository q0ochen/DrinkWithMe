package com.drinkwithme.app.social

import android.content.Context
import com.drinkwithme.core.social.UserProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationScopedAccountDeletionTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val steps = mutableListOf<String>()
    private var deleteAccount: suspend () -> Unit = {
        steps += "Server"
        steps += "lokal aufgeräumt und abgemeldet"
    }
    private val accountService = object : AccountService {
        override suspend fun signInWithGoogle(activityContext: Context): UserProfile = error("nicht benötigt")
        override suspend fun signOut(): Unit = error("nicht benötigt")
        override suspend fun deleteAccount() = this@ApplicationScopedAccountDeletionTest.deleteAccount()
    }
    private val deletion = ApplicationScopedAccountDeletion(accountService, applicationScope)

    @AfterTest
    fun tearDown() = applicationScope.cancel()

    @Test
    fun reportsTheResultUntilItWasShown() = runTest(dispatcher) {
        deletion.start()

        assertEquals(AccountDeletionState.Deleted, deletion.state.value)
        deletion.resultShown()
        assertEquals(AccountDeletionState.Idle, deletion.state.value)
    }

    @Test
    fun keepsRunningWhenTheScreenThatStartedItIsGone() = runTest(dispatcher) {
        val server = CompletableDeferred<Unit>()
        deleteAccount = {
            server.await()
            steps += "lokal aufgeräumt und abgemeldet"
        }
        // Simuliert das ViewModel: Es startet die Löschung und beobachtet sie in seinem eigenen Scope.
        val viewModelScope = CoroutineScope(Job() + dispatcher)
        viewModelScope.launch { deletion.state.collect {} }
        deletion.start()
        assertEquals(AccountDeletionState.Running, deletion.state.value)

        viewModelScope.cancel() // Bildschirm geschlossen (Activity beendet)
        server.complete(Unit)

        assertEquals(listOf("lokal aufgeräumt und abgemeldet"), steps)
        assertEquals(AccountDeletionState.Deleted, deletion.state.value, "Ein neuer Bildschirm sieht das Ergebnis noch.")
    }

    @Test
    fun aSecondStartWhileRunningIsIgnored() = runTest(dispatcher) {
        val server = CompletableDeferred<Unit>()
        var calls = 0
        deleteAccount = {
            calls++
            server.await()
        }

        deletion.start()
        deletion.start()
        deletion.resultShown() // läuft noch – nichts zu quittieren
        assertEquals(AccountDeletionState.Running, deletion.state.value)
        server.complete(Unit)

        assertEquals(1, calls)
        assertEquals(AccountDeletionState.Deleted, deletion.state.value)
    }

    @Test
    fun failureIsReportedAndAllowsAnotherAttempt() = runTest(dispatcher) {
        val error = SocialServiceException("Der Server ist gerade nicht erreichbar.")
        deleteAccount = { throw error }

        deletion.start()

        assertEquals(error, assertIs<AccountDeletionState.Failed>(deletion.state.value).error)
        deletion.resultShown()
        deleteAccount = { steps += "zweiter Versuch" }
        deletion.start()
        assertEquals(listOf("zweiter Versuch"), steps)
        assertEquals(AccountDeletionState.Deleted, deletion.state.value)
    }

    @Test
    fun anEndedApplicationScopeDoesNotLeaveItRunningForever() = runTest(dispatcher) {
        applicationScope.cancel()

        deletion.start()

        assertEquals(AccountDeletionState.Idle, deletion.state.value)
        assertTrue(steps.isEmpty())
    }
}
