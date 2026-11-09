package com.drinkwithme.app.social

import android.content.Context
import com.drinkwithme.app.push.PushTokenRegistrar
import com.drinkwithme.core.social.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAccountServiceTest {
    private val steps = mutableListOf<String>()
    private val errors = mutableListOf<Throwable>()
    private var removeTokenDocument: suspend () -> Unit = { steps += "removeTokenDocument" }
    private var invalidateToken: suspend () -> Unit = { steps += "invalidateToken" }
    private var deleteAccount: suspend () -> Unit = { steps += "deleteAccount" }
    private val erasedUsers = mutableListOf<String>()

    private val service = DefaultAccountService(
        idTokens = object : GoogleIdTokenSource {
            override suspend fun requestIdToken(activityContext: Context): String = error("nicht benötigt")
            override suspend fun clearCredentialState() {
                steps += "clearCredentialState"
            }
        },
        authenticator = object : GoogleAuthenticator {
            override suspend fun signInWithGoogleIdToken(idToken: String): UserProfile = error("nicht benötigt")
            override suspend fun signOut() {
                steps += "signOut"
            }
            override fun signedInUserId(): String = "u1"
        },
        profiles = object : UserProfileWriter {
            override suspend fun write(profile: UserProfile) = Unit
        },
        pushTokens = object : PushTokenRegistrar {
            override suspend fun registerCurrentToken() = Unit
            override suspend fun registerToken(token: String) = Unit
            override suspend fun removeCurrentTokenDocument() = removeTokenDocument()
            override suspend fun invalidateToken() = invalidateToken.invoke()
        },
        accountDeleter = { deleteAccount() },
        localUserData = { userId -> erasedUsers += userId },
        onBackgroundError = { errors += it },
        sideEffectTimeoutMillis = 10_000,
    )

    @Test
    fun signOutRemovesTokenDocumentInvalidatesTokenAndSignsOut() = runTest {
        service.signOut()

        assertEquals(listOf("removeTokenDocument", "invalidateToken", "signOut", "clearCredentialState"), steps)
    }

    @Test
    fun tokenIsInvalidatedEvenIfRemovingTheDocumentHangs() = runTest {
        removeTokenDocument = { awaitCancellation() } // z. B. offline: Token nicht erreichbar

        service.signOut()

        assertEquals(listOf("invalidateToken", "signOut", "clearCredentialState"), steps)
        assertTrue(currentTime <= 10_000, "Jeder Schritt hat seinen eigenen Timeout.")
    }

    @Test
    fun signOutCompletesEvenIfInvalidatingTheTokenFailsOrHangs() = runTest {
        removeTokenDocument = { throw IllegalStateException("kein Netz") }
        invalidateToken = { awaitCancellation() }

        service.signOut()

        assertEquals(listOf("signOut", "clearCredentialState"), steps)
        assertEquals(1, errors.size)
        assertTrue(currentTime <= 20_000)
    }

    @Test
    fun deleteAccountDeletesOnServerErasesLocalDataAndSignsOut() = runTest {
        service.deleteAccount()

        // Das Token-Dokument hat der Server schon gelöscht – lokal wird nur das Token ungültig gemacht.
        assertEquals(listOf("deleteAccount", "invalidateToken", "signOut", "clearCredentialState"), steps)
        assertEquals(listOf("u1"), erasedUsers)
    }

    @Test
    fun failedDeletionKeepsTheUserSignedIn() = runTest {
        deleteAccount = { throw SocialServiceException("Der Server ist gerade nicht erreichbar.") }

        assertFailsWith<SocialServiceException> { service.deleteAccount() }

        assertTrue(steps.isEmpty())
        assertTrue(erasedUsers.isEmpty())
    }
}
