package com.drinkwithme.app.data

import com.drinkwithme.core.domain.Drink
import com.drinkwithme.core.domain.DrinkCategory
import com.drinkwithme.core.domain.DrinkRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Speichert Getränke dauerhaft als JSON-Datei im App-Speicher. */
class JsonFileDrinkRepository(private val file: File) : DrinkRepository {
    private val lock = Any()

    override fun allDrinks(): List<Drink> = synchronized(lock) { load() }

    override fun save(drink: Drink) = synchronized(lock) {
        persist(load().filterNot { it.id == drink.id } + drink)
    }

    override fun delete(id: String) = synchronized(lock) {
        persist(load().filterNot { it.id == id })
    }

    private fun load(): List<Drink> {
        if (!file.exists()) return emptyList()
        val text = file.readText()
        if (text.isBlank()) return emptyList()
        val array = JSONArray(text)
        return (0 until array.length()).map { array.getJSONObject(it).toDrink() }
    }

    private fun persist(drinks: List<Drink>) {
        file.parentFile?.mkdirs()
        val array = JSONArray().apply { drinks.forEach { put(it.toJson()) } }
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(array.toString())
        if (!temp.renameTo(file)) {
            file.writeText(array.toString())
            temp.delete()
        }
    }

    private fun Drink.toJson() = JSONObject()
        .put(KEY_ID, id)
        .put(KEY_NAME, name)
        .put(KEY_CATEGORY, category.name)
        .put(KEY_VOLUME, volumeInMilliliters)
        .put(KEY_NOTE, note ?: JSONObject.NULL)
        .put(KEY_CONSUMED_AT, consumedAt.toString())

    private fun JSONObject.toDrink() = Drink(
        id = getString(KEY_ID),
        name = getString(KEY_NAME),
        category = runCatching { DrinkCategory.valueOf(getString(KEY_CATEGORY)) }.getOrDefault(DrinkCategory.OTHER),
        volumeInMilliliters = getInt(KEY_VOLUME),
        note = if (isNull(KEY_NOTE)) null else getString(KEY_NOTE),
        consumedAt = Instant.parse(getString(KEY_CONSUMED_AT)),
    )

    private companion object {
        const val KEY_ID = "id"
        const val KEY_NAME = "name"
        const val KEY_CATEGORY = "category"
        const val KEY_VOLUME = "volumeInMilliliters"
        const val KEY_NOTE = "note"
        const val KEY_CONSUMED_AT = "consumedAt"
    }
}
