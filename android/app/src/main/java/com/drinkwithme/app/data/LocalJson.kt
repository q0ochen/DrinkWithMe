package com.drinkwithme.app.data

import com.drinkwithme.core.bar.BarItem
import com.drinkwithme.core.bar.DrinkCategory
import com.drinkwithme.core.bar.DrinkIcon
import com.drinkwithme.core.bar.ValueSource
import com.drinkwithme.core.log.DrinkEntry
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Ergebnis beim Lesen einer lokalen Datei. */
sealed interface DecodeResult<out T> {
    /** Gelesen; einzelne unbrauchbare Datensätze wurden übersprungen ([skipped]). */
    data class Decoded<T>(val items: List<T>, val skipped: Int) : DecodeResult<T>

    /** Die Datei ist als Ganzes unlesbar (kein JSON oder falscher Aufbau). */
    data object Corrupt : DecodeResult<Nothing>
}

/**
 * Gemeinsamer Aufbau der lokalen Dateien: `{ "version": 1, "<listKey>": [ … ] }`.
 * Lesen ist tolerant: unbekannte Felder werden ignoriert, unbrauchbare Datensätze übersprungen, unbekannte Icons
 * und Kategorien (z. B. von einer neueren App-Version) werden zu „Sonstiges“.
 */
abstract class LocalListJson<T>(private val listKey: String) {
    fun encode(items: List<T>): String = JSONObject()
        .put(KEY_VERSION, CURRENT_VERSION)
        .put(listKey, JSONArray().apply { items.forEach { put(toJson(it)) } })
        .toString()

    fun decode(text: String): DecodeResult<T> {
        val root = try {
            JSONObject(text)
        } catch (error: JSONException) {
            return DecodeResult.Corrupt
        }
        val array = root.optJSONArray(listKey) ?: return DecodeResult.Corrupt
        var skipped = 0
        val items = (0 until array.length()).mapNotNull { index ->
            val parsed = array.optJSONObject(index)?.let { fromJsonOrNull(it) }
            if (parsed == null) skipped++
            parsed
        }
        return DecodeResult.Decoded(items, skipped)
    }

    protected abstract fun toJson(item: T): JSONObject

    /** `null` = Datensatz unbrauchbar. */
    protected abstract fun fromJsonOrNull(json: JSONObject): T?

    companion object {
        const val KEY_VERSION = "version"
        const val CURRENT_VERSION = 1
    }
}

/** `bar.json`: die eigene Bar. */
object BarJson : LocalListJson<BarItem>("items") {
    override fun toJson(item: BarItem): JSONObject = JSONObject()
        .put("id", item.id)
        .put("name", item.name)
        .put("icon", item.icon.key)
        .put("category", item.category.name)
        .put("servingMilliliters", item.servingMilliliters)
        .put("alcoholPercent", item.alcoholPercent.jsonSafe())
        .put("sugarGramsPer100Ml", item.sugarGramsPer100Ml.jsonSafe())
        .put("aliases", JSONArray().apply { item.aliases.forEach { put(it) } })
        .put("valueSource", item.valueSource.name)
        .put("sortOrder", item.sortOrder)

    override fun fromJsonOrNull(json: JSONObject): BarItem? {
        val id = json.nonBlankStringOrNull("id") ?: return null
        val name = json.nonBlankStringOrNull("name") ?: return null
        val serving = json.intOrNull("servingMilliliters") ?: return null
        return BarItem(
            id = id,
            name = name,
            icon = DrinkIcon.fromKey(json.stringOrNull("icon") ?: ""),
            category = DrinkCategory.fromName(json.stringOrNull("category") ?: ""),
            servingMilliliters = serving,
            alcoholPercent = json.doubleOrNull("alcoholPercent") ?: 0.0,
            sugarGramsPer100Ml = json.doubleOrNull("sugarGramsPer100Ml") ?: 0.0,
            aliases = json.stringList("aliases"),
            valueSource = if (json.stringOrNull("valueSource") == ValueSource.USER.name) ValueSource.USER else ValueSource.DEFAULT,
            sortOrder = json.intOrNull("sortOrder") ?: 0,
        )
    }
}

/** `history.json`: die vollständige Trinkhistorie (nur auf dem Gerät). */
object HistoryJson : LocalListJson<DrinkEntry>("entries") {
    override fun toJson(item: DrinkEntry): JSONObject = JSONObject()
        .put("id", item.id)
        .put("barItemId", item.barItemId ?: JSONObject.NULL)
        .put("name", item.name)
        .put("icon", item.icon.key)
        .put("category", item.category.name)
        .put("volumeMilliliters", item.volumeMilliliters)
        .put("alcoholPercent", item.alcoholPercent.jsonSafe())
        .put("sugarGramsPer100Ml", item.sugarGramsPer100Ml.jsonSafe())
        .put("consumedAt", item.consumedAt.toString())
        .put("shareWithFriends", item.shareWithFriends)

    override fun fromJsonOrNull(json: JSONObject): DrinkEntry? {
        val id = json.nonBlankStringOrNull("id") ?: return null
        val name = json.nonBlankStringOrNull("name") ?: return null
        val volume = json.intOrNull("volumeMilliliters") ?: return null
        val consumedAt = json.instantOrNull("consumedAt") ?: return null
        return DrinkEntry(
            id = id,
            barItemId = json.nonBlankStringOrNull("barItemId"),
            name = name,
            icon = DrinkIcon.fromKey(json.stringOrNull("icon") ?: ""),
            category = DrinkCategory.fromName(json.stringOrNull("category") ?: ""),
            volumeMilliliters = volume,
            alcoholPercent = json.doubleOrNull("alcoholPercent") ?: 0.0,
            sugarGramsPer100Ml = json.doubleOrNull("sugarGramsPer100Ml") ?: 0.0,
            consumedAt = consumedAt,
            // Fehlt die Angabe, bleibt der Eintrag sicherheitshalber privat.
            shareWithFriends = json.booleanOrNull("shareWithFriends") ?: false,
        )
    }
}
