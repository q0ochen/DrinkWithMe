package com.drinkwithme.app.routing

import com.drinkwithme.app.supabase.AuthCallback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
            IntentRouter.route(IntentRouter.ACTION_OPEN_EVENT, null, extras),
        )
        assertNull(IntentRouter.route(IntentRouter.ACTION_OPEN_EVENT, null, none), "ohne Ereignis-ID nichts")
    }

    @Test
    fun ignoresLauncherAndUnknownIntents() {
        assertNull(IntentRouter.route("android.intent.action.MAIN", null, none))
        assertNull(IntentRouter.route(IntentRouter.ACTION_VIEW, "https://example.org/", none))
        assertNull(IntentRouter.route(null, null, none))
    }
}
