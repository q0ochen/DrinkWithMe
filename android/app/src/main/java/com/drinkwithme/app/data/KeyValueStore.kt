package com.drinkwithme.app.data

import android.content.SharedPreferences

/** Kleiner Schlüssel-Wert-Speicher (Einstellungen, Merker). Als Schnittstelle, damit die Logik ohne Android testbar ist. */
interface KeyValueStore {
    fun getString(key: String): String?

    /** `null` entfernt den Schlüssel. */
    fun putString(key: String, value: String?)
}

fun KeyValueStore.getBoolean(key: String, default: Boolean): Boolean = when (getString(key)) {
    "true" -> true
    "false" -> false
    else -> default
}

fun KeyValueStore.putBoolean(key: String, value: Boolean) = putString(key, value.toString())

/**
 * [KeyValueStore] in SharedPreferences. Mit [synchronous] wird per `commit()` sofort geschrieben (z. B. für das
 * Refresh-Token, das sich bei jeder Erneuerung ändert); sonst per `apply()` im Hintergrund.
 */
class SharedPreferencesKeyValueStore(
    private val preferences: SharedPreferences,
    private val synchronous: Boolean = false,
) : KeyValueStore {

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String?) {
        val editor = preferences.edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        if (synchronous) editor.commit() else editor.apply()
    }
}

/** Flüchtiger Speicher für Tests und Vorschauen. */
class InMemoryKeyValueStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
    private val values = java.util.concurrent.ConcurrentHashMap(initial)

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }

    fun snapshot(): Map<String, String> = HashMap(values)
}
