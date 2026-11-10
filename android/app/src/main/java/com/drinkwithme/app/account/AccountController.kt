package com.drinkwithme.app.account

import com.drinkwithme.app.push.PushSubscriptionSync
import com.drinkwithme.app.supabase.AccountRemote
import com.drinkwithme.app.supabase.AuthCallback
import com.drinkwithme.app.supabase.ErrorMessages
import com.drinkwithme.app.supabase.ProfileLookup
import com.drinkwithme.app.supabase.SocialException
import com.drinkwithme.app.supabase.SupabaseSession
import com.drinkwithme.core.social.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Anmeldung, Abmeldung und Konto löschen – so, wie die Oberfläche sie braucht. */
interface AccountController {
    /** `false`, wenn `supabase.properties` fehlt („Server noch nicht eingerichtet“). */
    val isConfigured: Boolean
    val currentUser: StateFlow<UserProfile?>
    val currentEmail: StateFlow<String?>

    /** URL für den Anmelde-Browser (PKCE; merkt sich den Verifier im Speicher). @throws SocialException.NotConfigured */
    suspend fun signInUrl(): String

    /** Rückkehr aus dem Browser. @throws SocialException mit deutscher Meldung */
    suspend fun completeSignIn(callback: AuthCallback): UserProfile

    suspend fun signOut()

    /** Edge Function `delete-account`, danach lokal abmelden; die lokale Historie bleibt. */
    suspend fun deleteAccount()
}

/** Ohne eingerichteten Server: nie angemeldet. */
class UnconfiguredAccountController : AccountController {
    override val isConfigured: Boolean = false
    override val currentUser: StateFlow<UserProfile?> = MutableStateFlow<UserProfile?>(null).asStateFlow()
    override val currentEmail: StateFlow<String?> = MutableStateFlow<String?>(null).asStateFlow()
    override suspend fun signInUrl(): String = throw SocialException.NotConfigured()
    override suspend fun completeSignIn(callback: AuthCallback): UserProfile = throw SocialException.NotConfigured()
    override suspend fun signOut() = Unit
    override suspend fun deleteAccount(): Unit = throw SocialException.NotConfigured()
}

/**
 * Mit Supabase. Nach der Anmeldung wird das eigene Profil aus `profiles` geladen (Name/Bild), vor dem Abmelden das
 * Push-Abo dieses Geräts entfernt (solange noch ein Token da ist). [clearAccountData] räumt kontobezogene Merker
 * auf; [onSessionChanged] passt die Hintergrundarbeit an (Abruf, Push-Abo). Beides passiert auch, wenn die Sitzung
 * ohne Abmelden endet (der Server lehnt das Refresh-Token ab) – sonst bekäme ein danach angemeldetes Konto z. B. alte
 * „zuletzt gesehen“-Merker und eine Flut alter Meldungen.
 */
class SupabaseAccountController(
    private val session: SupabaseSession,
    private val profiles: ProfileLookup,
    private val account: AccountRemote,
    private val pushSync: PushSubscriptionSync,
    private val clearAccountData: () -> Unit,
    private val onSessionChanged: () -> Unit,
) : AccountController {
    override val isConfigured: Boolean = true

    // Getter: Die Sitzung wird erst beim ersten Zugriff gelesen (nicht beim Erzeugen auf dem Hauptthread).
    override val currentUser: StateFlow<UserProfile?> get() = session.currentUser
    override val currentEmail: StateFlow<String?> get() = session.currentEmail

    init {
        session.sessionLostListener = {
            clearAccountData()
            onSessionChanged()
        }
    }

    override suspend fun signInUrl(): String = session.beginSignIn()

    override suspend fun completeSignIn(callback: AuthCallback): UserProfile {
        val code = when (callback) {
            is AuthCallback.Code -> callback.code
            is AuthCallback.Failure -> throw SocialException.NotSignedIn(
                if (callback.error == "access_denied") ErrorMessages.SIGN_IN_CANCELLED else ErrorMessages.SIGN_IN_FAILED,
            )
        }
        val signedIn = session.completeSignIn(code)
        try {
            profiles.profile(signedIn.id)?.let { session.updateProfile(it) }
        } catch (error: SocialException) {
            // Name aus den Google-Daten reicht vorerst; beim nächsten Mal klappt es.
        }
        onSessionChanged()
        return session.currentUser.value ?: signedIn
    }

    override suspend fun signOut() {
        pushSync.removeForSignOut()
        session.signOut()
        clearAccountData()
        onSessionChanged()
    }

    override suspend fun deleteAccount() {
        account.deleteAccount()
        session.clearLocal()
        clearAccountData()
        onSessionChanged()
    }
}
