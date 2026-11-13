package com.drinkwithme.app.routing

import com.drinkwithme.app.supabase.AuthCallback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MapExtras(private val values: Map<String, Any>) : IntentExtras {
    override fun string(key: String): String? = values[key] as? String
    override fun boolean(key: String): Boolean = values[key] as? Boolean ?: false
}

class IntentRouterTest {
    private val none = MapExtras(emptyMap())

    @Test
    fun routesSignInCallback() {
        assertEquals(
            AppRequest.SignInCallback(AuthCallback.Code("c0de")),
            IntentRouter.route(IntentRouter.ACTION_VIEW, "drinkwithme://auth-callback?code=c0de", none),
        )
    }

    @Test
    fun routesInviteLinksFromAppAndWeb() {
        val token = "AbCdEfGhIjKlMnOpQrStUvWxYz012345"
        assertEquals(AppRequest.AcceptInvite(token), IntentRouter.route(IntentRouter.ACTION_VIEW, "drinkwithme://einladung/$token", none))
        assertEquals(AppRequest.AcceptInvite(token), IntentRouter.routeLink("https://q0ochen.github.io/DrinkWithMe/einladung/$token"))
        assertNull(IntentRouter.routeLink("drinkwithme://einladung/kurz"))
        assertNull(IntentRouter.routeLink(token), "ein nacktes Token ist kein Link")
    }

    @Test
    fun routesNotificationTapsWithEventDetails() {
        val extras = MapExtras(
            mapOf(
                IntentRouter.EXTRA_EVENT_ID to "ev-1",
                IntentRouter.EXTRA_TITLE to "Anna trinkt gerade",
                IntentRouter.EXTRA_BODY to "Apfelschorle (500 ml)",
                IntentRouter.EXTRA_AUTO_READ to true,
            ),
        )
        assertEquals(
            AppRequest.OpenEvent("ev-1", "Anna trinkt gerade", "Apfelschorle (500 ml)", autoRead = true),
            IntentRouter.route(IntentRouter.ACTION_OPEN_EVENT, null, extras, trusted = true),
        )
        assertNull(IntentRouter.route(IntentRouter.ACTION_OPEN_EVENT, null, none, trusted = true), "ohne Ereignis-ID nichts")
    }

    @Test
    fun otherAppsCannotInjectTextsOrSpeechIntoTheEventScreen() {
        // Eine fremde App startet die exportierte MainActivity direkt mit eigenen Texten und „sofort vorlesen“.
        val forged = MapExtras(
            mapOf(
                IntentRouter.EXTRA_EVENT_ID to "x",
                IntentRouter.EXTRA_TITLE to "Bob trinkt gerade",
                IntentRouter.EXTRA_BODY to "Gefälschter Text",
                IntentRouter.EXTRA_AUTO_READ to true,
            ),
        )
        assertEquals(
            AppRequest.OpenEvent("x", title = null, body = null, autoRead = false),
            IntentRouter.route(IntentRouter.ACTION_OPEN_EVENT, null, forged),
            "nur die ID; der Text kommt dann ausschließlich vom Server",
        )
    }

    @Test
    fun onlyTheNonExportedNotificationEntryIsTrusted() {
        assertTrue(IntentRouter.isTrustedEntry("com.drinkwithme.app.NotificationEntry"))
        assertFalse(IntentRouter.isTrustedEntry("com.drinkwithme.app.MainActivity"))
        assertFalse(IntentRouter.isTrustedEntry(null))
    }

    @Test
    fun intentsAreRoutedOnlyOnARealLaunch() {
        assertTrue(IntentRouter.shouldRoute(restored = false, launchedFromHistory = false))
        assertFalse(IntentRouter.shouldRoute(restored = true, launchedFromHistory = false), "nach Drehung")
        assertFalse(
            IntentRouter.shouldRoute(restored = false, launchedFromHistory = true),
            "aus „Zuletzt verwendet“: alte Einladung/Benachrichtigung nicht noch einmal",
        )
    }

    @Test
    fun ignoresLauncherAndUnknownIntents() {
        assertNull(IntentRouter.route("android.intent.action.MAIN", null, none))
        assertNull(IntentRouter.route(IntentRouter.ACTION_VIEW, "https://example.org/", none))
        assertNull(IntentRouter.route(null, null, none))
    }
}
