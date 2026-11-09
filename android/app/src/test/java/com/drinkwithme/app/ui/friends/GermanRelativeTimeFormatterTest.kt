package com.drinkwithme.app.ui.friends

import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class GermanRelativeTimeFormatterTest {
    private val formatter = GermanRelativeTimeFormatter(ZoneOffset.UTC)
    private val now = Instant.parse("2026-11-09T14:30:00Z")

    @Test
    fun formatsRecentTimesRelatively() {
        assertEquals("gerade eben", formatter.format(now.minusSeconds(30), now))
        assertEquals("gerade eben", formatter.format(now.plusSeconds(90), now))
        assertEquals("vor 1 Min.", formatter.format(now.minusSeconds(60), now))
        assertEquals("vor 5 Min.", formatter.format(now.minusSeconds(5 * 60 + 20), now))
        assertEquals("vor 59 Min.", formatter.format(now.minusSeconds(59 * 60), now))
        assertEquals("vor 1 Std.", formatter.format(now.minusSeconds(60 * 60), now))
        assertEquals("vor 23 Std.", formatter.format(now.minusSeconds(23 * 60 * 60 + 59 * 60), now))
    }

    @Test
    fun formatsOlderTimesAsDate() {
        assertEquals("08.11. 14:30", formatter.format(now.minusSeconds(24 * 60 * 60), now))
    }
}
