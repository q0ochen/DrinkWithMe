package com.drinkwithme.core.voice

import java.text.Normalizer
import java.util.Locale

/**
 * Vereinheitlicht gesprochenen bzw. erkannten Text: Kleinschreibung, ä→ae, ö→oe, ü→ue, ß→ss,
 * sonstige Akzente entfernt (é→e), alles außer a–z und 0–9 wird zu Leerzeichen.
 */
object GermanTextNormalizer {
    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val SEPARATORS = Regex("[^a-z0-9]+")

    fun normalize(text: String): String {
        val composed = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase(Locale.GERMAN)
        val replaced = buildString(composed.length) {
            for (char in composed) {
                when (char) {
                    'ä' -> append("ae")
                    'ö' -> append("oe")
                    'ü' -> append("ue")
                    'ß' -> append("ss")
                    else -> append(char)
                }
            }
        }
        val withoutAccents = Normalizer.normalize(replaced, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
        return withoutAccents.replace(SEPARATORS, " ").trim()
    }

    fun tokens(text: String): List<String> = normalize(text).split(' ').filter { it.isNotEmpty() }
}
