package com.drinkwithme.app.push

import com.drinkwithme.app.data.nonBlankStringOrNull
import org.json.JSONException
import org.json.JSONObject

/**
 * Inhalt einer Push-Nachricht von `notify-friends` (supabase/README.md):
 * `{ "type": "drink", "eventId", "authorId", "recipientId", "title": "Anna trinkt gerade", "body": "Apfelschorle (500 ml)" }`.
 */
data class DrinkPushMessage(
    val eventId: String,
    val authorId: String,
    val recipientId: String,
    val title: String,
    val body: String,
)

object DrinkPushParser {
    const val TYPE_DRINK = "drink"
    private const val MAX_TEXT_LENGTH = 200

    /** `null` bei unbekanntem Typ, fehlenden Feldern oder kaputtem JSON. */
    fun parse(content: ByteArray): DrinkPushMessage? {
        val json = try {
            JSONObject(String(content, Charsets.UTF_8))
        } catch (error: JSONException) {
            return null
        }
        if (json.nonBlankStringOrNull("type") != TYPE_DRINK) return null
        return DrinkPushMessage(
            eventId = json.nonBlankStringOrNull("eventId")?.trim() ?: return null,
            authorId = json.nonBlankStringOrNull("authorId")?.trim() ?: return null,
            recipientId = json.nonBlankStringOrNull("recipientId")?.trim() ?: return null,
            title = json.nonBlankStringOrNull("title")?.trim()?.take(MAX_TEXT_LENGTH) ?: return null,
            body = json.nonBlankStringOrNull("body")?.trim()?.take(MAX_TEXT_LENGTH) ?: return null,
        )
    }
}
