package com.drinkwithme.app.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.drinkwithme.app.speech.SpeakerStatus
import com.drinkwithme.app.speech.SpeechHelpTexts

/** Was die Hilfe „Vorlesen einrichten“ öffnen kann (Browser, Bildschirme der Sprachausgabe). */
data class SpeechHelpActions(
    val openUrl: (String) -> Unit,
    val installSpeechData: () -> Unit,
    val openSpeechSettings: () -> Unit,
)

/**
 * Karte „Vorlesen einrichten“ dort, wo vorgelesen wird (z. B. Ereignis-Bildschirm), solange keine deutsche
 * Sprachausgabe nutzbar ist ([SpeakerStatus.needsSetup]).
 */
@Composable
fun SpeechSetupCard(status: SpeakerStatus, onOpenHelp: () -> Unit, actions: SpeechHelpActions, modifier: Modifier = Modifier) {
    if (!status.needsSetup) return
    SectionCard(SpeechHelpTexts.HELP_TITLE, modifier) {
        Text(SpeechHelpTexts.problem(status), style = MaterialTheme.typography.bodyLarge)
        if (status == SpeakerStatus.MISSING_DATA) {
            BigButton(SpeechHelpTexts.INSTALL_VOICE, actions.installSpeechData, outlined = true, modifier = Modifier.fillMaxWidth())
        } else {
            BigButton(SpeechHelpTexts.HELP_TITLE, onOpenHelp, outlined = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Hilfe „Vorlesen einrichten“: SherpaTTS aus F-Droid, Sprachdaten installieren, Einstellungen der Sprachausgabe. */
@Composable
fun SpeechHelpDialog(status: SpeakerStatus, onDismiss: () -> Unit, actions: SpeechHelpActions) {
    val missingData = status == SpeakerStatus.MISSING_DATA
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SpeechHelpTexts.HELP_TITLE) },
        text = { Text(if (missingData) SpeechHelpTexts.MISSING_DATA_HELP else SpeechHelpTexts.HELP_TEXT) },
        confirmButton = {
            if (missingData) {
                SmallTextButton(SpeechHelpTexts.INSTALL_VOICE, actions.installSpeechData)
            } else {
                SmallTextButton("SherpaTTS bei F-Droid", { actions.openUrl(SpeechHelpTexts.SHERPA_FDROID_URL) })
            }
        },
        dismissButton = {
            Row {
                SmallTextButton("Sprachausgabe-Einstellungen", actions.openSpeechSettings)
                SmallTextButton("Schließen", onDismiss)
            }
        },
    )
}
