package com.drinkwithme.core.voice

import com.drinkwithme.core.bar.BarItem
import kotlin.math.max

/** Ordnet Ergebnisse einer Spracherkennung einem Befehl oder einem Getränk der Bar zu. */
fun interface VoiceMatcher {
    /**
     * @param candidates Erkennungsergebnisse, das wahrscheinlichste zuerst (Android liefert bis zu 5).
     * @param bar die eigene Bar.
     */
    fun match(candidates: List<String>, bar: List<BarItem>): VoiceMatch
}

/**
 * Sprachabgleich gegen Befehle und die Bar.
 *
 * Ablauf je Kandidat (in der gelieferten Reihenfolge; das erste Ergebnis außer [VoiceMatch.NoMatch] gilt):
 * 1. Normalisieren ([GermanTextNormalizer]).
 * 2. Befehle: „rückgängig“ oder beginnt mit „nein“/„nee“ → [VoiceCommand.UNDO]; „vorlesen“/„lies … vor“ →
 *    [VoiceCommand.READ_ALOUD]; „heute“ zusammen mit „wie viel“/„wieviel“/„was …“ → [VoiceCommand.TODAY].
 * 3. Füll- und Zahlwörter entfernen ([VoiceVocabulary]), dann gegen Name **und** Aliase jedes Getränks
 *    vergleichen ([similarity], Standard: Jaro-Winkler + Kölner Phonetik). Ein einzelnes gesprochenes Wort,
 *    das ein Wortende des **Namens** ist („Wein“ in „Rotwein“, „Bier“ in „Weizenbier“), zählt [COMPOUND_PART_SCORE].
 *    Ist es nur Wortende eines **Alias** („Eis“ in „Kaffee mit Eis“, „Bier“ in „Helles Bier“), kann das Getränk
 *    damit nicht gewinnen, wohl aber als Zweitbestes eine Rückfrage auslösen: „Eis“ allein trägt keinen Eiskaffee
 *    ein, „Weizen“ fragt bei alkoholfreiem Weißbier und Weißwein nach, statt still den Wein zu nehmen.
 * 4. Eindeutig ab [minimumSimilarity] (0,88) mit Abstand ≥ [minimumGap] (0,05) zum Zweitbesten, sonst
 *    [VoiceMatch.Ambiguous]. Ausnahme: Stimmt das Gesprochene genau mit dem **Namen** eines Getränks überein
 *    ([EXACT_SCORE]) und beim Zweitbesten nichts genau, gilt dieses Getränk („alkoholfreies Weißbier“ vor
 *    „Alkoholfreies Bier“). Ein genauer Alias reicht dafür nicht: Er kann eine andere Schreibweise eines eigenen
 *    Getränks sein („Caffè Latte“ als Alias, „Café Latte“ selbst angelegt) – dann wird nachgefragt.
 *
 * `VoiceCommandMatcher.match(…)` nutzt die Standardwerte; für andere Werte eine Instanz erzeugen.
 */
class VoiceCommandMatcher(
    private val similarity: TextSimilarity = PhoneticTextSimilarity(),
    private val minimumSimilarity: Double = MINIMUM_SIMILARITY,
    private val minimumGap: Double = MINIMUM_GAP,
) : VoiceMatcher {

    override fun match(candidates: List<String>, bar: List<BarItem>): VoiceMatch {
        val labelled = bar.map { item -> Labelled(item, phraseOf(item.name), item.aliases.mapNotNull(::phraseOf)) }
        for (candidate in candidates) {
            val result = matchCandidate(candidate, labelled)
            if (result != VoiceMatch.NoMatch) return result
        }
        return VoiceMatch.NoMatch
    }

    private fun matchCandidate(candidate: String, labelled: List<Labelled>): VoiceMatch {
        val tokens = GermanTextNormalizer.tokens(candidate)
        commandOf(tokens)?.let { return VoiceMatch.Command(it) }
        val spoken = Phrase.of(tokens.filterNot(VoiceVocabulary::isIgnorable)) ?: return VoiceMatch.NoMatch

        val ranked = labelled.map { scored(spoken, it) }.sortedByDescending { it.score }
        val best = ranked.firstOrNull() ?: return VoiceMatch.NoMatch
        if (best.score < minimumSimilarity - EPSILON) return VoiceMatch.NoMatch
        val runnerUp = ranked.drop(1).maxByOrNull { it.contender } ?: return VoiceMatch.Drink(best.item)
        val onlyExact = isExact(best.nameScore) && !isExact(runnerUp.contender)
        if (!onlyExact && best.score - runnerUp.contender < minimumGap - EPSILON) {
            return VoiceMatch.Ambiguous(best.item, runnerUp.item)
        }
        return VoiceMatch.Drink(best.item)
    }

    private fun isExact(score: Double): Boolean = score >= EXACT_SCORE - EPSILON

    private fun scored(spoken: Phrase, labelled: Labelled): Scored {
        val nameScore = labelled.name?.let { name ->
            val part = if (isWordEnding(spoken, name)) COMPOUND_PART_SCORE else 0.0
            max(direct(spoken, name), part)
        } ?: 0.0
        val aliasScore = labelled.aliases.maxOfOrNull { direct(spoken, it) } ?: 0.0
        val aliasPart = if (labelled.aliases.any { isWordEnding(spoken, it) }) COMPOUND_PART_SCORE else 0.0
        val score = max(nameScore, aliasScore)
        return Scored(labelled.item, score, nameScore, max(score, aliasPart))
    }

    private fun direct(spoken: Phrase, label: Phrase): Double = similarity.similarity(spoken.compact, label.compact)

    /** „wein“ ist Wortende von „rotwein“; „cola“ ist ein Wort von „cola zero“. */
    private fun isWordEnding(spoken: Phrase, label: Phrase): Boolean {
        val word = spoken.tokens.singleOrNull() ?: return false
        if (word.length < MIN_COMPOUND_PART_LENGTH) return false
        return label.tokens.any { token ->
            token.endsWith(word) && (token.length > word.length || label.tokens.size > 1)
        }
    }

    private fun phraseOf(label: String): Phrase? {
        val tokens = GermanTextNormalizer.tokens(label)
        val meaningful = tokens.filterNot(VoiceVocabulary::isIgnorable)
        return Phrase.of(meaningful.ifEmpty { tokens })
    }

    private fun commandOf(tokens: List<String>): VoiceCommand? {
        if (tokens.isEmpty()) return null
        return when {
            "rueckgaengig" in tokens || tokens.first() in UNDO_FIRST_WORDS -> VoiceCommand.UNDO
            "vorlesen" in tokens || ("lies" in tokens && "vor" in tokens) -> VoiceCommand.READ_ALOUD
            "heute" in tokens && asksHowMuch(tokens) -> VoiceCommand.TODAY
            else -> null
        }
    }

    private fun asksHowMuch(tokens: List<String>): Boolean =
        "wieviel" in tokens || "wieviele" in tokens || tokens.first() == "was" ||
            tokens.zipWithNext().any { (first, second) -> first == "wie" && (second == "viel" || second == "viele") }

    private data class Phrase(val tokens: List<String>) {
        val compact: String = tokens.joinToString("")

        companion object {
            fun of(tokens: List<String>): Phrase? = if (tokens.isEmpty()) null else Phrase(tokens)
        }
    }

    private data class Labelled(val item: BarItem, val name: Phrase?, val aliases: List<Phrase>)

    /**
     * [score]: bester Wert über Name und Aliase (damit gewinnt ein Getränk); [nameScore]: Wert des Namens allein;
     * [contender]: zusätzlich Alias-Wortenden – zählt nur als Zweitbestes, also nur für eine Rückfrage.
     */
    private data class Scored(val item: BarItem, val score: Double, val nameScore: Double, val contender: Double)

    /** Vertragswerte und Standard-Abgleicher (erlaubt `VoiceCommandMatcher.match(kandidaten, bar)`). */
    companion object : VoiceMatcher {
        /** Mindestähnlichkeit für einen Treffer. */
        const val MINIMUM_SIMILARITY = 0.88

        /** Mindestabstand des besten zum zweitbesten Getränk. */
        const val MINIMUM_GAP = 0.05

        /**
         * Wert für ein gesprochenes Wort, das ein Wortende eines Namens ist: über der Schwelle, aber mit mehr als
         * [MINIMUM_GAP] Abstand zu einem exakten Treffer („Bier“ → Alias „Bier“ von Pils vor „Weizenbier“) und über
         * bloßen Anfangsähnlichkeiten („Wein“ ~ „Weizen“ ≈ 0,92).
         */
        const val COMPOUND_PART_SCORE = 0.94

        /** Wert einer genauen Übereinstimmung; genau beim Namen schlägt sie jeden nicht genauen Treffer. */
        const val EXACT_SCORE = 1.0

        /** Kürzere gesprochene Wörter zählen nicht als Wortende („Tee“ ja, „ee“ nein). */
        const val MIN_COMPOUND_PART_LENGTH = 3

        private const val EPSILON = 1e-9
        private val UNDO_FIRST_WORDS = setOf("nein", "nee")
        private val standard: VoiceCommandMatcher by lazy { VoiceCommandMatcher() }

        override fun match(candidates: List<String>, bar: List<BarItem>): VoiceMatch = standard.match(candidates, bar)
    }
}
