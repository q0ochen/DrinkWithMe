package com.drinkwithme.core.domain

sealed class DrinkValidationException(message: String) : IllegalArgumentException(message) {
    data object EmptyName : DrinkValidationException("Bitte gib einen Namen für dein Getränk ein.")

    data class NameTooLong(val maximum: Int) :
        DrinkValidationException("Der Name darf höchstens $maximum Zeichen lang sein.")

    data class InvalidVolume(val range: IntRange) :
        DrinkValidationException("Die Menge muss zwischen ${range.first} und ${range.last} ml liegen.")

    data class NoteTooLong(val maximum: Int) :
        DrinkValidationException("Die Notiz darf höchstens $maximum Zeichen lang sein.")
}

/** Prüft Benutzereingaben; eigene Regeln werden als neue Implementierung eingesteckt. */
fun interface DrinkValidator {
    /** @throws DrinkValidationException wenn der Entwurf ungültig ist. */
    fun validate(draft: DrinkDraft)
}

class DefaultDrinkValidator(
    private val maximumNameLength: Int = 50,
    private val allowedVolume: IntRange = 1..5_000,
    private val maximumNoteLength: Int = 280,
) : DrinkValidator {
    override fun validate(draft: DrinkDraft) {
        val name = draft.name.trim()
        if (name.isEmpty()) throw DrinkValidationException.EmptyName
        if (name.length > maximumNameLength) throw DrinkValidationException.NameTooLong(maximumNameLength)
        if (draft.volumeInMilliliters !in allowedVolume) throw DrinkValidationException.InvalidVolume(allowedVolume)
        val note = draft.note
        if (note != null && note.length > maximumNoteLength) throw DrinkValidationException.NoteTooLong(maximumNoteLength)
    }
}
