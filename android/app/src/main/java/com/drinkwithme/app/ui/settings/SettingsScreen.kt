package com.drinkwithme.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drinkwithme.app.push.PushTexts
import com.drinkwithme.app.speech.SpeakerStatus
import com.drinkwithme.app.speech.SpeechHelpTexts
import com.drinkwithme.app.ui.common.AppIcons
import com.drinkwithme.app.ui.common.BigButton
import com.drinkwithme.app.ui.common.ConfirmDialog
import com.drinkwithme.app.ui.common.DangerButton
import com.drinkwithme.app.ui.common.MinTouchTarget
import com.drinkwithme.app.ui.common.SectionCard
import com.drinkwithme.app.ui.common.SectionTitle
import com.drinkwithme.app.ui.common.SmallTextButton
import com.drinkwithme.app.ui.common.SpeechHelpActions
import com.drinkwithme.app.ui.common.SpeechHelpDialog
import com.drinkwithme.app.ui.common.focusFrame
import com.drinkwithme.app.ui.friends.SignInCard
import com.drinkwithme.core.health.HealthReferences
import com.drinkwithme.core.sharing.AlcoholSharingConsent

/** Plattform-Aktionen der Einstellungen (Browser, System-Bildschirme, Erlaubnis). */
data class SettingsActions(
    val onSignIn: () -> Unit,
    val openUrl: (String) -> Unit,
    val speechHelp: SpeechHelpActions,
    /** `true`, wenn Benachrichtigungen erlaubt sind (vor Android 13 immer). */
    val notificationsAllowed: Boolean,
    val requestNotificationPermission: () -> Unit,
    val versionName: String,
)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, signingIn: Boolean, actions: SettingsActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.onPrepareSpeech()
        viewModel.refreshPush()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("Einstellungen") }

        item {
            if (!state.configured) {
                SectionCard("Konto") { Text("Server noch nicht eingerichtet. Ohne Server gibt es keine Freunde – alles andere funktioniert.") }
            } else {
                val user = state.user
                if (user == null) {
                    SignInCard(signingIn, actions.onSignIn)
                } else {
                    SectionCard("Konto") {
                        Text("Angemeldet als ${user.displayName}" + (state.email?.let { " ($it)" } ?: ""))
                        BigButton("Abmelden", viewModel::onSignOutRequested, outlined = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                        DangerButton("Konto löschen", viewModel::onDeleteAccountRequested, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        item {
            SectionCard("Teilen mit Freunden") {
                SettingSwitch(
                    label = "Alkoholische Getränke mit Freunden teilen",
                    checked = state.alcoholConsent == AlcoholSharingConsent.ALLOWED,
                    onChange = viewModel::onAlcoholSharingChanged,
                )
                Text(
                    "Geteilt werden nur Name, Icon, Art, Menge in ml und Zeitpunkt. Gramm, Zucker und Statistik bleiben " +
                        "immer auf diesem Gerät.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item { NotificationsCard(state, actions, viewModel) }

        item {
            SectionCard("Vorlesen") {
                Text(speakerText(state.speakerStatus), style = MaterialTheme.typography.bodyMedium)
                BigButton("Vorlesen testen", viewModel::onTestSpeech, icon = AppIcons.Speaker, modifier = Modifier.fillMaxWidth())
                if (state.speakerStatus == SpeakerStatus.MISSING_DATA) {
                    BigButton(SpeechHelpTexts.INSTALL_VOICE, actions.speechHelp.installSpeechData, outlined = true, modifier = Modifier.fillMaxWidth())
                }
                if (state.speakerStatus == SpeakerStatus.NO_ENGINE || state.speakerStatus == SpeakerStatus.NO_GERMAN_VOICE) {
                    BigButton(SpeechHelpTexts.HELP_TITLE, { viewModel.onSpeechHelp(true) }, outlined = true, modifier = Modifier.fillMaxWidth())
                }
                SettingSwitch("Kurze Ansage nach dem Eintragen", state.announceLogged, viewModel::onAnnounceLoggedChanged)
                SettingSwitch("Getränke von Freunden beim Öffnen vorlesen", state.autoReadEvents, viewModel::onAutoReadEventsChanged)
                Text(
                    "Ist ein Screenreader wie TalkBack aktiv, spricht die App nur, wenn du ausdrücklich „Vorlesen“ wählst.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            SectionCard("Verlauf") {
                SettingSwitch("Bezugswerte (${HealthReferences.ALCOHOL_SOURCE}) im Alkohol-Diagramm", state.showAlcoholBands, viewModel::onShowAlcoholBandsChanged)
            }
        }

        item {
            SectionCard("Info") {
                Text(HealthReferences.INFO_TEXT)
                Text(HealthReferences.ADULTS_ONLY_NOTE, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Drink With Me ist freie Software. Code und Grafiken stehen unter CC0 1.0 (gemeinfrei). Alle Icons sind " +
                        "selbst gezeichnet. Verwendete freie Bibliotheken (Apache 2.0): AndroidX/Jetpack Compose, OkHttp, " +
                        "UnifiedPush Connector, Tink.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Version ${actions.versionName}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (state.confirmSignOut) {
        ConfirmDialog(
            title = "Abmelden?",
            text = "Deine Bar und dein Verlauf bleiben auf diesem Gerät. Freunde siehst du erst wieder nach der Anmeldung.",
            confirmLabel = "Abmelden",
            onConfirm = { viewModel.onSignOutConfirmed(true) },
            onDismiss = { viewModel.onSignOutConfirmed(false) },
        )
    }
    if (state.confirmDeleteAccount) {
        ConfirmDialog(
            title = "Konto wirklich löschen?",
            text = "Dein Profil, deine Freundschaften, Anfragen und geteilten Getränke werden vom Server gelöscht. Das lässt " +
                "sich nicht rückgängig machen. Deine Bar und dein Verlauf auf diesem Gerät bleiben erhalten.",
            confirmLabel = "Endgültig löschen",
            onConfirm = { viewModel.onDeleteAccountConfirmed(true) },
            onDismiss = { viewModel.onDeleteAccountConfirmed(false) },
        )
    }
    if (state.speechHelpOpen) {
        SpeechHelpDialog(state.speakerStatus, { viewModel.onSpeechHelp(false) }, actions.speechHelp)
    }
}

@Composable
private fun NotificationsCard(state: SettingsUiState, actions: SettingsActions, viewModel: SettingsViewModel) {
    SectionCard("Benachrichtigungen") {
        if (state.user != null && !actions.notificationsAllowed) {
            Text("Benachrichtigungen sind für Drink With Me ausgeschaltet.")
            BigButton("Benachrichtigungen erlauben", actions.requestNotificationPermission, modifier = Modifier.fillMaxWidth())
        }
        when (val push = state.push) {
            PushStatus.Unavailable -> Text(POLLING_INFO)
            PushStatus.NoDistributor -> {
                Text(
                    "Für sofortige Benachrichtigungen empfehlen wir die kostenlose App ntfy aus F-Droid. Ohne ntfy prüft " +
                        "Drink With Me etwa alle 15 Minuten und beim Öffnen auf neue Getränke deiner Freunde.",
                )
                BigButton("ntfy bei F-Droid öffnen", { actions.openUrl(PushTexts.NTFY_FDROID_URL) }, modifier = Modifier.fillMaxWidth())
                SmallTextButton("Erneut prüfen", viewModel::refreshPush)
            }
            is PushStatus.Choose -> {
                Text("Wähle die App, über die Benachrichtigungen sofort ankommen (empfohlen: ntfy):")
                push.distributors.forEach { distributor ->
                    BigButton(
                        "Über ${distributor.label} einrichten",
                        { viewModel.onUseDistributor(distributor) },
                        outlined = !distributor.isNtfy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(POLLING_INFO, style = MaterialTheme.typography.bodyMedium)
            }
            is PushStatus.Waiting -> {
                Text("Wird über ${push.distributor.label} eingerichtet … Falls ${push.distributor.label} nachfragt, bitte dort bestätigen.")
                SmallTextButton("Erneut prüfen", viewModel::refreshPush)
                SmallTextButton("Abbrechen", viewModel::onDisablePush)
            }
            is PushStatus.Active -> {
                Text("Sofortige Benachrichtigungen über ${push.distributor.label} sind aktiv.")
                SmallTextButton("Ausschalten", viewModel::onDisablePush)
            }
        }
        state.pushError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .focusFrame()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = MinTouchTarget),
    ) {
        Column(Modifier.weight(1f)) { Text(label) }
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun speakerText(status: SpeakerStatus): String = when (status) {
    SpeakerStatus.INITIALIZING -> "Die Sprachausgabe wird vorbereitet …"
    SpeakerStatus.READY -> "Die deutsche Stimme ist bereit."
    SpeakerStatus.MISSING_DATA -> SpeechHelpTexts.MISSING_DATA_TEXT
    SpeakerStatus.NO_GERMAN_VOICE, SpeakerStatus.NO_ENGINE -> SpeechHelpTexts.NO_VOICE_TEXT
}

private const val POLLING_INFO =
    "Ohne Push-App prüft Drink With Me etwa alle 15 Minuten und beim Öffnen auf neue Getränke deiner Freunde."
