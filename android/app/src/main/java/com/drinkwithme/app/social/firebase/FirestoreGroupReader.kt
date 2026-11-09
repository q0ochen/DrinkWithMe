package com.drinkwithme.app.social.firebase

import com.drinkwithme.app.social.firebase.FirestoreSchema.Group
import com.drinkwithme.core.social.CurrentUserProvider
import com.drinkwithme.core.social.DrinkGroup
import com.drinkwithme.core.social.GroupReader
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Live-Liste der Runden, in denen der angemeldete Nutzer Mitglied ist; abgemeldet: leer. */
class FirestoreGroupReader(
    private val firestore: FirebaseFirestore,
    private val users: CurrentUserProvider,
) : GroupReader {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMyGroups(): Flow<List<DrinkGroup>> =
        users.currentUser
            .map { it?.id }
            .distinctUntilChanged()
            .flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(emptyList<DrinkGroup>())
                } else {
                    firestore.collection(FirestoreSchema.GROUPS)
                        .whereArrayContains(Group.MEMBER_IDS, userId)
                        .snapshotsAsFlow()
                        .map { snapshot ->
                            snapshot.documents.mapNotNull { document ->
                                GroupMapper.fromDocument(document.id, document.data.orEmpty())
                            }
                        }
                        .emptyOnPermissionDenied("Runden")
                }
            }
}
