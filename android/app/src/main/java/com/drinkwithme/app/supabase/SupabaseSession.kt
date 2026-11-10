package com.drinkwithme.app.supabase

import com.drinkwithme.app.data.KeyValueStore
import com.drinkwithme.core.social.SessionProvider
import com.drinkwithme.core.social.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * - Lehnt der Server das Refresh-Token ab, gilt der Nutzer als abgemeldet; bei Netzfehlern bleibt er angemeldet.
 */
class SupabaseSession(
    private val auth: AuthApi,
    private val store: SessionStore,
    private val pending: KeyValueStore,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
) : SessionProvider, AccessTokenSource, CurrentUserId {

    private val mutex = Mutex()
    private var stored: StoredSession? = store.load()
    private var accessToken: String? = null
    private var accessTokenExpiresAt: Instant = Instant.EPOCH
    private val userFlow = MutableStateFlow(stored?.toProfile())
    private val emailFlow = MutableStateFlow(stored?.email)

    override val currentUser: StateFlow<UserProfile?> = userFlow.asStateFlow()

    /** E-Mail-Adresse des angemeldeten Kontos (nur zur Anzeige in den Einstellungen). */
    val currentEmail: StateFlow<String?> = emailFlow.asStateFlow()

    val isSignedIn: Boolean get() = userFlow.value != null

    override fun currentUserId(): String? = userFlow.value?.id

    /** Startet die Anmeldung: URL für den Browser. */
    fun beginSignIn(): String {
        val verifier = Pkce.newVerifier(random)
        pending.putString(KEY_PKCE_VERIFIER, verifier)
        return auth.authorizeUrl(Pkce.challenge(verifier))
    }

    /** Schließt die Anmeldung mit dem Code aus `drinkwithme://auth-callback?code=…` ab. */
    suspend fun completeSignIn(code: String): UserProfile = mutex.withLock {
        val verifier = pending.getString(KEY_PKCE_VERIFIER)
            ?: throw SocialException.NotSignedIn(ErrorMessages.SIGN_IN_EXPIRED)
        val tokens = auth.exchangeCode(code, verifier)
        pending.putString(KEY_PKCE_VERIFIER, null)
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
        val current = stored ?: return@withLock
        if (current.userId != profile.id) return@withLock
        val updated = current.copy(displayName = profile.displayName, avatarUrl = profile.avatarUrl)
        persist(updated)
        userFlow.value = updated.toProfile()
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
    suspend fun clearLocal() = mutex.withLock {
        store.clear()
        stored = null
        accessToken = null
        accessTokenExpiresAt = Instant.EPOCH
        userFlow.value = null
        emailFlow.value = null
    }

    private suspend fun refreshLocked(): String {
        val current = stored ?: throw SocialException.NotSignedIn()
        val tokens = try {
            auth.refresh(current.refreshToken)
        } catch (error: SocialException.Server) {
            if (error.status in REJECTED_REFRESH_STATUS) {
                forgetLocked()
                throw SocialException.NotSignedIn(ErrorMessages.SESSION_EXPIRED)
            }
            throw error
        }
        apply(tokens, previous = current)
        return tokens.accessToken
    }

    private fun apply(tokens: AuthTokens, previous: StoredSession?): UserProfile {
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
        userFlow.value = profile
        emailFlow.value = session.email
        return profile
    }

    private fun persist(session: StoredSession) {
        stored = session
        try {
            store.save(session)
        } catch (error: GeneralSecurityException) {
            // Ohne funktionierenden Keystore bleibt die Sitzung nur bis zum Beenden der App erhalten.
        } catch (error: RuntimeException) {
            // Manche Geräte melden Keystore-Fehler als RuntimeException (z. B. ProviderException) – wie oben.
        }
    }

    private fun forgetLocked() {
        store.clear()
        stored = null
        accessToken = null
        accessTokenExpiresAt = Instant.EPOCH
        userFlow.value = null
        emailFlow.value = null
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
