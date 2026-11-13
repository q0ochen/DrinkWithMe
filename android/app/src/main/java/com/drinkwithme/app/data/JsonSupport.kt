package com.drinkwithme.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeParseException

// Kleine, tolerante Lese-Hilfen für org.json. Sie verhalten sich unter Android und in JVM-Tests gleich
// (z. B. liefert Androids optString für JSON-null den Text "null" – diese Hilfen nicht).

/** Text oder `null` (fehlend, JSON-null oder kein Text). */
fun JSONObject.stringOrNull(key: String): String? = opt(key) as? String

/** Nicht leerer Text oder `null`. */
fun JSONObject.nonBlankStringOrNull(key: String): String? = stringOrNull(key)?.takeIf { it.isNotBlank() }

/** Endliche Zahl oder `null`. Android liefert Double/Integer, die JVM-Umsetzung teils BigDecimal. */
fun JSONObject.doubleOrNull(key: String): Double? = (opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() }

/** Ganze Zahl oder `null` (Nachkommastellen → `null`). */
fun JSONObject.intOrNull(key: String): Int? {
    val number = opt(key) as? Number ?: return null
    val value = number.toDouble()
    if (!value.isFinite() || value % 1.0 != 0.0 || value > Int.MAX_VALUE || value < Int.MIN_VALUE) return null
    return value.toInt()
}

/** Ganze Zahl (64 Bit) oder `null`, z. B. Unix-Zeitstempel. */
fun JSONObject.longOrNull(key: String): Long? {
    val number = opt(key) as? Number ?: return null
    val value = number.toDouble()
    if (!value.isFinite() || value % 1.0 != 0.0) return null
    return number.toLong()
}

fun JSONObject.booleanOrNull(key: String): Boolean? = opt(key) as? Boolean

/** ISO-8601-Zeitpunkt (auch mit Offset wie „+00:00“ von PostgREST) oder `null`. */
fun JSONObject.instantOrNull(key: String): Instant? = stringOrNull(key)?.let(::parseInstantOrNull)

/** Liste von Texten; andere Werte werden übersprungen. */
fun JSONObject.stringList(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.opt(it) as? String }
}

/** Alle Objekte eines Arrays; andere Werte werden übersprungen. */
fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

/** Liest `2026-11-10T12:00:00Z`, `2026-11-10T12:00:00.123+00:00` usw. */
fun parseInstantOrNull(text: String): Instant? = try {
    java.time.OffsetDateTime.parse(text).toInstant()
} catch (error: DateTimeParseException) {
    try {
        Instant.parse(text)
    } catch (again: DateTimeParseException) {
        null
    }
}

/** Zahl für JSON: org.json lehnt NaN/Unendlich ab, daher hier 0. */
fun Double.jsonSafe(): Double = if (isFinite()) this else 0.0
