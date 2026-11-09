package com.drinkwithme.app.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.drinkwithme.core.social.DrinkGroup
import com.drinkwithme.core.social.InviteCode
import com.drinkwithme.core.social.usecase.DefaultCreateGroupUseCase

@Composable
fun CreateGroupDialog(
    isWorking: Boolean,
    errorMessage: String?,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Runde") },
        text = {
            DialogTextField(
                value = name,
                onValueChange = { name = it },
                label = "Name, z. B. Stammtisch",
                supportingText = "${name.trim().length}/${DefaultCreateGroupUseCase.MAXIMUM_GROUP_NAME_LENGTH} Zeichen",
                errorMessage = errorMessage,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = !isWorking) { Text("Erstellen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

@Composable
fun JoinGroupDialog(
    isWorking: Boolean,
    errorMessage: String?,
    onJoin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Runde beitreten") },
        text = {
            DialogTextField(
                value = code,
                onValueChange = { code = InviteCodeText.sanitizeInput(it) },
                label = "Einladungscode, z. B. ${InviteCodeText.EXAMPLE}",
                supportingText = "Den ${InviteCode.LENGTH}-stelligen Code bekommst du von deinen Freunden.",
                errorMessage = errorMessage,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )
        },
        confirmButton = {
            TextButton(onClick = { onJoin(code) }, enabled = !isWorking) { Text("Beitreten") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

@Composable
fun LeaveGroupDialog(
    group: DrinkGroup,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val lastMemberHint = if (group.members.size <= 1) {
        " Du bist das letzte Mitglied – die Runde wird dann gelöscht."
    } else {
        " Mit dem Code kannst du später wieder beitreten."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Runde verlassen?") },
        text = {
            Text("Möchtest du „${group.name}“ wirklich verlassen? Du siehst dann die Getränke dieser Runde nicht mehr.$lastMemberHint")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Verlassen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

@Composable
private fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String,
    errorMessage: String?,
    keyboardOptions: KeyboardOptions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            supportingText = { Text(supportingText) },
            isError = errorMessage != null,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            modifier = Modifier.fillMaxWidth(),
        )
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
