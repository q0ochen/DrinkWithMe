package com.drinkwithme.core.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InviteCodeTest {
    @Test
    fun normalizesInput() {
        assertEquals("K7MX2QAB", InviteCode.parse(" k7mx-2qab ")?.value)
    }

    @Test
    fun rejectsWrongLength() {
        assertNull(InviteCode.parse("K7MX2QA"))
        assertNull(InviteCode.parse("K7MX2QABC"))
        assertNull(InviteCode.parse(""))
    }

    @Test
    fun rejectsAmbiguousCharacters() {
        listOf("K7MX2QAO", "K7MX2QAI", "K7MX2QAL", "K7MX2QA0", "K7MX2QA1", "K7MX2QA!").forEach {
            assertNull(InviteCode.parse(it), it)
        }
    }
}
