package com.drinkwithme.app.polling

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Hält die Hintergrundarbeit der Vernetzung passend zum Zustand: ohne Push-Distributor regelmäßig abrufen,
 * mit Push nicht; angemeldet das Push-Abo mit dem Server abgleichen. Aufzurufen beim Start, nach An-/Abmeldung
 * und wenn sich der Push-Endpunkt ändert.
 */
class SocialBackground(
    private val serverConfigured: Boolean,
    private val isSignedIn: () -> Boolean,
    private val isPushActive: () -> Boolean,
    private val polling: PollingScheduler,
    private val scheduleSubscriptionSync: () -> Unit,
) {
    fun refresh() {
        val signedIn = updatePolling()
        if (signedIn) scheduleSubscriptionSync()
    }

    /**
     * Nur den Abruf anpassen (z. B. nach einem Abgleich des Push-Abos: Erst ein bestätigtes Abo ersetzt den Abruf).
     * Plant bewusst keinen weiteren Abgleich. @return ob angemeldet
     */
    fun updatePolling(): Boolean {
        val signedIn = serverConfigured && isSignedIn()
        if (PollingPolicy.shouldPoll(serverConfigured, signedIn, isPushActive())) polling.schedule() else polling.cancel()
        return signedIn
    }
}

/**
 * Beim Wechsel in den Vordergrund höchstens alle [minInterval] nachschauen (Freunde neu laden, ohne Push abrufen).
 * Rückkehr aus dem Browser, dem Teilen-Menü oder der Spracherkennung kostet so keine weitere Runde Anfragen.
 */
class ForegroundRefreshThrottle(private val clock: Clock, private val minInterval: Duration = DEFAULT_INTERVAL) {
    private var last: Instant? = null

    /** `true`, wenn jetzt nachgeschaut werden soll (und merkt sich den Zeitpunkt). */
    @Synchronized
    fun tryAcquire(): Boolean {
        val now = clock.instant()
        val previous = last
        // Auch wenn die Uhr zurückgestellt wurde, nicht lange sperren.
        if (previous != null && !now.isBefore(previous) && now.isBefore(previous.plus(minInterval))) return false
        last = now
        return true
    }

    companion object {
        val DEFAULT_INTERVAL: Duration = Duration.ofMinutes(5)
    }
}
