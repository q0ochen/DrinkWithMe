package com.drinkwithme.app.social.firebase

import com.drinkwithme.app.social.DisplayNames
import com.drinkwithme.app.social.UserProfileWriter
import com.drinkwithme.app.social.firebase.FirestoreSchema.User
import com.drinkwithme.core.social.UserProfile
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/** Schreibt `users/{uid}` (Name und Foto), damit die Cloud Functions den Namen in Runden übernehmen. */
class FirestoreUserProfileWriter(private val firestore: FirebaseFirestore) : UserProfileWriter {

    override suspend fun write(profile: UserProfile) {
        val fields = mapOf(
            User.DISPLAY_NAME to DisplayNames.sanitize(profile.displayName),
            // Die Regeln erlauben höchstens 2048 Zeichen – ein längerer Link wird weggelassen.
            User.PHOTO_URL to profile.photoUrl?.takeIf { it.length <= MAXIMUM_PHOTO_URL_LENGTH },
            User.UPDATED_AT to FieldValue.serverTimestamp(),
        )
        firestore.collection(FirestoreSchema.USERS)
            .document(profile.id)
            .set(fields, SetOptions.merge())
            .await()
    }

    private companion object {
        const val MAXIMUM_PHOTO_URL_LENGTH = 2048
    }
}
