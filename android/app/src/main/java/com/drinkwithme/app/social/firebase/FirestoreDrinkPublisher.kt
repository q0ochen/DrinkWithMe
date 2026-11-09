package com.drinkwithme.app.social.firebase

import com.drinkwithme.app.social.PendingUnpublishRetrier
import com.drinkwithme.app.social.PendingUnpublishStore
import com.drinkwithme.app.social.firebase.FirestoreSchema.Group
import com.drinkwithme.app.social.firebase.FirestoreSchema.GroupDrink
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
 * Verteilt ein eigenes Getränk per Batch in alle eigenen Runden (`groups/{gid}/drinks/{uid}_{drinkId}`)
 * bzw. löscht es dort wieder. Ohne Anmeldung oder ohne Runde wird nichts geteilt.
 *
 * Löschen im abgemeldeten Zustand (oder ein fehlgeschlagenes Löschen) wird im [pending]-Speicher
 * vorgemerkt und beim nächsten Anmelden desselben Nutzers nachgeholt ([retryPendingUnpublishes]).
 */
class FirestoreDrinkPublisher(
    private val firestore: FirebaseFirestore,
    private val users: CurrentUserProvider,
    private val pending: PendingUnpublishStore,
) : DrinkPublisher, PendingUnpublishRetrier {

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
        val documentId = GroupDrink.documentId(author.id, drink.id)
        commitInBatches(groupIds) { batch, groupId -> batch.set(drinkDocument(groupId, documentId), document) }
    }

    override suspend fun unpublish(drinkId: String) {
        val userId = users.currentUser.first()?.id
        if (userId == null) {
            // Abgemeldet fehlt die Berechtigung: für den zuletzt angemeldeten Nutzer vormerken.
            pending.addForLastSignedInUser(drinkId)
            return
        }
        try {
            removeFromGroups(userId, drinkId, myGroupIds(userId))
        } catch (error: Exception) {
            // z. B. kein Netz oder abgebrochen: beim nächsten Start bzw. Anmelden erneut versuchen.
            pending.add(userId, drinkId)
            throw error
        }
    }

    override suspend fun retryPendingUnpublishes(userId: String) {
        val drinkIds = pending.drinkIdsFor(userId)
        if (drinkIds.isEmpty()) return
        val groupIds = myGroupIds(userId)
        drinkIds.forEach { drinkId ->
            removeFromGroups(userId, drinkId, groupIds)
            pending.remove(userId, drinkId)
        }
    }

    /** Löschen nicht vorhandener Dokumente erlauben die Regeln – so reicht ein Batch über alle Runden. */
    private suspend fun removeFromGroups(userId: String, drinkId: String, groupIds: List<String>) {
        val documentId = GroupDrink.documentId(userId, drinkId)
        commitInBatches(groupIds) { batch, groupId -> batch.delete(drinkDocument(groupId, documentId)) }
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
