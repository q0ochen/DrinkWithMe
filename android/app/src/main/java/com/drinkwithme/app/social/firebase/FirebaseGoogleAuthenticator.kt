package com.drinkwithme.app.social.firebase

import com.drinkwithme.app.social.DisplayNames
import com.drinkwithme.app.social.GoogleAuthenticator
import com.drinkwithme.app.social.SocialServiceException
import com.drinkwithme.core.social.CurrentUserProvider
import com.drinkwithme.core.social.UserProfile
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** Firebase Auth: liefert den angemeldeten Nutzer und meldet mit einem Google-ID-Token an bzw. ab. */
class FirebaseGoogleAuthenticator(private val auth: FirebaseAuth) : CurrentUserProvider, GoogleAuthenticator {

    override val currentUser: Flow<UserProfile?> = callbackFlow {
        // Sofort den bekannten Stand liefern; der Listener meldet danach jede Änderung.
        trySend(auth.currentUser?.toUserProfile())
        val listener = FirebaseAuth.AuthStateListener { changedAuth ->
            trySend(changedAuth.currentUser?.toUserProfile())
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signInWithGoogleIdToken(idToken: String): UserProfile {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = try {
            auth.signInWithCredential(credential).await()
        } catch (error: FirebaseNetworkException) {
            throw SocialServiceException("Keine Verbindung zum Server. Bitte prüfe deine Internetverbindung.", error)
        } catch (error: FirebaseException) {
            throw SocialServiceException("Die Anmeldung ist fehlgeschlagen. Bitte versuche es erneut.", error)
        }
        val user = result.user ?: throw SocialServiceException("Die Anmeldung ist fehlgeschlagen. Bitte versuche es erneut.")
        return user.toUserProfile()
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    /** Synchron: Firebase Auth kennt den angemeldeten Nutzer nach dem Start sofort (aus dem lokalen Speicher). */
    override fun signedInUserId(): String? = auth.currentUser?.uid
}

private fun FirebaseUser.toUserProfile() = UserProfile(
    id = uid,
    displayName = DisplayNames.sanitize(displayName ?: email?.substringBefore('@')),
    photoUrl = photoUrl?.toString(),
)
