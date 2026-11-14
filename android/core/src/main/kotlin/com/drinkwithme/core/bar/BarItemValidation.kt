package com.drinkwithme.core.bar

/** Ein Verstoß gegen die Regeln für Bar-Getränke. Die Meldungen werden direkt angezeigt. */
sealed class BarItemError(val message: String) {
    data object EmptyName : BarItemError("Bitte gib einen Namen ein.")

    data class NameTooLong(val maximum: Int) :
        BarItemError("Der Name darf höchstens $maximum Zeichen lang sein.")

    data class ServingOutOfRange(val range: IntRange) :
        BarItemError("Die Portion muss zwischen ${range.first} und ${range.last} ml liegen.")

    data class AlcoholOutOfRange(val maximum: Double) :
        BarItemError("Der Alkoholgehalt muss zwischen 0 und ${germanNumber(maximum)} Vol.-% liegen.")

    data class SugarOutOfRange(val maximum: Double) :
        BarItemError("Der Zuckergehalt muss zwischen 0 und ${germanNumber(maximum)} g pro 100 ml liegen.")

    data object BlankAlias : BarItemError("Ein Alternativname ist leer.")

    data class AliasTooLong(val maximum: Int) :
        BarItemError("Ein Alternativname darf höchstens $maximum Zeichen lang sein.")
}

/** Ungültiges Bar-Getränk; [errors] enthält alle gefundenen Verstöße. */
class InvalidBarItemException(val errors: List<BarItemError>) :
    IllegalArgumentException(errors.joinToString(" ") { it.message })

/** Prüft ein Bar-Getränk. Eigene Regeln werden als weitere Implementierung eingesteckt (Open/Closed). */
fun interface BarItemValidator {
    /** Liefert alle Verstöße; eine leere Liste bedeutet „gültig“. */
    fun validate(item: BarItem): List<BarItemError>
}

/** Wirft [InvalidBarItemException], wenn [item] ungültig ist, sonst gibt es [item] unverändert zurück. */
fun BarItemValidator.requireValid(item: BarItem): BarItem {
    val errors = validate(item)
    if (errors.isNotEmpty()) throw InvalidBarItemException(errors)
    return item
}

/**
 * Regeln aus dem Vertrag: Name 1–40 Zeichen (ohne Rand-Leerraum, gezählt in Unicode-Zeichen wie auf dem
 * Server), Portion 1–2000 ml, 0–80 Vol.-%, 0–100 g Zucker pro 100 ml, Alternativnamen nicht leer.
 */
class DefaultBarItemValidator(
    private val maximumNameLength: Int = BarItem.NAME_MAX_LENGTH,
    private val servingRange: IntRange = BarItem.SERVING_RANGE_MILLILITERS,
    private val maximumAlcoholPercent: Double = BarItem.ALCOHOL_PERCENT_MAX,
    private val maximumSugarPer100Ml: Double = BarItem.SUGAR_GRAMS_PER_100_ML_MAX,
    private val maximumAliasLength: Int = BarItem.ALIAS_MAX_LENGTH,
) : BarItemValidator {

    override fun validate(item: BarItem): List<BarItemError> = buildList {
        val name = item.name.trim()
        when {
            name.isEmpty() -> add(BarItemError.EmptyName)
            name.characterCount() > maximumNameLength -> add(BarItemError.NameTooLong(maximumNameLength))
        }
        if (item.servingMilliliters !in servingRange) add(BarItemError.ServingOutOfRange(servingRange))
        if (!item.alcoholPercent.isWithin(maximumAlcoholPercent)) {
            add(BarItemError.AlcoholOutOfRange(maximumAlcoholPercent))
        }
        if (!item.sugarGramsPer100Ml.isWithin(maximumSugarPer100Ml)) {
            add(BarItemError.SugarOutOfRange(maximumSugarPer100Ml))
        }
        val aliases = item.aliases.map { it.trim() }
        if (aliases.any { it.isEmpty() }) add(BarItemError.BlankAlias)
        if (aliases.any { it.characterCount() > maximumAliasLength }) add(BarItemError.AliasTooLong(maximumAliasLength))
    }

    private fun Double.isWithin(maximum: Double): Boolean = isFinite() && this >= 0.0 && this <= maximum

    private fun String.characterCount(): Int = codePointCount(0, length)
}

/** „80“ statt „80.0“, „12,5“ statt „12.5“. */
private fun germanNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString().replace('.', ',')
