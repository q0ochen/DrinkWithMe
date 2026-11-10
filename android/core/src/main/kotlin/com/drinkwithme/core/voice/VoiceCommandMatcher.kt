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
 *    das ein Wortende eines Namens ist („Wein“ in „Rotwein“, „Bier“ in „Weizenbier“), zählt [COMPOUND_PART_SCORE].
 * 4. Eindeutig ab [minimumSimilarity] (0,88) mit Abstand ≥ [minimumGap] (0,05) zum Zweitbesten, sonst
 *    [VoiceMatch.Ambiguous].
 *
 * `VoiceCommandMatcher.match(…)` nutzt die Standardwerte; für andere Werte eine Instanz erzeugen.
 */
class VoiceCommandMatcher(
    private val similarity: TextSimilarity = PhoneticTextSimilarity(),
    private val minimumSimilarity: Double = MINIMUM_SIMILARITY,
    private val minimumGap: Double = MINIMUM_GAP,
) : VoiceMatcher {

    override fun match(candidates: List<String>, bar: List<BarItem>): VoiceMatch {
        val labelled = bar.map { item -> Labelled(item, labelsOf(item)) }
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

        val ranked = labelled
            .map { (item, labels) -> Scored(item, labels.maxOfOrNull { score(spoken, it) } ?: 0.0) }
            .sortedByDescending { it.score }
        val best = ranked.firstOrNull() ?: return VoiceMatch.NoMatch
        if (best.score < minimumSimilarity - EPSILON) return VoiceMatch.NoMatch
        val runnerUp = ranked.getOrNull(1)
        if (runnerUp != null && best.score - runnerUp.score < minimumGap - EPSILON) {
            return VoiceMatch.Ambiguous(best.item, runnerUp.item)
        }
        return VoiceMatch.Drink(best.item)
    }

    private fun score(spoken: Phrase, label: Phrase): Double {
        val direct = similarity.similarity(spoken.compact, label.compact)
        val part = if (isWordEnding(spoken, label)) COMPOUND_PART_SCORE else 0.0
        return max(direct, part)
    }

    /** „wein“ ist Wortende von „rotwein“; „cola“ ist ein Wort von „cola zero“. */
    private fun isWordEnding(spoken: Phrase, label: Phrase): Boolean {
        val word = spoken.tokens.singleOrNull() ?: return false
        if (word.length < MIN_COMPOUND_PART_LENGTH) return false
        return label.tokens.any { token ->
            token.endsWith(word) && (token.length > word.length || label.tokens.size > 1)
        }
    }

    private fun labelsOf(item: BarItem): List<Phrase> = (listOf(item.name) + item.aliases).mapNotNull { label ->
        val tokens = GermanTextNormalizer.tokens(label)
        val meaningful = tokens.filterNot(VoiceVocabulary::isIgnorable)
        Phrase.of(meaningful.ifEmpty { tokens })
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

    private data class Labelled(val item: BarItem, val labels: List<Phrase>)

    private data class Scored(val item: BarItem, val score: Double)

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

        /** Kürzere gesprochene Wörter zählen nicht als Wortende („Tee“ ja, „ee“ nein). */
        const val MIN_COMPOUND_PART_LENGTH = 3

        private const val EPSILON = 1e-9
        private val UNDO_FIRST_WORDS = setOf("nein", "nee")
        private val standard: VoiceCommandMatcher by lazy { VoiceCommandMatcher() }

        override fun match(candidates: List<String>, bar: List<BarItem>): VoiceMatch = standard.match(candidates, bar)
    }
}
