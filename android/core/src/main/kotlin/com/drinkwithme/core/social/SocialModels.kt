package com.drinkwithme.core.social

import com.drinkwithme.core.bar.DrinkCategory
import com.drinkwithme.core.bar.DrinkIcon
import java.time.Instant

/** Öffentliches Profil (Tabelle `profiles`) – ohne E-Mail-Adresse. */
data class UserProfile(val id: String, val displayName: String, val avatarUrl: String?)

/** Eine bestätigte Freundschaft. */
data class Friend(val profile: UserProfile, val since: Instant)

/** Offene Freundschaftsanfrage – eingehend (an mich) oder ausgehend (von mir). */
data class FriendRequest(val id: String, val other: UserProfile, val direction: Direction, val createdAt: Instant) { enum class Direction { INCOMING, OUTGOING } }

/** Ein geteiltes Getränk eines Freundes (Tabelle `drink_events`). */
data class FriendDrinkEvent(val id: String, val author: UserProfile, val name: String, val icon: DrinkIcon,
                            val category: DrinkCategory, val volumeMilliliters: Int, val consumedAt: Instant)
