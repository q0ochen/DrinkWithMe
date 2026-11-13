package com.drinkwithme.app.ui.bar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.drinkwithme.app.ui.common.BigButton
import com.drinkwithme.app.ui.common.ConfirmDialog
import com.drinkwithme.app.ui.common.DangerButton
import com.drinkwithme.app.ui.common.DrinkIconImage
import com.drinkwithme.app.ui.common.SectionTitle
import com.drinkwithme.app.ui.common.focusFrame
import com.drinkwithme.core.bar.DrinkCategory
import com.drinkwithme.core.bar.DrinkIcon
import com.drinkwithme.core.bar.displayName

/** Getränk anlegen oder bearbeiten: Name, Icon, Art, Portion, Vol.-%, Zucker, andere Namen für die Spracheingabe. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrinkEditorDialog(
    editor: EditorState,
    onChange: (DrinkForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onDeleteConfirmed: (Boolean) -> Unit,
) {
    val form = editor.form
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle(if (form.isNew) "Eigenes Getränk" else "Getränk bearbeiten")
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { onChange(form.copy(name = it)) },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Icon", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrinkIcon.entries.forEach { icon ->
                        val selected = icon == form.icon
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            tonalElevation = if (selected) 4.dp else 0.dp,
                            modifier = Modifier
                                .focusFrame()
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { onChange(form.copy(icon = icon)) })
                                .semantics { contentDescription = DrinkIconLabels.label(icon) },
                        ) {
                            DrinkIconImage(icon, size = 48.dp, modifier = Modifier.padding(6.dp))
                        }
                    }
                }
                Text("Gewählt: ${DrinkIconLabels.label(form.icon)}", style = MaterialTheme.typography.bodyMedium)

                Text("Art", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrinkCategory.entries.forEach { category ->
                        FilterChip(
                            selected = category == form.category,
                            onClick = { onChange(form.copy(category = category)) },
                            label = { Text(category.displayName) },
                            modifier = Modifier.focusFrame(RoundedCornerShape(8.dp)).heightIn(min = 48.dp),
                        )
                    }
                }

                NumberField("Portion in ml", form.servingText, KeyboardType.Number) { onChange(form.copy(servingText = it)) }
                NumberField("Alkohol in Vol.-%", form.alcoholText, KeyboardType.Decimal) { onChange(form.copy(alcoholText = it)) }
                NumberField("Zucker in g pro 100 ml", form.sugarText, KeyboardType.Decimal) { onChange(form.copy(sugarText = it)) }
                OutlinedTextField(
                    value = form.aliasesText,
                    onValueChange = { onChange(form.copy(aliasesText = it)) },
                    label = { Text("Andere Namen für die Spracheingabe") },
                    supportingText = { Text("Mit Komma getrennt, z. B. „Schorle, Apfelsaftschorle“") },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (editor.errors.isNotEmpty()) {
                    Text(
                        editor.errors.joinToString("\n"),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }

                BigButton("Speichern", onSave, modifier = Modifier.fillMaxWidth())
                BigButton("Abbrechen", onDismiss, outlined = true, modifier = Modifier.fillMaxWidth())
                if (!form.isNew) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        DangerButton("Aus der Bar entfernen", onDelete, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    if (editor.confirmDelete) {
        ConfirmDialog(
            title = "Getränk entfernen?",
            text = "„${form.name}“ wird aus deiner Bar entfernt. Bereits eingetragene Getränke bleiben im Verlauf.",
            confirmLabel = "Entfernen",
            onConfirm = { onDeleteConfirmed(true) },
            onDismiss = { onDeleteConfirmed(false) },
        )
    }
}

@Composable
private fun NumberField(label: String, value: String, keyboardType: KeyboardType, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}
