package com.drinkwithme.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drinkwithme.app.R
import com.drinkwithme.core.bar.DrinkIcon

/** Mindestgröße für Bedienelemente (Vertrag: Ziele ≥ 48 dp). */
val MinTouchTarget: Dp = 48.dp

/**
 * Deutlich sichtbarer Rahmen, wenn das folgende Element den Fokus hat (D-Pad/Tastatur, z. B. auf dem Fernseher).
 * Muss **vor** `clickable`/`focusable` in der Modifier-Kette stehen.
 */
fun Modifier.focusFrame(shape: Shape = RoundedCornerShape(12.dp)): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .then(if (focused) Modifier.border(3.dp, color, shape) else Modifier)
}

/** Großer, bildschirmbreiter Knopf mit optionalem Symbol. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    minHeight: Dp = 56.dp,
    enabled: Boolean = true,
    outlined: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
    val buttonModifier = modifier.focusFrame(RoundedCornerShape(28.dp)).heightIn(min = minHeight)
    if (outlined) {
        OutlinedButton(onClick = onClick, modifier = buttonModifier, enabled = enabled) { content() }
    } else {
        Button(onClick = onClick, modifier = buttonModifier, enabled = enabled) { content() }
    }
}

/** Kleiner Textknopf mit Fokusrahmen und ≥ 48 dp Höhe. */
@Composable
fun SmallTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.focusFrame(RoundedCornerShape(20.dp)).heightIn(min = MinTouchTarget),
    ) { Text(text) }
}

/** Überschrift eines Bereichs (für Screenreader als Überschrift markiert). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(vertical = 8.dp).semantics { heading() },
    )
}

/** Karte mit Überschrift. */
@Composable
fun SectionCard(title: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            content()
        }
    }
}

/** Rückfrage mit zwei Knöpfen. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String = "Abbrechen",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { SmallTextButton(confirmLabel, onConfirm) },
        dismissButton = { SmallTextButton(dismissLabel, onDismiss) },
    )
}

/** Hinweis mit Symbol in einer Zeile. */
@Composable
fun InfoRow(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Ein selbst gezeichnetes Getränke-Icon (`R.drawable.drink_<key>`). */
@Composable
fun DrinkIconImage(icon: DrinkIcon, modifier: Modifier = Modifier, size: Dp = 48.dp, contentDescription: String? = null) {
    Image(
        painter = painterResource(DrinkIconResources.drawable(icon)),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}

/** Zuordnung Icon-Schlüssel → Drawable (ohne Reflection, damit R8/Lint jedes Icon sehen). */
object DrinkIconResources {
    fun drawable(icon: DrinkIcon): Int = when (icon) {
        DrinkIcon.WATER -> R.drawable.drink_water
        DrinkIcon.SPARKLING_WATER -> R.drawable.drink_sparkling_water
        DrinkIcon.COFFEE -> R.drawable.drink_coffee
        DrinkIcon.TEA -> R.drawable.drink_tea
        DrinkIcon.JUICE -> R.drawable.drink_juice
        DrinkIcon.APPLE_SPRITZER -> R.drawable.drink_apple_spritzer
        DrinkIcon.COLA -> R.drawable.drink_cola
        DrinkIcon.LEMONADE -> R.drawable.drink_lemonade
        DrinkIcon.ICED_TEA -> R.drawable.drink_iced_tea
        DrinkIcon.ENERGY_DRINK -> R.drawable.drink_energy_drink
        DrinkIcon.MILK -> R.drawable.drink_milk
        DrinkIcon.MALT_BEER -> R.drawable.drink_malt_beer
        DrinkIcon.BEER -> R.drawable.drink_beer
        DrinkIcon.WHEAT_BEER -> R.drawable.drink_wheat_beer
        DrinkIcon.RADLER -> R.drawable.drink_radler
        DrinkIcon.RED_WINE -> R.drawable.drink_red_wine
        DrinkIcon.WHITE_WINE -> R.drawable.drink_white_wine
        DrinkIcon.SPARKLING_WINE -> R.drawable.drink_sparkling_wine
        DrinkIcon.SHOT -> R.drawable.drink_shot
        DrinkIcon.LONGDRINK -> R.drawable.drink_longdrink
        DrinkIcon.COCKTAIL -> R.drawable.drink_cocktail
        DrinkIcon.OTHER -> R.drawable.drink_other
    }
}

/** Fehlerfarbe-freundlicher Knopf für gefährliche Aktionen (Konto löschen). */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        modifier = modifier.focusFrame(RoundedCornerShape(28.dp)).heightIn(min = 56.dp),
    ) { Text(text) }
}
