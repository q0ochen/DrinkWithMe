package com.drinkwithme.app.social

/** Fehler rund um Anmeldung und Trinkrunden – die Meldung ist deutsch und wird direkt angezeigt. */
class SocialServiceException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Der Nutzer hat die Google-Anmeldung abgebrochen – kein Fehler, es ist einfach nichts zu tun. */
class SignInCancelledException(cause: Throwable? = null) : Exception("Anmeldung abgebrochen.", cause)
