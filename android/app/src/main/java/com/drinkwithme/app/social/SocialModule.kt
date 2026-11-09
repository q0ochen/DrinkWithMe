package com.drinkwithme.app.social

import com.drinkwithme.app.push.PushTokenRegistrar
import com.drinkwithme.core.social.CurrentUserProvider
import com.drinkwithme.core.social.DrinkPublisher
import com.drinkwithme.core.social.FeedReader
import com.drinkwithme.core.social.GroupMembershipService
import com.drinkwithme.core.social.GroupReader

/**
 * Alle Bausteine der Trinkrunden als Abstraktionen. Existiert nur, wenn ein Server
 * (Firebase) eingerichtet ist; die konkreten Klassen verdrahtet der Composition Root.
 */
class SocialModule(
    val currentUserProvider: CurrentUserProvider,
    val signedInUser: SignedInUserIdProvider,
    val accountService: AccountService,
    val groupReader: GroupReader,
    val groupMembershipService: GroupMembershipService,
    val feedReader: FeedReader,
    val drinkPublisher: DrinkPublisher,
    val pushTokenRegistrar: PushTokenRegistrar,
    /**
     * Holt vorgemerkte Löschungen geteilter Getränke (abgemeldet, offline oder unter einem anderen Konto gelöscht)
     * beim nächsten Anmelden des teilenden Kontos nach.
     */
    val pendingUnpublishSync: PendingUnpublishSync,
)
