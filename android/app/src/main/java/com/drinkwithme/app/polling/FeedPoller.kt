package com.drinkwithme.app.polling

import com.drinkwithme.app.data.NotifiedEvents
import com.drinkwithme.app.push.DrinkNotificationContent
import com.drinkwithme.app.push.FriendDrinkNotifier
import com.drinkwithme.app.supabase.CurrentUserId
import com.drinkwithme.core.social.FeedService
import com.drinkwithme.core.speech.EventTexts
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Merker des Abrufs: Zeitpunkt des letzten erfolgreichen Abrufs (`null` = noch nie, z. B. direkt nach der Anmeldung). */
interface PollingState {
    var lastSeenEventAt: Instant?
}

/** Ergebnis eines Abrufs. */
sealed interface PollResult {
    data object NotSignedIn : PollResult

    /** Erster Abruf: Bisheriges gilt als gesehen, damit nach der Anmeldung keine Flut an Meldungen kommt. */
    data class Baseline(val known: Int) : PollResult

    data class Checked(val notified: Int) : PollResult
}

/**
 * Abruf ohne Push-Distributor (alle 15 Minuten und beim Öffnen): holt geteilte Getränke der Freunde und zeigt
 * zu neuen Ereignissen eine Benachrichtigung – keine doppelt (gemeinsamer Merker mit Push, [NotifiedEvents]).
 *
 * Abgefragt werden die Ereignisse ab „letzter Abruf − [lookback]“: Der Server nimmt Zeitpunkte bis 24 Stunden
 * rückwirkend an (z. B. offline eingetragen und später geteilt), solche Ereignisse sollen nicht verloren gehen.
 */
class FeedPoller(
    private val feed: FeedService,
    private val currentUser: CurrentUserId,
    private val state: PollingState,
    private val notified: NotifiedEvents,
    private val notifier: FriendDrinkNotifier,
    private val clock: Clock,
    private val lookback: Duration = Duration.ofHours(24),
    private val maxNotificationsPerPoll: Int = 10,
) {
    suspend fun poll(): PollResult {
        val me = currentUser.currentUserId() ?: return PollResult.NotSignedIn
        val now = clock.instant()
        val lastSeen = state.lastSeenEventAt
        val since = (lastSeen?.takeIf { it.isBefore(now) } ?: now).minus(lookback)
        val events = feed.recentEvents(since, LIMIT).filter { it.author.id != me }

        if (lastSeen == null) {
            events.forEach { notified.markIfNew(it.id) }
            state.lastSeenEventAt = now
            return PollResult.Baseline(events.size)
        }

        val fresh = events.sortedBy { it.consumedAt }.filter { notified.markIfNew(it.id) }
        fresh.takeLast(maxNotificationsPerPoll).forEach { event ->
            notifier.show(
                DrinkNotificationContent(
                    eventId = event.id,
                    title = EventTexts.notificationTitle(event),
                    body = EventTexts.notificationBody(event),
                ),
            )
        }
        state.lastSeenEventAt = now
        return PollResult.Checked(fresh.size.coerceAtMost(maxNotificationsPerPoll))
    }

    private companion object {
        const val LIMIT = 100
    }
}

/** Wann abgerufen wird: nur mit eingerichtetem Server, angemeldet und ohne aktiven Push-Distributor. */
object PollingPolicy {
    fun shouldPoll(serverConfigured: Boolean, signedIn: Boolean, pushActive: Boolean): Boolean =
        serverConfigured && signedIn && !pushActive
}

/** Plant den regelmäßigen Abruf (unter Android: WorkManager, alle 15 Minuten, nur mit Netz). */
interface PollingScheduler {
    fun schedule()
    fun cancel()
}
