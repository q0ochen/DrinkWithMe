package com.drinkwithme.core.domain

/** Lesender Zugriff (Interface Segregation). */
interface DrinkReader {
    fun allDrinks(): List<Drink>
}

/** Schreibender Zugriff (Interface Segregation). */
interface DrinkWriter {
    fun save(drink: Drink)
    fun delete(id: String)
}

/** Vollständiges Repository – Implementierungen sind austauschbar (Liskov / Dependency Inversion). */
interface DrinkRepository : DrinkReader, DrinkWriter
