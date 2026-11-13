package com.drinkwithme.app.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.drinkwithme.app.stats.ChartScale
import com.drinkwithme.app.stats.GermanFormat
import com.drinkwithme.app.stats.HistoryTexts
import com.drinkwithme.core.health.AlcoholRiskBand
import com.drinkwithme.core.health.HealthReferences
import com.drinkwithme.core.health.ReferenceLineStyle

/**
 * Zeichnet die [StatisticsCard] als PNG-taugliches Bild (1080 × 1350, hell, hoher Kontrast). Bewusst mit
 * `android.graphics` statt Compose, damit das Bild unabhängig vom Bildschirm (Größe, Dunkelmodus) gleich aussieht.
 * Bänder nicht grün; Linien unterscheiden sich in der Strichart, nicht nur in der Farbe.
 */
class StatisticsImageRenderer {

    fun render(card: StatisticsCard): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)
        var y = MARGIN + 40f
        canvas.drawText(card.title, MARGIN, y, paint(60f, TEXT, bold = true))
        y += 56f
        canvas.drawText(card.dateLine, MARGIN, y, paint(34f, MUTED))
        y += 70f
        val figurePaint = paint(40f, TEXT)
        card.keyFigures.forEach { line ->
            canvas.drawText(line, MARGIN, y, figurePaint)
            y += 56f
        }
        y += 24f
        y = drawSugarChart(canvas, card, y)
        y += 40f
        y = drawAlcoholChart(canvas, card, y)
        y += 36f
        val notePaint = paint(28f, MUTED)
        card.notes.forEach { note ->
            wrap(note, notePaint, WIDTH - 2 * MARGIN).forEach { line ->
                canvas.drawText(line, MARGIN, y, notePaint)
                y += 36f
            }
        }
        canvas.drawText(card.footer, MARGIN, HEIGHT - MARGIN, paint(28f, PRIMARY, bold = true))
        return bitmap
    }

    private fun drawSugarChart(canvas: Canvas, card: StatisticsCard, top: Float): Float {
        canvas.drawText(HistoryTexts.SUGAR_CHART_TITLE, MARGIN, top, paint(36f, TEXT, bold = true))
        val area = RectF(MARGIN + AXIS_LABEL_WIDTH, top + 24f, WIDTH - MARGIN, top + 24f + CHART_HEIGHT)
        val references = HealthReferences.dailySugarLines
        val upper = ChartScale.upperBound(card.sugarDays.map { it.grams }, references.map { it.grams })
        drawBars(canvas, area, card.sugarDays.map { it.grams }, upper, SUGAR_BAR)
        references.forEach { line ->
            val y = area.bottom - ChartScale.fraction(line.grams, upper) * area.height()
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = REFERENCE
                strokeWidth = 4f
                style = Paint.Style.STROKE
                if (line.style == ReferenceLineStyle.DASHED) pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
            }
            canvas.drawLine(area.left, y, area.right, y, linePaint)
            canvas.drawText(GermanFormat.grams(line.grams), MARGIN, y + 10f, paint(26f, TEXT))
        }
        val first = card.sugarDays.firstOrNull()?.date
        val last = card.sugarDays.lastOrNull()?.date
        if (first != null && last != null) {
            val labelPaint = paint(26f, MUTED)
            canvas.drawText(GermanFormat.dayMonth(first), area.left, area.bottom + 34f, labelPaint)
            val lastLabel = GermanFormat.dayMonth(last)
            canvas.drawText(lastLabel, area.right - labelPaint.measureText(lastLabel), area.bottom + 34f, labelPaint)
        }
        return area.bottom + 50f
    }

    private fun drawAlcoholChart(canvas: Canvas, card: StatisticsCard, top: Float): Float {
        canvas.drawText(HistoryTexts.ALCOHOL_CHART_TITLE, MARGIN, top, paint(36f, TEXT, bold = true))
        val area = RectF(MARGIN + AXIS_LABEL_WIDTH, top + 24f, WIDTH - MARGIN, top + 24f + CHART_HEIGHT)
        val values = card.alcoholWeeks.map { it.grams }
        val upper = ChartScale.upperBound(values, listOf(HealthReferences.WEEKLY_ALCOHOL_HIGH_ABOVE_GRAMS))
        if (card.showAlcoholBands) {
            HealthReferences.weeklyAlcoholBands.forEach { band ->
                val bandTop = area.bottom - ChartScale.fraction(band.toGrams ?: upper, upper) * area.height()
                val bandBottom = area.bottom - ChartScale.fraction(band.fromGrams, upper) * area.height()
                canvas.drawRect(area.left, bandTop, area.right, bandBottom, fill(bandColor(band.band)))
                canvas.drawText(band.band.label, area.right - 300f, bandBottom - 12f, paint(24f, TEXT))
            }
        }
        drawBars(canvas, area, values, upper, ALCOHOL_BAR)
        val labelPaint = paint(26f, TEXT)
        listOf(HealthReferences.WEEKLY_ALCOHOL_MODERATE_FROM_GRAMS, HealthReferences.WEEKLY_ALCOHOL_HIGH_ABOVE_GRAMS).forEach {
            val y = area.bottom - ChartScale.fraction(it, upper) * area.height()
            canvas.drawText(GermanFormat.grams(it), MARGIN, y + 10f, labelPaint)
        }
        return area.bottom + 20f
    }

    private fun drawBars(canvas: Canvas, area: RectF, values: List<Double>, upper: Double, barColor: Int) {
        if (values.isEmpty()) return
        val slot = area.width() / values.size
        val barPaint = fill(barColor)
        values.forEachIndexed { index, value ->
            val height = ChartScale.fraction(value, upper) * area.height()
            val left = area.left + index * slot + slot * 0.15f
            val right = area.left + (index + 1) * slot - slot * 0.15f
            canvas.drawRect(left, area.bottom - height, right, area.bottom, barPaint)
        }
        canvas.drawLine(area.left, area.bottom, area.right, area.bottom, Paint().apply { color = TEXT; strokeWidth = 3f })
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        text.split(' ').forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                current = candidate
            } else {
                lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun bandColor(band: AlcoholRiskBand): Int = when (band) {
        AlcoholRiskBand.LOW_RISK -> BAND_LOW
        AlcoholRiskBand.MODERATE_RISK -> BAND_MODERATE
        AlcoholRiskBand.HIGH_RISK -> BAND_HIGH
    }

    companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1350
        private const val MARGIN = 64f
        private const val AXIS_LABEL_WIDTH = 96f
        private const val CHART_HEIGHT = 300f

        // Farben als ARGB; Texte erreichen auf Weiß mindestens 4,5:1.
        private const val BACKGROUND = 0xFFFFFFFF.toInt()
        private const val TEXT = 0xFF1B1F1F.toInt()
        private const val MUTED = 0xFF455A64.toInt()
        private const val PRIMARY = 0xFF0E5F6C.toInt()
        private const val SUGAR_BAR = 0xFF12707F.toInt()
        private const val ALCOHOL_BAR = 0xFF5E3C99.toInt()
        private const val REFERENCE = 0xFF263238.toInt()
        private const val BAND_LOW = 0xFFECEFF1.toInt()
        private const val BAND_MODERATE = 0xFFFFE0B2.toInt()
        private const val BAND_HIGH = 0xFFFFCDD2.toInt()
    }
}
