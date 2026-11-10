package com.drinkwithme.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.drinkwithme.app.di.AppContainer
import com.drinkwithme.app.routing.AppRequest
import com.drinkwithme.app.routing.IntentExtras
import com.drinkwithme.app.routing.IntentRouter
import com.drinkwithme.app.ui.AppViewModel
import com.drinkwithme.app.ui.DrinkWithMeApp
import com.drinkwithme.app.ui.theme.DrinkWithMeTheme

/**
 * Einzige Activity. Nimmt Links (Anmelde-Rücksprung, Einladung) und Benachrichtigungen entgegen; `singleTask` im
 * Manifest sorgt dafür, dass sie in dieser Instanz ankommen (onNewIntent). Benachrichtigungen kommen über den nicht
 * exportierten Einstieg [IntentRouter.NOTIFICATION_ENTRY]; nur ihnen glaubt die App Texte und „sofort vorlesen“.
 */
class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as DrinkWithMeApplication).container
    private val appViewModel: AppViewModel by viewModels { container.viewModelFactory }
    private var receivedSignInCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Nur beim echten Start auswerten – nicht nach einer Drehung und nicht beim Neustart aus „Zuletzt verwendet“.
        if (IntentRouter.shouldRoute(restored = savedInstanceState != null, launchedFromHistory = intent.launchedFromHistory())) {
            route(intent)
        }
        setContent {
            DrinkWithMeTheme {
                DrinkWithMeApp(container, appViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (IntentRouter.shouldRoute(restored = false, launchedFromHistory = intent.launchedFromHistory())) route(intent)
    }

    override fun onResume() {
        super.onResume()
        // Browser ohne Rücksprung geschlossen: „Anmeldung läuft“ beenden.
        if (!receivedSignInCallback) appViewModel.onResumedWithoutCallback()
        receivedSignInCallback = false
        // Zurück aus den Einstellungen/F-Droid: Vielleicht gibt es jetzt eine (deutsche) Sprachausgabe.
        container.speaker.recheckIfNotReady()
        // Freunde neu laden und abrufen nur beim echten Wechsel in den Vordergrund (DrinkWithMeApplication).
    }

    private fun route(intent: Intent?) {
        if (intent == null) return
        val trusted = IntentRouter.isTrustedEntry(intent.component?.className)
        val request = IntentRouter.route(intent.action, intent.dataString, AndroidIntentExtras(intent), trusted)
        // Erledigt: Ein späteres getIntent() sieht nur noch einen einfachen Start, nicht noch einmal diesen Auftrag.
        setIntent(Intent(Intent.ACTION_MAIN))
        if (request == null) return
        if (request is AppRequest.SignInCallback) receivedSignInCallback = true
        appViewModel.handle(request)
    }

    private fun Intent.launchedFromHistory(): Boolean = flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0

    private class AndroidIntentExtras(private val intent: Intent) : IntentExtras {
        override fun string(key: String): String? = intent.getStringExtra(key)
        override fun boolean(key: String): Boolean = intent.getBooleanExtra(key, false)
    }
}
