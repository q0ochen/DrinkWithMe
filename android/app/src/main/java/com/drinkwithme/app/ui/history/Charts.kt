package com.drinkwithme.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drinkwithme.app.stats.ChartScale
import com.drinkwithme.app.stats.GermanFormat
import com.drinkwithme.app.stats.HistoryTexts
import com.drinkwithme.app.ui.theme.ChartColors
import com.drinkwithme.core.health.AlcoholRiskBand
import com.drinkwithme.core.health.HealthReferences
import com.drinkwithme.core.health.ReferenceLineStyle
import com.drinkwithme.core.stats.DailySugar
import com.drinkwithme.core.stats.WeeklyAlcohol

@Composable
private fun chartColors(): ChartColors = if (isSystemInDarkTheme()) ChartColors.Dark else ChartColors.Light

/**
 * Zucker pro Tag als Balken mit Bezugslinien: 50 g durchgezogen, 25 g gestrichelt (Strichart + Beschriftung,
 * nicht nur Farbe). Für Screenreader gibt es die Beschreibung als Text.
 */
@Composable
fun SugarChart(days: List<DailySugar>, modifier: Modifier = Modifier) {
    val colors = chartColors()
    val measurer = rememberTextMeasurer()
    val description = HistoryTexts.sugarChartDescription(days)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(220.dp)
            .semantics { contentDescription = description },
    ) {
        val references = HealthReferences.dailySugarLines
        val upper = ChartScale.upperBound(days.map { it.grams }, references.map { it.grams })
        val area = chartArea()
        drawBars(days.map { it.grams }, upper, area, colors.sugarBar, colors.axis)
        references.forEach { line ->
            val y = area.bottom - ChartScale.fraction(line.grams, upper) * area.height
            drawLine(
                color = colors.referenceLine,
                start = Offset(area.left, y),
                end = Offset(area.right, y),
                strokeWidth = 2.dp.toPx(),
                pathEffect = if (line.style == ReferenceLineStyle.DASHED) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null,
            )
            label(measurer, GermanFormat.grams(line.grams), Offset(0f, y - 9.sp.toPx()), colors)
        }
        if (days.isNotEmpty()) {
            label(measurer, GermanFormat.dayMonth(days.first().date), Offset(area.left, area.bottom + 4.dp.toPx()), colors)
            val last = GermanFormat.dayMonth(days.last().date)
            val width = measurer.measure(last, labelStyle(colors)).size.width
            label(measurer, last, Offset(area.right - width, area.bottom + 4.dp.toPx()), colors)
        }
    }
}

/**
 * Alkohol pro Woche (Mo–So) als Balken; dahinter optional die DGE-Bänder (ausblendbar, nicht grün, beschriftet).
 */
@Composable
fun AlcoholChart(weeks: List<WeeklyAlcohol>, showBands: Boolean, modifier: Modifier = Modifier) {
    val colors = chartColors()
    val measurer = rememberTextMeasurer()
    val description = HistoryTexts.alcoholChartDescription(weeks, showBands)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(220.dp)
            .semantics { contentDescription = description },
    ) {
        val values = weeks.map { it.grams }
        val upper = ChartScale.upperBound(values, listOf(HealthReferences.WEEKLY_ALCOHOL_HIGH_ABOVE_GRAMS))
        val area = chartArea()
        if (showBands) {
            HealthReferences.weeklyAlcoholBands.forEach { band ->
                val top = area.bottom - ChartScale.fraction(band.toGrams ?: upper, upper) * area.height
                val bottom = area.bottom - ChartScale.fraction(band.fromGrams, upper) * area.height
                drawRect(
                    color = when (band.band) {
                        AlcoholRiskBand.LOW_RISK -> colors.bandLow
                        AlcoholRiskBand.MODERATE_RISK -> colors.bandModerate
                        AlcoholRiskBand.HIGH_RISK -> colors.bandHigh
                    },
                    topLeft = Offset(area.left, top),
                    size = Size(area.width, bottom - top),
                )
                val text = band.band.label
                val width = measurer.measure(text, labelStyle(colors)).size.width
                label(measurer, text, Offset(area.right - width - 4.dp.toPx(), bottom - 18.sp.toPx()), colors)
            }
        }
        drawBars(values, upper, area, colors.alcoholBar, colors.axis)
        listOf(HealthReferences.WEEKLY_ALCOHOL_MODERATE_FROM_GRAMS, HealthReferences.WEEKLY_ALCOHOL_HIGH_ABOVE_GRAMS).forEach {
            val y = area.bottom - ChartScale.fraction(it, upper) * area.height
            label(measurer, GermanFormat.grams(it), Offset(0f, y - 9.sp.toPx()), colors)
        }
        if (weeks.isNotEmpty()) {
            label(measurer, HistoryTexts.weekLabel(weeks.first()), Offset(area.left, area.bottom + 4.dp.toPx()), colors)
            val last = HistoryTexts.weekLabel(weeks.last())
            val width = measurer.measure(last, labelStyle(colors)).size.width
            label(measurer, last, Offset(area.right - width, area.bottom + 4.dp.toPx()), colors)
        }
    }
}

/** Zeichenfläche: links Platz für Achsenwerte, unten für Datumsangaben. */
private data class ChartArea(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

private fun DrawScope.chartArea() = ChartArea(
    left = 44.dp.toPx(),
    top = 8.dp.toPx(),
    right = size.width,
    bottom = size.height - 26.dp.toPx(),
)

private fun DrawScope.drawBars(values: List<Double>, upper: Double, area: ChartArea, barColor: androidx.compose.ui.graphics.Color, axis: androidx.compose.ui.graphics.Color) {
    if (values.isNotEmpty()) {
        val slot = area.width / values.size
        values.forEachIndexed { index, value ->
            val height = ChartScale.fraction(value, upper) * area.height
            drawRect(
                color = barColor,
                topLeft = Offset(area.left + index * slot + slot * 0.18f, area.bottom - height),
                size = Size(slot * 0.64f, height),
            )
        }
    }
    drawLine(axis, Offset(area.left, area.bottom), Offset(area.right, area.bottom), strokeWidth = 2.dp.toPx())
}

private fun labelStyle(colors: ChartColors) = TextStyle(color = colors.label, fontSize = 12.sp)

private fun DrawScope.label(measurer: TextMeasurer, text: String, topLeft: Offset, colors: ChartColors) {
    drawText(measurer, text, topLeft = topLeft, style = labelStyle(colors))
}
