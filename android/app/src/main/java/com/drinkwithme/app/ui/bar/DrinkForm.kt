package com.drinkwithme.app.ui.bar

import com.drinkwithme.core.IdGenerator
import com.drinkwithme.core.bar.BarItem
import com.drinkwithme.core.bar.BarItemValidator
import com.drinkwithme.core.bar.DefaultBarItemValidator
import com.drinkwithme.core.bar.DrinkCategory
import com.drinkwithme.core.bar.DrinkIcon
import com.drinkwithme.core.bar.ValueSource
import com.drinkwithme.core.bar.normalized

/** Eingaben im Formular „Getränk bearbeiten“ (Zahlen als Text, damit Zwischenstände wie „4,“ möglich sind). */
data class DrinkForm(
    val original: BarItem?,
    val name: String,
    val icon: DrinkIcon,
    val category: DrinkCategory,
    val servingText: String,
    val alcoholText: String,
    val sugarText: String,
    val aliasesText: String,
    val sortOrder: Int,
) {
    val isNew: Boolean get() = original == null
}

/** Ergebnis der Prüfung des Formulars. */
sealed interface FormResult {
    data class Valid(val item: BarItem) : FormResult
    data class Invalid(val errors: List<String>) : FormResult
}

/** Formular ↔ [BarItem]: deutsche Zahlen („4,9“), Aliase mit Komma getrennt, Prüfung mit dem Kern-Validator. */
class DrinkFormMapper(
    private val idGenerator: IdGenerator,
    private val validator: BarItemValidator = DefaultBarItemValidator(),
) {
    fun fromItem(item: BarItem): DrinkForm = DrinkForm(
        original = item,
        name = item.name,
        icon = item.icon,
        category = item.category,
        servingText = item.servingMilliliters.toString(),
        alcoholText = germanNumber(item.alcoholPercent),
        sugarText = germanNumber(item.sugarGramsPer100Ml),
        aliasesText = item.aliases.joinToString(", "),
        sortOrder = item.sortOrder,
    )

    fun newForm(sortOrder: Int): DrinkForm = DrinkForm(
        original = null,
        name = "",
        icon = DrinkIcon.OTHER,
        category = DrinkCategory.OTHER,
        servingText = "250",
        alcoholText = "0",
        sugarText = "0",
        aliasesText = "",
        sortOrder = sortOrder,
    )

    fun toItem(form: DrinkForm): FormResult {
        val errors = mutableListOf<String>()
        val serving = form.servingText.trim().toIntOrNull()
        if (serving == null) errors += "Bitte gib die Portion als ganze Zahl in ml ein."
        val alcohol = parseNumber(form.alcoholText)
        if (alcohol == null) errors += "Bitte gib den Alkoholgehalt als Zahl ein (z. B. 4,9)."
        val sugar = parseNumber(form.sugarText)
        if (sugar == null) errors += "Bitte gib den Zuckergehalt als Zahl ein (z. B. 10,6)."
        if (serving == null || alcohol == null || sugar == null) return FormResult.Invalid(errors)

        val original = form.original
        val valuesUnchanged = original != null && original.servingMilliliters == serving &&
            original.alcoholPercent == alcohol && original.sugarGramsPer100Ml == sugar
        val item = BarItem(
            id = original?.id ?: idGenerator.newId(),
            name = form.name,
            icon = form.icon,
            category = form.category,
            servingMilliliters = serving,
            alcoholPercent = alcohol,
            sugarGramsPer100Ml = sugar,
            aliases = splitAliases(form.aliasesText),
            valueSource = if (valuesUnchanged) original.valueSource else ValueSource.USER,
            sortOrder = form.sortOrder,
        ).normalized()
        val invalid = validator.validate(item)
        return if (invalid.isEmpty()) FormResult.Valid(item) else FormResult.Invalid(invalid.map { it.message })
    }

    companion object {
        /** „4,9“, „4.9“ und „ 5 “ → Zahl; sonst `null`. */
        fun parseNumber(text: String): Double? {
            val clean = text.trim().replace(',', '.')
            if (clean.isEmpty() || clean.count { it == '.' } > 1) return null
            return clean.toDoubleOrNull()?.takeIf { it.isFinite() }
        }

        /** „12,5“ statt „12.5“, „5“ statt „5.0“. */
        fun germanNumber(value: Double): String =
            if (value % 1.0 == 0.0) value.toLong().toString() else value.toString().replace('.', ',')

        fun splitAliases(text: String): List<String> =
            text.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    }
}

/** Deutsche Namen der Icons für die Auswahl (und als Beschreibung für Screenreader). */
object DrinkIconLabels {
    fun label(icon: DrinkIcon): String = when (icon) {
        DrinkIcon.WATER -> "Wasser"
        DrinkIcon.SPARKLING_WATER -> "Mineralwasser"
        DrinkIcon.COFFEE -> "Kaffee"
        DrinkIcon.TEA -> "Tee"
        DrinkIcon.JUICE -> "Saft"
        DrinkIcon.APPLE_SPRITZER -> "Schorle"
        DrinkIcon.COLA -> "Cola"
        DrinkIcon.LEMONADE -> "Limonade"
        DrinkIcon.ICED_TEA -> "Eistee"
        DrinkIcon.ENERGY_DRINK -> "Energydrink"
        DrinkIcon.MILK -> "Milch"
        DrinkIcon.MALT_BEER -> "Malzbier"
        DrinkIcon.BEER -> "Bier"
        DrinkIcon.WHEAT_BEER -> "Weizenbier"
        DrinkIcon.RADLER -> "Radler"
        DrinkIcon.RED_WINE -> "Rotwein"
        DrinkIcon.WHITE_WINE -> "Weißwein"
        DrinkIcon.SPARKLING_WINE -> "Sekt"
        DrinkIcon.SHOT -> "Schnaps"
        DrinkIcon.LONGDRINK -> "Longdrink"
        DrinkIcon.COCKTAIL -> "Cocktail"
        DrinkIcon.OTHER -> "Sonstiges"
    }
}
