package com.drinkwithme.core.social

/** Ungültige Eingaben rund um Trinkrunden – die Meldungen werden direkt angezeigt. */
sealed class SocialValidationException(message: String) : IllegalArgumentException(message) {
    data object EmptyGroupName : SocialValidationException("Bitte gib der Runde einen Namen.")

    data class GroupNameTooLong(val maximum: Int) :
        SocialValidationException("Der Rundenname darf höchstens $maximum Zeichen lang sein.")

    data object InvalidInviteCode : SocialValidationException(
        "Der Code muss aus ${InviteCode.LENGTH} Zeichen bestehen, z. B. K7MX2QAB.",
    )
}

/** Es gibt keine Trinkrunde mit dem eingegebenen Code. */
class GroupNotFoundException : NoSuchElementException("Zu diesem Code gibt es keine Runde.")
