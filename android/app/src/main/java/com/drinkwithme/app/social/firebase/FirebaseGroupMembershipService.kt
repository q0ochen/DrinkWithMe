package com.drinkwithme.app.social.firebase

import com.drinkwithme.core.social.DrinkGroup
import com.drinkwithme.core.social.GroupMembershipService
import com.drinkwithme.core.social.GroupNotFoundException
import com.drinkwithme.core.social.InviteCode
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/**
 * Erstellen, Beitreten und Verlassen über die aufrufbaren Cloud Functions (Region europe-west3) –
 * Runden-Dokumente schreibt ausschließlich der Server.
 */
class FirebaseGroupMembershipService(
    private val functions: FirebaseFunctions,
    private val errors: FunctionsErrorTranslator = FunctionsErrorTranslator(),
) : GroupMembershipService {

    override suspend fun createGroup(name: String): DrinkGroup =
        GroupMapper.fromCallableResult(call(CloudFunctions.CREATE_GROUP, mapOf(CloudFunctions.PARAM_NAME to name)))

    override suspend fun joinGroup(code: InviteCode): DrinkGroup =
        GroupMapper.fromCallableResult(
            call(CloudFunctions.JOIN_GROUP, mapOf(CloudFunctions.PARAM_CODE to code.value)) { GroupNotFoundException() },
        )

    override suspend fun leaveGroup(groupId: String) {
        call(CloudFunctions.LEAVE_GROUP, mapOf(CloudFunctions.PARAM_GROUP_ID to groupId))
    }

    private suspend fun call(
        functionName: String,
        input: Map<String, Any>,
        notFound: (() -> Exception)? = null,
    ): Any? = try {
        // Bewusst getData() statt .data: In firebase-functions 21.1.0 (BoM 33.6/33.7) ist das Kotlin-Property
        // `data` privat (firebase-android-sdk#6522); getData() ist in allen Versionen öffentlich.
        functions.getHttpsCallable(functionName).call(input).await().getData()
    } catch (error: FirebaseFunctionsException) {
        throw errors.translate(error, notFound)
    }
}
