package com.drinkwithme.app.di

import android.content.Context
import android.util.Log
import com.drinkwithme.app.social.DefaultAccountService
import com.drinkwithme.app.social.SocialModule
import com.drinkwithme.app.social.firebase.CloudFunctions
import com.drinkwithme.app.social.firebase.CredentialManagerGoogleIdTokenSource
import com.drinkwithme.app.social.firebase.FirebaseGoogleAuthenticator
import com.drinkwithme.app.social.firebase.FirebaseGroupMembershipService
import com.drinkwithme.app.social.firebase.FirestoreDrinkPublisher
import com.drinkwithme.app.social.firebase.FirestoreFeedReader
import com.drinkwithme.app.social.firebase.FirestoreGroupReader
import com.drinkwithme.app.social.firebase.FirestorePushTokenRegistrar
import com.drinkwithme.app.social.firebase.FirestoreUserProfileWriter
import com.drinkwithme.app.social.firebase.ResourceWebClientIdProvider
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import com.google.firebase.messaging.messaging

/** Verdrahtet die Firebase-Implementierungen der Trinkrunden. */
internal object FirebaseSocialModuleFactory {
    private const val TAG = "DrinkWithMe"

    /**
     * `null`, solange Firebase nicht eingerichtet ist (keine google-services.json beim Bauen) –
     * dann läuft die App wie bisher nur lokal.
     */
    fun createIfConfigured(context: Context): SocialModule? {
        val appContext = context.applicationContext
        if (FirebaseApp.getApps(appContext).isEmpty()) return null

        val firestore = Firebase.firestore
        val authenticator = FirebaseGoogleAuthenticator(Firebase.auth)
        val pushTokens = FirestorePushTokenRegistrar(firestore, Firebase.messaging, authenticator)
        return SocialModule(
            currentUserProvider = authenticator,
            accountService = DefaultAccountService(
                idTokens = CredentialManagerGoogleIdTokenSource(appContext, ResourceWebClientIdProvider(appContext)),
                authenticator = authenticator,
                profiles = FirestoreUserProfileWriter(firestore),
                pushTokens = pushTokens,
                onBackgroundError = { error -> Log.w(TAG, "Nebenaufgabe bei An-/Abmeldung fehlgeschlagen.", error) },
            ),
            groupReader = FirestoreGroupReader(firestore, authenticator),
            groupMembershipService = FirebaseGroupMembershipService(Firebase.functions(CloudFunctions.REGION)),
            feedReader = FirestoreFeedReader(firestore, authenticator),
            drinkPublisher = FirestoreDrinkPublisher(firestore, authenticator),
            pushTokenRegistrar = pushTokens,
        )
    }
}
