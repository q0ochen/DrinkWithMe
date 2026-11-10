package com.drinkwithme.app.ui.bar

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drinkwithme.app.stats.GermanFormat
import com.drinkwithme.app.ui.common.AppIcons
import com.drinkwithme.app.ui.common.BigButton
import com.drinkwithme.app.ui.common.ConfirmDialog
import com.drinkwithme.app.ui.common.DrinkIconImage
import com.drinkwithme.app.ui.common.MinTouchTarget
import com.drinkwithme.app.ui.common.SectionTitle
import com.drinkwithme.app.ui.common.SmallTextButton
import com.drinkwithme.app.ui.common.focusFrame
import com.drinkwithme.app.voice.RecognizeSpeechContract
import com.drinkwithme.core.bar.BarItem
import com.drinkwithme.core.bar.CatalogEntry
import com.drinkwithme.core.health.alcoholGramsPerServing
import com.drinkwithme.core.health.isAlcoholic
import com.drinkwithme.core.health.sugarGramsPerServing
import com.drinkwithme.core.sharing.SharingPolicy
import com.drinkwithme.core.voice.VoiceMatch
import java.time.ZoneId

/** Bar: großes Raster, ein Tipp = getrunken, langes Drücken oder „⋮“ = bearbeiten. */
@Composable
fun BarScreen(viewModel: BarViewModel, onMessage: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val voiceLauncher = rememberLauncherForActivityResult(RecognizeSpeechContract()) { viewModel.onVoiceResults(it) }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BarHeader(
                    voiceAvailable = state.voiceAvailable,
                    onSpeak = {
                        try {
                            voiceLauncher.launch(viewModel.voiceRequest())
                        } catch (error: ActivityNotFoundException) {
                            onMessage(VOICE_UNAVAILABLE)
                        }
                    },
                    onAdd = viewModel::onOpenCatalog,
                )
            }
            if (state.items.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(EMPTY_BAR, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 24.dp))
                }
            }
            items(state.items, key = { it.id }) { item ->
                BarItemCard(
                    item = item,
                    onLog = { viewModel.onDrinkTapped(item) },
                    onLogPrivately = { viewModel.onLogPrivately(item) },
                    onEdit = { viewModel.onEdit(item) },
                    onMoveStart = { viewModel.onMove(item, towardsStart = true) },
                    onMoveEnd = { viewModel.onMove(item, towardsStart = false) },
                )
            }
        }

        state.confirmation?.let { confirmation ->
            ConfirmationBar(
                confirmation = confirmation,
                onUndo = viewModel::onUndo,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    state.pendingConsent?.let { pending ->
        AlcoholConsentDialog(
            drinkName = pending.item.name,
            onAnswer = viewModel::onConsentAnswered,
        )
    }
    state.ambiguity?.let { ambiguity -> AmbiguityDialog(ambiguity, viewModel::onAmbiguityResolved) }
    state.confirmUndo?.let { entry ->
        ConfirmDialog(
            title = "Eintrag löschen?",
            text = "Den letzten Eintrag „${entry.name}“ von ${GermanFormat.time(entry.consumedAt, ZoneId.systemDefault())} Uhr löschen?",
            confirmLabel = "Löschen",
            onConfirm = { viewModel.onConfirmUndo(true) },
            onDismiss = { viewModel.onConfirmUndo(false) },
        )
    }
    state.catalogChoices?.let { choices ->
        CatalogDialog(
            choices = choices,
            onAdd = viewModel::onAddFromCatalog,
            onCustom = viewModel::onAddCustom,
            onDismiss = viewModel::onCloseCatalog,
        )
    }
    state.editor?.let { editor ->
        DrinkEditorDialog(
            editor = editor,
            onChange = viewModel::onEditorChange,
            onSave = viewModel::onEditorSave,
            onDismiss = viewModel::onEditorDismiss,
            onDelete = viewModel::onEditorDeleteRequested,
            onDeleteConfirmed = viewModel::onEditorDeleteConfirmed,
        )
    }
}

@Composable
private fun BarHeader(voiceAvailable: Boolean, onSpeak: () -> Unit, onAdd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Bar")
        Text(HINT, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (voiceAvailable) {
                BigButton("Sprechen", onSpeak, icon = AppIcons.Mic, modifier = Modifier.weight(1f))
            }
            BigButton("Getränk hinzufügen", onAdd, icon = AppIcons.Add, outlined = true, modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BarItemCard(
    item: BarItem,
    onLog: () -> Unit,
    onLogPrivately: () -> Unit,
    onEdit: () -> Unit,
    onMoveStart: () -> Unit,
    onMoveEnd: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val description = itemDescription(item)
    Card(Modifier.fillMaxWidth()) {
        Box {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusFrame()
                    .combinedClickable(
                        onClickLabel = "Jetzt eintragen",
                        onLongClickLabel = "Bearbeiten",
                        onLongClick = onEdit,
                        onClick = onLog,
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = description
                        customActions = listOf(
                            CustomAccessibilityAction("Privat eintragen") { onLogPrivately(); true },
                            CustomAccessibilityAction("Bearbeiten") { onEdit(); true },
                            CustomAccessibilityAction("Nach vorne schieben") { onMoveStart(); true },
                            CustomAccessibilityAction("Nach hinten schieben") { onMoveEnd(); true },
                        )
                    }
                    .heightIn(min = 150.dp)
                    .padding(horizontal = 12.dp, vertical = 16.dp),
            ) {
                DrinkIconImage(item.icon, size = 72.dp)
                Text(item.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(GermanFormat.milliliters(item.servingMilliliters), style = MaterialTheme.typography.bodyMedium)
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.focusFrame(RoundedCornerShape(24.dp)).size(MinTouchTarget),
                ) {
                    Icon(AppIcons.More, contentDescription = "Mehr zu ${item.name}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Privat eintragen (nicht teilen)") }, onClick = { menuOpen = false; onLogPrivately() })
                    DropdownMenuItem(text = { Text("Bearbeiten") }, onClick = { menuOpen = false; onEdit() })
                    DropdownMenuItem(text = { Text("Nach vorne schieben") }, onClick = { menuOpen = false; onMoveStart() })
                    DropdownMenuItem(text = { Text("Nach hinten schieben") }, onClick = { menuOpen = false; onMoveEnd() })
                }
            }
        }
    }
}

/** Textfassung einer Kachel: Name, Menge, Zucker/Alkohol pro Portion. */
private fun itemDescription(item: BarItem): String {
    val parts = mutableListOf(item.name, GermanFormat.milliliters(item.servingMilliliters))
    parts += "${GermanFormat.grams(item.sugarGramsPerServing)} Zucker"
    if (item.isAlcoholic) parts += "${GermanFormat.grams(item.alcoholGramsPerServing)} Alkohol"
    return parts.joinToString(", ") + ". Antippen trägt jetzt ein."
}

@Composable
private fun ConfirmationBar(confirmation: LoggedConfirmation, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                listOfNotNull(confirmation.text, confirmation.sharingHint).joinToString(" "),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            BigButton("Rückgängig", onUndo, icon = AppIcons.Undo, minHeight = 64.dp, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Einmalige Frage beim ersten alkoholischen Getränk (Teilen-Regel, Vertrag 1.5). */
@Composable
fun AlcoholConsentDialog(drinkName: String, onAnswer: (Boolean?) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(null) },
        title = { Text(SharingPolicy.ALCOHOL_CONSENT_QUESTION) },
        text = {
            Text(
                "„$drinkName“ ist ein alkoholisches Getränk. Geteilt werden nur Name, Menge in ml und Zeitpunkt – " +
                    "nie Gramm, Zucker oder Statistik. ${SharingPolicy.ALCOHOL_CONSENT_HINT}",
            )
        },
        confirmButton = { SmallTextButton(SharingPolicy.ALCOHOL_CONSENT_YES, { onAnswer(true) }) },
        dismissButton = { SmallTextButton(SharingPolicy.ALCOHOL_CONSENT_NO, { onAnswer(false) }) },
    )
}

/** „Meinst du … oder …?“ mit zwei großen Knöpfen. */
@Composable
private fun AmbiguityDialog(ambiguity: VoiceMatch.Ambiguous, onChoose: (BarItem?) -> Unit) {
    AlertDialog(
        onDismissRequest = { onChoose(null) },
        title = { Text("Meinst du …") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(ambiguity.first, ambiguity.second).forEach { item ->
                    BigButton(item.name, { onChoose(item) }, minHeight = 72.dp, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        dismissButton = { SmallTextButton("Keins davon", { onChoose(null) }) },
    )
}

@Composable
private fun CatalogDialog(
    choices: List<CatalogEntry>,
    onAdd: (CatalogEntry) -> Unit,
    onCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Getränk hinzufügen") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    BigButton("Eigenes Getränk anlegen", onCustom, icon = AppIcons.Edit, modifier = Modifier.fillMaxWidth())
                }
                items(choices, key = { it.item.id }) { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusFrame()
                            .heightIn(min = 64.dp)
                            .clickable(onClickLabel = "Zur Bar hinzufügen") { onAdd(entry) }
                            .padding(8.dp),
                    ) {
                        DrinkIconImage(entry.item.icon, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.item.name, style = MaterialTheme.typography.titleMedium)
                            val detail = listOfNotNull(
                                GermanFormat.milliliters(entry.item.servingMilliliters),
                                entry.note,
                            ).joinToString(" · ")
                            Text(detail, style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(AppIcons.Add, contentDescription = null)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { SmallTextButton("Schließen", onDismiss) },
    )
}

private const val HINT = "Tippe auf ein Getränk, um es jetzt einzutragen. Lange drücken oder „⋮“ zum Bearbeiten."
private const val EMPTY_BAR = "Deine Bar ist leer. Füge mit „Getränk hinzufügen“ deine Getränke hinzu."
private const val VOICE_UNAVAILABLE = "Die Spracheingabe ist auf diesem Gerät nicht verfügbar."
