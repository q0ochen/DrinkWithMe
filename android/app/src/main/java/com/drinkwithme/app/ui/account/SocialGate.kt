package com.drinkwithme.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drinkwithme.core.social.UserProfile

/**
 * Zeigt je nach Zustand den Einrichtungs-Hinweis, die Anmeldung oder den eigentlichen Inhalt.
 *
 * @param account `null`, wenn kein Server eingerichtet ist.
 */
@Composable
fun SocialGate(
    account: AccountUiState?,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (UserProfile) -> Unit,
) {
    val user = account?.user
    when {
        account == null -> ServerNotConfiguredCard(modifier)
        user != null -> Box(modifier = modifier.fillMaxSize()) { content(user) }
        account.isCheckingUser -> LoadingBox(modifier)
        else -> SignInCard(
            isSigningIn = account.isSigningIn,
            errorMessage = account.errorMessage,
            onSignIn = onSignIn,
            modifier = modifier,
        )
    }
}

@Composable
fun SignInCard(
    isSigningIn: Boolean,
    errorMessage: String?,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredCard(modifier) {
        Text("🥂", style = MaterialTheme.typography.displaySmall)
        Text("Trinkrunden", style = MaterialTheme.typography.titleLarge)
        Text(
            "Melde dich mit deinem Google-Konto an, um Trinkrunden mit deinen Freunden zu gründen " +
                "und zu sehen, was sie gerade trinken.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onSignIn, enabled = !isSigningIn) {
            if (isSigningIn) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Mit Google anmelden")
            }
        }
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ServerNotConfiguredCard(modifier: Modifier = Modifier) {
    CenteredCard(modifier) {
        Text("🛠️", style = MaterialTheme.typography.displaySmall)
        Text(
            "Server noch nicht eingerichtet – siehe docs/FIREBASE_EINRICHTUNG.md",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            "Bis dahin funktioniert „Meine Getränke“ wie gewohnt – nur ohne Freunde und Trinkrunden.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}
