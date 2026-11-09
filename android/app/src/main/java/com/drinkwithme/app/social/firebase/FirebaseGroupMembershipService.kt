package com.drinkwithme.app.social.firebase

import com.drinkwithme.core.social.DrinkGroup
import com.drinkwithme.core.social.GroupMembershipService
import com.drinkwithme.core.social.InviteCode

/**
 * Erstellen, Beitreten und Verlassen über die aufrufbaren Cloud Functions (Region europe-west3) –
 * Runden-Dokumente schreibt ausschließlich der Server.
 *
 * `joinGroup` wirft bei unbekanntem Code `GroupNotFoundException`, bei voller Runde bzw. zu vielen
 * falschen Codes eine `SocialServiceException` mit deutscher Meldung (siehe [CallableErrors]).
 */
class FirebaseGroupMembershipService(private val callables: CallableFunctions) : GroupMembershipService {

    override suspend fun createGroup(name: String): DrinkGroup =
        GroupMapper.fromCallableResult(callables.call(CloudFunctions.CREATE_GROUP, mapOf(CloudFunctions.PARAM_NAME to name)))

    override suspend fun joinGroup(code: InviteCode): DrinkGroup =
        GroupMapper.fromCallableResult(callables.call(CloudFunctions.JOIN_GROUP, mapOf(CloudFunctions.PARAM_CODE to code.value)))

    override suspend fun leaveGroup(groupId: String) {
        callables.call(CloudFunctions.LEAVE_GROUP, mapOf(CloudFunctions.PARAM_GROUP_ID to groupId))
    }
}
