package com.drinkwithme.core

import com.drinkwithme.core.domain.DefaultDrinkValidator
import com.drinkwithme.core.domain.DrinkCategory
import com.drinkwithme.core.domain.DrinkDraft
import com.drinkwithme.core.domain.DrinkValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DrinkValidatorTest {
    private val validator = DefaultDrinkValidator()

    @Test
    fun acceptsValidDraft() {
        validator.validate(DrinkDraft("Tee", DrinkCategory.TEA, 250))
    }

    @Test
    fun rejectsBlankName() {
        assertFailsWith<DrinkValidationException.EmptyName> {
            validator.validate(DrinkDraft("   ", DrinkCategory.TEA, 250))
        }
    }

    @Test
    fun rejectsTooLongName() {
        val error = assertFailsWith<DrinkValidationException.NameTooLong> {
            validator.validate(DrinkDraft("a".repeat(51), DrinkCategory.TEA, 250))
        }
        assertEquals(50, error.maximum)
    }

    @Test
    fun rejectsVolumeOutOfRange() {
        listOf(0, -10, 5_001).forEach { volume ->
            assertFailsWith<DrinkValidationException.InvalidVolume> {
                validator.validate(DrinkDraft("Wasser", DrinkCategory.WATER, volume))
            }
        }
    }

    @Test
    fun rejectsTooLongNote() {
        assertFailsWith<DrinkValidationException.NoteTooLong> {
            validator.validate(DrinkDraft("Wasser", DrinkCategory.WATER, 200, note = "x".repeat(281)))
        }
    }
}
