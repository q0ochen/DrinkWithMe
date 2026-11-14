package com.drinkwithme.core.speech

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Deutsche Zahlwörter zum Vorlesen: „zweihundertfünfzig“, „siebenundzwanzig“, „zwei Komma fünf“.
 *
 * Ganze Zahlen werden bis 999 999 999 ausgeschrieben (Millionen getrennt: „zwei Millionen dreihunderttausend“),
 * größere Beträge bleiben Ziffern. Nachkommastellen werden einzeln gelesen („zwölf Komma zwei fünf“).
 */
object GermanNumberWords {
    private val UNITS = arrayOf(
        "null", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun",
        "zehn", "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn", "sechzehn", "siebzehn", "achtzehn", "neunzehn",
    )
    private val TENS = arrayOf("", "", "zwanzig", "dreißig", "vierzig", "fünfzig", "sechzig", "siebzig", "achtzig", "neunzig")
    private const val MILLION = 1_000_000L
    private const val LARGEST_WRITTEN = 999_999_999L

    /** Grundzahl als Wort; „eins“ für 1. */
    fun cardinal(number: Long): String = when {
        number < 0 -> if (number == Long.MIN_VALUE) number.toString() else "minus ${cardinal(-number)}"
        number == 0L -> UNITS[0]
        number > LARGEST_WRITTEN -> number.toString()
        else -> belowBillion(number)
    }

    fun cardinal(number: Int): String = cardinal(number.toLong())

    /**
     * Dezimalzahl, kaufmännisch auf [maxFractionDigits] Stellen gerundet, ohne überflüssige Nullen:
     * 2,5 → „zwei Komma fünf“, 2,0 → „zwei“, 0,4 → „null Komma vier“.
     */
    fun decimal(value: Double, maxFractionDigits: Int = 1): String {
        val rounded = round(value, maxFractionDigits)
        val negative = rounded.signum() < 0
        val absolute = rounded.abs()
        val integer = absolute.toBigInteger()
        val integerPart = if (integer.bitLength() < Long.SIZE_BITS - 1) cardinal(integer.toLong()) else integer.toString()
        val fraction = absolute.toPlainString().substringAfter('.', missingDelimiterValue = "")
        val words = if (fraction.isEmpty()) integerPart else "$integerPart Komma " + fraction.map { UNITS[it - '0'] }.joinToString(" ")
        return if (negative) "minus $words" else words
    }

    /**
     * Ordnungszahl von 1 bis 99 mit der Endung „-en“ (nach „dem“/„am“, z. B. für Tage im Monat):
     * 1 → „ersten“, 3 → „dritten“, 7 → „siebten“, 9 → „neunten“, 20 → „zwanzigsten“, 31 → „einunddreißigsten“.
     */
    fun ordinalDative(number: Int): String {
        require(number in 1..99) { "Ordnungszahlen werden von 1 bis 99 unterstützt." }
        return when {
            number == 1 -> "ersten"
            number == 3 -> "dritten"
            number == 7 -> "siebten"
            number == 8 -> "achten"
            number < 20 -> "${cardinal(number)}ten"
            else -> "${cardinal(number)}sten"
        }
    }

    /** Rundet wie [decimal] (für Prüfungen wie „ist es genau eins?“). */
    internal fun round(value: Double, maxFractionDigits: Int): BigDecimal {
        require(value.isFinite()) { "Nur endliche Zahlen können vorgelesen werden." }
        require(maxFractionDigits >= 0) { "maxFractionDigits darf nicht negativ sein." }
        val rounded = BigDecimal.valueOf(value).setScale(maxFractionDigits, RoundingMode.HALF_UP)
        return if (rounded.signum() == 0) BigDecimal.ZERO else rounded.stripTrailingZeros().let {
            if (it.scale() < 0) it.setScale(0) else it
        }
    }

    private fun belowBillion(number: Long): String {
        val millions = number / MILLION
        val rest = (number % MILLION).toInt()
        if (millions == 0L) return belowMillion(rest)
        val millionWords = if (millions == 1L) "eine Million" else "${belowThousand(millions.toInt(), standalone = false)} Millionen"
        return if (rest == 0) millionWords else "$millionWords ${belowMillion(rest)}"
    }

    private fun belowMillion(number: Int): String {
        val thousands = number / 1000
        val rest = number % 1000
        val thousandWords = when (thousands) {
            0 -> ""
            else -> belowThousand(thousands, standalone = false) + "tausend"
        }
        val restWords = if (rest == 0) "" else belowThousand(rest, standalone = true)
        return thousandWords + restWords
    }

    /** @param standalone `true`, wenn die Zahl am Ende steht („…eins“), sonst „ein…“ („eintausend“). */
    private fun belowThousand(number: Int, standalone: Boolean): String {
        val hundreds = number / 100
        val rest = number % 100
        val hundredWords = if (hundreds == 0) "" else prefixForm(hundreds) + "hundert"
        val restWords = if (rest == 0) "" else belowHundred(rest, standalone)
        return hundredWords + restWords
    }

    private fun belowHundred(number: Int, standalone: Boolean): String = when {
        number == 1 -> if (standalone) "eins" else "ein"
        number < 20 -> UNITS[number]
        number % 10 == 0 -> TENS[number / 10]
        else -> prefixForm(number % 10) + "und" + TENS[number / 10]
    }

    private fun prefixForm(digit: Int): String = if (digit == 1) "ein" else UNITS[digit]
}
