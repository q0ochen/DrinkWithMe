package com.drinkwithme.app.speech

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Vorlesen mit Androids `TextToSpeech` – mit der vom Nutzer gewählten Sprachausgabe (frei, z. B. SherpaTTS).
 * Deutsch: `Locale.GERMANY`, Rückfall `Locale.GERMAN`.
 */
class AndroidSpeaker(context: Context) : Speaker, TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val statusFlow = MutableStateFlow(SpeakerStatus.INITIALIZING)
    private val lock = Any()
    private var engine: TextToSpeech? = null
    private var pendingText: String? = null
    private var earlyInitStatus: Int? = null
    private var utteranceCounter = 0

    override val status: StateFlow<SpeakerStatus> = statusFlow.asStateFlow()

    override fun prepare() {
        val created = synchronized(lock) {
            if (engine != null) return
            TextToSpeech(appContext, this).also { engine = it }
        }
        // Manche Sprachausgaben melden sich schon im Konstruktor zurück (bevor engine gesetzt war).
        val early = synchronized(lock) { earlyInitStatus.also { earlyInitStatus = null } }
        if (early != null) applyInit(created, early)
    }

    override fun onInit(status: Int) {
        val tts = synchronized(lock) { engine.also { if (it == null) earlyInitStatus = status } } ?: return
        applyInit(tts, status)
    }

    private fun applyInit(tts: TextToSpeech, status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            statusFlow.value = SpeakerStatus.NO_ENGINE
            return
        }
        val germany = tts.setLanguage(Locale.GERMANY)
        val german = if (germany >= 0) null else tts.setLanguage(Locale.GERMAN)
        statusFlow.value = GermanVoiceSelection.status(germany, german)
        val text = synchronized(lock) { pendingText.also { pendingText = null } }
        if (text != null) speak(text)
    }

    override fun speak(text: String) {
        prepare()
        val tts = synchronized(lock) {
            // Prüfung im Lock: applyInit setzt erst den Status und holt dann den vorgemerkten Text ab.
            if (statusFlow.value == SpeakerStatus.INITIALIZING) {
                pendingText = text
                return
            }
            engine.takeIf { statusFlow.value == SpeakerStatus.READY }
        } ?: return
        val id = synchronized(lock) { "drinkwithme-${++utteranceCounter}" }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    override fun stop() {
        synchronized(lock) { engine }?.stop()
    }

    /** Nach dem Installieren von Sprachdaten neu starten, damit die Stimme erkannt wird. */
    fun restart() {
        synchronized(lock) {
            engine?.shutdown()
            engine = null
        }
        statusFlow.value = SpeakerStatus.INITIALIZING
        prepare()
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
