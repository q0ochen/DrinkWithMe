package com.drinkwithme.app.social.firebase

import com.drinkwithme.app.social.firebase.FirestoreSchema.GroupDrink
import com.drinkwithme.core.social.CurrentUserProvider
import com.drinkwithme.core.social.FeedEntry
import com.drinkwithme.core.social.FeedReader
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant

/** Live-Feed `groups/{groupId}/drinks`, neueste zuerst; abgemeldet: leer. */
class FirestoreFeedReader(
    private val firestore: FirebaseFirestore,
    private val users: CurrentUserProvider,
) : FeedReader {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFeed(groupId: String, limit: Int): Flow<List<FeedEntry>> =
        users.currentUser
            .map { it?.id }
            .distinctUntilChanged()
            .flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(emptyList<FeedEntry>())
                } else {
                    firestore.collection(FirestoreSchema.GROUPS)
                        .document(groupId)
                        .collection(FirestoreSchema.DRINKS)
                        .orderBy(GroupDrink.CONSUMED_AT, Query.Direction.DESCENDING)
                        .limit(limit.toLong())
                        .snapshotsAsFlow()
                        .map { snapshot -> snapshot.documents.mapNotNull { it.toFeedEntry(groupId) } }
                        .emptyOnPermissionDenied("Feed")
                }
            }

    private fun DocumentSnapshot.toFeedEntry(groupId: String): FeedEntry? {
        val consumedAt = getTimestamp(GroupDrink.CONSUMED_AT)
            ?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) }
        return GroupDrinkMapper.fromDocument(groupId, id, data.orEmpty(), consumedAt)
    }
}
