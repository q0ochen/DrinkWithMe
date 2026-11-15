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
        // Zahlen wie „0,0“ zählen beim Abgleich nicht: Beide alkoholfreien Biere klingen gleich, also nachfragen.
        assertEquals(
            VoiceMatch.Ambiguous(catalogItem(DefaultBarCatalog.ALKOHOLFREIES_BIER), catalogItem(DefaultBarCatalog.ALKOHOLFREIES_BIER_0_0)),
            match("ein alkoholfreies Bier"),
        )
        assertEquals(drink(DefaultBarCatalog.ALKOHOLFREIES_BIER_0_0), match("ein alkoholfreies Pils"))
        assertEquals(drink(DefaultBarCatalog.ALKOHOLFREIES_WEISSBIER_0_0), match("alkoholfreies Weizen"))
        assertEquals(drink(DefaultBarCatalog.ALKOHOLFREIES_WEISSBIER_0_0), match("ein alkoholfreies Weißbier"))
        assertEquals(drink(DefaultBarCatalog.RADLER), match("ein Radler"))
        assertEquals(drink(DefaultBarCatalog.ALKOHOLFREIES_RADLER_0_0), match("ein alkoholfreies Radler"))
        assertEquals(drink(DefaultBarCatalog.SPEZI), match("ein Spezi"))
        assertEquals(drink(DefaultBarCatalog.SPEZI), match("Cola Mix"))
        assertEquals(drink(DefaultBarCatalog.COLA), match("eine Cola"))
        assertEquals(drink(DefaultBarCatalog.ORANGENSAFT), match("Orange"))
        assertEquals(drink(DefaultBarCatalog.ESPRESSO), match("einen Espresso"))
        assertEquals(drink(DefaultBarCatalog.CAPPUCCINO), match("Cappuccino"))
        assertEquals(drink(DefaultBarCatalog.LATTE_MACCHIATO), match("Latte Macchiato"))
        assertEquals(drink(DefaultBarCatalog.MILCHKAFFEE), match("Milchkaffee"))
        assertEquals(drink(DefaultBarCatalog.EISKAFFEE), match("Eiskaffee"))
        assertEquals(drink(DefaultBarCatalog.KAKAO), match("heiße Schokolade"))
        assertEquals(drink(DefaultBarCatalog.WEINSCHORLE), match("eine Weinschorle"))
        assertEquals(drink(DefaultBarCatalog.MALZBIER), match("Malzbier"))
        assertEquals(drink(DefaultBarCatalog.KAFFEE), match("Ich hatte eine Tasse Kaffee"))
        assertEquals(drink(DefaultBarCatalog.KAFFEE), match("einen Café"))
        assertEquals(drink(DefaultBarCatalog.SEKT), match("ein Glas Secco"))
    }

    @Test
    fun wordEndingMatchesAnUnambiguousCompound() {
        val bar = listOf(barItem("Weizenbier"), barItem("Wasser"))
        assertEquals(VoiceMatch.Drink(bar[0]), matcher.match(listOf("ein Bier"), bar))
        val wine = barItem("Rotwein trocken")
        assertEquals(VoiceMatch.Drink(wine), matcher.match(listOf("Wein"), listOf(wine, barItem("Kaffee"))))
    }

    @Test
    fun exactNameOrAliasWinsOverCloseNames() {
        val beer = catalogItem(DefaultBarCatalog.ALKOHOLFREIES_BIER)
        val beer00 = catalogItem(DefaultBarCatalog.ALKOHOLFREIES_BIER_0_0)
        val wheat00 = catalogItem(DefaultBarCatalog.ALKOHOLFREIES_WEISSBIER_0_0)
        // „alkoholfreiesbier“ ~ „alkoholfreiesweissbier“ ≈ 0,953 – ohne die Regel wäre der Abstand nur ≈ 0,047 (< 0,05).
        assertEquals(VoiceMatch.Drink(wheat00), matcher.match(listOf("ein alkoholfreies Weißbier"), listOf(beer, wheat00)))
        assertEquals(VoiceMatch.Drink(beer), matcher.match(listOf("ein alkoholfreies Bier"), listOf(beer, wheat00)))
        assertEquals(VoiceMatch.Drink(beer00), matcher.match(listOf("alkoholfreies Bier"), listOf(beer00, wheat00)))
        assertEquals(VoiceMatch.Drink(wheat00), matcher.match(listOf("alkoholfreies Weißbier"), listOf(beer00, wheat00)))
        // Passen beide genau (Zahlen zählen nicht), wird weiter nachgefragt.
        assertEquals(VoiceMatch.Ambiguous(beer, beer00), matcher.match(listOf("alkoholfreies Bier"), listOf(beer, beer00)))
    }

    @Test
    fun anExactAliasDoesNotOverruleACloseOwnDrink() {
        // Ein Alias ist unsichtbar: „Caffè Latte“ (Alias von Latte Macchiato) und das eigene „Café Latte“ sind
        // dasselbe Getränk in anderer Schreibweise – welches gemeint ist, hängt nur an der Spracherkennung.
        val latte = catalogItem(DefaultBarCatalog.LATTE_MACCHIATO)
        val own = barItem("Café Latte")
        assertEquals(VoiceMatch.Ambiguous(latte, own), matcher.match(listOf("Caffè Latte"), listOf(latte, own)))
        assertEquals(VoiceMatch.Drink(own), matcher.match(listOf("Café Latte"), listOf(latte, own)))
        val pils = catalogItem(DefaultBarCatalog.PILS)
        val beers = barItem("Biere")
        assertEquals(VoiceMatch.Ambiguous(pils, beers), matcher.match(listOf("Bier"), listOf(pils, beers)))
    }

    @Test
    fun aliasWordEndingsAskButNeverDecide() {
        // Ein Wortende eines Alias („Eis“ in „Kaffee mit Eis“) trägt allein nichts ein …
        val spritzer = catalogItem(DefaultBarCatalog.WEINSCHORLE)
        val spezi = catalogItem(DefaultBarCatalog.SPEZI)
        val zero = catalogItem(DefaultBarCatalog.COLA_ZERO)
        assertEquals(VoiceMatch.NoMatch, matcher.match(listOf("Eis"), listOf(catalogItem(DefaultBarCatalog.EISKAFFEE))))
        assertEquals(VoiceMatch.NoMatch, matcher.match(listOf("Wein"), listOf(spritzer)))
        assertEquals(VoiceMatch.NoMatch, matcher.match(listOf("Orange"), listOf(spezi)))
        assertEquals(VoiceMatch.NoMatch, matcher.match(listOf("Mix"), listOf(spezi)))
        // … und keine Schorle mit etwas anderem wird zur Weinschorle.
        assertEquals(VoiceMatch.NoMatch, matcher.match(listOf("eine Schorle mit Apfel"), listOf(spritzer)))
        assertEquals(VoiceMatch.NoMatch, matcher.match(listOf("Schorle ohne Wein"), listOf(spritzer)))
        // … löst aber eine Rückfrage aus, statt ein knapp ähnliches Getränk still zu nehmen.
        val wheat00 = catalogItem(DefaultBarCatalog.ALKOHOLFREIES_WEISSBIER_0_0)
        val white = catalogItem(DefaultBarCatalog.WEISSWEIN)
        assertEquals(VoiceMatch.Ambiguous(white, wheat00), matcher.match(listOf("ein Weizen"), listOf(wheat00, white)))
        val ownHelles = barItem("Helles", aliases = listOf("Helles Bier"))
        val alcoholFree = catalogItem(DefaultBarCatalog.ALKOHOLFREIES_BIER)
        assertEquals(VoiceMatch.Ambiguous(alcoholFree, ownHelles), matcher.match(listOf("ein Bier"), listOf(ownHelles, alcoholFree)))
        val milkCoffee = catalogItem(DefaultBarCatalog.MILCHKAFFEE)
        val cocoa = catalogItem(DefaultBarCatalog.KAKAO)
        assertEquals(VoiceMatch.Ambiguous(milkCoffee, cocoa), matcher.match(listOf("Milch"), listOf(milkCoffee, cocoa)))
        val longdrink = catalogItem(DefaultBarCatalog.LONGDRINK)
        assertEquals(VoiceMatch.Ambiguous(zero, longdrink), matcher.match(listOf("eine Cola"), listOf(longdrink, zero)))
        // Der Name zählt weiter voll: „Cola“ ist ein Wort von „Cola zero“.
        assertEquals(VoiceMatch.Drink(zero), matcher.match(listOf("Cola"), listOf(zero)))
        val defaultBarWithCocoa = DefaultBarCatalog.defaultSelection() + cocoa
        val spritzerInBar = defaultBarWithCocoa.first { it.id == DefaultBarCatalog.APFELSCHORLE }
        assertEquals(VoiceMatch.Drink(spritzerInBar), matcher.match(listOf("Schorle"), defaultBarWithCocoa))
        assertEquals(VoiceMatch.Drink(cocoa), matcher.match(listOf("eine heiße Schokolade"), defaultBarWithCocoa))
    }

    @Test
    fun newDrinksDoNotTakeOverExistingWords() {
        val red = catalogItem(DefaultBarCatalog.ROTWEIN)
        val spritzer = catalogItem(DefaultBarCatalog.WEINSCHORLE)
        assertEquals(VoiceMatch.Drink(red), matcher.match(listOf("ein Glas Wein"), listOf(red, spritzer)))
        assertEquals(VoiceMatch.Drink(spritzer), matcher.match(listOf("Weinschorle"), listOf(red, spritzer)))
        val cola = catalogItem(DefaultBarCatalog.COLA)
        val zero = catalogItem(DefaultBarCatalog.COLA_ZERO)
        val spezi = catalogItem(DefaultBarCatalog.SPEZI)
        assertEquals(VoiceMatch.Drink(cola), matcher.match(listOf("eine Cola"), listOf(cola, zero, spezi)))
        assertEquals(VoiceMatch.Drink(spezi), matcher.match(listOf("Cola Mix"), listOf(cola, zero, spezi)))
        // „Cola zero“ ohne Cola zero in der Bar ist nicht die süße Spezi.
        assertEquals(VoiceMatch.NoMatch, matcher.match(listOf("Cola zero"), listOf(spezi)))
        // Ohne normale Cola ist „eine Cola“ wirklich offen: Cola zero oder Cola-Mix? Dann nachfragen.
        assertEquals(VoiceMatch.Ambiguous(zero, spezi), matcher.match(listOf("eine Cola"), listOf(zero, spezi)))
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
        val exactScores = mapOf("eee" to 1.0, "fff" to 0.97)
        val exactStub = VoiceCommandMatcher(similarity = { _, label -> exactScores[label] ?: 0.0 })
        val e = barItem("eee")
        val e2 = barItem("eee", id = "zweites-eee")
        val f = barItem("fff")
        assertEquals(VoiceMatch.Drink(e), exactStub.match(listOf("xyz"), listOf(f, e))) // genau vor 0,97
        assertEquals(VoiceMatch.Ambiguous(e, e2), exactStub.match(listOf("xyz"), listOf(e, e2))) // zweimal genau
        assertEquals(1.0, VoiceCommandMatcher.EXACT_SCORE)
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
