import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Plattformunabhängiger Kern (Domain, Use Cases, Sharing) – reines Kotlin/JVM,
// damit die Geschäftslogik ohne Android-Abhängigkeiten testbar bleibt.
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
    // Flow/suspend in den Schnittstellen der Trinkrunden (reines Kotlin, kein Android).
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
