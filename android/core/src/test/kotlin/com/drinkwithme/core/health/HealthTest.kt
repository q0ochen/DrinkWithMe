package com.drinkwithme.core.health

import com.drinkwithme.core.bar.DefaultBarCatalog
import com.drinkwithme.core.catalogItem
import com.drinkwithme.core.entryOf
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HealthTest {
    private val delta = 1e-9

    @Test
    fun alcoholGramsFormula() {
        assertEquals(19.6, HealthMath.alcoholGrams(500, 4.9), delta) // Pils
        assertEquals(1.6, HealthMath.alcoholGrams(500, 0.4), delta) // Alkoholfreies Bier
        assertEquals(12.0, HealthMath.alcoholGrams(125, 12.0), delta) // Rotwein
        assertEquals(6.4, HealthMath.alcoholGrams(20, 40.0), delta) // Schnaps
        assertEquals(0.0, HealthMath.alcoholGrams(250, 0.0), delta)
    }

    @Test
    fun sugarGramsFormula() {
        assertEquals(34.98, HealthMath.sugarGrams(330, 10.6), delta) // Cola
        assertEquals(27.5, HealthMath.sugarGrams(500, 5.5), delta) // Apfelschorle
        assertEquals(0.0, HealthMath.sugarGrams(250, 0.0), delta)
    }

    @Test
    fun alcoholicFrom1Point2Percent() {
        assertTrue(HealthMath.isAlcoholic(1.2))
        assertTrue(HealthMath.isAlcoholic(4.9))
        assertFalse(HealthMath.isAlcoholic(1.19))
        assertFalse(HealthMath.isAlcoholic(0.4))
        assertFalse(HealthMath.isAlcoholic(0.0))
    }

    @Test
    fun sugarLevels() {
        assertFalse(HealthMath.isSugary(4.99))
        assertTrue(HealthMath.isSugary(5.0))
        assertFalse(HealthMath.isVerySugary(7.99))
        assertTrue(HealthMath.isVerySugary(8.0))
        assertEquals(SugarLevel.LOW, HealthMath.sugarLevel(4.8))
        assertEquals(SugarLevel.SUGARY, HealthMath.sugarLevel(5.5))
        assertEquals(SugarLevel.VERY_SUGARY, HealthMath.sugarLevel(10.6))
    }

    @Test
    fun catalogClassification() {
        val pils = catalogItem(DefaultBarCatalog.PILS)
        val alcoholFree = catalogItem(DefaultBarCatalog.ALKOHOLFREIES_BIER)
        val milk = catalogItem(DefaultBarCatalog.MILCH)
        val cola = catalogItem(DefaultBarCatalog.COLA)
        val schorle = catalogItem(DefaultBarCatalog.APFELSCHORLE)

        assertTrue(pils.isAlcoholic)
        assertFalse(alcoholFree.isAlcoholic)
        assertEquals(1.6, alcoholFree.alcoholGramsPerServing, delta)
        assertFalse(milk.isSugary) // Milchzucker 4,8 g liegt unter der Schwelle
        assertTrue(cola.isVerySugary)
        assertEquals(SugarLevel.SUGARY, schorle.sugarLevel)
        assertTrue(schorle.isSugary)
        assertFalse(schorle.isVerySugary)
        assertEquals(34.98, cola.sugarGramsPerServing, delta)
    }

    @Test
    fun entryExtensionsUseTheLoggedVolume() {
        val entry = entryOf(catalogItem(DefaultBarCatalog.PILS), Instant.EPOCH, volume = 330)
        assertEquals(12.936, entry.alcoholGrams, delta)
        assertEquals(0.99, entry.sugarGrams, delta)
        assertTrue(entry.isAlcoholic)
        assertFalse(entry.isSugary)
        assertFalse(entry.isVerySugary)
        assertEquals(SugarLevel.LOW, entry.sugarLevel)
    }

    @Test
    fun weeklyAlcoholBandsFollowDge2024() {
        assertEquals(AlcoholRiskBand.LOW_RISK, HealthReferences.weeklyAlcoholBand(0.0))
        assertEquals(AlcoholRiskBand.LOW_RISK, HealthReferences.weeklyAlcoholBand(26.99))
        assertEquals(AlcoholRiskBand.MODERATE_RISK, HealthReferences.weeklyAlcoholBand(27.0))
        assertEquals(AlcoholRiskBand.MODERATE_RISK, HealthReferences.weeklyAlcoholBand(81.0))
        assertEquals(AlcoholRiskBand.HIGH_RISK, HealthReferences.weeklyAlcoholBand(81.01))
        assertEquals(listOf("risikoarm", "moderates Risiko", "riskant"), AlcoholRiskBand.entries.map { it.label })
        assertEquals(
            listOf(
                AlcoholBand(AlcoholRiskBand.LOW_RISK, 0.0, 27.0),
                AlcoholBand(AlcoholRiskBand.MODERATE_RISK, 27.0, 81.0),
                AlcoholBand(AlcoholRiskBand.HIGH_RISK, 81.0, null),
            ),
            HealthReferences.weeklyAlcoholBands,
        )
    }

    @Test
    fun referenceTextsAreExact() {
        assertEquals("Es gibt keine unbedenkliche Menge Alkohol. Weniger ist besser.", HealthReferences.ALCOHOL_MANDATORY_NOTE)
        assertEquals("Getränke sind nur ein Teil davon.", HealthReferences.SUGAR_NOTE)
        assertEquals(
            "Diese App ist ein persönliches Getränketagebuch. Sie stellt keine Diagnose und ersetzt keine ärztliche " +
                "Beratung. Kinder, Jugendliche, Schwangere und Stillende sollten keinen Alkohol trinken.",
            HealthReferences.INFO_TEXT,
        )
        assertEquals(
            listOf(
                SugarReferenceLine(
                    50.0,
                    "Obergrenze für freien Zucker in der gesamten Ernährung (bei 2000 kcal)",
                    ReferenceLineStyle.SOLID,
                ),
                SugarReferenceLine(25.0, "WHO: unter 25 g bringt zusätzlichen Nutzen", ReferenceLineStyle.DASHED),
            ),
            HealthReferences.dailySugarLines,
        )
        assertEquals("DGE 2024", HealthReferences.ALCOHOL_SOURCE)
    }

    @Test
    fun noPermissionLanguageInReferenceTexts() {
        val texts = listOf(
            HealthReferences.ALCOHOL_MANDATORY_NOTE, HealthReferences.SUGAR_NOTE, HealthReferences.INFO_TEXT,
            HealthReferences.DAILY_SUGAR_LIMIT_LABEL, HealthReferences.DAILY_SUGAR_WHO_LABEL, HealthReferences.ADULTS_ONLY_NOTE,
        ) + AlcoholRiskBand.entries.map { it.label }
        texts.forEach { text ->
            assertFalse("übrig" in text, text)
            assertFalse("gesund " in text && "Alkohol" in text, text)
        }
    }
}
