package com.drinkwithme.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GermanNumberWordsTest {
    @Test
    fun knownCardinals() {
        val expected = mapOf(
            0 to "null", 1 to "eins", 2 to "zwei", 6 to "sechs", 7 to "sieben", 10 to "zehn", 11 to "elf", 12 to "zwölf",
            13 to "dreizehn", 16 to "sechzehn", 17 to "siebzehn", 19 to "neunzehn", 20 to "zwanzig", 21 to "einundzwanzig",
            27 to "siebenundzwanzig", 30 to "dreißig", 35 to "fünfunddreißig", 42 to "zweiundvierzig", 60 to "sechzig",
            70 to "siebzig", 99 to "neunundneunzig", 100 to "einhundert", 101 to "einhunderteins", 111 to "einhundertelf",
            121 to "einhunderteinundzwanzig", 200 to "zweihundert", 250 to "zweihundertfünfzig",
            330 to "dreihundertdreißig", 999 to "neunhundertneunundneunzig", 1000 to "eintausend", 1001 to "eintausendeins",
            1100 to "eintausendeinhundert", 1580 to "eintausendfünfhundertachtzig", 2000 to "zweitausend",
            2500 to "zweitausendfünfhundert", 7777 to "siebentausendsiebenhundertsiebenundsiebzig",
            9999 to "neuntausendneunhundertneunundneunzig", 10000 to "zehntausend", 21000 to "einundzwanzigtausend",
            101000 to "einhunderteintausend", 999999 to "neunhundertneunundneunzigtausendneunhundertneunundneunzig",
        )
        expected.forEach { (number, words) -> assertEquals(words, GermanNumberWords.cardinal(number), "$number") }
    }

    @Test
    fun largeAndNegativeNumbers() {
        assertEquals("eine Million", GermanNumberWords.cardinal(1_000_000))
        assertEquals("eine Million eins", GermanNumberWords.cardinal(1_000_001))
        assertEquals("zwei Millionen dreihunderttausend", GermanNumberWords.cardinal(2_300_000))
        assertEquals("minus fünf", GermanNumberWords.cardinal(-5))
        assertEquals("1000000000", GermanNumberWords.cardinal(1_000_000_000L))
    }

    @Test
    fun allNumbersUpTo9999AreDistinctWordsWithoutDigits() {
        val words = (0..9999).map { GermanNumberWords.cardinal(it) }
        assertEquals(10_000, words.toSet().size)
        words.forEachIndexed { number, word ->
            assertTrue(word.all { it in 'a'..'z' || it in "äöüß" }, "$number: $word")
            assertFalse("eins" in word.removeSuffix("eins"), "$number: $word") // „eins“ nur am Ende
            assertFalse("sechszehn" in word || "siebenzehn" in word || "sechszig" in word || "siebenzig" in word, word)
        }
    }

    @Test
    fun decimals() {
        assertEquals("zwei Komma fünf", GermanNumberWords.decimal(2.5))
        assertEquals("zwei", GermanNumberWords.decimal(2.0))
        assertEquals("null Komma vier", GermanNumberWords.decimal(0.4))
        assertEquals("eins Komma fünf", GermanNumberWords.decimal(1.5))
        assertEquals("neunzehn Komma sechs", GermanNumberWords.decimal(19.6))
        assertEquals("einhundert", GermanNumberWords.decimal(100.0))
        assertEquals("zwei Komma drei", GermanNumberWords.decimal(2.25)) // kaufmännisch gerundet
        assertEquals("eins Komma eins", GermanNumberWords.decimal(1.05))
        assertEquals("zwölf Komma zwei fünf", GermanNumberWords.decimal(12.25, maxFractionDigits = 2))
        assertEquals("zwölf Komma null fünf", GermanNumberWords.decimal(12.05, maxFractionDigits = 2))
        assertEquals("dreizehn", GermanNumberWords.decimal(12.6, maxFractionDigits = 0))
        assertEquals("minus eins Komma fünf", GermanNumberWords.decimal(-1.5))
        assertEquals("null", GermanNumberWords.decimal(-0.04))
        assertFailsWith<IllegalArgumentException> { GermanNumberWords.decimal(Double.NaN) }
    }

    @Test
    fun ordinalsForDates() {
        val expected = mapOf(
            1 to "ersten", 2 to "zweiten", 3 to "dritten", 4 to "vierten", 7 to "siebten", 8 to "achten", 9 to "neunten",
            11 to "elften", 16 to "sechzehnten", 19 to "neunzehnten", 20 to "zwanzigsten", 21 to "einundzwanzigsten",
            30 to "dreißigsten", 31 to "einunddreißigsten",
        )
        expected.forEach { (number, words) -> assertEquals(words, GermanNumberWords.ordinalDative(number)) }
        assertFailsWith<IllegalArgumentException> { GermanNumberWords.ordinalDative(0) }
    }
}
