package com.drinkwithme.app.data

import android.content.SharedPreferences
import com.drinkwithme.app.social.KeyValueStorage

/**
 * [KeyValueStorage] in SharedPreferences. Schreibt mit `commit()` (synchron), damit eine Vormerkung
 * auch dann erhalten bleibt, wenn die App direkt danach beendet wird – aufgerufen wird nur im Hintergrund.
 */
class SharedPreferencesKeyValueStorage(private val preferences: SharedPreferences) : KeyValueStorage {

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String?) {
        val editor = preferences.edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        editor.commit()
    }
}
