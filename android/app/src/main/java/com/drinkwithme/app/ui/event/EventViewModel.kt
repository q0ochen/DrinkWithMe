package com.drinkwithme.app.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drinkwithme.app.routing.AppRequest
import com.drinkwithme.app.speech.AnnouncementPolicy
import com.drinkwithme.app.speech.ScreenReaderDetector
import com.drinkwithme.app.speech.Speaker
import com.drinkwithme.app.speech.SpeakerStatus
import com.drinkwithme.app.stats.GermanFormat
import com.drinkwithme.app.supabase.EventLookup
import com.drinkwithme.app.supabase.SocialException
import com.drinkwithme.core.bar.DrinkIcon
import com.drinkwithme.core.social.FriendDrinkEvent
import com.drinkwithme.core.speech.EventTexts
import com.drinkwithme.core.speech.GermanArticles
import com.drinkwithme.core.speech.SpeechTexts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.ZoneId

/** Ereignis-Bildschirm: großer Satz, Details, Vorlese-Satz und Icon. */
data class EventUiState(
    val loading: Boolean = true,
    val headline: String = "",
    val detail: String? = null,
    val speech: String = "",
    val icon: DrinkIcon = DrinkIcon.OTHER,
    /** Fehlt eine (deutsche) Sprachausgabe, zeigt der Bildschirm die Hilfe „Vorlesen einrichten“. */
    val speakerStatus: SpeakerStatus = SpeakerStatus.INITIALIZING,
    val speechHelpOpen: Boolean = false,
)

/** Liest „Anna trinkt gerade“ / „Apfelschorle (500 ml)“ aus einer Benachrichtigung, falls das Ereignis offline nicht ladbar ist. */
object NotificationTextParser {
    private const val TITLE_SUFFIX = " trinkt gerade"
    private val BODY = Regex("^(.+) \\((\\d{1,4}) ml\\)$")

    data class Parsed(val author: String, val drink: String, val volumeMilliliters: Int?)

    fun parse(title: String?, body: String?): Parsed? {
        val author = title?.trim()?.takeIf { it.endsWith(TITLE_SUFFIX) }?.removeSuffix(TITLE_SUFFIX)?.trim() ?: return null
        val text = body?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val match = BODY.matchEntire(text)
        return if (match != null) {
            Parsed(author, match.groupValues[1].trim(), match.groupValues[2].toIntOrNull())
        } else {
            Parsed(author, text, null)
        }
    }
}

/**
 * Lädt das Ereignis, zeigt den großen Satz („Anna trinkt eine Apfelschorle.“) und liest ihn vor: automatisch bei
 * der Benachrichtigungs-Aktion „Vorlesen“ (ausdrücklich gewünscht) oder, wenn eingestellt, beim Öffnen – dann aber
 * nicht bei aktivem Screenreader.
 */
class EventViewModel(
    private val request: AppRequest.OpenEvent,
    private val lookup: EventLookup?,
    private val speaker: Speaker,
    private val screenReader: ScreenReaderDetector,
    private val autoReadSetting: () -> Boolean,
    private val clock: Clock,
    private val zone: () -> ZoneId,
) : ViewModel() {

    private val mutableState = MutableStateFlow(fallbackState(loading = true))
    val state: StateFlow<EventUiState> = mutableState.asStateFlow()

    init {
        speaker.prepare()
        viewModelScope.launch {
            speaker.status.collect { status -> mutableState.update { it.copy(speakerStatus = status) } }
        }
        viewModelScope.launch {
            val event = try {
                lookup?.event(request.eventId)
            } catch (error: SocialException) {
                null
            }
            val loaded = event?.let(::stateFor) ?: fallbackState(loading = false)
            mutableState.update { loaded.copy(speakerStatus = it.speakerStatus, speechHelpOpen = it.speechHelpOpen) }
            val explicit = request.autoRead
            if (explicit) {
                speakOrHelp()
            } else if (autoReadSetting() && AnnouncementPolicy.shouldSpeak(screenReader.isScreenReaderActive(), false)) {
                speaker.speak(state.value.speech)
            }
        }
    }

    /** Knopf „Vorlesen“. Ohne nutzbare Stimme öffnet er die Hilfe „Vorlesen einrichten“. */
    fun onReadAloud() = speakOrHelp()

    fun onSpeechHelp(open: Boolean) = mutableState.update { it.copy(speechHelpOpen = open) }

    private fun speakOrHelp() {
        if (!speaker.speak(state.value.speech)) onSpeechHelp(true)
    }

    private fun stateFor(event: FriendDrinkEvent): EventUiState {
        val minutes = Duration.between(event.consumedAt, clock.instant()).toMinutes()
        val whenText = when {
            minutes < 1 -> "gerade eben"
            minutes < 60 -> "vor $minutes Minuten"
            else -> "um ${GermanFormat.time(event.consumedAt, zone())} Uhr"
        }
        return EventUiState(
            loading = false,
            headline = EventTexts.headline(event),
            detail = "${GermanFormat.milliliters(event.volumeMilliliters)} · $whenText",
            speech = SpeechTexts.drinkEvent(event, withVolume = true),
            icon = event.icon,
        )
    }

    private fun fallbackState(loading: Boolean): EventUiState {
        val parsed = NotificationTextParser.parse(request.title, request.body)
        if (parsed == null) {
            val text = listOfNotNull(request.title, request.body).joinToString(": ").ifBlank { "Ein Freund trinkt gerade etwas." }
            return EventUiState(loading = loading, headline = text, speech = SpeechTexts.speakable(text))
        }
        return EventUiState(
            loading = loading,
            headline = "${parsed.author} trinkt ${GermanArticles.withIndefiniteArticle(parsed.drink)}.",
            detail = parsed.volumeMilliliters?.let { GermanFormat.milliliters(it) },
            speech = SpeechTexts.drinkEvent(parsed.author, parsed.drink, parsed.volumeMilliliters),
        )
    }
}
