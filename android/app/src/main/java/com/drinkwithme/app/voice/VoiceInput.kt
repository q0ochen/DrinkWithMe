package com.drinkwithme.app.voice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.result.contract.ActivityResultContract
import com.drinkwithme.core.bar.BarItem

/**
 * Anfrage an die Spracherkennungs-App des Systems. Bewusst **ohne** `EXTRA_PREFER_OFFLINE` (manche Erkenner
 * scheitern sonst ohne installiertes Offline-Paket) und ohne eigene Mikrofon-Berechtigung.
 */
data class VoiceRequest(
    val language: String,
    val maxResults: Int,
    val prompt: String,
    /** Bar-Namen und Aliase als Hinweis für den Erkenner (`EXTRA_BIASING_STRINGS`, erst ab Android 13). */
    val biasingStrings: List<String>,
)

object VoiceRequestFactory {
    const val LANGUAGE = "de-DE"
    const val MAX_RESULTS = 5
    const val PROMPT = "Was trinkst du?"
    const val BIASING_MIN_SDK = 33
    private const val MAX_BIASING_STRINGS = 100

    fun create(bar: List<BarItem>, sdkInt: Int): VoiceRequest = VoiceRequest(
        language = LANGUAGE,
        maxResults = MAX_RESULTS,
        prompt = PROMPT,
        biasingStrings = if (sdkInt >= BIASING_MIN_SDK) biasing(bar) else emptyList(),
    )

    private fun biasing(bar: List<BarItem>): List<String> = bar
        .flatMap { listOf(it.name) + it.aliases }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .take(MAX_BIASING_STRINGS)
}

/** Startet die Spracherkennung (Activity Result API) und liefert bis zu fünf Kandidaten oder `null` (abgebrochen). */
class RecognizeSpeechContract : ActivityResultContract<VoiceRequest, List<String>?>() {

    override fun createIntent(context: Context, input: VoiceRequest): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, input.language)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, input.maxResults)
            putExtra(RecognizerIntent.EXTRA_PROMPT, input.prompt)
            if (input.biasingStrings.isNotEmpty() && Build.VERSION.SDK_INT >= VoiceRequestFactory.BIASING_MIN_SDK) {
                putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(input.biasingStrings))
            }
        }

    override fun parseResult(resultCode: Int, intent: Intent?): List<String>? =
        if (resultCode == Activity.RESULT_OK) intent?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) else null
}

/** Ob Spracheingabe möglich ist – sonst wird der Mikrofon-Knopf gar nicht gezeigt. */
fun interface VoiceInputAvailability {
    fun isAvailable(): Boolean
}

/** Verfügbar, wenn eine App `ACTION_RECOGNIZE_SPEECH` annimmt **und** ein Erkennungsdienst vorhanden ist. */
class AndroidVoiceInputAvailability(private val context: Context) : VoiceInputAvailability {
    override fun isAvailable(): Boolean {
        val activities = context.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0)
        return activities.isNotEmpty() && SpeechRecognizer.isRecognitionAvailable(context)
    }
}
