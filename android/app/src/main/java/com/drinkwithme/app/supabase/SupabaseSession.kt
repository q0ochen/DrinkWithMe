package com.drinkwithme.app.supabase

import com.drinkwithme.app.data.KeyValueStore
import com.drinkwithme.core.social.SessionProvider
import com.drinkwithme.core.social.UserProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Liefert gültige Access-Tokens für Aufrufe im Namen des Nutzers. */
interface AccessTokenSource {
    /** Gültiges Token (wird bei Bedarf erneuert). @throws SocialException.NotSignedIn */
    suspend fun accessToken(): String

    /** Nach einer 401-Antwort: [rejected] verwerfen und ein neues Token holen. */
    suspend fun refreshAfterUnauthorized(rejected: String): String
}

/** ID des angemeldeten Nutzers ohne Netz (z. B. für die Prüfung von `recipientId` einer Push-Nachricht). */
fun interface CurrentUserId {
    fun currentUserId(): String?
}

/**
 * Anmeldung bei Supabase (Google über PKCE im Browser), Erneuern und Abmelden.
 *
 * - [beginSignIn] erzeugt Verifier/Challenge und liefert die URL für den Custom Tab; der Verifier wird gespeichert,
 *   damit die Anmeldung auch übersteht, dass Android die App im Hintergrund beendet.
 * - [completeSignIn] tauscht den Code gegen eine Sitzung.
 * - Das Refresh-Token wird bei jeder Erneuerung ersetzt und sofort (verschlüsselt) gespeichert.
 * - Lehnt der Server das Refresh-Token ab, gilt der Nutzer als abgemeldet ([sessionLostListener] räumt dann auf wie
 *   beim Abmelden); bei Netzfehlern bleibt er angemeldet.
 * - Gelesen und geschrieben wird der Speicher (Keystore, Datei) nur auf [io] und erst bei Bedarf: nicht im
 *   Konstruktor, der beim App-Start auf dem Hauptthread läuft. [warmUp] liest vorab im Hintergrund.
 */
class SupabaseSession(
    private val auth: AuthApi,
    private val store: SessionStore,
    private val pending: KeyValueStore,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : SessionProvider, AccessTokenSource, CurrentUserId {

    /** Gespeicherte Sitzung und beobachtbarer Zustand – erst beim ersten Zugriff gelesen. */
    private inner class Loaded(initial: StoredSession?) {
        var stored: StoredSession? = initial
        val user = MutableStateFlow(initial?.toProfile())
        val email = MutableStateFlow(initial?.email)
    }

    private val mutex = Mutex()
    private val loaded by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Loaded(readStoredSession()) }
    private var accessToken: String? = null
    private var accessTokenExpiresAt: Instant = Instant.EPOCH

    /**
     * Wird aufgerufen, wenn die Sitzung endet, ohne dass der Nutzer sich abgemeldet hat (der Server lehnt das
     * Refresh-Token ab, z. B. nach Widerruf oder Konto-Löschung an anderer Stelle).
     */
    @Volatile
    var sessionLostListener: (() -> Unit)? = null

    override val currentUser: StateFlow<UserProfile?> by lazy { loaded.user.asStateFlow() }

    /** E-Mail-Adresse des angemeldeten Kontos (nur zur Anzeige in den Einstellungen). */
    val currentEmail: StateFlow<String?> by lazy { loaded.email.asStateFlow() }

    val isSignedIn: Boolean get() = loaded.user.value != null

    override fun currentUserId(): String? = loaded.user.value?.id

    /** Liest die gespeicherte Sitzung im Hintergrund, damit spätere Zugriffe (z. B. vom Hauptthread) nicht warten. */
    suspend fun warmUp() {
        withContext(io) { loaded }
    }

    /** Startet die Anmeldung: URL für den Browser. */
    suspend fun beginSignIn(): String {
        val verifier = Pkce.newVerifier(random)
        withContext(io) { pending.putString(KEY_PKCE_VERIFIER, verifier) }
        return auth.authorizeUrl(Pkce.challenge(verifier))
    }

    /** Schließt die Anmeldung mit dem Code aus `drinkwithme://auth-callback?code=…` ab. */
    suspend fun completeSignIn(code: String): UserProfile = mutex.withLock {
        val verifier = withContext(io) { pending.getString(KEY_PKCE_VERIFIER) }
            ?: throw SocialException.NotSignedIn(ErrorMessages.SIGN_IN_EXPIRED)
        val tokens = auth.exchangeCode(code, verifier)
        withContext(io) { pending.putString(KEY_PKCE_VERIFIER, null) }
        apply(tokens, previous = null)
    }

    override suspend fun accessToken(): String = mutex.withLock {
        val token = accessToken
        if (token != null && clock.instant().isBefore(accessTokenExpiresAt.minus(EXPIRY_MARGIN))) return token
        refreshLocked()
    }

    override suspend fun refreshAfterUnauthorized(rejected: String): String = mutex.withLock {
        val token = accessToken
        // Ein anderer Aufruf hat schon erneuert.
        if (token != null && token != rejected && clock.instant().isBefore(accessTokenExpiresAt)) return token
        refreshLocked()
    }

    /** Übernimmt Name/Bild aus der Tabelle `profiles` (maßgeblich gegenüber den Google-Metadaten). */
    suspend fun updateProfile(profile: UserProfile) = mutex.withLock {
        val state = withContext(io) { loaded }
        val current = state.stored ?: return@withLock
        if (current.userId != profile.id) return@withLock
        val updated = current.copy(displayName = profile.displayName, avatarUrl = profile.avatarUrl)
        persist(updated)
        state.user.value = updated.toProfile()
    }

    /** Meldet beim Server ab (falls möglich) und löscht die Sitzung auf dem Gerät. */
    suspend fun signOut() {
        try {
            // Holt bei Bedarf erst ein Token, damit der Server die Sitzung (und ihr Refresh-Token) beendet.
            auth.signOut(accessToken())
        } catch (error: SocialException) {
            // Abmelden klappt lokal immer, auch offline; der Server lässt die Sitzung dann ablaufen.
        }
        clearLocal()
    }

    /** Sitzung nur auf dem Gerät löschen (z. B. nach „Konto löschen“). */
    suspend fun clearLocal() = mutex.withLock { forgetLocked() }

    private suspend fun refreshLocked(): String {
        val current = withContext(io) { loaded }.stored ?: throw SocialException.NotSignedIn()
        val tokens = try {
            auth.refresh(current.refreshToken)
        } catch (error: SocialException.Server) {
            if (error.status in REJECTED_REFRESH_STATUS) {
                forgetLocked()
                sessionLostListener?.invoke()
                throw SocialException.NotSignedIn(ErrorMessages.SESSION_EXPIRED)
            }
            throw error
        }
        apply(tokens, previous = current)
        return tokens.accessToken
    }

    private suspend fun apply(tokens: AuthTokens, previous: StoredSession?): UserProfile {
        val sameUser = previous?.userId == tokens.user.id
        val session = StoredSession(
            refreshToken = tokens.refreshToken,
            userId = tokens.user.id,
            displayName = (if (sameUser) previous?.displayName else null)?.takeIf { it.isNotBlank() }
                ?: tokens.user.displayName?.takeIf { it.isNotBlank() }
                ?: DEFAULT_DISPLAY_NAME,
            avatarUrl = if (sameUser) previous?.avatarUrl else tokens.user.avatarUrl,
            email = tokens.user.email,
        )
        persist(session)
        accessToken = tokens.accessToken
        accessTokenExpiresAt = tokens.expiresAt
        val profile = session.toProfile()
        loaded.user.value = profile
        loaded.email.value = session.email
        return profile
    }

    private suspend fun persist(session: StoredSession) {
        withContext(io) {
            loaded.stored = session
            try {
                store.save(session)
            } catch (error: GeneralSecurityException) {
                // Ohne funktionierenden Keystore bleibt die Sitzung nur bis zum Beenden der App erhalten.
            } catch (error: RuntimeException) {
                // Manche Geräte melden Keystore-Fehler als RuntimeException (z. B. ProviderException) – wie oben.
            }
        }
    }

    private suspend fun forgetLocked() {
        val state = withContext(io) {
            store.clear()
            loaded
        }
        state.stored = null
        accessToken = null
        accessTokenExpiresAt = Instant.EPOCH
        state.user.value = null
        state.email.value = null
    }

    /** Ein gestörter Keystore darf den App-Start nie zum Absturz bringen: dann diesmal „nicht angemeldet“. */
    private fun readStoredSession(): StoredSession? = try {
        store.load()
    } catch (error: RuntimeException) {
        null
    }

    private fun StoredSession.toProfile() = UserProfile(id = userId, displayName = displayName, avatarUrl = avatarUrl)

    companion object {
        /** Wie der Server-Trigger: ohne Namen nie die E-Mail-Adresse zeigen. */
        const val DEFAULT_DISPLAY_NAME = "Drink-With-Me-Nutzer"
        private const val KEY_PKCE_VERIFIER = "pkce_verifier"
        private val EXPIRY_MARGIN: Duration = Duration.ofSeconds(60)

        /** GoTrue lehnt ungültige/abgelaufene/schon benutzte Refresh-Tokens mit 400 (früher 401/403) ab. */
        private val REJECTED_REFRESH_STATUS = setOf(400, 401, 403, 404)
    }
}
