package com.drinkwithme.app.social

/** Lokale Daten, die zu einer Nutzer-ID gehören und beim Löschen des Kontos mit verschwinden. */
fun interface LocalUserDataEraser {
    fun eraseUser(userId: String)
}

/**
 * Löscht die Daten aller [erasers] – auch dann, wenn einer davon scheitert. Der erste Fehler wird danach
 * weitergereicht (weitere hängen als „suppressed“ daran), damit er gemeldet werden kann.
 */
class CompositeLocalUserDataEraser(private val erasers: List<LocalUserDataEraser>) : LocalUserDataEraser {

    override fun eraseUser(userId: String) {
        var firstError: Exception? = null
        for (eraser in erasers) {
            try {
                eraser.eraseUser(userId)
            } catch (error: Exception) {
                val earlier = firstError
                if (earlier == null) firstError = error else earlier.addSuppressed(error)
            }
        }
        firstError?.let { throw it }
    }
}
