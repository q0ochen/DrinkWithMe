package com.drinkwithme.app.social.firebase

import com.drinkwithme.app.social.DisplayNames
import com.drinkwithme.app.social.SocialServiceException
import com.drinkwithme.app.social.firebase.CloudFunctions.GroupDto
import com.drinkwithme.app.social.firebase.FirestoreSchema.Group
import com.drinkwithme.core.social.DrinkGroup
import com.drinkwithme.core.social.GroupMember
import com.drinkwithme.core.social.InviteCode

/**
 * Wandelt Rohdaten aus Firestore bzw. aus den Cloud Functions in [DrinkGroup] um.
 * Kennt keine Firebase-Typen und ist daher ohne Android testbar.
 */
internal object GroupMapper {

    /** Dokument `groups/{groupId}`; `null`, wenn es unvollständig ist (z. B. ohne gültigen Code). */
    fun fromDocument(id: String, data: Map<String, Any?>): DrinkGroup? {
        val inviteCode = (data[Group.INVITE_CODE] as? String)?.let { InviteCode.parse(it) } ?: return null
        val namesById = data[Group.MEMBERS] as? Map<*, *> ?: emptyMap<String, Any?>()
        val members = (data[Group.MEMBER_IDS] as? List<*>).orEmpty()
            .filterIsInstance<String>()
            .map { userId ->
                val member = namesById[userId] as? Map<*, *>
                GroupMember(userId, DisplayNames.sanitize(member?.get(Group.MEMBER_DISPLAY_NAME) as? String))
            }
        return DrinkGroup(
            id = id,
            name = data[Group.NAME] as? String ?: "",
            inviteCode = inviteCode,
            ownerId = data[Group.OWNER_ID] as? String ?: "",
            members = members,
        )
    }

    /** Antwort `{ group: GroupDto }` von `createGroup` und `joinGroup`. */
    fun fromCallableResult(result: Any?): DrinkGroup {
        val dto = (result as? Map<*, *>)?.get(CloudFunctions.RESULT_GROUP) as? Map<*, *> ?: throw unexpectedResponse()
        val id = dto[GroupDto.ID] as? String ?: throw unexpectedResponse()
        val inviteCode = (dto[GroupDto.INVITE_CODE] as? String)?.let { InviteCode.parse(it) } ?: throw unexpectedResponse()
        val members = (dto[GroupDto.MEMBERS] as? List<*>).orEmpty().mapNotNull { raw ->
            val member = raw as? Map<*, *> ?: return@mapNotNull null
            val userId = member[GroupDto.MEMBER_USER_ID] as? String ?: return@mapNotNull null
            GroupMember(userId, DisplayNames.sanitize(member[GroupDto.MEMBER_DISPLAY_NAME] as? String))
        }
        return DrinkGroup(
            id = id,
            name = dto[GroupDto.NAME] as? String ?: "",
            inviteCode = inviteCode,
            ownerId = dto[GroupDto.OWNER_ID] as? String ?: "",
            members = members,
        )
    }

    private fun unexpectedResponse() =
        SocialServiceException("Unerwartete Antwort vom Server. Bitte aktualisiere die App und versuche es erneut.")
}
