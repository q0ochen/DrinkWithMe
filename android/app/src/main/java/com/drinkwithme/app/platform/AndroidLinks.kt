package com.drinkwithme.app.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Öffnet Seiten und System-Bildschirme. Die Google-Anmeldung läuft **immer im Browser** (Custom Tab, sonst
 * `ACTION_VIEW`) – nie in einer WebView, damit Google-Passwörter die App nie erreichen.
 */
class AndroidLinks {

    /** Anmelde-Seite öffnen; `false`, wenn es keinen Browser gibt (z. B. manche Fernseher). */
    fun openSignIn(activityContext: Context, url: String): Boolean {
        val uri = Uri.parse(url)
        if (CustomTabsClient.getPackageName(activityContext, null) != null) {
            try {
                CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(activityContext, uri)
                return true
            } catch (error: ActivityNotFoundException) {
                // Weiter mit einem normalen Browser.
            }
        }
        return openInBrowser(activityContext, url)
    }

    fun openInBrowser(activityContext: Context, url: String): Boolean =
        start(activityContext, Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))

    /** „Sprachdaten installieren“ der gewählten Sprachausgabe. */
    fun installSpeechData(activityContext: Context): Boolean =
        start(activityContext, Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))

    /** Android-Einstellungen der Sprachausgabe (nicht auf jedem Gerät vorhanden). */
    fun openSpeechSettings(activityContext: Context): Boolean = start(activityContext, Intent(ACTION_TTS_SETTINGS))

    /** Benachrichtigungs-Einstellungen dieser App. */
    fun openNotificationSettings(activityContext: Context): Boolean = start(
        activityContext,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activityContext.packageName),
    )

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (error: ActivityNotFoundException) {
        false
    } catch (error: SecurityException) {
        false
    }

    private companion object {
        const val ACTION_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
    }
}
