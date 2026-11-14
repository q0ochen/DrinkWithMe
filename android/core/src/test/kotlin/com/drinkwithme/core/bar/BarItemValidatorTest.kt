package com.drinkwithme.core.bar

import com.drinkwithme.core.barItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BarItemValidatorTest {
    private val validator = DefaultBarItemValidator()
    private val valid = barItem("Apfelschorle", aliases = listOf("Schorle"), sugarPer100Ml = 5.5, serving = 500)

    @Test
    fun acceptsValidItem() {
        assertTrue(validator.validate(valid).isEmpty())
    }

    @Test
    fun nameMustNotBeBlank() {
        assertEquals(listOf<BarItemError>(BarItemError.EmptyName), validator.validate(valid.copy(name = "   ")))
    }

    @Test
    fun nameMayHaveFortyCharacters() {
        assertTrue(validator.validate(valid.copy(name = "a".repeat(40))).isEmpty())
        assertTrue(validator.validate(valid.copy(name = "  ${"a".repeat(40)}  ")).isEmpty())
        assertEquals(listOf<BarItemError>(BarItemError.NameTooLong(40)), validator.validate(valid.copy(name = "a".repeat(41))))
    }

    @Test
    fun nameLengthCountsCharactersLikeTheServer() {
        val fortyEmojis = "🍺".repeat(40) // 40 Zeichen, aber 80 UTF-16-Einheiten
        assertTrue(validator.validate(valid.copy(name = fortyEmojis)).isEmpty())
    }

    @Test
    fun servingMustBeBetween1And2000() {
        listOf(1, 2000).forEach { assertTrue(validator.validate(valid.copy(servingMilliliters = it)).isEmpty(), "$it") }
        listOf(0, -1, 2001).forEach {
            assertEquals(
                listOf<BarItemError>(BarItemError.ServingOutOfRange(1..2000)),
                validator.validate(valid.copy(servingMilliliters = it)),
            )
        }
    }

    @Test
    fun alcoholMustBeBetween0And80Percent() {
        listOf(0.0, 0.4, 80.0).forEach { assertTrue(validator.validate(valid.copy(alcoholPercent = it)).isEmpty(), "$it") }
        listOf(-0.1, 80.1, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertEquals(
                listOf<BarItemError>(BarItemError.AlcoholOutOfRange(80.0)),
                validator.validate(valid.copy(alcoholPercent = it)),
                "$it",
            )
        }
    }

    @Test
    fun sugarMustBeBetween0And100Grams() {
        listOf(0.0, 100.0).forEach { assertTrue(validator.validate(valid.copy(sugarGramsPer100Ml = it)).isEmpty()) }
        listOf(-1.0, 100.5, Double.NaN).forEach {
            assertEquals(
                listOf<BarItemError>(BarItemError.SugarOutOfRange(100.0)),
                validator.validate(valid.copy(sugarGramsPer100Ml = it)),
            )
        }
    }

    @Test
    fun aliasesMustNotBeBlankOrTooLong() {
        assertEquals(listOf<BarItemError>(BarItemError.BlankAlias), validator.validate(valid.copy(aliases = listOf(" "))))
        assertEquals(
            listOf<BarItemError>(BarItemError.AliasTooLong(40)),
            validator.validate(valid.copy(aliases = listOf("x".repeat(41)))),
        )
    }

    @Test
    fun collectsAllErrorsWithGermanMessages() {
        val errors = validator.validate(
            valid.copy(name = "", servingMilliliters = 0, alcoholPercent = 99.0, sugarGramsPer100Ml = 101.0),
        )
        assertEquals(
            listOf(
                "Bitte gib einen Namen ein.",
                "Die Portion muss zwischen 1 und 2000 ml liegen.",
                "Der Alkoholgehalt muss zwischen 0 und 80 Vol.-% liegen.",
                "Der Zuckergehalt muss zwischen 0 und 100 g pro 100 ml liegen.",
            ),
            errors.map { it.message },
        )
        assertEquals("Der Name darf höchstens 40 Zeichen lang sein.", BarItemError.NameTooLong(40).message)
        assertEquals("Ein Alternativname darf höchstens 40 Zeichen lang sein.", BarItemError.AliasTooLong(40).message)
        assertEquals("Der Alkoholgehalt muss zwischen 0 und 12,5 Vol.-% liegen.", BarItemError.AlcoholOutOfRange(12.5).message)
    }

    @Test
    fun requireValidThrowsWithAllErrors() {
        assertSame(valid, validator.requireValid(valid))
        val error = assertFailsWith<InvalidBarItemException> { validator.requireValid(valid.copy(name = "", servingMilliliters = 0)) }
        assertEquals(2, error.errors.size)
        assertEquals("Bitte gib einen Namen ein. Die Portion muss zwischen 1 und 2000 ml liegen.", error.message)
    }

    @Test
    fun customRulesCanBePluggedIn() {
        val strict = DefaultBarItemValidator(maximumNameLength = 5)
        assertEquals(listOf<BarItemError>(BarItemError.NameTooLong(5)), strict.validate(valid))
    }

    @Test
    fun normalizedTrimsAndRemovesDuplicateAliases() {
        val messy = valid.copy(name = "  Apfelschorle ", aliases = listOf(" Schorle ", "", "schorle", "APFELSCHORLE", "Saftschorle"))
        assertEquals(valid.copy(aliases = listOf("Schorle", "Saftschorle")), messy.normalized())
    }
}
