package com.drinkwithme.app.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MessageQueueTest {
    @Test
    fun identicalTextsGetTheirOwnIdsSoTheSnackbarKeepsGoing() {
        val queue = MessageQueue()
        queue.show("Keine Verbindung")
        queue.show("Keine Verbindung")
        queue.show("Angemeldet als Anna.")

        // Die Snackbar wartet auf die ID des ersten Eintrags. Bei gleichem Text muss sie sich trotzdem ändern,
        // sonst bleibt die Warteschlange für immer stehen.
        val first = queue.messages.value.first()
        queue.consume(first.id)
        val second = queue.messages.value.first()
        assertEquals("Keine Verbindung", second.text)
        assertNotEquals(first.id, second.id)

        queue.consume(second.id)
        assertEquals(listOf("Angemeldet als Anna."), queue.texts)
        queue.consume(queue.messages.value.first().id)
        assertTrue(queue.messages.value.isEmpty())
    }

    @Test
    fun consumingAnUnknownIdChangesNothing() {
        val queue = MessageQueue()
        queue.show("A")
        queue.consume(Long.MAX_VALUE)
        assertEquals(listOf("A"), queue.texts)
    }
}
