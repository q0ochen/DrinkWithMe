import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Öffentliche Server-Werte (nicht geheim, dürfen eingecheckt werden): android/app/supabase.properties mit
// supabaseUrl, supabaseAnonKey, vapidPublicKey und webAppUrl (Vorlage: supabase.properties.example).
// Fehlt die Datei, baut die App trotzdem; die Vernetzung zeigt dann „Server noch nicht eingerichtet“.
val supabaseProperties = Properties().apply {
    val propertiesFile = file("supabase.properties")
    if (propertiesFile.exists()) propertiesFile.inputStream().use { load(it) }
}

fun supabaseValue(key: String): String = supabaseProperties.getProperty(key, "").trim()

/** Als Java-String-Literal für BuildConfig (Backslashes und Anführungszeichen maskiert). */
fun String.asJavaStringLiteral(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.drinkwithme.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.drinkwithme.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"

        buildConfigField("String", "SUPABASE_URL", supabaseValue("supabaseUrl").asJavaStringLiteral())
        buildConfigField("String", "SUPABASE_ANON_KEY", supabaseValue("supabaseAnonKey").asJavaStringLiteral())
        buildConfigField("String", "VAPID_PUBLIC_KEY", supabaseValue("vapidPublicKey").asJavaStringLiteral())
        buildConfigField("String", "WEB_APP_URL", supabaseValue("webAppUrl").asJavaStringLiteral())
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Sicherheitsnetz: Android-Methoden liefern in JVM-Tests Standardwerte statt einer Ausnahme.
        // Die getestete Logik ist ohnehin frei von android.*.
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            // Doppelte Lizenz-/Metadateien aus OkHttp, Okio und Tink (kommt mit dem UnifiedPush-Connector).
            excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    // Nur freie Bibliotheken (Apache 2.0) aus Google Maven (AndroidX) und Maven Central – F-Droid-tauglich.
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Anmeldung im Browser (Custom Tab, PKCE) – nie in einer WebView.
    implementation("androidx.browser:browser:1.8.0")
    // Abruf ohne Push-Distributor (alle 15 Minuten) und Hochladen des Push-Abos.
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    // Supabase-REST/Auth/Edge Functions direkt per HTTPS.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Push über UnifiedPush (z. B. ntfy aus F-Droid); entschlüsselt Web Push (RFC 8291) selbst.
    implementation("org.unifiedpush.android:connector:3.3.5")

    // Tests der reinen Kotlin-Logik des App-Moduls: ./gradlew :app:testDebugUnitTest
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.2.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // Echte org.json-Umsetzung für JVM-Tests (in android.jar ist sie nur ein Stub).
    testImplementation("org.json:json:20250517")
}
