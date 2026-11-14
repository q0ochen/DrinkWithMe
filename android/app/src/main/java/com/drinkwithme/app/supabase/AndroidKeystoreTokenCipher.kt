package com.drinkwithme.app.supabase

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Woher der AES-Schlüssel kommt (unter Android: der Android-Keystore; in Tests: ein Software-Schlüssel). */
internal interface SecretKeySource {
    /** Vorhandener Schlüssel oder `null`. */
    fun existing(): SecretKey?

    /** Vorhandener oder neu erzeugter Schlüssel. */
    fun existingOrNew(): SecretKey
}

/**
 * AES-256-GCM mit einem Schlüssel, der den Android-Keystore nie verlässt. Format: `v1:<IV base64>:<Daten base64>`.
 * Ohne Fremdbibliothek (kein security-crypto nötig).
 *
 * Manche Geräte melden Keystore-Störungen als Laufzeitfehler (`ProviderException`, z. B. „Keystore busy“, oder
 * `IllegalStateException`). Beim Entschlüsseln wird dann einmal wiederholt und sonst [KeystoreUnavailableException]
 * gemeldet – nie ein Absturz beim App-Start.
 */
class AndroidKeystoreTokenCipher internal constructor(private val keys: SecretKeySource) : TokenCipher {

    constructor(alias: String = DEFAULT_ALIAS) : this(AndroidKeystoreKeys(alias))

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keys.existingOrNew())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return listOf(VERSION, encoder.encodeToString(cipher.iv), encoder.encodeToString(encrypted)).joinToString(":")
    }

    override fun decrypt(encrypted: String): String? {
        val parts = encrypted.split(':')
        if (parts.size != 3 || parts[0] != VERSION) return null
        var failure: RuntimeException? = null
        repeat(ATTEMPTS) {
            try {
                return decryptOnce(parts[1], parts[2])
            } catch (error: GeneralSecurityException) {
                return null
            } catch (error: IllegalArgumentException) {
                return null
            } catch (error: IOException) {
                return null
            } catch (error: RuntimeException) {
                failure = error
            }
        }
        throw KeystoreUnavailableException(checkNotNull(failure))
    }

    private fun decryptOnce(iv: String, data: String): String? {
        val key = keys.existing() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, decoder.decode(iv)))
        return String(cipher.doFinal(decoder.decode(data)), Charsets.UTF_8)
    }

    private companion object {
        const val DEFAULT_ALIAS = "drinkwithme_refresh_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val VERSION = "v1"
        const val ATTEMPTS = 2
        val encoder: Base64.Encoder = Base64.getEncoder()
        val decoder: Base64.Decoder = Base64.getDecoder()
    }
}

/** Schlüssel im Android-Keystore (wird beim ersten Verschlüsseln erzeugt). */
private class AndroidKeystoreKeys(private val alias: String) : SecretKeySource {

    override fun existing(): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    override fun existingOrNew(): SecretKey {
        existing()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_BITS = 256
    }
}
