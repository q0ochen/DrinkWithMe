package com.drinkwithme.app.supabase

import com.drinkwithme.app.data.instantOrNull
import com.drinkwithme.app.data.intOrNull
import com.drinkwithme.app.data.nonBlankStringOrNull
import com.drinkwithme.app.data.objects
import com.drinkwithme.app.social.OutgoingRequest
import com.drinkwithme.core.bar.DrinkCategory
import com.drinkwithme.core.bar.DrinkIcon
import com.drinkwithme.core.sharing.SharedDrink
import com.drinkwithme.core.social.FriendDrinkEvent
import com.drinkwithme.core.social.FriendRequest
import com.drinkwithme.core.social.UserProfile
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant

/** Zeilen der Tabelle `friendships` aus Sicht von [myId]: die andere Person und seit wann. */
data class FriendshipRow(val otherId: String, val since: Instant)

/** Zeile der Tabelle `friend_requests` aus Sicht des Empfängers (nur Empfänger dürfen sie lesen). */
data class IncomingRequestRow(val id: String, val fromUser: String, val createdAt: Instant)

/** Zeile der Tabelle `drink_events` (ohne Profil). */
data class DrinkEventRow(
    val id: String,
    val authorId: String,
    val name: String,
    val icon: DrinkIcon,
    val category: DrinkCategory,
    val volumeMilliliters: Int,
    val consumedAt: Instant,
)

/**
 * Wandelt PostgREST-JSON in Kern-Modelle um und umgekehrt. Tolerant: unvollständige Zeilen werden übersprungen,
 * unbekannte Icons/Kategorien (neuere App-Version eines Freundes) werden zu „Sonstiges“.
 */
object SupabaseMappers {
    /** Ersatzname, wenn ein Profil (noch) nicht lesbar ist – wie in der Edge Function. */
    const val UNKNOWN_NAME = "Jemand"

    fun array(body: String): JSONArray = try {
        JSONArray(body)
    } catch (error: JSONException) {
        throw SocialException.InvalidResponse("Antwort ist keine Liste")
    }

    fun profile(json: JSONObject): UserProfile? {
        val id = json.nonBlankStringOrNull("id") ?: return null
        return UserProfile(
            id = id,
            displayName = json.nonBlankStringOrNull("display_name")?.trim() ?: UNKNOWN_NAME,
            avatarUrl = json.nonBlankStringOrNull("avatar_url")?.takeIf { it.startsWith("https://") },
        )
    }

    fun profiles(body: String): List<UserProfile> = array(body).objects().mapNotNull(::profile)

    /** Ergebnis von `accept_invite`: ein Objekt (zusammengesetzter Typ) oder eine Liste mit einem Objekt. */
    fun invitingProfile(body: String): UserProfile {
        val value = try {
            JSONTokener(body).nextValue()
        } catch (error: JSONException) {
            null
        }
        val json = when (value) {
            is JSONObject -> value
            is JSONArray -> value.optJSONObject(0)
            else -> null
        }
        return json?.let(::profile) ?: throw SocialException.InvalidResponse("Profil fehlt")
    }

    /** Ergebnis einer RPC, die Text liefert (z. B. `create_invite_link`): `"token"`. */
    fun rpcText(body: String): String {
        val value = try {
            JSONTokener(body).nextValue()
        } catch (error: JSONException) {
            null
        }
        return (value as? String)?.takeIf { it.isNotBlank() }
            ?: throw SocialException.InvalidResponse("Text erwartet")
    }

    fun friendships(body: String, myId: String): List<FriendshipRow> = array(body).objects().mapNotNull { json ->
        val a = json.nonBlankStringOrNull("user_a") ?: return@mapNotNull null
        val b = json.nonBlankStringOrNull("user_b") ?: return@mapNotNull null
        val since = json.instantOrNull("created_at") ?: return@mapNotNull null
        val other = when (myId) {
            a -> b
            b -> a
            else -> return@mapNotNull null
        }
        FriendshipRow(other, since)
    }

    fun incomingRequests(body: String): List<IncomingRequestRow> = array(body).objects().mapNotNull { json ->
        IncomingRequestRow(
            id = json.nonBlankStringOrNull("id") ?: return@mapNotNull null,
            fromUser = json.nonBlankStringOrNull("from_user") ?: return@mapNotNull null,
            createdAt = json.instantOrNull("created_at") ?: return@mapNotNull null,
        )
    }

    /** Eingehende Anfragen mit dem Profil des Absenders. */
    fun toIncomingRequests(rows: List<IncomingRequestRow>, profiles: Map<String, UserProfile>): List<FriendRequest> =
        rows.map { row ->
            FriendRequest(row.id, profiles[row.fromUser] ?: placeholder(row.fromUser), FriendRequest.Direction.INCOMING, row.createdAt)
        }

    /** Ergebnis von `list_outgoing_friend_requests`: `[{ id, email_hint, created_at }]`. */
    fun outgoingRequests(body: String): List<OutgoingRequest> = array(body).objects().mapNotNull { json ->
        OutgoingRequest(
            id = json.nonBlankStringOrNull("id") ?: return@mapNotNull null,
            emailHint = json.nonBlankStringOrNull("email_hint")?.trim() ?: return@mapNotNull null,
            createdAt = json.instantOrNull("created_at") ?: return@mapNotNull null,
        )
    }

    fun drinkEvents(body: String): List<DrinkEventRow> = array(body).objects().mapNotNull(::drinkEvent)

    fun drinkEvent(json: JSONObject): DrinkEventRow? {
        val volume = json.intOrNull("volume_ml") ?: return null
        return DrinkEventRow(
            id = json.nonBlankStringOrNull("id") ?: return null,
            authorId = json.nonBlankStringOrNull("author_id") ?: return null,
            name = json.nonBlankStringOrNull("name") ?: return null,
            icon = DrinkIcon.fromKey(json.nonBlankStringOrNull("icon") ?: ""),
            category = DrinkCategory.fromName(json.nonBlankStringOrNull("category") ?: ""),
            volumeMilliliters = volume,
            consumedAt = json.instantOrNull("consumed_at") ?: return null,
        )
    }

    fun toFriendDrinkEvent(row: DrinkEventRow, author: UserProfile?): FriendDrinkEvent = FriendDrinkEvent(
        id = row.id,
        author = author ?: placeholder(row.authorId),
        name = row.name,
        icon = row.icon,
        category = row.category,
        volumeMilliliters = row.volumeMilliliters,
        consumedAt = row.consumedAt,
    )

    /**
     * Körper für `POST rest/v1/drink_events`: genau die erlaubten Spalten `id, name, icon, category, volume_ml,
     * consumed_at` (`author_id` setzt der Server aus dem JWT). Nie Vol.-%, Zucker oder Gramm.
     */
    fun drinkEventInsert(drink: SharedDrink): String = JSONObject()
        .put("id", drink.id)
        .put("name", eventName(drink.name))
        .put("icon", drink.icon.key)
        .put("category", drink.category.name)
        .put("volume_ml", drink.volumeMilliliters)
        .put("consumed_at", drink.consumedAt.toString())
        .toString()

    /**
     * Name so, wie ihn der Server annimmt: ohne Steuerzeichen, ohne Rand-Leerraum, höchstens 40 Zeichen
     * (gezählt wie in Postgres nach Unicode-Zeichen, nicht nach UTF-16-Einheiten).
     */
    fun eventName(name: String): String {
        val clean = name.replace(CONTROL_CHARACTERS, " ").trim()
        if (clean.codePointCount(0, clean.length) <= NAME_MAX_LENGTH) return clean
        return clean.substring(0, clean.offsetByCodePoints(0, NAME_MAX_LENGTH)).trim()
    }

    /** PostgREST-Filter `in.(a,b,c)` für UUIDs. */
    fun inFilter(ids: Collection<String>): String = "in.(${ids.joinToString(",")})"

    fun placeholder(id: String) = UserProfile(id = id, displayName = UNKNOWN_NAME, avatarUrl = null)

    private const val NAME_MAX_LENGTH = 40
    private val CONTROL_CHARACTERS = Regex("\\p{Cntrl}+")
}
