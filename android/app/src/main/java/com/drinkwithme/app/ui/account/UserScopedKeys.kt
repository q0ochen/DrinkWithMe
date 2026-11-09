package com.drinkwithme.app.ui.account

import com.drinkwithme.core.social.UserProfile

/**
 * Schlüssel für ein ViewModel, das Daten eines bestimmten Nutzers hält (`viewModel(key = …)`): Jeder Nutzer
 * bekommt eine eigene Instanz, ein anderes Konto sieht also nie zwischengespeicherte Runden oder Getränke
 * des vorherigen.
 */
fun userScopedKey(name: String, user: UserProfile): String = "$name-${user.id}"
