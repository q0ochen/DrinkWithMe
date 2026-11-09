package com.drinkwithme.app.social.firebase

import com.drinkwithme.app.social.SocialServiceException
import com.drinkwithme.core.social.GroupNotFoundException
import com.google.firebase.functions.FirebaseFunctionsException

/** Übersetzt Fehler der Cloud Functions in Ausnahmen mit verständlicher deutscher Meldung. */
class FunctionsErrorTranslator {

    /** @param functionName aufgerufene Funktion – manche Fehlercodes bedeuten je nach Funktion etwas anderes. */
    fun translate(error: FirebaseFunctionsException, functionName: String): Exception =
        CallableErrors.translate(error.code.name, functionName, error)
}

/**
 * Die eigentliche Zuordnung Fehlercode → Meldung. Arbeitet mit dem **Namen** des Codes
 * (`FirebaseFunctionsException.Code.name`, z. B. `"NOT_FOUND"`), damit sie ohne Firebase/Android testbar ist.
 */
internal object CallableErrors {
    const val GROUP_FULL = "Diese Runde ist voll (höchstens 50 Mitglieder)."
    const val TOO_MANY_WRONG_CODES = "Zu viele falsche Codes. Bitte versuche es in einer Stunde noch einmal."

    fun translate(codeName: String, functionName: String, cause: Throwable? = null): Exception {
        if (functionName == CloudFunctions.JOIN_GROUP) {
            when (codeName) {
                "NOT_FOUND" -> return GroupNotFoundException().also { notFound ->
                    if (cause != null) runCatching { notFound.initCause(cause) }
                }
                "FAILED_PRECONDITION" -> return SocialServiceException(GROUP_FULL, cause)
                "RESOURCE_EXHAUSTED" -> return SocialServiceException(TOO_MANY_WRONG_CODES, cause)
            }
        }
        return SocialServiceException(genericMessage(codeName), cause)
    }

    private fun genericMessage(codeName: String): String = when (codeName) {
        "NOT_FOUND" ->
            "Die Server-Funktion wurde nicht gefunden. Sind die Cloud Functions bereitgestellt? " +
                "Siehe docs/FIREBASE_EINRICHTUNG.md."
        "INVALID_ARGUMENT" -> "Der Server hat die Eingabe abgelehnt. Bitte prüfe sie und versuche es erneut."
        "RESOURCE_EXHAUSTED" -> "Gerade gibt es zu viele Anfragen. Bitte versuche es später noch einmal."
        "ABORTED", "FAILED_PRECONDITION" -> "Das hat gerade nicht geklappt. Bitte versuche es noch einmal."
        "UNAUTHENTICATED" -> "Bitte melde dich erneut an."
        "PERMISSION_DENIED" -> "Dafür fehlt dir die Berechtigung."
        "UNAVAILABLE", "DEADLINE_EXCEEDED", "INTERNAL" ->
            "Der Server ist gerade nicht erreichbar. Bitte prüfe deine Internetverbindung und versuche es erneut."
        else -> "Das hat leider nicht geklappt. Bitte versuche es später erneut."
    }
}
