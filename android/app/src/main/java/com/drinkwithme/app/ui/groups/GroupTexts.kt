package com.drinkwithme.app.ui.groups

import com.drinkwithme.core.sharing.GermanShareFormatter
import com.drinkwithme.core.social.DrinkGroup

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
