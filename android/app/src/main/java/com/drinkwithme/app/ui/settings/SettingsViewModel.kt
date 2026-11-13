package com.drinkwithme.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drinkwithme.app.account.AccountController
import com.drinkwithme.app.data.AppSettings
import com.drinkwithme.app.push.DistributorInfo
import com.drinkwithme.app.speech.Speaker
import com.drinkwithme.app.speech.SpeakerStatus
import com.drinkwithme.app.speech.SpeechHelpTexts
import com.drinkwithme.app.supabase.PushSubscription
import com.drinkwithme.app.supabase.SocialException
import com.drinkwithme.app.ui.common.MessageQueue
import com.drinkwithme.core.sharing.AlcoholSharingConsent
import com.drinkwithme.core.social.UserProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Was die Einstellungen über Push wissen und tun dürfen (unter Android: UnifiedPush). */
interface PushSettings {
    /** `false` ohne Server oder ohne öffentlichen VAPID-Schlüssel – dann nur der Abruf alle 15 Minuten. */
    val available: Boolean
    val endpoint: StateFlow<PushSubscription?>
    val lastError: StateFlow<String?>
    fun distributors(): List<DistributorInfo>
    fun selected(): String?
    fun use(distributor: DistributorInfo): Boolean
    fun disable()
}

/** Stand des Push-Assistenten. */
sealed interface PushStatus {
    data object Unavailable : PushStatus

    /** Kein Distributor installiert: ntfy (F-Droid) empfehlen. */
    data object NoDistributor : PushStatus

    /** Distributoren vorhanden, aber keiner gewählt. */
    data class Choose(val distributors: List<DistributorInfo>) : PushStatus

    /** Gewählt, Endpunkt noch nicht da (Distributor antwortet noch / Bestätigung in der Push-App nötig). */
    data class Waiting(val distributor: DistributorInfo) : PushStatus

    data class Active(val distributor: DistributorInfo) : PushStatus
}

data class SettingsUiState(
    val configured: Boolean = false,
    val user: UserProfile? = null,
    val email: String? = null,
    val alcoholConsent: AlcoholSharingConsent = AlcoholSharingConsent.UNKNOWN,
    val showAlcoholBands: Boolean = true,
    val announceLogged: Boolean = true,
    val autoReadEvents: Boolean = false,
    val speakerStatus: SpeakerStatus = SpeakerStatus.INITIALIZING,
    val push: PushStatus = PushStatus.Unavailable,
    val pushError: String? = null,
    val confirmDeleteAccount: Boolean = false,
    val confirmSignOut: Boolean = false,
    val busy: Boolean = false,
    val speechHelpOpen: Boolean = false,
)

/** Einstellungen: Konto, Teilen, Benachrichtigungen, Vorlesen, Anzeige, Info. */
class SettingsViewModel(
    private val account: AccountController,
    private val settings: AppSettings,
    private val push: PushSettings,
    private val speaker: Speaker,
    private val messages: MessageQueue,
    private val io: CoroutineDispatcher,
) : ViewModel() {

    private val mutableState = MutableStateFlow(SettingsUiState(configured = account.isConfigured))
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(account.currentUser, account.currentEmail, settings.alcoholConsent) { user, email, consent ->
                Triple(user, email, consent)
            }.collect { (user, email, consent) ->
                mutableState.update { it.copy(user = user, email = email, alcoholConsent = consent) }
            }
        }
        viewModelScope.launch {
            combine(settings.showAlcoholBands.value, settings.announceLogged.value, settings.autoReadEvents.value) { bands, announce, autoRead ->
                Triple(bands, announce, autoRead)
            }.collect { (bands, announce, autoRead) ->
                mutableState.update { it.copy(showAlcoholBands = bands, announceLogged = announce, autoReadEvents = autoRead) }
            }
        }
        viewModelScope.launch {
            speaker.status.collect { status -> mutableState.update { it.copy(speakerStatus = status) } }
        }
        viewModelScope.launch {
            combine(push.endpoint, push.lastError) { _, error -> error }.collect { error ->
                mutableState.update { it.copy(pushError = error) }
                refreshPush()
            }
        }
    }

    // ---------- Konto ----------

    fun onSignOutRequested() = mutableState.update { it.copy(confirmSignOut = true) }

    fun onSignOutConfirmed(confirmed: Boolean) {
        mutableState.update { it.copy(confirmSignOut = false) }
        if (confirmed) busy { account.signOut(); messages.show(SIGNED_OUT) }
    }

    fun onDeleteAccountRequested() = mutableState.update { it.copy(confirmDeleteAccount = true) }

    fun onDeleteAccountConfirmed(confirmed: Boolean) {
        mutableState.update { it.copy(confirmDeleteAccount = false) }
        if (confirmed) busy { account.deleteAccount(); messages.show(ACCOUNT_DELETED) }
    }

    // ---------- Teilen und Anzeige ----------

    fun onAlcoholSharingChanged(allowed: Boolean) =
        settings.setAlcoholConsent(if (allowed) AlcoholSharingConsent.ALLOWED else AlcoholSharingConsent.DENIED)

    fun onShowAlcoholBandsChanged(show: Boolean) = settings.showAlcoholBands.set(show)

    fun onAnnounceLoggedChanged(enabled: Boolean) = settings.announceLogged.set(enabled)

    fun onAutoReadEventsChanged(enabled: Boolean) = settings.autoReadEvents.set(enabled)

    // ---------- Vorlesen ----------

    fun onPrepareSpeech() = speaker.prepare()

    fun onTestSpeech() {
        if (!speaker.speak(SpeechHelpTexts.TEST_SENTENCE)) mutableState.update { it.copy(speechHelpOpen = true) }
    }

    fun onSpeechHelp(open: Boolean) = mutableState.update { it.copy(speechHelpOpen = open) }

    // ---------- Benachrichtigungen ----------

    fun refreshPush() {
        if (!push.available) {
            mutableState.update { it.copy(push = PushStatus.Unavailable) }
            return
        }
        viewModelScope.launch {
            val status = withContext(io) {
                val installed = push.distributors()
                val selected = push.selected()
                val chosen = installed.firstOrNull { it.packageName == selected }
                when {
                    chosen != null && push.endpoint.value != null -> PushStatus.Active(chosen)
                    chosen != null -> PushStatus.Waiting(chosen)
                    installed.isEmpty() -> PushStatus.NoDistributor
                    else -> PushStatus.Choose(installed)
                }
            }
            mutableState.update { it.copy(push = status) }
        }
    }

    fun onUseDistributor(distributor: DistributorInfo) {
        viewModelScope.launch {
            val ok = withContext(io) { push.use(distributor) }
            if (!ok) messages.show(PUSH_SETUP_FAILED)
            refreshPush()
        }
    }

    fun onDisablePush() {
        viewModelScope.launch {
            withContext(io) { push.disable() }
            messages.show(PUSH_DISABLED)
            refreshPush()
        }
    }

    private fun busy(block: suspend () -> Unit) {
        if (state.value.busy) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            try {
                block()
            } catch (error: SocialException) {
                messages.show(error.userMessage)
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        const val SIGNED_OUT = "Du bist abgemeldet. Bar und Verlauf bleiben auf diesem Gerät."
        const val ACCOUNT_DELETED = "Dein Konto wurde gelöscht. Dein Verlauf auf diesem Gerät bleibt erhalten."
        const val PUSH_SETUP_FAILED = "Die Benachrichtigungen konnten nicht eingerichtet werden."
        const val PUSH_DISABLED = "Sofortige Benachrichtigungen sind aus. Die App prüft etwa alle 15 Minuten."
    }
}
