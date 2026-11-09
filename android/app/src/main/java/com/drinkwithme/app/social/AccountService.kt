package com.drinkwithme.app.social

import android.content.Context
import com.drinkwithme.app.push.PushTokenRegistrar
import com.drinkwithme.core.social.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Besorgt über die System-UI ein Google-ID-Token (Credential Manager). */
interface GoogleIdTokenSource {
    /**
     * @param activityContext Kontext der sichtbaren Activity – darüber zeigt Android den Kontoauswahl-Dialog.
     * @throws SignInCancelledException wenn der Nutzer den Dialog schließt.
     * @throws SocialServiceException bei allen anderen Fehlern (deutsche Meldung).
     */
    suspend fun requestIdToken(activityContext: Context): String

    /** Vergisst die zuletzt gewählte Anmeldung, damit beim nächsten Mal wieder alle Konten angeboten werden. */
    suspend fun clearCredentialState()
}

/** Meldet mit einem Google-ID-Token beim Server an bzw. wieder ab. */
interface GoogleAuthenticator {
    /** @throws SocialServiceException wenn die Anmeldung fehlschlägt. */
    suspend fun signInWithGoogleIdToken(idToken: String): UserProfile

    suspend fun signOut()
}

/** Schreibt das öffentliche Profil des Nutzers, damit Freunde seinen Namen sehen. */
interface UserProfileWriter {
    suspend fun write(profile: UserProfile)
}

/** Anmelde-Ablauf aus Sicht der Oberfläche. */
interface AccountService {
    /** @see GoogleIdTokenSource.requestIdToken */
    suspend fun signInWithGoogle(activityContext: Context): UserProfile

    suspend fun signOut()
}

/**
 * Setzt Anmeldung und Abmeldung aus den Einzelbausteinen zusammen:
 * Google-Token → Server-Anmeldung → Profil → Push-Token (bzw. rückwärts beim Abmelden).
 *
 * Profil und Push-Token sind „nice to have“: Schlagen sie fehl, bleibt der Nutzer trotzdem
 * angemeldet; der Fehler wird nur gemeldet ([onBackgroundError]).
 */
class DefaultAccountService(
    private val idTokens: GoogleIdTokenSource,
    private val authenticator: GoogleAuthenticator,
    private val profiles: UserProfileWriter,
    private val pushTokens: PushTokenRegistrar,
    private val onBackgroundError: (Throwable) -> Unit = {},
    private val sideEffectTimeoutMillis: Long = 10_000,
) : AccountService {

    override suspend fun signInWithGoogle(activityContext: Context): UserProfile {
        val idToken = idTokens.requestIdToken(activityContext)
        val user = authenticator.signInWithGoogleIdToken(idToken)
        bestEffort { profiles.write(user) }
        bestEffort { pushTokens.registerCurrentToken() }
        return user
    }

    override suspend fun signOut() {
        // Das Token muss vor dem Abmelden gelöscht werden – danach fehlt die Berechtigung dafür.
        bestEffort { pushTokens.unregisterCurrentToken() }
        authenticator.signOut()
        bestEffort { idTokens.clearCredentialState() }
    }

    /** Führt [block] aus, ohne dass ein Fehler oder ein fehlendes Netz den Ablauf blockiert. */
    private suspend fun bestEffort(block: suspend () -> Unit) {
        try {
            withTimeoutOrNull(sideEffectTimeoutMillis) { block() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            onBackgroundError(error)
        }
    }
}
