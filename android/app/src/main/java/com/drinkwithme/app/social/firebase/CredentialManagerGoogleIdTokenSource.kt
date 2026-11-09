package com.drinkwithme.app.social.firebase

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.drinkwithme.app.social.GoogleIdTokenSource
import com.drinkwithme.app.social.SignInCancelledException
import com.drinkwithme.app.social.SocialServiceException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * Google-Anmeldung über den Android Credential Manager mit „Über Google anmelden“
 * ([GetSignInWithGoogleOption]): Der Dialog zeigt immer **alle** Google-Konten des Geräts (und „Konto hinzufügen“),
 * damit man nach dem Abmelden auch ein anderes Konto wählen kann – keine automatische Auswahl, keine Sperrzeit
 * nach dem Wegklicken.
 */
class CredentialManagerGoogleIdTokenSource(
    appContext: Context,
    private val webClientIds: WebClientIdProvider,
) : GoogleIdTokenSource {

    private val credentialManager = CredentialManager.create(appContext)

    override suspend fun requestIdToken(activityContext: Context): String {
        val webClientId = webClientIds.webClientId() ?: throw SocialServiceException(
            "Die Google-Anmeldung ist noch nicht eingerichtet (default_web_client_id fehlt) – " +
                "siehe docs/FIREBASE_EINRICHTUNG.md.",
        )
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        val response = try {
            credentialManager.getCredential(activityContext, request)
        } catch (cancelled: GetCredentialCancellationException) {
            throw SignInCancelledException(cancelled)
        } catch (noAccount: NoCredentialException) {
            throw SocialServiceException(
                "Es wurde kein Google-Konto gefunden. Bitte füge in den Android-Einstellungen ein Google-Konto hinzu " +
                    "und versuche es erneut.",
                noAccount,
            )
        } catch (error: GetCredentialException) {
            throw SocialServiceException(SIGN_IN_FAILED, error)
        }
        return idTokenFrom(response.credential)
    }

    override suspend fun clearCredentialState() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }

    private fun idTokenFrom(credential: Credential): String {
        if (credential !is CustomCredential || credential.type !in GOOGLE_ID_TOKEN_TYPES) {
            throw SocialServiceException(SIGN_IN_FAILED)
        }
        return try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (error: GoogleIdTokenParsingException) {
            throw SocialServiceException(SIGN_IN_FAILED, error)
        }
    }

    private companion object {
        const val SIGN_IN_FAILED = "Die Google-Anmeldung ist fehlgeschlagen. Bitte versuche es erneut."

        /**
         * Typ der Antwort laut Dokumentation: [GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL].
         * Neuere Versionen der Google-Bibliothek kennzeichnen Antworten von „Über Google anmelden“ zusätzlich
         * als eigenen Typ; beide tragen dieselben Daten und werden mit `createFrom` gelesen.
         */
        val GOOGLE_ID_TOKEN_TYPES = setOf(
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            "com.google.android.libraries.identity.googleid.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL",
        )
    }
}
