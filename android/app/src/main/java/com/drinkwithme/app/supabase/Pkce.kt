package com.drinkwithme.app.supabase

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PKCE (RFC 7636) für die Anmeldung im Browser: Der `code_verifier` bleibt auf dem Gerät, an Supabase geht nur
 * die `code_challenge` = base64url(SHA-256(verifier)) mit Methode S256.
 */
object Pkce {
    /** Zufallsbytes für den Verifier: 64 Byte → 86 Zeichen base64url (erlaubt sind 43–128). */
    const val VERIFIER_BYTES = 64

    private val VERIFIER_PATTERN = Regex("^[A-Za-z0-9._~-]{43,128}$")
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun newVerifier(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(VERIFIER_BYTES)
        random.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }

    fun challenge(verifier: String): String {
        require(isValidVerifier(verifier)) { "Ungültiger PKCE-Verifier." }
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return encoder.encodeToString(digest)
    }

    fun isValidVerifier(verifier: String): Boolean = VERIFIER_PATTERN.matches(verifier)
}
