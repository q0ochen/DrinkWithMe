package com.drinkwithme.app.publish

import com.drinkwithme.app.data.KeyValueStore
import com.drinkwithme.app.data.nonBlankStringOrNull
import com.drinkwithme.app.data.objects
import com.drinkwithme.app.supabase.SocialException
import com.drinkwithme.core.log.DrinkEntry
import com.drinkwithme.core.log.HistoryReader
import com.drinkwithme.core.sharing.SharingPolicy
import com.drinkwithme.core.social.EventPublisher
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Clock
import java.time.Duration

/** Ein Teilen-Auftrag, der wegen fehlender Verbindung später nachgeholt wird. Gespeichert wird nur die ID. */
sealed interface PendingShare {
    val entryId: String

    data class Publish(override val entryId: String) : PendingShare
    data class Unpublish(override val entryId: String) : PendingShare
}

/**
 * Warteschlange der nachzuholenden Aufträge (in Aufrufreihenfolge, ohne Doppelte). Ein „Zurückziehen“ macht ein
 * noch wartendes „Teilen“ desselben Eintrags überflüssig.
 */
class PendingShareQueue(private val store: KeyValueStore, private val capacity: Int = 200) {
    private val lock = Any()

    fun add(share: PendingShare) = synchronized(lock) {
        val current = load().filterNot { it == share || (share is PendingShare.Unpublish && it == PendingShare.Publish(share.entryId)) }
        save((current + share).takeLast(capacity))
    }

    fun snapshot(): List<PendingShare> = synchronized(lock) { load() }

    fun remove(share: PendingShare) = synchronized(lock) { save(load().filterNot { it == share }) }

    fun clear() = synchronized(lock) { store.putString(KEY, null) }

    private fun load(): List<PendingShare> {
        val text = store.getString(KEY) ?: return emptyList()
        return try {
            JSONArray(text).objects().mapNotNull { json ->
                val id = json.nonBlankStringOrNull("id") ?: return@mapNotNull null
                when (json.nonBlankStringOrNull("op")) {
                    OP_PUBLISH -> PendingShare.Publish(id)
                    OP_UNPUBLISH -> PendingShare.Unpublish(id)
                    else -> null
                }
            }
        } catch (error: JSONException) {
            emptyList()
        }
    }

    private fun save(shares: List<PendingShare>) {
        if (shares.isEmpty()) {
            store.putString(KEY, null)
            return
        }
        val array = JSONArray()
        shares.forEach { share ->
            val op = if (share is PendingShare.Publish) OP_PUBLISH else OP_UNPUBLISH
            array.put(JSONObject().put("op", op).put("id", share.entryId))
        }
        store.putString(KEY, array.toString())
    }

    private companion object {
        const val KEY = "pending_shares"
        const val OP_PUBLISH = "publish"
        const val OP_UNPUBLISH = "unpublish"
    }
}

/** Plant das Nachholen (unter Android per WorkManager mit Netzbedingung). */
fun interface ShareRetryScheduler {
    fun scheduleRetry()
}

/**
 * Decorator um den echten [EventPublisher]: Ohne Verbindung wird der Auftrag vorgemerkt und später nachgeholt,
 * statt verloren zu gehen. Andere Fehler (z. B. abgelehnt) gehen unverändert weiter.
 */
class RetryingEventPublisher(
    private val delegate: EventPublisher,
    private val queue: PendingShareQueue,
    private val scheduler: ShareRetryScheduler,
) : EventPublisher {

    override suspend fun publish(entry: DrinkEntry) = attempt(PendingShare.Publish(entry.id)) { delegate.publish(entry) }

    override suspend fun unpublish(entryId: String) = attempt(PendingShare.Unpublish(entryId)) { delegate.unpublish(entryId) }

    private suspend fun attempt(share: PendingShare, operation: suspend () -> Unit) {
        try {
            operation()
        } catch (error: SocialException.Network) {
            queue.add(share)
            scheduler.scheduleRetry()
        }
    }
}

/** Ergebnis eines Nachhol-Laufs. */
enum class RetryOutcome { DONE, RETRY_LATER }

/**
 * Holt vorgemerkte Aufträge nach. „Teilen“ nur, wenn der Eintrag noch existiert, laut [policy] noch geteilt
 * werden soll und jünger ist als [maxAge] (der Server nimmt nur Zeitpunkte der letzten 24 Stunden an).
 */
class ShareRetryProcessor(
    private val queue: PendingShareQueue,
    private val history: HistoryReader,
    private val policy: SharingPolicy,
    private val publisher: EventPublisher,
    private val clock: Clock,
    private val maxAge: Duration = Duration.ofHours(23),
) {
    suspend fun run(): RetryOutcome {
        for (share in queue.snapshot()) {
            try {
                when (share) {
                    is PendingShare.Publish -> history.entry(share.entryId)
                        ?.takeIf { policy.shouldPublish(it) && isFresh(it) }
                        ?.let { publisher.publish(it) }
                    is PendingShare.Unpublish -> if (policy.canUnpublish()) publisher.unpublish(share.entryId)
                }
                queue.remove(share)
            } catch (error: SocialException.Network) {
                return RetryOutcome.RETRY_LATER
            } catch (error: SocialException) {
                // Abgelehnt oder abgemeldet: Nachholen würde nicht helfen.
                queue.remove(share)
            }
        }
        return RetryOutcome.DONE
    }

    private fun isFresh(entry: DrinkEntry): Boolean = Duration.between(entry.consumedAt, clock.instant()) < maxAge
}
