package com.drinkwithme.app.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

/** Teilen über das Android-Teilen-Menü (Bilder per FileProvider, Texte wie Einladungs-Links direkt). */
class AndroidSharing(private val appContext: Context) {
    private val files = ShareImageFiles(appContext.cacheDir)

    /** Speichert das Bild als PNG im freigegebenen Cache-Ordner; `null`, wenn das nicht klappt (z. B. Speicher voll). */
    fun saveImage(bitmap: Bitmap, date: LocalDate): File? =
        files.writeImage(date) { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }

    /** Öffnet das Teilen-Menü für ein gespeichertes Bild. `false`, wenn keine App teilen kann. */
    fun shareImage(activityContext: Context, file: File, title: String): Boolean {
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = ShareImageFiles.MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return start(activityContext, Intent.createChooser(send, title))
    }

    /** Öffnet das Teilen-Menü für einen Text (z. B. Einladungs-Link). */
    fun shareText(activityContext: Context, text: String, title: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return start(activityContext, Intent.createChooser(send, title))
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (error: ActivityNotFoundException) {
        false
    }
}
