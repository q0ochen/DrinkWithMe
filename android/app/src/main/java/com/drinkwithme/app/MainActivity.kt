package com.drinkwithme.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drinkwithme.app.push.PushPayload
import com.drinkwithme.app.ui.DrinkWithMeApp
import com.drinkwithme.app.ui.theme.DrinkWithMeTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Runde aus einer angetippten Push-Benachrichtigung, deren Feed geöffnet werden soll. */
    private val groupToOpen = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DrinkWithMeApplication).container
        // Nach einer Drehung nicht erneut springen – nur beim ersten Start aus der Benachrichtigung.
        if (savedInstanceState == null) groupToOpen.value = intent?.groupIdExtra()

        setContent {
            DrinkWithMeTheme {
                val pendingGroupId by groupToOpen.collectAsStateWithLifecycle()
                DrinkWithMeApp(
                    viewModelFactory = container.viewModelFactory,
                    isSocialAvailable = container.isSocialAvailable,
                    onShare = { text -> container.drinkSharer.share(this, text) },
                    pendingGroupId = pendingGroupId,
                    onPendingGroupHandled = { groupToOpen.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.groupIdExtra()?.let { groupToOpen.value = it }
    }

    private fun Intent.groupIdExtra(): String? = getStringExtra(PushPayload.KEY_GROUP_ID)
}
