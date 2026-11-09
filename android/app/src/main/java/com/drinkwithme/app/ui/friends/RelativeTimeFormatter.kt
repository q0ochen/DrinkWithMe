package com.drinkwithme.app.ui.friends

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatiert einen Zeitpunkt relativ zu „jetzt“, z. B. „vor 5 Min.“. */
fun interface RelativeTimeFormatter {
    fun format(time: Instant, now: Instant): String
}

/** Deutsche Variante: „gerade eben“, „vor 5 Min.“, „vor 2 Std.“, danach Datum und Uhrzeit. */
class GermanRelativeTimeFormatter(
    private val zone: ZoneId = ZoneId.systemDefault(),
) : RelativeTimeFormatter {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM. HH:mm", Locale.GERMANY)

    override fun format(time: Instant, now: Instant): String {
        val minutes = Duration.between(time, now).toMinutes()
        return when {
            minutes < 1 -> "gerade eben" // auch bei leicht vorgehender Uhr eines Freundes
            minutes < 60 -> "vor $minutes Min."
            minutes < 24 * 60 -> "vor ${minutes / 60} Std."
            else -> time.atZone(zone).format(dateTimeFormatter)
        }
    }
}
