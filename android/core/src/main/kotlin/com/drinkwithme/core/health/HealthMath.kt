package com.drinkwithme.core.health

import com.drinkwithme.core.bar.BarItem
import com.drinkwithme.core.log.DrinkEntry

/** Zuckerstufe eines Getränks (orientiert an den Stufen der britischen Zuckerabgabe). */
enum class SugarLevel {
    /** Unter 5 g/100 ml. */
    LOW,

    /** Ab 5 g/100 ml. */
    SUGARY,

    /** Ab 8 g/100 ml. */
    VERY_SUGARY,
}

/**
 * Gesundheitsrechnung. Gespeichert werden nur ml, Vol.-% und g/100 ml; Gramm werden erst beim Anzeigen berechnet.
 */
object HealthMath {
    /** Dichte von Ethanol in g/ml. */
    const val ETHANOL_GRAMS_PER_MILLILITER = 0.8

    /** Ab diesem Alkoholgehalt (Vol.-%) gilt ein Getränk als alkoholisch. */
    const val ALCOHOLIC_MIN_PERCENT = 1.2

    /** Ab diesem Zuckergehalt (g/100 ml) gilt ein Getränk als zuckerhaltig. */
    const val SUGARY_MIN_GRAMS_PER_100_ML = 5.0

    /** Ab diesem Zuckergehalt (g/100 ml) gilt ein Getränk als stark zuckerhaltig. */
    const val VERY_SUGARY_MIN_GRAMS_PER_100_ML = 8.0

    /** `alcoholGrams = ml × Vol.-% / 100 × 0,8` */
    fun alcoholGrams(volumeMilliliters: Int, alcoholPercent: Double): Double =
        volumeMilliliters * alcoholPercent / 100.0 * ETHANOL_GRAMS_PER_MILLILITER

    /** `sugarGrams = ml × Zucker / 100` */
    fun sugarGrams(volumeMilliliters: Int, sugarGramsPer100Ml: Double): Double =
        volumeMilliliters * sugarGramsPer100Ml / 100.0

    fun isAlcoholic(alcoholPercent: Double): Boolean = alcoholPercent >= ALCOHOLIC_MIN_PERCENT

    fun isSugary(sugarGramsPer100Ml: Double): Boolean = sugarGramsPer100Ml >= SUGARY_MIN_GRAMS_PER_100_ML

    fun isVerySugary(sugarGramsPer100Ml: Double): Boolean = sugarGramsPer100Ml >= VERY_SUGARY_MIN_GRAMS_PER_100_ML

    fun sugarLevel(sugarGramsPer100Ml: Double): SugarLevel = when {
        isVerySugary(sugarGramsPer100Ml) -> SugarLevel.VERY_SUGARY
        isSugary(sugarGramsPer100Ml) -> SugarLevel.SUGARY
        else -> SugarLevel.LOW
    }
}

/** Gramm Alkohol dieses Eintrags (auch bei alkoholfreiem Bier mit 0,4 % vollständig). */
val DrinkEntry.alcoholGrams: Double get() = HealthMath.alcoholGrams(volumeMilliliters, alcoholPercent)

/** Gramm Zucker dieses Eintrags. */
val DrinkEntry.sugarGrams: Double get() = HealthMath.sugarGrams(volumeMilliliters, sugarGramsPer100Ml)

val DrinkEntry.isAlcoholic: Boolean get() = HealthMath.isAlcoholic(alcoholPercent)
val DrinkEntry.isSugary: Boolean get() = HealthMath.isSugary(sugarGramsPer100Ml)
val DrinkEntry.isVerySugary: Boolean get() = HealthMath.isVerySugary(sugarGramsPer100Ml)
val DrinkEntry.sugarLevel: SugarLevel get() = HealthMath.sugarLevel(sugarGramsPer100Ml)

/** Gramm Alkohol einer Standardportion. */
val BarItem.alcoholGramsPerServing: Double get() = HealthMath.alcoholGrams(servingMilliliters, alcoholPercent)

/** Gramm Zucker einer Standardportion. */
val BarItem.sugarGramsPerServing: Double get() = HealthMath.sugarGrams(servingMilliliters, sugarGramsPer100Ml)

val BarItem.isAlcoholic: Boolean get() = HealthMath.isAlcoholic(alcoholPercent)
val BarItem.isSugary: Boolean get() = HealthMath.isSugary(sugarGramsPer100Ml)
val BarItem.isVerySugary: Boolean get() = HealthMath.isVerySugary(sugarGramsPer100Ml)
val BarItem.sugarLevel: SugarLevel get() = HealthMath.sugarLevel(sugarGramsPer100Ml)
