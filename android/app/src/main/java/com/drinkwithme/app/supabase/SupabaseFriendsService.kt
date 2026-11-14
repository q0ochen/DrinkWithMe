package com.drinkwithme.app.supabase

import com.drinkwithme.app.social.OutgoingRequest
import com.drinkwithme.app.social.OutgoingRequestsService
import com.drinkwithme.core.social.Friend
import com.drinkwithme.core.social.FriendRequest
import com.drinkwithme.core.social.FriendsService
import com.drinkwithme.core.social.InviteLinks
import com.drinkwithme.core.social.UserProfile
import org.json.JSONObject

/** Liest sichtbare Profile per ID (RLS: selbst, Freunde, offene Anfragen). */
class ProfileLookup(private val api: SupabaseApi) {
    suspend fun profiles(ids: Collection<String>): Map<String, UserProfile> {
        val distinct = ids.toSet()
        if (distinct.isEmpty()) return emptyMap()
        val response = api.get(
            "rest/v1/profiles",
            listOf("select" to "id,display_name,avatar_url", "id" to SupabaseMappers.inFilter(distinct)),
        )
        return SupabaseMappers.profiles(response.body).associateBy { it.id }
    }

    suspend fun profile(id: String): UserProfile? = profiles(listOf(id))[id]
}

/**
 * [FriendsService] über Supabase: Lesen per PostgREST (RLS), Schreiben nur über die RPCs aus
 * `supabase/migrations/20261110120400_rpc.sql` und `20261110130000_anfragen_ohne_kontoauskunft.sql`.
 * Ausgehende Anfragen gibt es nur ohne Empfänger ([OutgoingRequestsService]).
 */
class SupabaseFriendsService(
    private val api: SupabaseApi,
    private val profiles: ProfileLookup,
    private val currentUser: CurrentUserId,
    private val webAppUrl: String,
) : FriendsService, OutgoingRequestsService {

    override suspend fun friends(): List<Friend> {
        val me = requireUserId()
        val response = api.get("rest/v1/friendships", listOf("select" to "user_a,user_b,created_at"))
        val rows = SupabaseMappers.friendships(response.body, me)
        val byId = profiles.profiles(rows.map { it.otherId })
        return rows
            .map { Friend(byId[it.otherId] ?: SupabaseMappers.placeholder(it.otherId), it.since) }
            .sortedBy { it.profile.displayName.lowercase() }
    }

    /** Eingehende Anfragen (nur Empfänger dürfen die Tabelle lesen) mit dem Profil des Absenders. */
    override suspend fun requests(): List<FriendRequest> {
        val me = requireUserId()
        val response = api.get(
            "rest/v1/friend_requests",
            listOf("select" to "id,from_user,created_at", "to_user" to "eq.$me", "order" to "created_at.desc"),
        )
        val rows = SupabaseMappers.incomingRequests(response.body)
        return SupabaseMappers.toIncomingRequests(rows, profiles.profiles(rows.map { it.fromUser }))
    }

    /** Eigene Anfragen: nur ID, maskierte Adresse und Zeitpunkt – nie, ob bzw. wen sie erreichen. */
    override suspend fun outgoingRequests(): List<OutgoingRequest> =
        SupabaseMappers.outgoingRequests(api.rpc("list_outgoing_friend_requests").body)

    /** Antwort ist immer neutral – der Server verrät nicht, ob es die Adresse gibt. */
    override suspend fun sendRequestByEmail(email: String) {
        api.rpc("send_friend_request", JSONObject().put("email", email.trim()).toString())
    }

    override suspend fun respond(requestId: String, accept: Boolean) {
        api.rpc("respond_friend_request", JSONObject().put("request_id", requestId).put("accept", accept).toString())
    }

    /** Zurückziehen der eigenen Anfrage (idempotent; fremde oder erledigte Anfragen ignoriert der Server). */
    override suspend fun cancel(requestId: String) {
        api.rpc("cancel_friend_request", JSONObject().put("request_id", requestId).toString())
    }

    override suspend fun createInviteLink(): String {
        val token = SupabaseMappers.rpcText(api.rpc("create_invite_link").body)
        return InviteLinks.webLink(webAppUrl, token)
    }

    override suspend fun acceptInvite(token: String): UserProfile {
        val response = api.rpc("accept_invite", JSONObject().put("token", token).toString())
        return SupabaseMappers.invitingProfile(response.body)
    }

    override suspend fun removeFriend(userId: String) {
        api.rpc("remove_friend", JSONObject().put("friend_id", userId).toString())
    }

    private fun requireUserId(): String = currentUser.currentUserId() ?: throw SocialException.NotSignedIn()
}
