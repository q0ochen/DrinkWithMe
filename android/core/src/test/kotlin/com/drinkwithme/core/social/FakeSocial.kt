package com.drinkwithme.core.social

import com.drinkwithme.core.domain.Drink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeGroupService : GroupMembershipService, GroupReader {
    val groups = MutableStateFlow<List<DrinkGroup>>(emptyList())
    val createdNames = mutableListOf<String>()
    val joinedCodes = mutableListOf<InviteCode>()
    val leftGroupIds = mutableListOf<String>()

    override fun observeMyGroups(): Flow<List<DrinkGroup>> = groups

    override suspend fun createGroup(name: String): DrinkGroup =
        group(name).also { createdNames += name }

    override suspend fun joinGroup(code: InviteCode): DrinkGroup =
        group("Beigetreten", code).also { joinedCodes += code }

    override suspend fun leaveGroup(groupId: String) {
        leftGroupIds += groupId
    }

    companion object {
        fun group(name: String, code: InviteCode = InviteCode.parse("ABCDEFGH")!!) = DrinkGroup(
            id = "id-$name",
            name = name,
            inviteCode = code,
            ownerId = "owner",
            members = listOf(GroupMember("owner", "Owner")),
        )
    }
}

class RecordingPublisher(private val failWith: Exception? = null) : DrinkPublisher {
    val published = mutableListOf<Drink>()
    val unpublished = mutableListOf<String>()

    override suspend fun publish(drink: Drink) {
        failWith?.let { throw it }
        published += drink
    }

    override suspend fun unpublish(drinkId: String) {
        failWith?.let { throw it }
        unpublished += drinkId
    }
}
