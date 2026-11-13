import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Plattformunabhängiger Kern (Bar, Trinkhistorie, Gesundheit, Statistik, Teilen-Regel, Sprache,
// Vorlesen, Ports der Vernetzung) – reines Kotlin/JVM ohne Android-Abhängigkeiten, damit die
// Geschäftslogik schnell und vollständig testbar bleibt (Vertrag: docs/ARCHITEKTUR.md, Abschnitt 1).
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Flow/suspend in den Ports der Vernetzung und Hintergrund-Teilen im Decorator (frei, Maven Central).
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
