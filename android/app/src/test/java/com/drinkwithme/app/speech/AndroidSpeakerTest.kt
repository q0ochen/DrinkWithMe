package com.drinkwithme.app.speech

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Sprachausgabe nach Drehbuch: [languageResult] wie `TextToSpeech.setLanguage`, Init-Status per [finishInit]. */
private class FakeEngine(private val onInit: (Int) -> Unit, var languageResult: Int) : SpeechEngine {
    val spoken = mutableListOf<String>()
    val languages = mutableListOf<Locale>()
    var shutDown = false

    fun finishInit(status: Int) = onInit(status)

    override fun setLanguage(locale: Locale): Int {
        languages += locale
        return languageResult
    }

    override fun speak(text: String, utteranceId: String) {
        spoken += text
    }

    override fun stop() = Unit

    override fun shutdown() {
        shutDown = true
    }
}

private class FakeEngines(var languageResult: Int = 0) : SpeechEngineFactory {
    val created = mutableListOf<FakeEngine>()
    val last: FakeEngine get() = created.last()

    override fun create(onInit: (Int) -> Unit): SpeechEngine = FakeEngine(onInit, languageResult).also { created += it }
}

class AndroidSpeakerTest {
    private val engines = FakeEngines()
    private val speaker = AndroidSpeaker(engines)

    @Test
    fun speaksWhenReadyAndReportsIt() {
        speaker.prepare()
        engines.last.finishInit(SpeechEngine.SUCCESS)
        assertEquals(SpeakerStatus.READY, speaker.status.value)
        assertTrue(speaker.speak("Anna trinkt eine Apfelschorle."))
        assertEquals(listOf("Anna trinkt eine Apfelschorle."), engines.last.spoken)
    }

    @Test
    fun textBeforeTheEngineIsReadyIsSpokenLater() {
        assertTrue(speaker.speak("Hallo"), "vorgemerkt")
        engines.last.finishInit(SpeechEngine.SUCCESS)
        assertEquals(listOf("Hallo"), engines.last.spoken)
    }

    @Test
    fun withoutGermanVoiceSpeakReportsFailure() {
        engines.languageResult = GermanVoiceSelection.LANG_MISSING_DATA
        speaker.prepare()
        engines.last.finishInit(SpeechEngine.SUCCESS)
        assertEquals(SpeakerStatus.MISSING_DATA, speaker.status.value)
        assertFalse(speaker.speak("Hallo"), "nichts wird gesprochen – die Oberfläche zeigt dann die Hilfe")
        assertTrue(engines.last.spoken.isEmpty())
    }

    @Test
    fun recoversAfterVoiceDataWasInstalled() {
        engines.languageResult = GermanVoiceSelection.LANG_MISSING_DATA
        speaker.prepare()
        engines.last.finishInit(SpeechEngine.SUCCESS)
        speaker.prepare()
        assertEquals(1, engines.created.size, "prepare startet nicht ständig neu")

        // Nutzer installiert die Sprachdaten und kehrt in die App zurück.
        engines.languageResult = 0
        speaker.recheckIfNotReady()
        assertTrue(engines.created.first().shutDown)
        assertEquals(2, engines.created.size, "neue Sprachausgabe (bindet auch eine neu gewählte)")
        assertEquals(SpeakerStatus.INITIALIZING, speaker.status.value)
        engines.last.finishInit(SpeechEngine.SUCCESS)
        assertEquals(Locale.GERMANY, engines.last.languages.first(), "setLanguage läuft erneut")
        assertEquals(SpeakerStatus.READY, speaker.status.value)
        assertTrue(speaker.speak("Test"))
    }

    @Test
    fun recoversAfterAnEngineWasInstalled() {
        speaker.prepare()
        engines.last.finishInit(-1)
        assertEquals(SpeakerStatus.NO_ENGINE, speaker.status.value)
        speaker.recheckIfNotReady()
        engines.last.finishInit(SpeechEngine.SUCCESS)
        assertEquals(SpeakerStatus.READY, speaker.status.value)
    }

    @Test
    fun recheckLeavesAWorkingOrUnusedSpeakerAlone() {
        speaker.recheckIfNotReady()
        assertTrue(engines.created.isEmpty(), "noch nie gebraucht: nichts starten")
        speaker.prepare()
        speaker.recheckIfNotReady()
        assertEquals(1, engines.created.size, "startet gerade: nicht unterbrechen")
        engines.last.finishInit(SpeechEngine.SUCCESS)
        speaker.recheckIfNotReady()
        assertEquals(1, engines.created.size, "bereit: nichts zu tun")
    }

    @Test
    fun engineThatAnswersInsideItsConstructorIsHandled() {
        val early = object : SpeechEngineFactory {
            override fun create(onInit: (Int) -> Unit): SpeechEngine {
                onInit(SpeechEngine.SUCCESS)
                return FakeEngine({}, 0)
            }
        }
        val speaker = AndroidSpeaker(early)
        speaker.prepare()
        assertEquals(SpeakerStatus.READY, speaker.status.value)
    }

    @Test
    fun onlyBrokenSetupsNeedHelp() {
        assertTrue(SpeakerStatus.NO_ENGINE.needsSetup)
        assertTrue(SpeakerStatus.NO_GERMAN_VOICE.needsSetup)
        assertTrue(SpeakerStatus.MISSING_DATA.needsSetup)
        assertFalse(SpeakerStatus.READY.needsSetup)
        assertFalse(SpeakerStatus.INITIALIZING.needsSetup)
    }
}
