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
interface GoogleAuthenticator : SignedInUserIdProvider {
    /** @throws SocialServiceException wenn die Anmeldung fehlschlägt. */
    suspend fun signInWithGoogleIdToken(idToken: String): UserProfile

    suspend fun signOut()
}

/** Schreibt das öffentliche Profil des Nutzers, damit Freunde seinen Namen sehen. */
interface UserProfileWriter {
    suspend fun write(profile: UserProfile)
}

/**
 * Löscht das Konto samt aller Daten auf dem Server: Mitgliedschaften, alle eigenen geteilten Getränke
 * (in allen Runden), Profil, Push-Tokens und die Anmeldung.
 */
fun interface AccountDeleter {
    /** @throws SocialServiceException wenn das nicht geklappt hat (deutsche Meldung) – dann bleibt alles, wie es war. */
    suspend fun deleteAccount()
}

/** Anmelde-Ablauf aus Sicht der Oberfläche. */
interface AccountService {
    /** @see GoogleIdTokenSource.requestIdToken */
    suspend fun signInWithGoogle(activityContext: Context): UserProfile

    suspend fun signOut()

    /**
     * Löscht das Konto auf dem Server und meldet danach ab. Lokale Getränke bleiben auf dem Gerät.
     * @throws SocialServiceException wenn das Löschen fehlschlägt – der Nutzer bleibt dann angemeldet.
     */
    suspend fun deleteAccount()
}

/**
 * Setzt Anmeldung, Abmeldung und Kontolöschung aus den Einzelbausteinen zusammen:
 * Google-Token → Server-Anmeldung → Profil → Push-Token (bzw. rückwärts beim Abmelden).
 *
 * Profil und Push-Token sind „nice to have“: Schlagen sie fehl, bleibt der Nutzer trotzdem
 * angemeldet; der Fehler wird nur gemeldet ([onBackgroundError]). Jeder dieser Schritte hat einen
 * eigenen Timeout ([sideEffectTimeoutMillis]), damit kein fehlendes Netz die Abmeldung blockiert.
 */
class DefaultAccountService(
    private val idTokens: GoogleIdTokenSource,
    private val authenticator: GoogleAuthenticator,
    private val profiles: UserProfileWriter,
    private val pushTokens: PushTokenRegistrar,
    private val accountDeleter: AccountDeleter,
    private val localUserData: LocalUserDataEraser = LocalUserDataEraser {},
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
        // Das Token-Dokument muss vor dem Abmelden gelöscht werden – danach fehlt die Berechtigung dafür.
        // Gewartet wird dabei nicht auf den Server (Firestore reiht das Löschen ein).
        bestEffort { pushTokens.removeCurrentTokenDocument() }
        finishSignOut()
    }

    override suspend fun deleteAccount() {
        val userId = authenticator.signedInUserId()
        accountDeleter.deleteAccount()
        // Profil, Token-Dokumente, geteilte Getränke und die Anmeldung hat der Server bereits gelöscht –
        // ab hier darf nichts mehr scheitern, sonst bliebe man mit einem gelöschten Konto angemeldet.
        if (userId != null) {
            try {
                localUserData.eraseUser(userId)
            } catch (error: Exception) {
                onBackgroundError(error)
            }
        }
        finishSignOut()
    }

    /** Gemeinsames Ende von Abmelden und Kontolöschung. */
    private suspend fun finishSignOut() {
        // Immer – auch wenn das Token-Dokument (z. B. offline) noch nicht gelöscht ist: Das Gerät soll keine
        // Pushs dieses Kontos mehr bekommen. Eigener Timeout, unabhängig vom vorherigen Schritt.
        bestEffort { pushTokens.invalidateToken() }
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
