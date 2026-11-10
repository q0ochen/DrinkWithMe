package com.drinkwithme.app.supabase

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
 * `supabase/migrations/20261110120400_rpc.sql`.
 */
class SupabaseFriendsService(
    private val api: SupabaseApi,
    private val profiles: ProfileLookup,
    private val currentUser: CurrentUserId,
    private val webAppUrl: String,
) : FriendsService {

    override suspend fun friends(): List<Friend> {
        val me = requireUserId()
        val response = api.get("rest/v1/friendships", listOf("select" to "user_a,user_b,created_at"))
        val rows = SupabaseMappers.friendships(response.body, me)
        val byId = profiles.profiles(rows.map { it.otherId })
        return rows
            .map { Friend(byId[it.otherId] ?: SupabaseMappers.placeholder(it.otherId), it.since) }
            .sortedBy { it.profile.displayName.lowercase() }
    }

    override suspend fun requests(): List<FriendRequest> {
        val me = requireUserId()
        val response = api.get(
            "rest/v1/friend_requests",
            listOf("select" to "id,from_user,to_user,created_at", "order" to "created_at.desc"),
        )
        val rows = SupabaseMappers.friendRequests(response.body)
        val others = rows.map { if (it.toUser == me) it.fromUser else it.toUser }
        return SupabaseMappers.toFriendRequests(rows, me, profiles.profiles(others))
    }

    /** Antwort ist immer neutral – der Server verrät nicht, ob es die Adresse gibt. */
    override suspend fun sendRequestByEmail(email: String) {
        api.rpc("send_friend_request", JSONObject().put("email", email.trim()).toString())
    }

    override suspend fun respond(requestId: String, accept: Boolean) {
        api.rpc("respond_friend_request", JSONObject().put("request_id", requestId).put("accept", accept).toString())
    }

    /** Zurückziehen = Löschen der eigenen Anfrage (RLS erlaubt das nur dem Absender). */
    override suspend fun cancel(requestId: String) {
        api.delete("rest/v1/friend_requests", listOf("id" to "eq.$requestId"))
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
