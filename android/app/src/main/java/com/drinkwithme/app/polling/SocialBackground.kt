package com.drinkwithme.app.polling

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
        val signedIn = serverConfigured && isSignedIn()
        if (PollingPolicy.shouldPoll(serverConfigured, signedIn, isPushActive())) polling.schedule() else polling.cancel()
        if (signedIn) scheduleSubscriptionSync()
    }
}
