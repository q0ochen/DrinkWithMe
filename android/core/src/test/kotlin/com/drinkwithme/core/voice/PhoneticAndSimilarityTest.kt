package com.drinkwithme.core.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhoneticAndSimilarityTest {
    private val delta = 1e-3

    @Test
    fun koelnerPhonetikKnownExamples() {
        assertEquals("65752682", KoelnerPhonetik.encode("Müller-Lüdenscheidt"))
        assertEquals("3412", KoelnerPhonetik.encode("Wikipedia"))
        assertEquals("17863", KoelnerPhonetik.encode("Breschnew"))
        assertEquals("068", KoelnerPhonetik.encode("Heinz"))
    }

    @Test
    fun koelnerPhonetikContextRules() {
        assertEquals("487", KoelnerPhonetik.encode("Cäsar")) // C im Anlaut vor A → 4
        assertEquals("85", KoelnerPhonetik.encode("Celle")) // C im Anlaut vor E → 8
        assertEquals("348", KoelnerPhonetik.encode("Fuchs")) // C nach U vor H → 4
        assertEquals("4837", KoelnerPhonetik.encode("Xaver")) // X → 48
        assertEquals("048", KoelnerPhonetik.encode("Hexe"))
        assertEquals("68", KoelnerPhonetik.encode("Matsch")) // T vor S → 8
        assertEquals("822", KoelnerPhonetik.encode("Stadt")) // durch Vokal getrennte gleiche Codes bleiben
        assertEquals("3", KoelnerPhonetik.encode("Phi")) // PH → 3
        assertEquals("", KoelnerPhonetik.encode("h-!"))
    }

    @Test
    fun drinksThatSoundAlikeShareACode() {
        assertEquals("158", KoelnerPhonetik.encode("Pils"))
        assertEquals("158", KoelnerPhonetik.encode("Pilz"))
        assertEquals("158", KoelnerPhonetik.encode("Bils"))
        assertEquals(KoelnerPhonetik.encode("Cola"), KoelnerPhonetik.encode("Kola"))
        assertEquals(KoelnerPhonetik.encode("Weißbier"), KoelnerPhonetik.encode("weissbier"))
        assertTrue(KoelnerPhonetik.encode("Apfelschorle") != KoelnerPhonetik.encode("Apfelsaftschorle"))
    }

    @Test
    fun jaroWinklerKnownValues() {
        assertEquals(0.944, JaroWinkler.jaro("MARTHA", "MARHTA"), delta)
        assertEquals(0.961, JaroWinkler.similarity("MARTHA", "MARHTA"), delta)
        assertEquals(0.822, JaroWinkler.jaro("DWAYNE", "DUANE"), delta)
        assertEquals(0.840, JaroWinkler.similarity("DWAYNE", "DUANE"), delta)
        assertEquals(0.767, JaroWinkler.jaro("DIXON", "DICKSONX"), delta)
        assertEquals(0.813, JaroWinkler.similarity("DIXON", "DICKSONX"), delta)
    }

    @Test
    fun jaroWinklerEdgeCases() {
        assertEquals(1.0, JaroWinkler.similarity("", ""))
        assertEquals(0.0, JaroWinkler.similarity("", "bier"))
        assertEquals(0.0, JaroWinkler.similarity("abc", "xyz"))
        assertEquals(1.0, JaroWinkler.similarity("pils", "pils"))
        assertEquals(JaroWinkler.similarity("apfelschorle", "apfelsaft"), JaroWinkler.similarity("apfelsaft", "apfelschorle"), 1e-12)
    }

    @Test
    fun drinkNameSimilarities() {
        assertEquals(0.950, JaroWinkler.similarity("apfelsaftschorle", "apfelschorle"), delta)
        assertEquals(0.913, JaroWinkler.similarity("apfelsaftschorle", "apfelsaft"), delta)
        assertTrue(JaroWinkler.similarity("apfelschorle", "apfelsaft") < 0.88)
    }

    @Test
    fun phoneticSimilarityRaisesSoundAlikesButNeverLowers() {
        val similarity = PhoneticTextSimilarity()
        assertEquals(0.833, JaroWinkler.similarity("bils", "pils"), delta)
        assertEquals(0.917, similarity.similarity("bils", "pils"), delta) // (0,833 + 1) / 2
        listOf("wasser" to "mineralwasser", "tee" to "eistee", "cola" to "colazero").forEach { (a, b) ->
            assertTrue(similarity.similarity(a, b) >= JaroWinkler.similarity(a, b), "$a/$b")
        }
        assertEquals(JaroWinkler.similarity("123", "124"), similarity.similarity("123", "124")) // keine Buchstaben → kein Lautcode
    }

    @Test
    fun normalizerHandlesUmlautsSharpSAccentsAndPunctuation() {
        assertEquals("weissbier", GermanTextNormalizer.normalize("Weißbier"))
        assertEquals("aepfel oel ueber", GermanTextNormalizer.normalize("Äpfel Öl Über"))
        assertEquals("aepfel", GermanTextNormalizer.normalize("Äpfel")) // zerlegtes Ä
        assertEquals("cafe creme", GermanTextNormalizer.normalize("Café Crème"))
        assertEquals("coca cola bitte", GermanTextNormalizer.normalize("  Coca-Cola, bitte! "))
        assertEquals(listOf("o", "saft", "0", "5"), GermanTextNormalizer.tokens("O-Saft 0,5"))
        assertEquals(emptyList(), GermanTextNormalizer.tokens(" ... "))
    }

    @Test
    fun vocabularyRemovesFillerAndNumberWords() {
        assertEquals(listOf("bier"), VoiceVocabulary.meaningfulTokens("Ich trinke ein Bier, bitte"))
        assertEquals(listOf("wasser"), VoiceVocabulary.meaningfulTokens("zwei Gläser Wasser"))
        assertEquals(listOf("milch"), VoiceVocabulary.meaningfulTokens("ein großes Glas Milch"))
        assertEquals(listOf("kaffee"), VoiceVocabulary.meaningfulTokens("Ich hatte eine Tasse Kaffee"))
        assertEquals(listOf("cola"), VoiceVocabulary.meaningfulTokens("0,5 Liter Cola"))
        assertEquals(listOf("espresso"), VoiceVocabulary.meaningfulTokens("einen doppelten Espresso"))
        assertTrue("siebenundzwanzig" in VoiceVocabulary.NUMBER_WORDS)
        assertTrue("fuenf" in VoiceVocabulary.NUMBER_WORDS)
        assertTrue("dreissig" in VoiceVocabulary.NUMBER_WORDS)
    }
}
