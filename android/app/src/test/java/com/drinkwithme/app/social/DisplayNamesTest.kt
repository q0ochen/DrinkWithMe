package com.drinkwithme.app.social

import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayNamesTest {

    @Test
    fun blankOrMissingNamesBecomeUnknown() {
        assertEquals("Unbekannt", DisplayNames.sanitize(null))
        assertEquals("Unbekannt", DisplayNames.sanitize("   "))
    }

    @Test
    fun namesAreTrimmed() {
        assertEquals("Anna", DisplayNames.sanitize("  Anna "))
    }

    @Test
    fun longNamesAreCutToFiftyCharacters() {
        assertEquals("a".repeat(50), DisplayNames.sanitize("a".repeat(80)))
    }

    @Test
    fun emojiIsNotCutInHalf() {
        val name = "a".repeat(49) + "🍺"
        val sanitized = DisplayNames.sanitize(name)
        assertEquals("a".repeat(49), sanitized)
    }
}
