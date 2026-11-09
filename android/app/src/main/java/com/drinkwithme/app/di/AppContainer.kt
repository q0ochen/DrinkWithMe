package com.drinkwithme.app.di

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.drinkwithme.app.data.JsonFileDrinkRepository
import com.drinkwithme.app.push.AndroidFriendDrinkNotifier
import com.drinkwithme.app.push.DefaultPushMessageHandler
import com.drinkwithme.app.push.PushMessageHandler
import com.drinkwithme.app.sharing.DrinkSharer
import com.drinkwithme.app.sharing.IntentDrinkSharer
import com.drinkwithme.app.social.SocialModule
import com.drinkwithme.app.ui.account.AccountViewModel
import com.drinkwithme.app.ui.add.AddDrinkViewModel
import com.drinkwithme.app.ui.friends.FriendsViewModel
import com.drinkwithme.app.ui.groups.GermanInviteMessageFormatter
import com.drinkwithme.app.ui.groups.GroupsViewModel
import com.drinkwithme.app.ui.list.DrinkListViewModel
import com.drinkwithme.core.domain.DefaultDrinkValidator
import com.drinkwithme.core.domain.DrinkRepository
import com.drinkwithme.core.sharing.GermanShareFormatter
import com.drinkwithme.core.social.PublishingDrinkRepository
import com.drinkwithme.core.social.usecase.DefaultCreateGroupUseCase
import com.drinkwithme.core.social.usecase.DefaultJoinGroupUseCase
import com.drinkwithme.core.social.usecase.DefaultLeaveGroupUseCase
import com.drinkwithme.core.social.usecase.DefaultObserveFeedUseCase
import com.drinkwithme.core.social.usecase.DefaultObserveMyGroupsUseCase
import com.drinkwithme.core.usecase.DefaultDeleteDrinkUseCase
import com.drinkwithme.core.usecase.DefaultFetchDrinksUseCase
import com.drinkwithme.core.usecase.DefaultLogDrinkUseCase
import com.drinkwithme.core.usecase.DefaultShareDrinksUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Abhängigkeiten, die die UI benötigt – nur Abstraktionen (Dependency Inversion). */
interface AppContainer {
    val drinkSharer: DrinkSharer
    val viewModelFactory: ViewModelProvider.Factory

    /** `true`, wenn ein Server (Firebase) eingerichtet ist – nur dann gibt es Trinkrunden. */
    val isSocialAvailable: Boolean

    /** Push-Ereignisse (neues Token, eingehende Nachricht); `null` ohne Server. */
    val pushHandler: PushMessageHandler?

    /**
     * Beim App-Start (nur mit Server): Push-Token auffrischen und im abgemeldeten Zustand gelöschte geteilte
     * Getränke nachholen, sobald derselbe Nutzer (wieder) angemeldet ist.
     */
    fun startSocialBackgroundWork()
}

/** Composition Root: einziger Ort, an dem konkrete Klassen verdrahtet werden. */
class DefaultAppContainer(
    context: Context,
    localRepository: DrinkRepository = JsonFileDrinkRepository(File(context.filesDir, "drinks.json")),
    /** Lebt so lange wie die App: Teilen und Token-Pflege laufen weiter, auch wenn ein Bildschirm schließt. */
    private val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val social: SocialModule? = FirebaseSocialModuleFactory.createIfConfigured(context),
) : AppContainer {

    /** Mit Server: lokal speichern und zusätzlich in den Trinkrunden teilen (Decorator aus dem Kern). */
    private val repository: DrinkRepository = social?.let { module ->
        PublishingDrinkRepository(
            local = localRepository,
            publisher = module.drinkPublisher,
            scope = applicationScope,
            onPublishError = { error -> Log.w(TAG, "Getränk konnte nicht mit den Trinkrunden geteilt werden.", error) },
        )
    } ?: localRepository

    private val formatter = GermanShareFormatter()
    private val logDrink = DefaultLogDrinkUseCase(DefaultDrinkValidator(), repository)
    private val fetchDrinks = DefaultFetchDrinksUseCase(repository)
    private val deleteDrink = DefaultDeleteDrinkUseCase(repository)
    private val shareDrinks = DefaultShareDrinksUseCase(formatter, formatter)

    override val drinkSharer: DrinkSharer = IntentDrinkSharer()

    override val isSocialAvailable: Boolean = social != null

    override val pushHandler: PushMessageHandler? = social?.let { module ->
        DefaultPushMessageHandler(
            registrar = module.pushTokenRegistrar,
            notifier = AndroidFriendDrinkNotifier(context.applicationContext),
            signedInUser = module.signedInUser,
            scope = applicationScope,
            onError = { error -> Log.w(TAG, "Push-Token konnte nicht aktualisiert werden.", error) },
        )
    }

    override fun startSocialBackgroundWork() {
        val module = social ?: return
        pushHandler?.onAppStarted()
        module.pendingUnpublishSync.start(applicationScope)
    }

    override val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { DrinkListViewModel(fetchDrinks, deleteDrink, shareDrinks) }
        initializer { AddDrinkViewModel(logDrink) }

        // Die Trinkrunden-ViewModels gibt es nur mit Server; die UI fragt sie nur dann an.
        if (social != null) {
            val observeMyGroups = DefaultObserveMyGroupsUseCase(social.groupReader)
            val membership = social.groupMembershipService
            initializer { AccountViewModel(social.currentUserProvider, social.accountService) }
            initializer { FriendsViewModel(observeMyGroups, DefaultObserveFeedUseCase(social.feedReader)) }
            initializer {
                GroupsViewModel(
                    observeMyGroups = observeMyGroups,
                    createGroup = DefaultCreateGroupUseCase(membership),
                    joinGroup = DefaultJoinGroupUseCase(membership),
                    leaveGroup = DefaultLeaveGroupUseCase(membership),
                    inviteMessages = GermanInviteMessageFormatter(),
                )
            }
        }
    }

    private companion object {
        const val TAG = "DrinkWithMe"
    }
}
