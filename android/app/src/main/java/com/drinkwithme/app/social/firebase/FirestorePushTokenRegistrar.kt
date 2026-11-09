package com.drinkwithme.app.social.firebase

import com.drinkwithme.app.push.PushTokenRegistrar
import com.drinkwithme.app.social.firebase.FirestoreSchema.Token
import com.drinkwithme.core.social.CurrentUserProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/** Speichert das FCM-Token unter `users/{uid}/tokens/{token}`, damit die Cloud Function Push senden kann. */
class FirestorePushTokenRegistrar(
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
    private val users: CurrentUserProvider,
) : PushTokenRegistrar {

    override suspend fun registerCurrentToken() {
        if (users.currentUser.first() == null) return
        registerToken(messaging.token.await())
    }

    override suspend fun registerToken(token: String) {
        val userId = users.currentUser.first()?.id ?: return
        val fields = mapOf(
            Token.PLATFORM to Token.PLATFORM_ANDROID,
            Token.UPDATED_AT to FieldValue.serverTimestamp(),
        )
        tokenDocument(userId, token).set(fields).await()
    }

    override suspend fun unregisterCurrentToken() {
        val userId = users.currentUser.first()?.id ?: return
        val token = messaging.token.await()
        tokenDocument(userId, token).delete().await()
        // Das Token selbst ungültig machen: Nach dem Abmelden soll dieses Gerät keine Pushs mehr bekommen,
        // selbst wenn das Löschen oben (z. B. offline) noch nicht beim Server angekommen ist.
        messaging.deleteToken().await()
    }

    private fun tokenDocument(userId: String, token: String): DocumentReference =
        firestore.collection(FirestoreSchema.USERS)
            .document(userId)
            .collection(FirestoreSchema.TOKENS)
            .document(token)
}
