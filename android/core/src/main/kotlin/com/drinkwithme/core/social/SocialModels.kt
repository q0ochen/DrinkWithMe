package com.drinkwithme.core.social

import com.drinkwithme.core.domain.DrinkCategory
import java.time.Instant

/** Angemeldeter Nutzer. */
data class UserProfile(
    val id: String,
    val displayName: String,
    val photoUrl: String? = null,
)

/** Mitglied einer Trinkrunde. */
data class GroupMember(
    val userId: String,
    val displayName: String,
)

/** Eine Trinkrunde: Alle Mitglieder sehen gegenseitig ihre geteilten Getränke. */
data class DrinkGroup(
    val id: String,
    val name: String,
    val inviteCode: InviteCode,
    val ownerId: String,
    val members: List<GroupMember>,
)

/** Ein Getränk eines Rundenmitglieds im Feed. */
data class FeedEntry(
    val drinkId: String,
    val groupId: String,
    val author: GroupMember,
    val name: String,
    val category: DrinkCategory,
    val volumeInMilliliters: Int,
    val note: String?,
    val consumedAt: Instant,
)
