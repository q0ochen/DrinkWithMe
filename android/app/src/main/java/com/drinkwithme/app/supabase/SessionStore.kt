package com.drinkwithme.app.supabase

import com.drinkwithme.app.data.KeyValueStore
import com.drinkwithme.app.data.nonBlankStringOrNull
import org.json.JSONException
import org.json.JSONObject

/**
 * Was die App zwischen zwei Starts von der Anmeldung behält: das (verschlüsselte) Refresh-Token und das
 * Profil zum Anzeigen („Angemeldet als …“). Das Access-Token lebt nur im Arbeitsspeicher.
 */
data class StoredSession(
    val refreshToken: String,
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val email: String?,
)

interface SessionStore {
    fun load(): StoredSession?
    fun save(session: StoredSession)
    fun clear()
}

/** Ver- und entschlüsselt kleine Geheimnisse (das Refresh-Token). */
interface TokenCipher {
    fun encrypt(plain: String): String

    /**
     * `null`, wenn sich der Text nicht (mehr) entschlüsseln lässt, z. B. nach einer Wiederherstellung auf einem neuen Gerät.
     * @throws KeystoreUnavailableException wenn der Schlüsselspeicher gerade nicht antwortet (der Text ist dann nicht
     *   unbedingt verloren – nicht löschen)
     */
    fun decrypt(encrypted: String): String?
}

/** Der Schlüsselspeicher hat mit einem Laufzeitfehler geantwortet (z. B. `ProviderException`, „Keystore busy“). */
class KeystoreUnavailableException(cause: Throwable) : Exception("Schlüsselspeicher nicht verfügbar", cause)

/**
 * Speichert die Sitzung im [store]; das Refresh-Token nur verschlüsselt ([cipher], unter Android mit einem
 * Schlüssel im Android-Keystore). Unlesbare Einträge gelten als „nicht angemeldet“ und werden gelöscht. Antwortet
 * der Schlüsselspeicher gerade nicht, gilt der Nutzer nur diesmal als abgemeldet; der Eintrag bleibt.
 */
class EncryptedSessionStore(
    private val store: KeyValueStore,
    private val cipher: TokenCipher,
) : SessionStore {

    override fun load(): StoredSession? {
        val text = store.getString(KEY) ?: return null
        val session = try {
            val json = JSONObject(text)
            val token = json.nonBlankStringOrNull("refreshTokenEncrypted")?.let(cipher::decrypt)
            val userId = json.nonBlankStringOrNull("userId")
            if (token == null || userId == null) {
                null
            } else {
                StoredSession(
                    refreshToken = token,
                    userId = userId,
                    displayName = json.nonBlankStringOrNull("displayName") ?: "",
                    avatarUrl = json.nonBlankStringOrNull("avatarUrl"),
                    email = json.nonBlankStringOrNull("email"),
                )
            }
        } catch (error: JSONException) {
            null
        } catch (error: KeystoreUnavailableException) {
            return null
        }
        if (session == null) clear()
        return session
    }

    override fun save(session: StoredSession) {
        val json = JSONObject()
            .put("refreshTokenEncrypted", cipher.encrypt(session.refreshToken))
            .put("userId", session.userId)
            .put("displayName", session.displayName)
            .put("avatarUrl", session.avatarUrl ?: JSONObject.NULL)
            .put("email", session.email ?: JSONObject.NULL)
        store.putString(KEY, json.toString())
    }

    override fun clear() = store.putString(KEY, null)

    private companion object {
        const val KEY = "session"
    }
}
