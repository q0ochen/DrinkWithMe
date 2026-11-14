package com.drinkwithme.core

import com.drinkwithme.core.bar.BarItem
import com.drinkwithme.core.bar.DefaultBarCatalog
import com.drinkwithme.core.bar.DrinkCategory
import com.drinkwithme.core.bar.DrinkIcon
import com.drinkwithme.core.log.DrinkEntry
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/** Uhr, deren Zeitpunkt im Test verstellt werden kann. */
class MutableClock(var now: Instant, private val zone: ZoneId = BERLIN) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

/** Fortlaufende IDs „id-1“, „id-2“ … */
class SequentialIds : IdGenerator {
    private val counter = AtomicInteger()
    override fun newId(): String = "id-${counter.incrementAndGet()}"
}

val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

/** Lokale Berliner Zeit („2026-11-10T18:00“) als Instant. */
fun berlin(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(BERLIN).toInstant()

fun catalogItem(id: String): BarItem = requireNotNull(DefaultBarCatalog.item(id)) { "Unbekannte Katalog-ID $id" }

private val entryIds = AtomicInteger()

/** Ein Eintrag aus einem Katalog-Getränk zu einem festen Zeitpunkt. */
fun entryOf(
    item: BarItem,
    at: Instant,
    volume: Int = item.servingMilliliters,
    share: Boolean = false,
    id: String = "entry-${entryIds.incrementAndGet()}",
) = DrinkEntry(
    id = id,
    barItemId = item.id,
    name = item.name,
    icon = item.icon,
    category = item.category,
    volumeMilliliters = volume,
    alcoholPercent = item.alcoholPercent,
    sugarGramsPer100Ml = item.sugarGramsPer100Ml,
    consumedAt = at,
    shareWithFriends = share,
)

/** Ein frei definierter Eintrag. */
fun entry(
    at: Instant,
    volume: Int = 500,
    alcoholPercent: Double = 0.0,
    sugarPer100Ml: Double = 0.0,
    share: Boolean = false,
    name: String = "Testgetränk",
    id: String = "entry-${entryIds.incrementAndGet()}",
) = DrinkEntry(
    id = id,
    barItemId = null,
    name = name,
    icon = DrinkIcon.OTHER,
    category = DrinkCategory.OTHER,
    volumeMilliliters = volume,
    alcoholPercent = alcoholPercent,
    sugarGramsPer100Ml = sugarPer100Ml,
    consumedAt = at,
    shareWithFriends = share,
)

/** Einfaches Bar-Getränk für Tests. */
fun barItem(
    name: String,
    id: String = "bar-${name.lowercase()}",
    aliases: List<String> = emptyList(),
    alcoholPercent: Double = 0.0,
    sugarPer100Ml: Double = 0.0,
    serving: Int = 250,
    sortOrder: Int = 0,
) = BarItem(
    id = id,
    name = name,
    icon = DrinkIcon.OTHER,
    category = DrinkCategory.OTHER,
    servingMilliliters = serving,
    alcoholPercent = alcoholPercent,
    sugarGramsPer100Ml = sugarPer100Ml,
    aliases = aliases,
    sortOrder = sortOrder,
)
