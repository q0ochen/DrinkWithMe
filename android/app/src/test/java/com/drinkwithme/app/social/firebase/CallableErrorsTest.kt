package com.drinkwithme.app.social.firebase

import com.drinkwithme.app.social.SocialServiceException
import com.drinkwithme.core.social.GroupNotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class CallableErrorsTest {

    @Test
    fun unknownCodeOnJoinIsGroupNotFound() {
        val cause = IllegalStateException("not-found")
        val error = CallableErrors.translate("NOT_FOUND", CloudFunctions.JOIN_GROUP, cause)
        assertIs<GroupNotFoundException>(error)
        assertSame(cause, error.cause)
    }

    @Test
    fun fullGroupIsFailedPrecondition() {
        val error = CallableErrors.translate("FAILED_PRECONDITION", CloudFunctions.JOIN_GROUP)
        assertIs<SocialServiceException>(error)
        assertEquals("Diese Runde ist voll (höchstens 50 Mitglieder).", error.message)
    }

    @Test
    fun tooManyWrongCodesIsResourceExhausted() {
        val error = CallableErrors.translate("RESOURCE_EXHAUSTED", CloudFunctions.JOIN_GROUP)
        assertIs<SocialServiceException>(error)
        assertEquals("Zu viele falsche Codes. Bitte versuche es in einer Stunde noch einmal.", error.message)
    }

    @Test
    fun joinSpecificMessagesDoNotLeakIntoOtherFunctions() {
        listOf(CloudFunctions.CREATE_GROUP, CloudFunctions.LEAVE_GROUP, CloudFunctions.DELETE_ACCOUNT).forEach { function ->
            val notFound = CallableErrors.translate("NOT_FOUND", function)
            assertIs<SocialServiceException>(notFound)
            assertEquals(true, notFound.message?.contains("Server-Funktion wurde nicht gefunden"), function)
            assertNotEquals(CallableErrors.GROUP_FULL, CallableErrors.translate("FAILED_PRECONDITION", function).message)
            assertNotEquals(
                CallableErrors.TOO_MANY_WRONG_CODES,
                CallableErrors.translate("RESOURCE_EXHAUSTED", function).message,
            )
        }
    }

    @Test
    fun networkProblemsAndUnknownCodesGiveGermanMessages() {
        val unavailable = CallableErrors.translate("UNAVAILABLE", CloudFunctions.DELETE_ACCOUNT)
        assertEquals(true, unavailable.message?.contains("Internetverbindung"))
        val unknown = CallableErrors.translate("SOMETHING_NEW", CloudFunctions.CREATE_GROUP)
        assertEquals("Das hat leider nicht geklappt. Bitte versuche es später erneut.", unknown.message)
    }
}
