package com.drinkwithme.core.voice

import kotlin.math.max
import kotlin.math.min

/** Ähnlichkeit zweier Texte zwischen 0 (nichts gemeinsam) und 1 (gleich). */
fun interface TextSimilarity {
    fun similarity(a: String, b: String): Double
}

/**
 * Jaro-Winkler-Ähnlichkeit: Jaro-Wert plus Bonus für einen gemeinsamen Anfang (bis 4 Zeichen, Faktor 0,1),
 * der Bonus gilt ab einem Jaro-Wert über 0,7 (wie bei Winkler).
 */
object JaroWinkler : TextSimilarity {
    const val PREFIX_SCALE = 0.1
    const val MAX_PREFIX_LENGTH = 4
    const val BOOST_THRESHOLD = 0.7

    override fun similarity(a: String, b: String): Double {
        val jaro = jaro(a, b)
        if (jaro <= BOOST_THRESHOLD) return jaro
        val prefix = a.zip(b).take(MAX_PREFIX_LENGTH).takeWhile { (x, y) -> x == y }.size
        return jaro + prefix * PREFIX_SCALE * (1.0 - jaro)
    }

    /** Reine Jaro-Ähnlichkeit. Zwei leere Texte gelten als gleich, ein leerer und ein nicht leerer als verschieden. */
    fun jaro(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val window = max(0, max(a.length, b.length) / 2 - 1)
        val matchedA = BooleanArray(a.length)
        val matchedB = BooleanArray(b.length)
        var matches = 0
        for (i in a.indices) {
            val from = max(0, i - window)
            val to = min(b.length - 1, i + window)
            for (j in from..to) {
                if (!matchedB[j] && a[i] == b[j]) {
                    matchedA[i] = true
                    matchedB[j] = true
                    matches++
                    break
                }
            }
        }
        if (matches == 0) return 0.0
        val orderA = a.filterIndexed { index, _ -> matchedA[index] }
        val orderB = b.filterIndexed { index, _ -> matchedB[index] }
        val transpositions = orderA.indices.count { orderA[it] != orderB[it] } / 2
        val m = matches.toDouble()
        return (m / a.length + m / b.length + (m - transpositions) / m) / 3.0
    }
}

/**
 * Jaro-Winkler auf dem Text, ergänzt um die Kölner Phonetik: Klingen zwei Wörter ähnlich
 * („Bils“/„Pils“), hebt der Mittelwert aus Text- und Lautcode-Ähnlichkeit den Wert an.
 * Die Lautähnlichkeit kann den Wert nur erhöhen, nie senken.
 */
class PhoneticTextSimilarity(
    private val encoder: PhoneticEncoder = KoelnerPhonetik,
    private val base: TextSimilarity = JaroWinkler,
) : TextSimilarity {
    override fun similarity(a: String, b: String): Double {
        val text = base.similarity(a, b)
        val codeA = encoder.encode(a)
        val codeB = encoder.encode(b)
        if (codeA.isEmpty() || codeB.isEmpty()) return text
        val sound = base.similarity(codeA, codeB)
        return max(text, (text + sound) / 2.0)
    }
}
