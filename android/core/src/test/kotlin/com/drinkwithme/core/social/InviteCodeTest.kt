package com.drinkwithme.core.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InviteCodeTest {
    @Test
    fun normalizesInput() {
        assertEquals("K7MX2Q", InviteCode.parse(" k7m-x2q ")?.value)
    }

    @Test
    fun rejectsWrongLength() {
        assertNull(InviteCode.parse("K7MX2"))
        assertNull(InviteCode.parse("K7MX2QA"))
        assertNull(InviteCode.parse(""))
    }

    @Test
    fun rejectsAmbiguousCharacters() {
        listOf("K7MX2O", "K7MX2I", "K7MX2L", "K7MX20", "K7MX21", "K7MX2!").forEach {
            assertNull(InviteCode.parse(it), it)
        }
    }
}
