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
 * Manifest sorgt dafür, dass sie in dieser Instanz ankommen (onNewIntent).
 */
class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as DrinkWithMeApplication).container
    private val appViewModel: AppViewModel by viewModels { container.viewModelFactory }
    private var receivedSignInCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Nach einer Drehung nicht erneut auswerten – nur beim echten Start.
        if (savedInstanceState == null) route(intent)
        setContent {
            DrinkWithMeTheme {
                DrinkWithMeApp(container, appViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route(intent)
    }

    override fun onResume() {
        super.onResume()
        // Browser ohne Rücksprung geschlossen: „Anmeldung läuft“ beenden.
        if (!receivedSignInCallback) appViewModel.onResumedWithoutCallback()
        receivedSignInCallback = false
        container.onAppForeground()
    }

    private fun route(intent: Intent?) {
        if (intent == null) return
        val request = IntentRouter.route(intent.action, intent.dataString, AndroidIntentExtras(intent)) ?: return
        if (request is AppRequest.SignInCallback) receivedSignInCallback = true
        appViewModel.handle(request)
    }

    private class AndroidIntentExtras(private val intent: Intent) : IntentExtras {
        override fun string(key: String): String? = intent.getStringExtra(key)
        override fun boolean(key: String): Boolean = intent.getBooleanExtra(key, false)
    }
}
