package com.drinkwithme.app.supabase

import java.security.ProviderException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Schlüsselquelle mit Software-Schlüssel; kann wie ein gestörter Android-Keystore Laufzeitfehler werfen. */
private class TestKeys(var failures: Int = 0, private val failure: () -> RuntimeException = { ProviderException("Keystore busy") }) :
    SecretKeySource {
    var key: SecretKey = newKey()
    var lookups = 0

    override fun existing(): SecretKey? {
        lookups++
        if (failures > 0) {
            failures--
            throw failure()
        }
        return key
    }

    override fun existingOrNew(): SecretKey = key

    companion object {
        fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }
}

class AndroidKeystoreTokenCipherTest {
    @Test
    fun roundTripsWithoutPlainText() {
        val cipher = AndroidKeystoreTokenCipher(TestKeys())
        val encrypted = cipher.encrypt("refresh-token")
        assertTrue(encrypted.startsWith("v1:"))
        assertTrue(!encrypted.contains("refresh-token"))
        assertEquals("refresh-token", cipher.decrypt(encrypted))
    }

    @Test
    fun foreignKeyOrBrokenTextMeansNotDecryptable() {
        val keys = TestKeys()
        val cipher = AndroidKeystoreTokenCipher(keys)
        val encrypted = cipher.encrypt("refresh-token")
        keys.key = TestKeys.newKey()
        assertNull(cipher.decrypt(encrypted), "z. B. nach Wiederherstellung auf einem neuen Gerät")
        assertNull(cipher.decrypt("v1:###:###"))
        assertNull(cipher.decrypt("kein-format"))
    }

    @Test
    fun keystoreRuntimeErrorsAreRetriedOnce() {
        val keys = TestKeys()
        val cipher = AndroidKeystoreTokenCipher(keys)
        val encrypted = cipher.encrypt("refresh-token")
        keys.failures = 1
        assertEquals("refresh-token", cipher.decrypt(encrypted))
    }

    @Test
    fun persistentKeystoreRuntimeErrorsBecomeUnavailableInsteadOfCrashing() {
        val keys = TestKeys()
        val cipher = AndroidKeystoreTokenCipher(keys)
        val encrypted = cipher.encrypt("refresh-token")
        keys.failures = 5
        assertFailsWith<KeystoreUnavailableException> { cipher.decrypt(encrypted) }

        val other = TestKeys(failures = 5) { IllegalStateException("Keystore kaputt") }
        assertFailsWith<KeystoreUnavailableException> { AndroidKeystoreTokenCipher(other).decrypt(encrypted) }
    }
}
