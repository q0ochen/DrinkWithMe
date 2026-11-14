package com.drinkwithme.core.voice

import com.drinkwithme.core.bar.BarItem

/** Ergebnis des Sprachabgleichs. */
sealed interface VoiceMatch {
    /** Eindeutiges Getränk aus der Bar. */
    data class Drink(val item: BarItem) : VoiceMatch

    /** Zwei Getränke kommen ähnlich gut in Frage – die App fragt nach („Meinst du … oder …?“). */
    data class Ambiguous(val first: BarItem, val second: BarItem) : VoiceMatch

    /** Ein Sprachbefehl. */
    data class Command(val cmd: VoiceCommand) : VoiceMatch

    /** Nichts erkannt. */
    data object NoMatch : VoiceMatch
}

/** Sprachbefehle: „rückgängig“/„nein“, „vorlesen“, „wie viel heute“. */
enum class VoiceCommand { UNDO, READ_ALOUD, TODAY }
