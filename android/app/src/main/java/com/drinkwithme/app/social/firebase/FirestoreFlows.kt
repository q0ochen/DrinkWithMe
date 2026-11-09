package com.drinkwithme.app.social.firebase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch

private const val TAG = "DrinkWithMe"

/** Beobachtet eine Firestore-Abfrage live; der Listener wird entfernt, sobald niemand mehr sammelt. */
internal fun Query.snapshotsAsFlow(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
        } else if (snapshot != null) {
            trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}

/**
 * Fehlende Leserechte ergeben eine leere Liste statt eines Absturzes – das passiert z. B.
 * kurz nach dem Abmelden oder nach dem Verlassen einer Runde, solange der Listener noch läuft.
 */
internal fun <T> Flow<List<T>>.emptyOnPermissionDenied(what: String): Flow<List<T>> = catch { error ->
    if (error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        Log.w(TAG, "Keine Leserechte für $what.", error)
        emit(emptyList())
    } else {
        throw error
    }
}
