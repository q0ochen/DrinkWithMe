package com.drinkwithme.app.supabase

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM mit einem Schlüssel, der den Android-Keystore nie verlässt. Format: `v1:<IV base64>:<Daten base64>`.
 * Ohne Fremdbibliothek (kein security-crypto nötig).
 */
class AndroidKeystoreTokenCipher(private val alias: String = DEFAULT_ALIAS) : TokenCipher {

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return listOf(VERSION, encoder.encodeToString(cipher.iv), encoder.encodeToString(encrypted)).joinToString(":")
    }

    override fun decrypt(encrypted: String): String? {
        val parts = encrypted.split(':')
        if (parts.size != 3 || parts[0] != VERSION) return null
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            val key = keyStore.getKey(alias, null) as? SecretKey ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, decoder.decode(parts[1])))
            String(cipher.doFinal(decoder.decode(parts[2])), Charsets.UTF_8)
        } catch (error: GeneralSecurityException) {
            null
        } catch (error: IllegalArgumentException) {
            null
        } catch (error: java.io.IOException) {
            null
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
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

    private companion object {
        const val DEFAULT_ALIAS = "drinkwithme_refresh_token"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val KEY_BITS = 256
        const val VERSION = "v1"
        val encoder: Base64.Encoder = Base64.getEncoder()
        val decoder: Base64.Decoder = Base64.getDecoder()
    }
}
