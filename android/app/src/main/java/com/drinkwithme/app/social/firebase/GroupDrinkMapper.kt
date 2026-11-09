package com.drinkwithme.app.social.firebase

import com.drinkwithme.app.social.DisplayNames
import com.drinkwithme.app.social.firebase.FirestoreSchema.GroupDrink
import com.drinkwithme.core.domain.Drink
import com.drinkwithme.core.domain.DrinkCategory
import com.drinkwithme.core.social.FeedEntry
import com.drinkwithme.core.social.GroupMember
import com.drinkwithme.core.social.UserProfile
import java.time.Instant

/**
 * Übersetzt zwischen [Drink]/[FeedEntry] und Dokumenten unter `groups/{groupId}/drinks/{drinkId}`.
 * Kennt keine Firebase-Typen (Zeitstempel werden übergeben) und ist daher ohne Android testbar.
 */
internal object GroupDrinkMapper {

    /**
     * Felder eines neuen Dokuments – exakt die im Vertrag genannten, denn die Sicherheitsregeln
     * prüfen `hasOnly` und `hasAll`. `note` wird deshalb auch als `null` mitgeschickt.
     */
    fun toDocument(drink: Drink, author: UserProfile, consumedAt: Any, createdAt: Any): Map<String, Any?> = mapOf(
        GroupDrink.DRINK_ID to drink.id,
        GroupDrink.AUTHOR_ID to author.id,
        GroupDrink.AUTHOR_NAME to DisplayNames.sanitize(author.displayName),
        GroupDrink.NAME to drink.name,
        GroupDrink.CATEGORY to drink.category.name,
        GroupDrink.VOLUME_IN_MILLILITERS to drink.volumeInMilliliters,
        GroupDrink.NOTE to drink.note,
        GroupDrink.CONSUMED_AT to consumedAt,
        GroupDrink.CREATED_AT to createdAt,
    )

    /** `null`, wenn Pflichtfelder fehlen – ein kaputtes Dokument soll nicht den ganzen Feed verhindern. */
    fun fromDocument(groupId: String, documentId: String, data: Map<String, Any?>, consumedAt: Instant?): FeedEntry? {
        val authorId = data[GroupDrink.AUTHOR_ID] as? String ?: return null
        val name = data[GroupDrink.NAME] as? String ?: return null
        val volume = (data[GroupDrink.VOLUME_IN_MILLILITERS] as? Number)?.toInt() ?: return null
        val category = data[GroupDrink.CATEGORY] as? String
        return FeedEntry(
            drinkId = data[GroupDrink.DRINK_ID] as? String ?: documentId,
            groupId = groupId,
            author = GroupMember(authorId, DisplayNames.sanitize(data[GroupDrink.AUTHOR_NAME] as? String)),
            name = name,
            category = DrinkCategory.entries.firstOrNull { it.name == category } ?: DrinkCategory.OTHER,
            volumeInMilliliters = volume,
            note = data[GroupDrink.NOTE] as? String,
            consumedAt = consumedAt ?: return null,
        )
    }
}
