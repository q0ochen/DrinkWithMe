package com.drinkwithme.app.social.firebase

import com.drinkwithme.app.social.GroupDrinkGateway
import com.drinkwithme.app.social.firebase.FirestoreSchema.Group
import com.drinkwithme.app.social.firebase.FirestoreSchema.GroupDrink
import com.drinkwithme.core.domain.Drink
import com.drinkwithme.core.social.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.tasks.await

/**
 * Firestore-Teil des Teilens: schreibt ein eigenes Getränk per Batch in Runden (`groups/{gid}/drinks/{uid}_{drinkId}`)
 * bzw. löscht es dort wieder. Wann und für welches Konto das passiert, entscheidet
 * [com.drinkwithme.app.social.AuthorAwareDrinkPublisher].
 */
class FirestoreDrinkPublisher(private val firestore: FirebaseFirestore) : GroupDrinkGateway {

    /** Fragt den Server (nicht nur den Cache), damit eine gerade beigetretene Runde nicht fehlt. */
    override suspend fun groupIdsOf(userId: String): List<String> =
        firestore.collection(FirestoreSchema.GROUPS)
            .whereArrayContains(Group.MEMBER_IDS, userId)
            .get()
            .await()
            .documents
            .map { it.id }

    override suspend fun share(drink: Drink, author: UserProfile, groupIds: List<String>) {
        val document = GroupDrinkMapper.toDocument(
            drink = drink,
            author = author,
            consumedAt = Timestamp(drink.consumedAt.epochSecond, drink.consumedAt.nano),
            createdAt = FieldValue.serverTimestamp(),
        )
        val documentId = GroupDrink.documentId(author.id, drink.id)
        commitInBatches(groupIds) { batch, groupId -> batch.set(drinkDocument(groupId, documentId), document) }
    }

    /** Löschen nicht vorhandener Dokumente erlauben die Regeln – so reicht ein Batch über alle Runden. */
    override suspend fun remove(authorId: String, drinkId: String, groupIds: List<String>) {
        val documentId = GroupDrink.documentId(authorId, drinkId)
        commitInBatches(groupIds) { batch, groupId -> batch.delete(drinkDocument(groupId, documentId)) }
    }

    private suspend fun commitInBatches(groupIds: List<String>, write: (WriteBatch, String) -> Unit) {
        groupIds.chunked(MAXIMUM_GROUPS_PER_BATCH).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { groupId -> write(batch, groupId) }
            batch.commit().await()
        }
    }

    private fun drinkDocument(groupId: String, documentId: String): DocumentReference =
        firestore.collection(FirestoreSchema.GROUPS)
            .document(groupId)
            .collection(FirestoreSchema.DRINKS)
            .document(documentId)

    private companion object {
        /**
         * Die Sicherheitsregeln lesen pro geteiltem Getränk das Runden-Dokument (Mitgliedschaft);
         * Firestore erlaubt höchstens 20 solcher Zugriffe pro Batch – daher höchstens 20 Runden je Batch.
         */
        const val MAXIMUM_GROUPS_PER_BATCH = 20
    }
}
