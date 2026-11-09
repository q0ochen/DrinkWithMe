package com.drinkwithme.app.social

/** [KeyValueStorage] für Tests – verhält sich wie SharedPreferences, nur im Speicher. */
class InMemoryKeyValueStorage : KeyValueStorage {
    val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}
