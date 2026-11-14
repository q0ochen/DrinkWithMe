package com.drinkwithme.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Feste Farben statt „Dynamic Color“, damit der Kontrast überall geprüft ≥ 4,5:1 ist (Texte auf Flächen).
// Petrol wie im App-Symbol; bewusst kein Grün für Bezugswerte (siehe ChartColors).
private val LightColors = lightColorScheme(
    primary = Color(0xFF0E5F6C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC9EBF0),
    onPrimaryContainer = Color(0xFF00222A),
    secondary = Color(0xFF4A5C66),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E4EC),
    onSecondaryContainer = Color(0xFF0B1D25),
    tertiary = Color(0xFF6B4A8A),
    onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    background = Color(0xFFF8FAFA),
    onBackground = Color(0xFF181C1D),
    surface = Color(0xFFF8FAFA),
    onSurface = Color(0xFF181C1D),
    surfaceVariant = Color(0xFFDDE4E6),
    onSurfaceVariant = Color(0xFF3A4446),
    outline = Color(0xFF5F6B6E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF86D3DF),
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF0B4F5A),
    onPrimaryContainer = Color(0xFFC9EBF0),
    secondary = Color(0xFFB8C8D1),
    onSecondary = Color(0xFF22323A),
    secondaryContainer = Color(0xFF394851),
    onSecondaryContainer = Color(0xFFD6E4EC),
    tertiary = Color(0xFFD7BBF5),
    onTertiary = Color(0xFF3B1F57),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    background = Color(0xFF101414),
    onBackground = Color(0xFFE0E3E3),
    surface = Color(0xFF101414),
    onSurface = Color(0xFFE0E3E3),
    surfaceVariant = Color(0xFF3F484A),
    onSurfaceVariant = Color(0xFFC6CFD1),
    outline = Color(0xFF8F9A9C),
)

/** Etwas größere Grundschrift; alles in sp, damit die Systemschriftgröße (bis 200 %) wirkt. */
private val AppTypography = Typography().let { base ->
    base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Der große Satz auf dem Ereignis-Bildschirm (Vertrag: mindestens 28 sp). */
val EventHeadlineStyle = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold)

@Composable
fun DrinkWithMeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

/** Farben der Diagramme (hell/dunkel), ohne Grün; Linien unterscheiden sich zusätzlich in der Strichart. */
data class ChartColors(
    val sugarBar: Color,
    val alcoholBar: Color,
    val referenceLine: Color,
    val axis: Color,
    val bandLow: Color,
    val bandModerate: Color,
    val bandHigh: Color,
    val label: Color,
) {
    companion object {
        val Light = ChartColors(
            sugarBar = Color(0xFF12707F),
            alcoholBar = Color(0xFF5E3C99),
            referenceLine = Color(0xFF263238),
            axis = Color(0xFF3A4446),
            bandLow = Color(0xFFE3E8EB),
            bandModerate = Color(0xFFFFE0B2),
            bandHigh = Color(0xFFFFCDD2),
            label = Color(0xFF181C1D),
        )
        val Dark = ChartColors(
            sugarBar = Color(0xFF86D3DF),
            alcoholBar = Color(0xFFCDB5F2),
            referenceLine = Color(0xFFE0E3E3),
            axis = Color(0xFFC6CFD1),
            bandLow = Color(0xFF2B3336),
            bandModerate = Color(0xFF4E3B1E),
            bandHigh = Color(0xFF552A2D),
            label = Color(0xFFE0E3E3),
        )
    }
}
