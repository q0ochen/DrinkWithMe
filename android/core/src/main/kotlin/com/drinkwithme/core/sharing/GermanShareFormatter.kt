package com.drinkwithme.core.sharing

import com.drinkwithme.core.domain.Drink
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Deutschsprachige Share-Texte. Weitere Sprachen oder Stile werden als
 * neue Formatter ergänzt – bestehender Code bleibt unverändert (Open/Closed).
 */
class GermanShareFormatter(
    private val zone: ZoneId = ZoneId.systemDefault(),
) : DrinkMessageFormatter, DrinkSummaryFormatter {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)
    private val dayFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

    override fun message(drink: Drink): String = buildString {
        append("${drink.category.emoji} Ich trinke gerade ${drink.name} ")
        append("(${VolumeFormatter.format(drink.volumeInMilliliters)}) ")
        append("um ${time(drink)} Uhr.")
        drink.note?.let { append(" „$it“") }
        append(" Trink mit mir! $HASHTAG")
    }

    override fun summary(drinks: List<Drink>, day: LocalDate): String {
        val header = "Mein Getränke-Tag am ${day.format(dayFormatter)}:"
        if (drinks.isEmpty()) return "$header\nNoch nichts getrunken – wer trinkt mit mir? $HASHTAG"

        val lines = drinks.map {
            "${it.category.emoji} ${time(it)} ${it.name} – ${VolumeFormatter.format(it.volumeInMilliliters)}"
        }
        val total = drinks.sumOf { it.volumeInMilliliters }
        return (listOf(header) + lines + listOf("Gesamt: ${VolumeFormatter.format(total)}", HASHTAG))
            .joinToString("\n")
    }

    private fun time(drink: Drink): String = drink.consumedAt.atZone(zone).format(timeFormatter)

    companion object {
        const val HASHTAG = "#DrinkWithMe"
    }
}
