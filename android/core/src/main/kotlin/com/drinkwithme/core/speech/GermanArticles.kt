package com.drinkwithme.core.speech

import java.util.Locale

/** Grammatisches Geschlecht eines Getränkenamens. */
enum class GrammaticalGender { MASCULINE, FEMININE, NEUTER }

/**
 * Unbestimmter Artikel im Akkusativ für Getränkenamen („Anna trinkt **eine** Apfelschorle“).
 *
 * Das Geschlecht ergibt sich aus dem Wortende des Hauptworts (deutsche Komposita: „Apfel**schorle**“,
 * „Rot**wein** trocken“, „Alkoholfreies **Bier**“) oder aus einem vorangestellten Gefäß („**Glas** Wein“).
 * Ist es unbekannt, steht der Name ohne Artikel („Anna trinkt Kombucha“) – das ist ebenfalls korrekt.
 */
object GermanArticles {
    private val CONTAINERS: Map<String, GrammaticalGender> = mapOf(
        "glas" to GrammaticalGender.NEUTER,
        "gläschen" to GrammaticalGender.NEUTER,
        "tasse" to GrammaticalGender.FEMININE,
        "flasche" to GrammaticalGender.FEMININE,
        "dose" to GrammaticalGender.FEMININE,
        "kanne" to GrammaticalGender.FEMININE,
        "becher" to GrammaticalGender.MASCULINE,
        "krug" to GrammaticalGender.MASCULINE,
        "pott" to GrammaticalGender.MASCULINE,
        "schluck" to GrammaticalGender.MASCULINE,
    )

    private val NOUN_ENDINGS: List<Pair<String, GrammaticalGender>> = (
        endings(
            GrammaticalGender.FEMININE,
            "schorle", "cola", "limonade", "limo", "milch", "brause", "mate", "schokolade", "bowle", "brühe", "maß", "halbe",
        ) + endings(
            GrammaticalGender.MASCULINE,
            "wein", "kaffee", "tee", "saft", "sekt", "schnaps", "likör", "drink", "cocktail", "espresso", "cappuccino",
            "macchiato", "mokka", "kakao", "whisky", "whiskey", "wodka", "vodka", "rum", "gin", "tequila", "korn",
            "smoothie", "shake", "prosecco", "champagner", "punsch", "grog", "kefir", "lassi", "sirup", "trunk", "most",
            "spritz", "sprudel", "kurzer", "klarer", "shot", "cidre",
        ) + endings(
            GrammaticalGender.NEUTER,
            "wasser", "bier", "pils", "pilsener", "pilsner", "weizen", "kölsch", "helles", "dunkles", "radler",
            "getränk", "tonic", "export",
        )
    ).sortedByDescending { it.first.length }

    private val DETERMINERS = setOf(
        "ein", "eine", "einen", "der", "die", "das", "den", "mein", "meine", "meinen", "kein", "keine", "keinen",
        "etwas", "noch",
    )

    private val WORD_SEPARATORS = Regex("[^\\p{L}]+")

    /** Geschlecht des Getränkenamens oder `null`, wenn es nicht sicher bestimmbar ist. */
    fun genderOf(drinkName: String): GrammaticalGender? {
        val words = words(drinkName)
        words.firstOrNull()?.let { CONTAINERS[it] }?.let { return it }
        for (word in words.asReversed()) {
            NOUN_ENDINGS.firstOrNull { (ending, _) -> word.endsWith(ending) }?.let { return it.second }
        }
        return null
    }

    /**
     * Name mit unbestimmtem Artikel im Akkusativ: „einen Kaffee“, „eine Apfelschorle“, „ein Wasser“.
     * Beginnt der Name schon mit Artikel oder Ziffer oder ist das Geschlecht unbekannt, bleibt er unverändert.
     */
    fun withIndefiniteArticle(drinkName: String): String {
        val name = drinkName.trim()
        if (name.isEmpty() || name.first().isDigit() || words(name).firstOrNull() in DETERMINERS) return name
        val article = when (genderOf(name)) {
            GrammaticalGender.MASCULINE -> "einen"
            GrammaticalGender.FEMININE -> "eine"
            GrammaticalGender.NEUTER -> "ein"
            null -> return name
        }
        return "$article $name"
    }

    private fun endings(gender: GrammaticalGender, vararg words: String): List<Pair<String, GrammaticalGender>> =
        words.map { it to gender }

    private fun words(text: String): List<String> =
        text.lowercase(Locale.GERMAN).split(WORD_SEPARATORS).filter { it.isNotEmpty() }
}
