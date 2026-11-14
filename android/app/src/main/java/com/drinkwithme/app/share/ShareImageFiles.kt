package com.drinkwithme.app.share

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDate

/**
 * Ablage der geteilten Statistik-Bilder im Cache-Unterordner `shared_images/` (nur dieser ist per FileProvider
 * freigegeben, siehe `res/xml/file_paths.xml`). Es bleibt immer nur das neueste Bild liegen.
 */
class ShareImageFiles(cacheDir: File) {
    val directory: File = File(cacheDir, DIRECTORY)

    /** Datei für ein neues Bild; ältere Bilder werden entfernt. */
    fun newImageFile(date: LocalDate): File {
        directory.mkdirs()
        directory.listFiles()?.forEach { it.delete() }
        return File(directory, fileName(date))
    }

    /**
     * Schreibt ein neues Bild mit [write] (`false` = nicht vollständig geschrieben). `null`, wenn das nicht klappt
     * (Speicher voll, Ordner nicht anlegbar) – dann keine halbe Datei liegen lassen und nichts teilen.
     */
    fun writeImage(date: LocalDate, write: (OutputStream) -> Boolean): File? {
        val file = newImageFile(date)
        val complete = try {
            FileOutputStream(file).use(write)
        } catch (error: IOException) {
            false
        }
        if (!complete) file.delete()
        return file.takeIf { complete }
    }

    companion object {
        const val DIRECTORY = "shared_images"
        const val SAVE_FAILED = "Das Bild konnte nicht gespeichert werden. Ist der Speicher voll?"

        /** Lesbarer, eindeutiger Name ohne Leerzeichen: `drink-with-me-statistik-2026-11-10.png`. */
        fun fileName(date: LocalDate): String = "drink-with-me-statistik-$date.png"

        const val MIME_TYPE = "image/png"
    }
}
