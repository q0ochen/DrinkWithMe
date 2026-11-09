plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // Liest android/app/google-services.json ein. Wird im App-Modul nur angewendet,
    // wenn diese Datei existiert (Firebase ist optional, siehe docs/TRINKRUNDEN_ARCHITEKTUR.md).
    id("com.google.gms.google-services") version "4.4.2" apply false
}
