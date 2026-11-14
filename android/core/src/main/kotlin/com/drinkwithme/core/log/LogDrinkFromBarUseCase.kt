package com.drinkwithme.core.log

import com.drinkwithme.core.IdGenerator
import com.drinkwithme.core.RandomIdGenerator
import com.drinkwithme.core.bar.BarItem
import com.drinkwithme.core.bar.BarItemValidator
import com.drinkwithme.core.bar.DefaultBarItemValidator
import com.drinkwithme.core.bar.requireValid
import java.time.Clock
import java.time.temporal.ChronoUnit

/**
 * „Ein Tipp = getrunken“: erzeugt aus einem Bar-Getränk einen [DrinkEntry] und speichert ihn lokal.
 *
 * - Zeitpunkt ist **immer jetzt** (aus [clock], auf Millisekunden gekürzt, damit gespeicherte und
 *   geteilte Zeitpunkte exakt übereinstimmen).
 * - Name, Icon, Kategorie, Vol.-% und Zucker werden kopiert (Snapshot).
 * - `shareWithFriends` entscheidet der [shareDecider] (die `SharingPolicy`).
 */
class LogDrinkFromBarUseCase(
    private val history: HistoryWriter,
    private val shareDecider: ShareDecider,
    private val clock: Clock,
    private val idGenerator: IdGenerator = RandomIdGenerator,
    private val validator: BarItemValidator = DefaultBarItemValidator(),
) {
    /**
     * @param volumeOverride abweichende Menge in ml (1–2000), sonst die Standardportion.
     * @param shareOverride siehe [ShareDecider.shareWithFriends].
     * @throws IllegalArgumentException bei ungültigem Bar-Getränk oder ungültiger Menge; dann wird nichts gespeichert.
     */
    operator fun invoke(barItem: BarItem, volumeOverride: Int? = null, shareOverride: Boolean? = null): DrinkEntry {
        validator.requireValid(barItem)
        val volume = volumeOverride ?: barItem.servingMilliliters
        val range = BarItem.SERVING_RANGE_MILLILITERS
        require(volume in range) { "Die Menge muss zwischen ${range.first} und ${range.last} ml liegen." }

        val entry = DrinkEntry(
            id = idGenerator.newId(),
            barItemId = barItem.id,
            name = barItem.name.trim(),
            icon = barItem.icon,
            category = barItem.category,
            volumeMilliliters = volume,
            alcoholPercent = barItem.alcoholPercent,
            sugarGramsPer100Ml = barItem.sugarGramsPer100Ml,
            consumedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS),
            shareWithFriends = shareDecider.shareWithFriends(barItem, shareOverride),
        )
        history.save(entry)
        return entry
    }
}
