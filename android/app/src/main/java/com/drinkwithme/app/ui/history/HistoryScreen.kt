package com.drinkwithme.app.ui.history

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drinkwithme.app.share.StatisticsCard
import com.drinkwithme.app.stats.HistoryOverview
import com.drinkwithme.app.stats.HistoryTexts
import com.drinkwithme.app.ui.common.AppIcons
import com.drinkwithme.app.ui.common.BigButton
import com.drinkwithme.app.ui.common.ConfirmDialog
import com.drinkwithme.app.ui.common.DrinkIconImage
import com.drinkwithme.app.ui.common.MinTouchTarget
import com.drinkwithme.app.ui.common.SectionCard
import com.drinkwithme.app.ui.common.SectionTitle
import com.drinkwithme.app.ui.common.SmallTextButton
import com.drinkwithme.app.ui.common.focusFrame
import com.drinkwithme.core.health.HealthReferences
import com.drinkwithme.core.log.DrinkEntry
import java.time.ZoneId

/**
 * Verlauf (Statistik und Historie): Kennzahlen, Diagramme mit Bezugswerten, Einträge – alles auch als Text.
 * Ab 600 dp zweispaltig (links Statistik, rechts Einträge).
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    wide: Boolean,
    renderPreview: (StatisticsCard) -> ImageBitmap,
    onShareImage: (StatisticsCard) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val overview = state.overview
    if (state.loading || overview == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        return
    }

    val statistics: LazyListScope.() -> Unit = {
        item { HistoryHeader(onShare = viewModel::onShareRequested, onReadToday = viewModel::onReadTodayAloud) }
        item { TodayCard(overview) }
        item { SugarCard(overview) }
        item { AlcoholCard(overview, state.showAlcoholBands, viewModel::onToggleBands) }
        item { TrendCard(overview) }
        item { Disclaimer() }
    }
    val entries: LazyListScope.() -> Unit = {
        item { SectionTitle("Einträge") }
        if (state.days.isEmpty()) {
            item { Text("Noch keine Einträge. Tippe in der Bar auf ein Getränk.", style = MaterialTheme.typography.bodyLarge) }
        }
        state.days.forEach { day ->
            item(key = "heading-${day.heading}") {
                Text(day.heading, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            items(day.entries, key = { it.id }) { entry ->
                EntryRow(entry, state.zone, onDelete = { viewModel.onDeleteRequested(entry) })
            }
        }
        if (state.hiddenEntries > 0) {
            item { SmallTextButton("Ältere Einträge anzeigen (${state.hiddenEntries})", viewModel::onShowAllEntries) }
        }
    }

    val padding = PaddingValues(16.dp)
    val spacing = Arrangement.spacedBy(12.dp)
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f), contentPadding = padding, verticalArrangement = spacing, content = statistics)
            LazyColumn(Modifier.weight(1f), contentPadding = padding, verticalArrangement = spacing, content = entries)
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = spacing) {
            statistics()
            entries()
        }
    }

    state.confirmDelete?.let { entry ->
        ConfirmDialog(
            title = "Eintrag löschen?",
            text = "${HistoryTexts.entryDescription(entry, state.zone)}. Der Eintrag wird aus deinem Verlauf gelöscht.",
            confirmLabel = "Löschen",
            onConfirm = { viewModel.onDeleteConfirmed(true) },
            onDismiss = { viewModel.onDeleteConfirmed(false) },
        )
    }
    state.sharePreview?.let { card ->
        SharePreviewDialog(
            card = card,
            render = renderPreview,
            onShare = {
                onShareImage(card)
                viewModel.onShareDismissed()
            },
            onDismiss = viewModel::onShareDismissed,
        )
    }
}

@Composable
private fun HistoryHeader(onShare: () -> Unit, onReadToday: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(HistoryTexts.TITLE)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            BigButton("Heute vorlesen", onReadToday, icon = AppIcons.Speaker, outlined = true, modifier = Modifier.weight(1f))
            BigButton("Als Bild teilen", onShare, icon = AppIcons.Share, outlined = true, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TodayCard(overview: HistoryOverview) {
    SectionCard("Heute") {
        HistoryTexts.todayLines(overview.today).forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        Text(HistoryTexts.alcoholFreeDaysThisWeek(overview.currentWeek), style = MaterialTheme.typography.bodyLarge)
        HistoryTexts.streak(overview.alcoholFreeStreak)?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SugarCard(overview: HistoryOverview) {
    var asText by rememberSaveable { mutableStateOf(false) }
    SectionCard(HistoryTexts.SUGAR_CHART_TITLE) {
        SugarChart(overview.dailySugar)
        Text("Durchgezogene Linie (50 g): ${HealthReferences.DAILY_SUGAR_LIMIT_LABEL}", style = MaterialTheme.typography.bodyMedium)
        Text("Gestrichelte Linie (25 g): ${HealthReferences.DAILY_SUGAR_WHO_LABEL}", style = MaterialTheme.typography.bodyMedium)
        Text("${HealthReferences.SUGAR_NOTE} ${HealthReferences.ADULTS_ONLY_NOTE}", style = MaterialTheme.typography.bodyMedium)
        TextToggle(asText) { asText = it }
        if (asText) overview.dailySugar.reversed().forEach { Text(HistoryTexts.sugarDayRow(it)) }
    }
}

@Composable
private fun AlcoholCard(overview: HistoryOverview, showBands: Boolean, onToggleBands: () -> Unit) {
    var asText by rememberSaveable { mutableStateOf(false) }
    SectionCard(HistoryTexts.ALCOHOL_CHART_TITLE) {
        AlcoholChart(overview.weeklyAlcohol, showBands)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .focusFrame()
                .toggleable(value = showBands, role = Role.Switch, onValueChange = { onToggleBands() })
                .heightIn(min = MinTouchTarget),
        ) {
            Text("Bezugswerte (${HealthReferences.ALCOHOL_SOURCE}) anzeigen", modifier = Modifier.weight(1f))
            Switch(checked = showBands, onCheckedChange = null)
        }
        Text(HealthReferences.ALCOHOL_MANDATORY_NOTE, style = MaterialTheme.typography.titleSmall)
        Text(HealthReferences.ADULTS_ONLY_NOTE, style = MaterialTheme.typography.bodyMedium)
        TextToggle(asText) { asText = it }
        if (asText) overview.weeklyAlcohol.reversed().forEach { Text(HistoryTexts.alcoholWeekRow(it, showBands)) }
    }
}

@Composable
private fun TrendCard(overview: HistoryOverview) {
    SectionCard("Vergleich mit dem Vormonat") {
        HistoryTexts.comparisonLines(overview.comparison).forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        Text("Durchschnitt der letzten 4 Wochen", style = MaterialTheme.typography.titleSmall)
        HistoryTexts.averageLines(overview.average).forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
private fun TextToggle(expanded: Boolean, onChange: (Boolean) -> Unit) {
    SmallTextButton(if (expanded) "Werte als Text ausblenden" else "Werte als Text anzeigen", { onChange(!expanded) })
}

@Composable
private fun EntryRow(entry: DrinkEntry, zone: ZoneId, onDelete: () -> Unit) {
    val description = HistoryTexts.entryDescription(entry, zone)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) {
        DrinkIconImage(entry.icon, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(
            onClick = onDelete,
            modifier = Modifier.focusFrame(RoundedCornerShape(24.dp)).semantics { contentDescription = "Löschen: $description" },
        ) {
            Icon(AppIcons.Delete, contentDescription = null)
        }
    }
}

@Composable
private fun Disclaimer() {
    Text(HealthReferences.INFO_TEXT, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
}

/** Vorschau vor dem Teilen: das erzeugte Bild und seine Textfassung. */
@Composable
private fun SharePreviewDialog(
    card: StatisticsCard,
    render: (StatisticsCard) -> ImageBitmap,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val bitmap = remember(card) { render(card) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle("Vorschau")
                Text("So sieht das Bild aus. Es enthält deine Werte – teile es nur, wenn du das möchtest.")
                Image(
                    bitmap = bitmap,
                    contentDescription = card.description,
                    modifier = Modifier.fillMaxWidth(),
                )
                BigButton("Teilen", onShare, icon = AppIcons.Share, modifier = Modifier.fillMaxWidth())
                BigButton("Abbrechen", onDismiss, outlined = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
