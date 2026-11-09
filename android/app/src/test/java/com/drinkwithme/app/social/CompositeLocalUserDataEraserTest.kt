package com.drinkwithme.app.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class CompositeLocalUserDataEraserTest {

    @Test
    fun erasesEveryStoreEvenIfOneFails() {
        val erased = mutableListOf<String>()
        val first = IllegalStateException("Speicher voll")
        val second = IllegalArgumentException("kaputt")
        val eraser = CompositeLocalUserDataEraser(
            listOf(
                LocalUserDataEraser { throw first },
                LocalUserDataEraser { userId -> erased += "a:$userId" },
                LocalUserDataEraser { throw second },
                LocalUserDataEraser { userId -> erased += "b:$userId" },
            ),
        )

        val error = assertFailsWith<IllegalStateException> { eraser.eraseUser("u1") }

        assertEquals(listOf("a:u1", "b:u1"), erased)
        assertSame(first, error)
        assertEquals(listOf<Throwable>(second), error.suppressed.toList())
    }

    @Test
    fun withoutErrorsNothingIsThrown() {
        val erased = mutableListOf<String>()
        CompositeLocalUserDataEraser(listOf(LocalUserDataEraser { erased += it })).eraseUser("u1")

        assertEquals(listOf("u1"), erased)
    }
}
