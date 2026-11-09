package com.drinkwithme.app.ui.account

import com.drinkwithme.core.social.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UserScopedKeysTest {

    @Test
    fun eachUserGetsHisOwnViewModelKey() {
        val anna = UserProfile("u1", "Anna")
        val ben = UserProfile("u2", "Ben")

        assertNotEquals(userScopedKey("friends", anna), userScopedKey("friends", ben))
        assertNotEquals(userScopedKey("friends", anna), userScopedKey("groups", anna))
        assertEquals(userScopedKey("friends", anna), userScopedKey("friends", anna.copy(displayName = "Anna B.")))
    }
}
