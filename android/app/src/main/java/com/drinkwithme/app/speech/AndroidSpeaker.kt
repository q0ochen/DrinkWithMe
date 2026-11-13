package com.drinkwithme.app.speech

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Die Teile von Androids `TextToSpeech`, die das Vorlesen braucht (dadurch ohne Android testbar). */
interface SpeechEngine {
    /** Wie `TextToSpeech.setLanguage`: ≥ 0 verfügbar, −1 Daten fehlen, −2 nicht unterstützt. */
    fun setLanguage(locale: Locale): Int

    /** Spricht sofort und unterbricht Vorheriges. */
    fun speak(text: String, utteranceId: String)

    fun stop()

    fun shutdown()

    companion object {
        /** Wie `TextToSpeech.SUCCESS`. */
        const val SUCCESS = 0
    }
}

/** Startet eine Sprachausgabe; [create]s Rückruf meldet, ob sie bereit ist (manche melden sich schon währenddessen). */
fun interface SpeechEngineFactory {
    fun create(onInit: (Int) -> Unit): SpeechEngine
}

/** [SpeechEngineFactory] mit Androids `TextToSpeech` und der vom Nutzer gewählten Sprachausgabe (z. B. SherpaTTS). */
class AndroidTextToSpeechFactory(context: Context) : SpeechEngineFactory {
    private val appContext = context.applicationContext

    override fun create(onInit: (Int) -> Unit): SpeechEngine {
        val tts = TextToSpeech(appContext) { status -> onInit(status) }
        return object : SpeechEngine {
            override fun setLanguage(locale: Locale): Int = tts.setLanguage(locale)
            override fun speak(text: String, utteranceId: String) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            }
            override fun stop() {
                tts.stop()
            }
            override fun shutdown() {
                tts.shutdown()
            }
        }
    }
}

/**
 * Vorlesen mit Androids `TextToSpeech` – mit der vom Nutzer gewählten Sprachausgabe (frei, z. B. SherpaTTS).
 * Deutsch: `Locale.GERMANY`, Rückfall `Locale.GERMAN`. Fehlt etwas, startet [recheckIfNotReady] die Sprachausgabe
 * nach der Rückkehr in die App neu (eine laufende `TextToSpeech`-Instanz bemerkt neu installierte Stimmen oder eine
 * neu gewählte Sprachausgabe nicht von selbst).
 */
class AndroidSpeaker(private val engines: SpeechEngineFactory) : Speaker {

    constructor(context: Context) : this(AndroidTextToSpeechFactory(context))

    private val statusFlow = MutableStateFlow(SpeakerStatus.INITIALIZING)
    private val lock = Any()
    private var engine: SpeechEngine? = null
    private var generation = 0
    private var pendingText: String? = null
    private var earlyInitStatus: Int? = null
    private var utteranceCounter = 0

    override val status: StateFlow<SpeakerStatus> = statusFlow.asStateFlow()

    override fun prepare() {
        val created = synchronized(lock) {
            if (engine != null) return
            val current = ++generation
            engines.create { status -> onEngineInit(current, status) }.also { engine = it }
        }
        // Manche Sprachausgaben melden sich schon beim Erzeugen zurück (bevor engine gesetzt war).
        val early = synchronized(lock) { earlyInitStatus.also { earlyInitStatus = null } }
        if (early != null) applyInit(created, early)
    }

    private fun onEngineInit(engineGeneration: Int, status: Int) {
        val tts = synchronized(lock) {
            // Rückmeldung einer schon ersetzten Sprachausgabe.
            if (engineGeneration != generation) return
            engine.also { if (it == null) earlyInitStatus = status }
        } ?: return
        applyInit(tts, status)
    }

    private fun applyInit(tts: SpeechEngine, status: Int) {
        if (status != SpeechEngine.SUCCESS) {
            synchronized(lock) { pendingText = null }
            statusFlow.value = SpeakerStatus.NO_ENGINE
            return
        }
        val germany = tts.setLanguage(Locale.GERMANY)
        val german = if (germany >= 0) null else tts.setLanguage(Locale.GERMAN)
        statusFlow.value = GermanVoiceSelection.status(germany, german)
        val text = synchronized(lock) { pendingText.also { pendingText = null } }
        if (text != null) speak(text)
    }

    override fun speak(text: String): Boolean {
        prepare()
        val tts = synchronized(lock) {
            // Prüfung im Lock: applyInit setzt erst den Status und holt dann den vorgemerkten Text ab.
            if (statusFlow.value == SpeakerStatus.INITIALIZING) {
                pendingText = text
                return true
            }
            engine.takeIf { statusFlow.value == SpeakerStatus.READY }
        } ?: return false
        val id = synchronized(lock) { "drinkwithme-${++utteranceCounter}" }
        tts.speak(text, id)
        return true
    }

    override fun recheckIfNotReady() {
        val old = synchronized(lock) {
            if (engine == null || !statusFlow.value.needsSetup) return
            engine.also {
                engine = null
                pendingText = null
            }
        }
        old?.shutdown()
        statusFlow.value = SpeakerStatus.INITIALIZING
        prepare()
    }

    override fun stop() {
        synchronized(lock) { engine }?.stop()
    }
}

/** Erkennt TalkBack & Co. (Dienste mit gesprochenem Feedback). */
class AndroidScreenReaderDetector(context: Context) : ScreenReaderDetector {
    private val manager = context.applicationContext.getSystemService(AccessibilityManager::class.java)

    override fun isScreenReaderActive(): Boolean {
        val accessibility = manager ?: return false
        if (!accessibility.isEnabled) return false
        return accessibility.isTouchExplorationEnabled ||
            accessibility.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN).isNotEmpty()
    }
}
