package com.drinkwithme.app.speech

import kotlinx.coroutines.flow.StateFlow

/** Zustand der Sprachausgabe. */
enum class SpeakerStatus {
    /** Noch nicht gestartet oder startet gerade. */
    INITIALIZING,

    /** Deutsche Stimme bereit. */
    READY,

    /** Eine deutsche Stimme ist bekannt, aber ihre Daten fehlen – „Sprachdaten installieren“ anbieten. */
    MISSING_DATA,

    /** Die Sprachausgabe kann kein Deutsch – Hilfe „Vorlesen einrichten“ zeigen. */
    NO_GERMAN_VOICE,

    /** Keine Sprachausgabe installiert – Hilfe „Vorlesen einrichten“ (z. B. SherpaTTS aus F-Droid). */
    NO_ENGINE,
}

/** Liest Texte vor (Vorlese-Sätze aus `SpeechTexts`). */
interface Speaker {
    val status: StateFlow<SpeakerStatus>

    /** Startet die Sprachausgabe, falls noch nicht geschehen (kostet etwas Zeit, daher nicht beim App-Start). */
    fun prepare()

    /** Spricht [text] und unterbricht Vorheriges. Ist die Sprachausgabe noch nicht bereit, wird der Text nachgeholt. */
    fun speak(text: String)

    fun stop()
}

/** Ob ein Screenreader (gesprochenes Feedback, z. B. TalkBack) aktiv ist. */
fun interface ScreenReaderDetector {
    fun isScreenReaderActive(): Boolean
}

/**
 * Wann die App von sich aus spricht: Mit aktivem Screenreader nicht (der liest ohnehin vor und beide würden sich
 * überlagern) – außer der Nutzer hat ausdrücklich „Vorlesen“ gewählt (Knopf oder Benachrichtigungs-Aktion).
 */
object AnnouncementPolicy {
    fun shouldSpeak(screenReaderActive: Boolean, explicitRequest: Boolean): Boolean = explicitRequest || !screenReaderActive
}

/**
 * Wahl der Stimme: `Locale.GERMANY`, sonst `Locale.GERMAN` (Werte wie `TextToSpeech.setLanguage`:
 * ≥ 0 verfügbar, −1 Daten fehlen, −2 nicht unterstützt).
 */
object GermanVoiceSelection {
    const val LANG_MISSING_DATA = -1
    const val LANG_NOT_SUPPORTED = -2

    /** @param germanyResult Ergebnis für de-DE; @param germanResult Ergebnis für de (nur nötig, wenn de-DE fehlt). */
    fun status(germanyResult: Int, germanResult: Int?): SpeakerStatus = when {
        germanyResult >= 0 -> SpeakerStatus.READY
        germanResult != null && germanResult >= 0 -> SpeakerStatus.READY
        germanyResult == LANG_MISSING_DATA || germanResult == LANG_MISSING_DATA -> SpeakerStatus.MISSING_DATA
        else -> SpeakerStatus.NO_GERMAN_VOICE
    }
}

/** Texte rund ums Vorlesen. */
object SpeechHelpTexts {
    const val HELP_TITLE = "Vorlesen einrichten"
    const val HELP_TEXT = "Auf diesem Gerät ist keine deutsche Sprachausgabe eingerichtet. Installiere zum Beispiel die " +
        "kostenlose App SherpaTTS aus F-Droid und wähle sie in den Android-Einstellungen unter „Bedienungshilfen“ → " +
        "„Sprachausgabe“ als bevorzugtes Modul aus. Öffne danach Drink With Me erneut."
    const val MISSING_DATA_TEXT = "Für die deutsche Stimme fehlen noch Sprachdaten."
    const val SHERPA_FDROID_URL = "https://f-droid.org/packages/org.woheller69.ttsengine/"
    const val TEST_SENTENCE = "So klingt das Vorlesen. Anna trinkt eine Apfelschorle."
}
