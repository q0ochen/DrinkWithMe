package com.drinkwithme.app.ui.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drinkwithme.app.social.OutgoingRequest
import com.drinkwithme.app.stats.GermanFormat
import com.drinkwithme.app.ui.common.AppIcons
import com.drinkwithme.app.ui.common.BigButton
import com.drinkwithme.app.ui.common.ConfirmDialog
import com.drinkwithme.app.ui.common.DrinkIconImage
import com.drinkwithme.app.ui.common.SectionCard
import com.drinkwithme.app.ui.common.SectionTitle
import com.drinkwithme.app.ui.common.SmallTextButton
import com.drinkwithme.app.ui.common.focusFrame
import com.drinkwithme.core.social.Friend
import com.drinkwithme.core.social.FriendDrinkEvent
import com.drinkwithme.core.social.FriendRequest
import com.drinkwithme.core.speech.EventTexts
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Freunde: Anmelde-Karte (optional) oder Feed, Anfragen, Freundesliste und Einladen. */
@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel,
    wide: Boolean,
    signingIn: Boolean,
    onSignIn: () -> Unit,
    onShareInvite: (String) -> Unit,
    onOpenEvent: (FriendDrinkEvent) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.inviteToShare) {
        state.inviteToShare?.let { link ->
            onShareInvite(link)
            viewModel.onInviteShared()
        }
    }

    if (!state.configured) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Freunde")
            SectionCard("Server noch nicht eingerichtet") { Text(FriendsViewModel.NOT_CONFIGURED) }
        }
        return
    }
    val user = state.user
    if (user == null) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Freunde")
            SignInCard(signingIn, onSignIn)
        }
        return
    }

    val social: LazyListScope.() -> Unit = {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Freunde")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    BigButton("Freund einladen", viewModel::onCreateInvite, icon = AppIcons.Share, modifier = Modifier.weight(1f))
                    BigButton("Per E-Mail hinzufügen", viewModel::onOpenEmailDialog, icon = AppIcons.Add, outlined = true, modifier = Modifier.weight(1f))
                }
                SmallTextButton("Aktualisieren", viewModel::refresh)
                if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        }
        if (state.incoming.isNotEmpty()) {
            item { SectionTitle("Anfragen an dich") }
            items(state.incoming, key = { it.id }) { request -> IncomingRequestRow(request, viewModel::onRespond) }
        }
        if (state.outgoing.isNotEmpty()) {
            item { SectionTitle("Deine Anfragen") }
            items(state.outgoing, key = { it.id }) { request -> OutgoingRequestRow(request, viewModel::onCancelRequest) }
        }
        item { SectionTitle("Deine Freunde") }
        if (state.friends.isEmpty()) {
            item { Text("Noch keine Freunde. Lade jemanden mit einem Link oder per E-Mail ein.") }
        }
        items(state.friends, key = { "friend-${it.profile.id}" }) { friend -> FriendRow(friend, viewModel::onRemoveRequested) }
    }
    val feed: LazyListScope.() -> Unit = {
        item { SectionTitle("Was deine Freunde trinken") }
        if (state.feed.isEmpty()) {
            item { Text("Noch keine geteilten Getränke.") }
        }
        items(state.feed, key = { "event-${it.id}" }) { event -> FeedRow(event, state.now, onOpenEvent) }
    }

    val padding = PaddingValues(16.dp)
    val spacing = Arrangement.spacedBy(8.dp)
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f), contentPadding = padding, verticalArrangement = spacing, content = social)
            LazyColumn(Modifier.weight(1f), contentPadding = padding, verticalArrangement = spacing, content = feed)
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = spacing) {
            social()
            feed()
        }
    }

    if (state.emailDialogOpen) EmailDialog(state.busy, viewModel::onSendEmailRequest, viewModel::onCloseEmailDialog)
    state.confirmRemove?.let { friend ->
        ConfirmDialog(
            title = "Freundschaft beenden?",
            text = "${friend.profile.displayName} sieht danach deine geteilten Getränke nicht mehr und du ihre nicht.",
            confirmLabel = "Beenden",
            onConfirm = { viewModel.onRemoveConfirmed(true) },
            onDismiss = { viewModel.onRemoveConfirmed(false) },
        )
    }
}

/** Anmeldung ist optional – ohne Konto funktioniert alles außer Freunden. */
@Composable
fun SignInCard(signingIn: Boolean, onSignIn: () -> Unit) {
    SectionCard("Mit Freunden trinken") {
        Text(
            "Melde dich mit deinem Google-Konto an, um Freunde hinzuzufügen und zu sehen, was sie trinken. " +
                "Die Anmeldung ist freiwillig: Bar, Verlauf, Statistik, Sprache und Vorlesen funktionieren auch ohne.",
        )
        Text(
            "Geteilt werden nur Name, Icon, Art, Menge in ml und Zeitpunkt eines Getränks – nie Gramm, Zucker oder " +
                "deine Statistik. Alkoholische Getränke erst nach deiner Zustimmung.",
            style = MaterialTheme.typography.bodyMedium,
        )
        BigButton(
            if (signingIn) "Anmeldung läuft …" else "Mit Google anmelden",
            onSignIn,
            enabled = !signingIn,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun IncomingRequestRow(request: FriendRequest, onRespond: (FriendRequest, Boolean) -> Unit) {
    SectionCard(null) {
        Text("${request.other.displayName} möchte mit dir befreundet sein.", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            BigButton("Annehmen", { onRespond(request, true) }, icon = AppIcons.Check, modifier = Modifier.weight(1f))
            BigButton("Ablehnen", { onRespond(request, false) }, icon = AppIcons.Close, outlined = true, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun OutgoingRequestRow(request: OutgoingRequest, onCancel: (OutgoingRequest) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(FriendsViewModel.outgoingLabel(request), modifier = Modifier.weight(1f))
        SmallTextButton("Zurückziehen", { onCancel(request) })
    }
}

@Composable
private fun FriendRow(friend: Friend, onRemove: (Friend) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Column(Modifier.weight(1f)) {
            Text(friend.profile.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                "Befreundet seit ${GermanFormat.fullDate(friend.since.atZone(ZoneId.systemDefault()).toLocalDate())}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        SmallTextButton("Entfernen", { onRemove(friend) })
    }
}

@Composable
private fun FeedRow(event: FriendDrinkEvent, now: Instant, onOpen: (FriendDrinkEvent) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .focusFrame()
            .clickable(onClickLabel = "Öffnen und vorlesen") { onOpen(event) }
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp),
    ) {
        DrinkIconImage(event.icon, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(EventTexts.headline(event), style = MaterialTheme.typography.titleMedium)
            Text("${GermanFormat.milliliters(event.volumeMilliliters)} · ${relativeTime(event.consumedAt, now)}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** „gerade eben“, „vor 5 Minuten“, „vor 2 Stunden“, sonst Datum und Uhrzeit. */
fun relativeTime(time: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val minutes = Duration.between(time, now).toMinutes()
    return when {
        minutes < 1 -> "gerade eben"
        minutes == 1L -> "vor 1 Minute"
        minutes < 60 -> "vor $minutes Minuten"
        minutes < 120 -> "vor 1 Stunde"
        minutes < 24 * 60 -> "vor ${minutes / 60} Stunden"
        else -> "${GermanFormat.dayMonth(time.atZone(zone).toLocalDate())} um ${GermanFormat.time(time, zone)} Uhr"
    }
}

@Composable
private fun EmailDialog(busy: Boolean, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Per E-Mail hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Gib die Google-E-Mail-Adresse deines Freundes ein. Die Person muss Drink With Me nutzen und angemeldet sein.")
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-Mail-Adresse") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { SmallTextButton("Anfrage senden", { onSend(email) }, enabled = !busy) },
        dismissButton = { SmallTextButton("Abbrechen", onDismiss) },
    )
}
