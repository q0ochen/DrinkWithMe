package com.drinkwithme.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Sicherheitsabfrage vor dem Löschen des Kontos – erklärt, was gelöscht wird und was bleibt. */
@Composable
fun DeleteAccountDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Konto löschen?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dein Konto wird endgültig gelöscht. Das lässt sich nicht rückgängig machen. Gelöscht werden:")
                Text(
                    "• dein Profil (Name und Profilbild)\n" +
                        "• deine Mitgliedschaften in allen Runden\n" +
                        "• alle Getränke, die du geteilt hast – in allen Runden\n" +
                        "• deine Anmeldung bei Drink With Me",
                )
                Text(
                    "Deine Getränke unter „Meine Getränke“ bleiben auf diesem Handy erhalten.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Endgültig löschen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}
