#!/usr/bin/env bash
# Erzeugt web/src/core/golden/kotlin-golden.json mit dem Kotlin-Kern aus android/core.
# Voraussetzungen: JDK 17+ und Gradle 8 (Umgebungsvariable GRADLE, sonst „gradle“ aus dem PATH).
# Der Kern wird dafür in ein temporäres Verzeichnis kopiert – android/ bleibt unverändert.
# Danach: cd web && npm test  (kotlinGenerated.golden.test.ts vergleicht den TypeScript-Port damit).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

cp -r "$repo/android/core" "$work/core"
rm -rf "$work/core/build"
mkdir -p "$work/core/src/test/kotlin/com/drinkwithme/core"
cp "$here/GoldenExport.kt" "$work/core/src/test/kotlin/com/drinkwithme/core/GoldenExport.kt"
cat >> "$work/core/build.gradle.kts" <<'GRADLE'

tasks.withType<Test>().configureEach {
    environment("GOLDEN_OUT", System.getenv("GOLDEN_OUT"))
    outputs.upToDateWhen { false }
}
GRADLE
cat > "$work/settings.gradle.kts" <<'GRADLE'
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
rootProject.name = "kotlin-golden"
include(":core")
GRADLE
cat > "$work/build.gradle.kts" <<'GRADLE'
plugins {
    id("org.jetbrains.kotlin.jvm") apply false
}
GRADLE
printf 'org.gradle.daemon=false\nkotlin.compiler.execution.strategy=in-process\norg.gradle.jvmargs=-Xmx1536m\n' > "$work/gradle.properties"

export GOLDEN_OUT="$repo/web/src/core/golden/kotlin-golden.json"
"${GRADLE:-gradle}" -p "$work" ${GRADLE_ARGS:-} :core:test --tests 'com.drinkwithme.core.GoldenExport' --console=plain
echo "Geschrieben: $GOLDEN_OUT"
