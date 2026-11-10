package com.drinkwithme.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.drinkwithme.app.ui.common.AppIcons
import com.drinkwithme.app.ui.common.focusFrame

/** Die vier Bereiche (Vertrag, Abschnitt 3). Gesundheit (Verlauf) ist ein Hauptbereich, nicht versteckt. */
enum class AppSection(val label: String) {
    BAR("Bar"),
    HISTORY("Verlauf"),
    FRIENDS("Freunde"),
    SETTINGS("Einstellungen"),
}

/** Symbol eines Bereichs (getrennt vom Enum, damit Logik-Tests keine Grafik laden). */
val AppSection.icon: ImageVector
    get() = when (this) {
        AppSection.BAR -> AppIcons.Bar
        AppSection.HISTORY -> AppIcons.History
        AppSection.FRIENDS -> AppIcons.Friends
        AppSection.SETTINGS -> AppIcons.Settings
    }

/** Ab dieser Breite (Tablet, TV) Seitenleiste statt unterer Navigation und zweispaltige Inhalte. */
val WideLayoutMinWidth = 600.dp

/**
 * Untere Navigation auf Handys, Seitenleiste (NavigationRail) ab 600 dp – auch auf dem Fernseher, wo sie per
 * D-Pad erreichbar bleibt. [content] erhält, ob das breite Layout aktiv ist.
 */
@Composable
fun AdaptiveScaffold(
    section: AppSection,
    onSectionSelected: (AppSection) -> Unit,
    snackbarHostState: SnackbarHostState,
    content: @Composable (wide: Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WideLayoutMinWidth
        if (wide) {
            Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    NavigationRail(Modifier.fillMaxHeight()) {
                        AppSection.entries.forEach { item ->
                            NavigationRailItem(
                                selected = item == section,
                                onClick = { onSectionSelected(item) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(item.label) },
                                modifier = Modifier.focusFrame(RoundedCornerShape(16.dp)).padding(vertical = 4.dp),
                            )
                        }
                    }
                    Box(Modifier.fillMaxSize()) { content(true) }
                }
            }
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    NavigationBar {
                        AppSection.entries.forEach { item ->
                            NavigationBarItem(
                                selected = item == section,
                                onClick = { onSectionSelected(item) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(item.label, maxLines = 1) },
                                modifier = Modifier.focusFrame(RoundedCornerShape(16.dp)),
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) { content(false) }
            }
        }
    }
}

/** Inhalt ohne Navigation (erster Start), mit Abstand zu den Systemleisten. */
@Composable
fun PlainScreen(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().systemBarsPadding()) { content() }
}
