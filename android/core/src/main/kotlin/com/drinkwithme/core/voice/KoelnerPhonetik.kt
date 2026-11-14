package com.drinkwithme.core.voice

import java.util.Locale

/** Bildet einen Text auf einen Lautcode ab, damit ähnlich klingende Wörter gleich behandelt werden. */
fun interface PhoneticEncoder {
    fun encode(text: String): String
}

/**
 * Kölner Phonetik (Postel 1969) – Lautcode für deutsche Wörter, z. B. „Müller-Lüdenscheidt“ → „65752682“.
 *
 * Buchstaben werden nach den Regeln (inkl. Kontext für C, D/T, P, X) in Ziffern übersetzt, aufeinanderfolgende
 * gleiche Ziffern zusammengefasst und alle „0“ außer am Anfang entfernt. H erzeugt keinen Code
 * (z. B. „Heinz“ → „068“).
 * Nicht-Buchstaben werden ignoriert, Umlaute wie ihre Grundvokale behandelt.
 */
object KoelnerPhonetik : PhoneticEncoder {
    private const val NO_CODE = '-'

    override fun encode(text: String): String {
        val letters = prepare(text)
        val output = StringBuilder()
        var lastCode = NO_CODE
        for (index in letters.indices) {
            val previous = letters.getOrNull(index - 1)
            val next = letters.getOrNull(index + 1)
            for (code in codesFor(letters[index], previous, next, isFirst = index == 0)) {
                if (code == NO_CODE) continue // H ist „durchsichtig“: gleiche Codes davor und danach werden zusammengefasst.
                val keepsZero = output.isEmpty() // „0“ bleibt nur als allererster Code erhalten.
                if (code != lastCode && (code != '0' || keepsZero)) output.append(code)
                lastCode = code
            }
        }
        return output.toString()
    }

    private fun prepare(text: String): String = buildString {
        for (char in text.lowercase(Locale.GERMAN)) {
            when (char) {
                'ä' -> append('a')
                'ö' -> append('o')
                'ü' -> append('u')
                'ß' -> append('s')
                in 'a'..'z' -> append(char)
            }
        }
    }

    private fun codesFor(letter: Char, previous: Char?, next: Char?, isFirst: Boolean): CharArray = when (letter) {
        'a', 'e', 'i', 'j', 'o', 'u', 'y' -> charArrayOf('0')
        'h' -> charArrayOf(NO_CODE)
        'b' -> charArrayOf('1')
        'p' -> charArrayOf(if (next == 'h') '3' else '1')
        'd', 't' -> charArrayOf(if (next != null && next in "csz") '8' else '2')
        'f', 'v', 'w' -> charArrayOf('3')
        'g', 'k', 'q' -> charArrayOf('4')
        'c' -> charArrayOf(codeForC(previous, next, isFirst))
        'x' -> if (previous != null && previous in "ckq") charArrayOf('8') else charArrayOf('4', '8')
        'l' -> charArrayOf('5')
        'm', 'n' -> charArrayOf('6')
        'r' -> charArrayOf('7')
        's', 'z' -> charArrayOf('8')
        else -> charArrayOf(NO_CODE)
    }

    private fun codeForC(previous: Char?, next: Char?, isFirst: Boolean): Char = when {
        isFirst -> if (next != null && next in "ahkloqrux") '4' else '8'
        previous != null && previous in "sz" -> '8'
        next != null && next in "ahkoqux" -> '4'
        else -> '8'
    }
}
