package com.drinkwithme.core.log

import com.drinkwithme.core.bar.BarItem

/**
 * Entscheidet beim Eintragen, ob ein Eintrag mit Freunden geteilt wird.
 *
 * Umgesetzt von `com.drinkwithme.core.sharing.SharingPolicy`; als eigene kleine Schnittstelle, damit die
 * Historie nicht vom Teilen-Paket abhängt (Dependency Inversion).
 */
fun interface ShareDecider {
    /**
     * @param shareOverride `false` = dieser Eintrag ist privat; `null` = Regel anwenden. `true` hebt nur eine
     *   Privat-Markierung auf – Anmeldung und Alkohol-Zustimmung bleiben Pflicht.
     */
    fun shareWithFriends(item: BarItem, shareOverride: Boolean?): Boolean
}
