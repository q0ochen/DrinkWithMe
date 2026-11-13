// Nur freie Gradle-Plugins (F-Droid-tauglich): kein Google-Services-Plugin, kein Firebase.
// Kotlin 2.2.21, weil der UnifiedPush-Connector (3.3.x) mit Kotlin 2.2 gebaut ist; laut JetBrains-Tabelle
// unterstützt Kotlin 2.2.20–2.2.21 AGP 7.3.1–8.11.1 und Gradle 7.6.3–8.14 (hier: AGP 8.7.3, Gradle 8.11.1).
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
