package com.drinkwithme.app.social.firebase

import com.drinkwithme.app.social.AccountDeleter

/**
 * Löscht das Konto über die Cloud Function `deleteAccount` (`{}` → `{}`, idempotent): Der Server verlässt alle
 * Runden, löscht alle eigenen geteilten Getränke, Profil, Push-Tokens und zuletzt die Anmeldung.
 */
class FirebaseAccountDeleter(private val callables: CallableFunctions) : AccountDeleter {

    override suspend fun deleteAccount() {
        callables.call(CloudFunctions.DELETE_ACCOUNT, emptyMap(), timeoutSeconds = DELETE_ACCOUNT_TIMEOUT_SECONDS)
    }

    private companion object {
        /** Entspricht dem Server-Timeout von `deleteAccount` (540 s); der Aufruf ist idempotent und wiederholbar. */
        const val DELETE_ACCOUNT_TIMEOUT_SECONDS = 540L
    }
}
