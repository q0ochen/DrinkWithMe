package com.drinkwithme.core.social

import com.drinkwithme.core.social.usecase.DefaultCreateGroupUseCase
import com.drinkwithme.core.social.usecase.DefaultJoinGroupUseCase
import com.drinkwithme.core.social.usecase.DefaultLeaveGroupUseCase
import com.drinkwithme.core.social.usecase.DefaultObserveMyGroupsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GroupUseCaseTest {
    private val service = FakeGroupService()

    @Test
    fun createGroupTrimsName() = runTest {
        DefaultCreateGroupUseCase(service)("  Stammtisch ")
        assertEquals(listOf("Stammtisch"), service.createdNames)
    }

    @Test
    fun createGroupRejectsBlankAndTooLongNames() = runTest {
        val createGroup = DefaultCreateGroupUseCase(service)
        assertFailsWith<SocialValidationException.EmptyGroupName> { createGroup("   ") }
        assertFailsWith<SocialValidationException.GroupNameTooLong> { createGroup("x".repeat(31)) }
        assertTrue(service.createdNames.isEmpty())
    }

    @Test
    fun joinGroupNormalizesCode() = runTest {
        DefaultJoinGroupUseCase(service)("k7mx2q")
        assertEquals(listOf("K7MX2Q"), service.joinedCodes.map { it.value })
    }

    @Test
    fun joinGroupRejectsInvalidCodeWithoutCallingService() = runTest {
        assertFailsWith<SocialValidationException.InvalidInviteCode> { DefaultJoinGroupUseCase(service)("abc") }
        assertTrue(service.joinedCodes.isEmpty())
    }

    @Test
    fun leaveGroupDelegates() = runTest {
        DefaultLeaveGroupUseCase(service)("id-1")
        assertEquals(listOf("id-1"), service.leftGroupIds)
    }

    @Test
    fun observeMyGroupsSortsByName() = runTest {
        service.groups.value = listOf(FakeGroupService.group("zocker"), FakeGroupService.group("Arbeit"))
        assertEquals(listOf("Arbeit", "zocker"), DefaultObserveMyGroupsUseCase(service)().first().map { it.name })
    }
}
