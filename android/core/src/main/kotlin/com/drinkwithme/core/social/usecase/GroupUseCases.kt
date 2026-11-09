package com.drinkwithme.core.social.usecase

import com.drinkwithme.core.social.DrinkGroup
import com.drinkwithme.core.social.GroupMembershipService
import com.drinkwithme.core.social.GroupReader
import com.drinkwithme.core.social.InviteCode
import com.drinkwithme.core.social.SocialValidationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Erstellt eine neue Trinkrunde. */
interface CreateGroupUseCase {
    /** @throws SocialValidationException bei ungültigem Namen. */
    suspend operator fun invoke(name: String): DrinkGroup
}

class DefaultCreateGroupUseCase(
    private val service: GroupMembershipService,
    private val maximumNameLength: Int = MAXIMUM_GROUP_NAME_LENGTH,
) : CreateGroupUseCase {
    override suspend fun invoke(name: String): DrinkGroup {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw SocialValidationException.EmptyGroupName
        if (trimmed.length > maximumNameLength) throw SocialValidationException.GroupNameTooLong(maximumNameLength)
        return service.createGroup(trimmed)
    }

    companion object {
        const val MAXIMUM_GROUP_NAME_LENGTH = 30
    }
}

/** Tritt einer Trinkrunde per Einladungscode bei. */
interface JoinGroupUseCase {
    /** @throws SocialValidationException.InvalidInviteCode bei falschem Format. */
    suspend operator fun invoke(rawCode: String): DrinkGroup
}

class DefaultJoinGroupUseCase(private val service: GroupMembershipService) : JoinGroupUseCase {
    override suspend fun invoke(rawCode: String): DrinkGroup {
        val code = InviteCode.parse(rawCode) ?: throw SocialValidationException.InvalidInviteCode
        return service.joinGroup(code)
    }
}

/** Verlässt eine Trinkrunde. */
interface LeaveGroupUseCase {
    suspend operator fun invoke(groupId: String)
}

class DefaultLeaveGroupUseCase(private val service: GroupMembershipService) : LeaveGroupUseCase {
    override suspend fun invoke(groupId: String) = service.leaveGroup(groupId)
}

/** Liefert die eigenen Trinkrunden, alphabetisch sortiert. */
interface ObserveMyGroupsUseCase {
    operator fun invoke(): Flow<List<DrinkGroup>>
}

class DefaultObserveMyGroupsUseCase(private val reader: GroupReader) : ObserveMyGroupsUseCase {
    override fun invoke(): Flow<List<DrinkGroup>> =
        reader.observeMyGroups().map { groups -> groups.sortedBy { it.name.lowercase() } }
}
