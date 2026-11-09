package com.drinkwithme.core.social

import com.drinkwithme.core.domain.Drink
import com.drinkwithme.core.domain.DrinkRepository
import com.drinkwithme.core.domain.DrinkVisibility
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Decorator um das lokale Repository: Speichert wie bisher lokal und teilt
 * zusätzlich nicht-private Getränke mit den Trinkrunden.
 *
 * Dadurch bleiben die bestehenden Use Cases unverändert (Open/Closed) und das
 * Repository ist überall austauschbar (Liskov). Das Teilen läuft im Hintergrund,
 * damit das lokale Speichern nie auf das Netzwerk warten muss.
 */
class PublishingDrinkRepository(
    private val local: DrinkRepository,
    private val publisher: DrinkPublisher,
    private val scope: CoroutineScope,
    private val onPublishError: (Throwable) -> Unit = {},
) : DrinkRepository {

    override fun allDrinks(): List<Drink> = local.allDrinks()

    override fun save(drink: Drink) {
        local.save(drink)
        if (drink.visibility == DrinkVisibility.FRIENDS) {
            launchSafely { publisher.publish(drink) }
        }
    }

    override fun delete(id: String) {
        local.delete(id)
        launchSafely { publisher.unpublish(id) }
    }

    private fun launchSafely(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                onPublishError(error)
            }
        }
    }
}
