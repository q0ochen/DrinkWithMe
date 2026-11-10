package com.drinkwithme.app.ui.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drinkwithme.app.ui.common.AppIcons
import com.drinkwithme.app.ui.common.BigButton
import com.drinkwithme.app.ui.common.DrinkIconImage
import com.drinkwithme.app.ui.theme.EventHeadlineStyle

/**
 * Ereignis-Bildschirm (nach Tipp auf eine Benachrichtigung): großer Satz (32 sp) und ein bildschirmbreiter
 * Knopf „Vorlesen“ (mindestens 96 dp hoch), der auf dem Fernseher sofort den Fokus hat.
 */
@Composable
fun EventScreen(viewModel: EventViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val readFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            readFocus.requestFocus()
        } catch (error: IllegalStateException) {
            // Noch nicht angeordnet – dann ohne vorgegebenen Fokus.
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            DrinkIconImage(state.icon, size = 120.dp)
            Text(
                state.headline,
                style = EventHeadlineStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            state.detail?.let { Text(it, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
            BigButton(
                "Vorlesen",
                viewModel::onReadAloud,
                icon = AppIcons.Speaker,
                minHeight = 96.dp,
                modifier = Modifier.fillMaxWidth().focusRequester(readFocus),
            )
            BigButton("Schließen", onClose, icon = AppIcons.Close, outlined = true, modifier = Modifier.fillMaxWidth())
        }
    }
}
