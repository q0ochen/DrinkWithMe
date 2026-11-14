package com.drinkwithme.app

import com.drinkwithme.app.push.DrinkNotificationContent
import com.drinkwithme.app.push.FriendDrinkNotifier
import com.drinkwithme.app.speech.ScreenReaderDetector
import com.drinkwithme.app.speech.Speaker
import com.drinkwithme.app.speech.SpeakerStatus
import com.drinkwithme.app.supabase.CurrentUserId
import com.drinkwithme.core.bar.BarItem
import com.drinkwithme.core.bar.DrinkCategory
import com.drinkwithme.core.bar.DrinkIcon
import com.drinkwithme.core.log.DrinkEntry
import com.drinkwithme.core.social.FeedService
import com.drinkwithme.core.social.FriendDrinkEvent
import com.drinkwithme.core.social.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Uhr, die nur auf Zuruf weiterläuft. */
class MutableClock(var now: Instant = Instant.parse("2026-11-10T12:00:00Z"), private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now
    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

fun barItem(
    id: String = "wasser",
    name: String = "Wasser",
    icon: DrinkIcon = DrinkIcon.WATER,
    category: DrinkCategory = DrinkCategory.WATER,
    serving: Int = 250,
    alcohol: Double = 0.0,
    sugar: Double = 0.0,
    aliases: List<String> = emptyList(),
    sortOrder: Int = 0,
) = BarItem(id, name, icon, category, serving, alcohol, sugar, aliases, sortOrder = sortOrder)

val PILS = barItem(id = "pils", name = "Pils", icon = DrinkIcon.BEER, category = DrinkCategory.BEER, serving = 500, alcohol = 4.9, sugar = 0.3, aliases = listOf("Bier"), sortOrder = 1)
val APFELSCHORLE = barItem(id = "schorle", name = "Apfelschorle", icon = DrinkIcon.APPLE_SPRITZER, category = DrinkCategory.JUICE, serving = 500, sugar = 5.5, aliases = listOf("Schorle"), sortOrder = 2)
val APFELSAFT = barItem(id = "saft", name = "Apfelsaft", icon = DrinkIcon.JUICE, category = DrinkCategory.JUICE, serving = 200, sugar = 10.0, sortOrder = 3)

fun entry(
    id: String = "e1",
    name: String = "Apfelschorle",
    volume: Int = 500,
    alcohol: Double = 0.0,
    sugar: Double = 5.5,
    at: Instant = Instant.parse("2026-11-10T11:00:00Z"),
    shared: Boolean = false,
) = DrinkEntry(id, null, name, DrinkIcon.APPLE_SPRITZER, DrinkCategory.JUICE, volume, alcohol, sugar, at, shared)

fun profile(id: String = "anna-id", name: String = "Anna") = UserProfile(id, name, null)

fun friendEvent(
    id: String = "ev-1",
    author: UserProfile = profile(),
    name: String = "Apfelschorle",
    volume: Int = 500,
    at: Instant = Instant.parse("2026-11-10T11:55:00Z"),
) = FriendDrinkEvent(id, author, name, DrinkIcon.APPLE_SPRITZER, DrinkCategory.JUICE, volume, at)

class FakeSpeaker(initial: SpeakerStatus = SpeakerStatus.READY) : Speaker {
    val spoken = mutableListOf<String>()
    var prepared = 0
    var rechecks = 0
    val statusFlow = MutableStateFlow(initial)
    override val status: StateFlow<SpeakerStatus> = statusFlow
    override fun prepare() {
        prepared++
    }
    override fun speak(text: String): Boolean {
        if (statusFlow.value.needsSetup) return false
        spoken += text
        return true
    }
    override fun recheckIfNotReady() {
        rechecks++
    }
    override fun stop() = Unit
}

class FakeScreenReader(var active: Boolean = false) : ScreenReaderDetector {
    override fun isScreenReaderActive(): Boolean = active
}

class RecordingNotifier : FriendDrinkNotifier {
    val shown = mutableListOf<DrinkNotificationContent>()
    override fun show(content: DrinkNotificationContent) {
        shown += content
    }
}

class FixedUser(var id: String?) : CurrentUserId {
    override fun currentUserId(): String? = id
}

/** Feed mit festen Ereignissen; merkt sich die Anfragen. */
class FakeFeed(var events: List<FriendDrinkEvent> = emptyList()) : FeedService {
    val requests = mutableListOf<Pair<Instant?, Int>>()
    override suspend fun recentEvents(since: Instant?, limit: Int): List<FriendDrinkEvent> {
        requests += since to limit
        return events.filter { since == null || it.consumedAt.isAfter(since) }.sortedByDescending { it.consumedAt }.take(limit)
    }
}
