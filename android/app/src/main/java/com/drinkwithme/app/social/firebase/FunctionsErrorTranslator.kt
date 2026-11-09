package com.drinkwithme.app.social.firebase

import com.drinkwithme.app.social.SocialServiceException
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.FirebaseFunctionsException.Code

/** Übersetzt Fehler der Cloud Functions in Ausnahmen mit verständlicher deutscher Meldung. */
class FunctionsErrorTranslator {

    /**
     * @param notFound Bedeutung von `not-found` für den jeweiligen Aufruf – bei `joinGroup`
     *   „unbekannter Code“, sonst fehlt vermutlich die Funktion auf dem Server.
     */
    fun translate(error: FirebaseFunctionsException, notFound: (() -> Exception)? = null): Exception =
        when (error.code) {
            Code.NOT_FOUND -> notFound?.invoke()?.also { runCatching { it.initCause(error) } } ?: SocialServiceException(
                "Die Server-Funktion wurde nicht gefunden. Sind die Cloud Functions bereitgestellt? " +
                    "Siehe docs/FIREBASE_EINRICHTUNG.md.",
                error,
            )
            Code.RESOURCE_EXHAUSTED -> SocialServiceException(
                "Diese Runde ist voll – mehr als 50 Mitglieder sind nicht möglich.",
                error,
            )
            Code.INVALID_ARGUMENT -> SocialServiceException(
                "Der Server hat die Eingabe abgelehnt. Bitte prüfe sie und versuche es erneut.",
                error,
            )
            Code.ABORTED -> SocialServiceException("Das hat gerade nicht geklappt. Bitte versuche es noch einmal.", error)
            Code.UNAUTHENTICATED -> SocialServiceException("Bitte melde dich erneut an.", error)
            Code.PERMISSION_DENIED -> SocialServiceException("Dafür fehlt dir die Berechtigung.", error)
            Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED, Code.INTERNAL -> SocialServiceException(
                "Der Server ist gerade nicht erreichbar. Bitte prüfe deine Internetverbindung und versuche es erneut.",
                error,
            )
            else -> SocialServiceException("Das hat leider nicht geklappt. Bitte versuche es später erneut.", error)
        }
}
