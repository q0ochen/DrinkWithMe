package com.drinkwithme.core.sharing

/** Wandelt Milliliter in gut lesbaren Text um („250 ml“, „1,5 l“). */
object VolumeFormatter {
    fun format(milliliters: Int): String {
        if (milliliters < 1_000) return "$milliliters ml"
        val tenthsOfLiter = (milliliters + 50) / 100
        val whole = tenthsOfLiter / 10
        val fraction = tenthsOfLiter % 10
        return if (fraction == 0) "$whole l" else "$whole,$fraction l"
    }
}
