package com.drinkwithme.app.ui.groups

import com.drinkwithme.core.sharing.GermanShareFormatter
import com.drinkwithme.core.social.DrinkGroup
import com.drinkwithme.core.social.InviteCode

/** Text, mit dem man Freunde in eine Runde einlädt (WhatsApp, Signal …). */
fun interface InviteMessageFormatter {
    fun message(group: DrinkGroup): String
}

class GermanInviteMessageFormatter : InviteMessageFormatter {
    override fun message(group: DrinkGroup): String =
        "🥂 Komm in meine Trinkrunde „${group.name}“ bei Drink With Me! " +
            "Einladungscode: ${group.inviteCode.value} ${GermanShareFormatter.HASHTAG}"
}

/** „3 Mitglieder: Anna, Ben, Du“ – der angemeldete Nutzer erscheint als „Du“ am Ende. */
object GroupMemberSummary {
    fun format(group: DrinkGroup, currentUserId: String): String {
        val count = group.members.size
        val label = if (count == 1) "1 Mitglied" else "$count Mitglieder"
        val (me, others) = group.members.partition { it.userId == currentUserId }
        val names = others.map { it.displayName } + me.map { "Du" }
        return if (names.isEmpty()) label else "$label: ${names.joinToString(", ")}"
    }
}

/** Anzeige und Eingabe des 8-stelligen Einladungscodes. */
object InviteCodeText {
    /** Beispiel für Beschriftungen, im Anzeigeformat. */
    const val EXAMPLE = "K7MX-2QAB"

    /** Gut lesbar in zwei Vierergruppen: „K7MX-2QAB“ (beim Beitreten wird der Bindestrich ignoriert). */
    fun display(code: InviteCode): String = code.value.chunked(DISPLAY_GROUP_SIZE).joinToString("-")

    /**
     * Bereinigt die Eingabe im Beitreten-Dialog: Großbuchstaben, nur Buchstaben, Ziffern und ein Bindestrich,
     * höchstens [InviteCode.LENGTH] Zeichen (ohne Bindestrich). Wird eine ganze Einladungsnachricht eingefügt,
     * wird der Code daraus übernommen.
     */
    fun sanitizeInput(raw: String): String {
        if (raw.any { it.isWhitespace() }) {
            raw.split(WHITESPACE).firstNotNullOfOrNull { InviteCode.parse(it) }?.let { return it.value }
        }
        val result = StringBuilder()
        var characters = 0
        for (char in raw.uppercase()) {
            when {
                char in 'A'..'Z' || char in '0'..'9' -> if (characters < InviteCode.LENGTH) {
                    result.append(char)
                    characters++
                }
                char == '-' -> if (characters in 1 until InviteCode.LENGTH && '-' !in result) result.append(char)
            }
        }
        return result.toString()
    }

    private const val DISPLAY_GROUP_SIZE = 4
    private val WHITESPACE = Regex("\\s+")
}
