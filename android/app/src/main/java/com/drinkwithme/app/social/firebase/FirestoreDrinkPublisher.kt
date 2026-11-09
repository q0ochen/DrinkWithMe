package com.drinkwithme.app.social.firebase

import com.drinkwithme.app.social.firebase.FirestoreSchema.Group
import com.drinkwithme.core.domain.Drink
import com.drinkwithme.core.domain.DrinkVisibility
import com.drinkwithme.core.social.CurrentUserProvider
import com.drinkwithme.core.social.DrinkPublisher
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * Verteilt ein eigenes Getränk per Batch in alle eigenen Runden (`groups/{gid}/drinks/{drinkId}`)
 * bzw. löscht es dort wieder. Ohne Anmeldung oder ohne Runde passiert nichts.
 */
class FirestoreDrinkPublisher(
    private val firestore: FirebaseFirestore,
    private val users: CurrentUserProvider,
) : DrinkPublisher {

    override suspend fun publish(drink: Drink) {
        if (drink.visibility != DrinkVisibility.FRIENDS) return
        val author = users.currentUser.first() ?: return
        val groupIds = myGroupIds(author.id)
        if (groupIds.isEmpty()) return

        val document = GroupDrinkMapper.toDocument(
            drink = drink,
            author = author,
            consumedAt = Timestamp(drink.consumedAt.epochSecond, drink.consumedAt.nano),
            createdAt = FieldValue.serverTimestamp(),
        )
        commitInBatches(groupIds) { batch, groupId -> batch.set(drinkDocument(groupId, drink.id), document) }
    }

    override suspend fun unpublish(drinkId: String) {
        val userId = users.currentUser.first()?.id ?: return
        // Löschen nicht vorhandener Dokumente erlauben die Regeln – so reicht ein Batch über alle Runden.
        commitInBatches(myGroupIds(userId)) { batch, groupId -> batch.delete(drinkDocument(groupId, drinkId)) }
    }

    /** Fragt den Server (nicht nur den Cache), damit eine gerade beigetretene Runde nicht fehlt. */
    private suspend fun myGroupIds(userId: String): List<String> =
        firestore.collection(FirestoreSchema.GROUPS)
            .whereArrayContains(Group.MEMBER_IDS, userId)
            .get()
            .await()
            .documents
            .map { it.id }

    private suspend fun commitInBatches(groupIds: List<String>, write: (WriteBatch, String) -> Unit) {
        groupIds.chunked(MAXIMUM_GROUPS_PER_BATCH).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { groupId -> write(batch, groupId) }
            batch.commit().await()
        }
    }

    private fun drinkDocument(groupId: String, drinkId: String): DocumentReference =
        firestore.collection(FirestoreSchema.GROUPS)
            .document(groupId)
            .collection(FirestoreSchema.DRINKS)
            .document(drinkId)

    private companion object {
        /**
         * Die Sicherheitsregeln lesen pro geteiltem Getränk das Runden-Dokument (Mitgliedschaft);
         * Firestore erlaubt höchstens 20 solcher Zugriffe pro Batch – daher höchstens 20 Runden je Batch.
         */
        const val MAXIMUM_GROUPS_PER_BATCH = 20
    }
}
