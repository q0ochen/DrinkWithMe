package com.drinkwithme.core.voice

import com.drinkwithme.core.bar.DefaultBarCatalog
import com.drinkwithme.core.barItem
import com.drinkwithme.core.catalogItem
import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceCommandMatcherTest {
    private val catalog = DefaultBarCatalog.items
    private val matcher = VoiceCommandMatcher()

    private fun match(vararg candidates: String) = matcher.match(candidates.toList(), catalog)
    private fun drink(id: String) = VoiceMatch.Drink(catalogItem(id))

    @Test
    fun beerPleaseMatchesPilsViaItsAlias() {
        assertEquals(drink(DefaultBarCatalog.PILS), match("ein Bier bitte"))
        assertEquals(drink(DefaultBarCatalog.PILS), match("Ich trinke heute ein Bier"))
        assertEquals(drink(DefaultBarCatalog.PILS), match("zwei Bier"))
    }

    @Test
    fun pilsAndSoundAlikes() {
        assertEquals(drink(DefaultBarCatalog.PILS), match("Pils"))
        assertEquals(drink(DefaultBarCatalog.PILS), match("Pilz"))
        assertEquals(drink(DefaultBarCatalog.PILS), match("Bils")) // nur über die Kölner Phonetik
    }

    @Test
    fun appleSpritzerVariants() {
        assertEquals(drink(DefaultBarCatalog.APFELSCHORLE), match("Apfelschorle"))
        assertEquals(drink(DefaultBarCatalog.APFELSCHORLE), match("eine Apfelsaftschorle"))
        assertEquals(drink(DefaultBarCatalog.APFELSCHORLE), match("Apfelsaft Schorle"))
        assertEquals(drink(DefaultBarCatalog.APFELSCHORLE), match("Schorle"))
        assertEquals(drink(DefaultBarCatalog.APFELSAFT), match("Apfelsaft"))
    }

    @Test
    fun withoutAliasesApfelsaftschorleIsAmbiguous() {
        val schorle = barItem("Apfelschorle")
        val juice = barItem("Apfelsaft")
        val bar = listOf(juice, schorle)
        assertEquals(VoiceMatch.Ambiguous(schorle, juice), matcher.match(listOf("Apfelsaftschorle"), bar))
        assertEquals(VoiceMatch.Drink(schorle), matcher.match(listOf("Apfelschorle"), bar))
        assertEquals(VoiceMatch.Drink(juice), matcher.match(listOf("Apfelsaft"), bar))
    }

    @Test
    fun ambiguousPairs() {
        assertEquals(
            VoiceMatch.Ambiguous(catalogItem(DefaultBarCatalog.ROTWEIN), catalogItem(DefaultBarCatalog.WEISSWEIN)),
            match("ein Glas Wein"),
        )
        assertEquals(
            VoiceMatch.Ambiguous(catalogItem(DefaultBarCatalog.ORANGENSAFT), catalogItem(DefaultBarCatalog.APFELSAFT)),
            match("Saft"),
        )
        assertEquals(
            VoiceMatch.Ambiguous(catalogItem(DefaultBarCatalog.APFELSAFT), catalogItem(DefaultBarCatalog.APFELSCHORLE)),
            match("Apfel"),
        )
        val small = barItem("Wasser", id = "klein", serving = 250)
        val large = barItem("Wasser", id = "gross", serving = 500)
        assertEquals(VoiceMatch.Ambiguous(small, large), matcher.match(listOf("Wasser"), listOf(small, large)))
    }

    @Test
    fun closeNamesAreDistinguished() {
        assertEquals(drink(DefaultBarCatalog.ROTWEIN), match("ich trinke einen Rotwein"))
        assertEquals(drink(DefaultBarCatalog.WEISSWEIN), match("WEISSWEIN"))
        assertEquals(drink(DefaultBarCatalog.COLA), match("Cola"))
        assertEquals(drink(DefaultBarCatalog.COLA_ZERO), match("Cola Zero"))
        assertEquals(drink(DefaultBarCatalog.COLA_ZERO), match("eine Cola light"))
        assertEquals(drink(DefaultBarCatalog.WASSER), match("Wasser"))
        assertEquals(drink(DefaultBarCatalog.MINERALWASSER), match("Sprudel"))
        assertEquals(drink(DefaultBarCatalog.TEE), match("eine Tasse Tee"))
        assertEquals(drink(DefaultBarCatalog.TEE), match("Kräutertee"))
        assertEquals(drink(DefaultBarCatalog.EISTEE), match("Eistee"))
        assertEquals(drink(DefaultBarCatalog.WEIZENBIER), match("ein Weißbier"))
        assertEquals(drink(DefaultBarCatalog.ALKOHOLFREIES_BIER), match("ein alkoholfreies Bier"))
        assertEquals(drink(DefaultBarCatalog.MALZBIER), match("Malzbier"))
        assertEquals(drink(DefaultBarCatalog.KAFFEE), match("Ich hatte eine Tasse Kaffee"))
    }

    @Test
    fun wordEndingMatchesAnUnambiguousCompound() {
        val bar = listOf(barItem("Weizenbier"), barItem("Wasser"))
        assertEquals(VoiceMatch.Drink(bar[0]), matcher.match(listOf("ein Bier"), bar))
        val wine = barItem("Rotwein trocken")
        assertEquals(VoiceMatch.Drink(wine), matcher.match(listOf("Wein"), listOf(wine, barItem("Kaffee"))))
    }

    @Test
    fun commands() {
        listOf("Rückgängig", "rückgängig machen", "bitte rückgängig", "Nein", "nein danke", "nee").forEach {
            assertEquals(VoiceMatch.Command(VoiceCommand.UNDO), match(it), it)
        }
        listOf("Vorlesen", "bitte vorlesen", "lies das bitte vor").forEach {
            assertEquals(VoiceMatch.Command(VoiceCommand.READ_ALOUD), match(it), it)
        }
        listOf("Wie viel heute?", "wieviel habe ich heute getrunken", "Was habe ich heute getrunken").forEach {
            assertEquals(VoiceMatch.Command(VoiceCommand.TODAY), match(it), it)
        }
    }

    @Test
    fun commandsWorkWithAnEmptyBar() {
        assertEquals(VoiceMatch.Command(VoiceCommand.UNDO), matcher.match(listOf("rückgängig"), emptyList()))
        assertEquals(VoiceMatch.NoMatch, matcher.match(listOf("Pils"), emptyList()))
    }

    @Test
    fun noMatch() {
        assertEquals(VoiceMatch.NoMatch, match("Fahrrad"))
        assertEquals(VoiceMatch.NoMatch, match(""))
        assertEquals(VoiceMatch.NoMatch, match("bitte"))
        assertEquals(VoiceMatch.NoMatch, match("ich trinke ein Glas"))
        assertEquals(VoiceMatch.NoMatch, matcher.match(emptyList(), catalog))
    }

    @Test
    fun candidatesAreCheckedInOrderAndTheFirstResultWins() {
        assertEquals(drink(DefaultBarCatalog.PILS), match("Fahrrad fahren", "ein Pils", "Wasser"))
        assertEquals(VoiceMatch.Command(VoiceCommand.UNDO), match("nein", "Wein"))
        assertEquals(
            VoiceMatch.Ambiguous(catalogItem(DefaultBarCatalog.ROTWEIN), catalogItem(DefaultBarCatalog.WEISSWEIN)),
            match("Wein", "nein"),
        )
    }

    @Test
    fun thresholdAndGapFollowTheContract() {
        val scores = mapOf("aaa" to 0.88, "bbb" to 0.83, "ccc" to 0.84, "ddd" to 0.879)
        val stub = VoiceCommandMatcher(similarity = { _, label -> scores[label] ?: 0.0 })
        val a = barItem("aaa")
        val b = barItem("bbb")
        val c = barItem("ccc")
        val d = barItem("ddd")
        assertEquals(VoiceMatch.Drink(a), stub.match(listOf("xyz"), listOf(b, a))) // 0,88 mit Abstand 0,05
        assertEquals(VoiceMatch.Ambiguous(a, c), stub.match(listOf("xyz"), listOf(a, c))) // Abstand 0,04
        assertEquals(VoiceMatch.NoMatch, stub.match(listOf("xyz"), listOf(d))) // unter 0,88
        assertEquals(0.88, VoiceCommandMatcher.MINIMUM_SIMILARITY)
        assertEquals(0.05, VoiceCommandMatcher.MINIMUM_GAP)
    }

    @Test
    fun thresholdsAreConfigurable() {
        assertEquals(VoiceMatch.NoMatch, VoiceCommandMatcher(minimumSimilarity = 0.99).match(listOf("Pilz"), catalog))
    }

    @Test
    fun companionUsesTheDefaultMatcher() {
        assertEquals(drink(DefaultBarCatalog.PILS), VoiceCommandMatcher.match(listOf("ein Bier bitte"), catalog))
        val asInterface: VoiceMatcher = VoiceCommandMatcher
        assertEquals(VoiceMatch.Command(VoiceCommand.TODAY), asInterface.match(listOf("wie viel heute"), catalog))
    }
}
