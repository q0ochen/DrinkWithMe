package com.drinkwithme.app.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Textdatei, die **atomar** ersetzt wird: Erst in `<name>.tmp` schreiben und auf den Datenträger zwingen, dann
 * umbenennen. Bricht die App mittendrin ab, bleibt die alte Datei vollständig erhalten.
 */
class AtomicTextFile(private val file: File) {

    /** Inhalt oder `null`, wenn die Datei fehlt oder nicht lesbar ist. */
    fun readOrNull(): String? = try {
        if (file.exists()) file.readText(Charsets.UTF_8) else null
    } catch (error: IOException) {
        null
    }

    /** Ersetzt den Inhalt atomar. */
    @Throws(IOException::class)
    fun write(text: String) {
        val directory = file.absoluteFile.parentFile ?: throw IOException("Kein Ordner für ${file.name}")
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Ordner ${directory.name} fehlt")
        val temp = File(directory, "${file.name}.tmp")
        FileOutputStream(temp).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        if (!temp.renameTo(file)) {
            // Nur auf Dateisystemen, die beim Umbenennen nicht ersetzen (nicht Android/Linux).
            file.delete()
            if (!temp.renameTo(file)) throw IOException("${file.name} konnte nicht gespeichert werden")
        }
    }

    /**
     * Legt eine unlesbare Datei beiseite (`<name>.defekt-<millis>`), statt sie beim nächsten Speichern zu
     * überschreiben – so bleibt sie für eine Rettung erhalten.
     */
    fun quarantine(nowMillis: Long): File? {
        if (!file.exists()) return null
        val target = File(file.absoluteFile.parentFile, "${file.name}.defekt-$nowMillis")
        return if (file.renameTo(target)) target else null
    }
}
