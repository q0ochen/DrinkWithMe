package com.drinkwithme.core.voice

import com.drinkwithme.core.speech.GermanNumberWords

/** Wörter, die beim Abgleich mit der Bar ignoriert werden (alle bereits normalisiert, siehe [GermanTextNormalizer]). */
object VoiceVocabulary {
    /** Füllwörter: „ich trinke/nehme/hatte“, Artikel, Gefäße, Mengenangaben, Höflichkeit. */
    val FILLER_WORDS: Set<String> = setOf(
        "ich", "trinke", "trink", "trinken", "nehme", "nehm", "nehmen", "hatte", "hab", "habe", "getrunken",
        "moechte", "will", "gerne", "gern", "bitte", "danke", "noch", "mal", "jetzt", "gerade", "heute",
        "ein", "eine", "einen", "einem", "einer", "eines", "der", "die", "das", "den", "dem", "des",
        "glas", "glaeser", "glaeschen", "flasche", "flaschen", "tasse", "tassen", "becher", "dose", "dosen",
        "kanne", "krug", "pott", "schluck",
        "liter", "milliliter", "zentiliter", "ml", "cl",
        "kleines", "kleine", "kleinen", "kleiner", "grosses", "grosse", "grossen", "grosser",
    )

    /** Zahlwörter (null bis hundert, „zwo“, Brüche, Vielfache) – ohne Bedeutung für die Getränkeauswahl. */
    val NUMBER_WORDS: Set<String> = buildSet {
        for (number in 0L..100L) add(GermanTextNormalizer.normalize(GermanNumberWords.cardinal(number)))
        addAll(
            listOf(
                "zwo", "hundert", "tausend", "komma", "halb", "halbe", "halben", "halber", "halbes", "viertel",
                "einmal", "zweimal", "dreimal", "doppelt", "doppelte", "doppelten", "doppelter", "doppeltes",
            ),
        )
    }

    /** `true`, wenn das (normalisierte) Wort für den Abgleich keine Bedeutung hat. */
    fun isIgnorable(token: String): Boolean =
        token in FILLER_WORDS || token in NUMBER_WORDS || token.all { it.isDigit() }

    /** Die bedeutungstragenden Wörter eines Textes. */
    fun meaningfulTokens(text: String): List<String> = GermanTextNormalizer.tokens(text).filterNot(::isIgnorable)
}
