package com.drinkwithme.app.social.firebase

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.drinkwithme.app.social.GoogleIdTokenSource
import com.drinkwithme.app.social.SignInCancelledException
import com.drinkwithme.app.social.SocialServiceException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * Google-Anmeldung über den Android Credential Manager.
 *
 * Erst werden nur Konten angeboten, die die App schon kennen (ggf. automatische Anmeldung);
 * gibt es keine, folgt die Auswahl aus allen Google-Konten des Geräts.
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
        val response = try {
            try {
                requestCredential(activityContext, webClientId, onlyAuthorizedAccounts = true)
            } catch (noKnownAccount: NoCredentialException) {
                requestCredential(activityContext, webClientId, onlyAuthorizedAccounts = false)
            }
        } catch (cancelled: GetCredentialCancellationException) {
            throw SignInCancelledException(cancelled)
        } catch (noAccount: NoCredentialException) {
            throw SocialServiceException(
                "Auf diesem Gerät ist kein Google-Konto verfügbar. Bitte füge in den Android-Einstellungen eines hinzu.",
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

    private suspend fun requestCredential(
        activityContext: Context,
        webClientId: String,
        onlyAuthorizedAccounts: Boolean,
    ): GetCredentialResponse {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(onlyAuthorizedAccounts)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(onlyAuthorizedAccounts)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        return credentialManager.getCredential(activityContext, request)
    }

    private fun idTokenFrom(credential: Credential): String {
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
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
    }
}
