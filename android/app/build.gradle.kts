plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase ist optional: Ohne android/app/google-services.json baut und läuft die App wie bisher
// (nur lokal); die Trinkrunden-Bereiche zeigen dann „Server noch nicht eingerichtet“.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.drinkwithme.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.drinkwithme.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Fester Debug-Schlüssel (kein Geheimnis, nur für Test-Builds): Dadurch haben alle
    // APKs aus GitHub Actions dieselbe Signatur und lassen sich als Update über eine
    // bereits installierte Version installieren, ohne dass gespeicherte Getränke verloren gehen.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Trinkrunden: Firebase (Versionen über die BoM; die KTX-APIs sind seit BoM 32.5.0 in den Hauptmodulen).
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-messaging")
    // Task.await() für die Firebase-Aufrufe in Coroutinen.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Google-Anmeldung über den Android Credential Manager.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Tests der reinen Kotlin-Logik des App-Moduls (Mapper, Formatierer, ViewModels): ./gradlew :app:testDebugUnitTest
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
