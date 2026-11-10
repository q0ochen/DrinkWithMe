package com.drinkwithme.app.share

import java.io.File
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

    companion object {
        const val DIRECTORY = "shared_images"

        /** Lesbarer, eindeutiger Name ohne Leerzeichen: `drink-with-me-statistik-2026-11-10.png`. */
        fun fileName(date: LocalDate): String = "drink-with-me-statistik-$date.png"

        const val MIME_TYPE = "image/png"
    }
}
