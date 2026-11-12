#!/usr/bin/env bash
# Baut die eigene F-Droid-Paketquelle (signierter Index + APK) für eine Testversion.
#
# Aufruf:   fdroid/build-repo.sh <apk> <versionCode> <arbeitsordner>
# Umgebung: FDROID_REPO_KEYSTORE     Base64 der PKCS12-Schlüsseldatei (GitHub-Secret)
#           FDROID_REPO_FINGERPRINT  erwarteter SHA-256 des Schlüssel-Zertifikats (klein, ohne Doppelpunkte)
# Braucht:  fdroid (fdroidserver), apksigner, keytool. Ergebnis: <arbeitsordner>/repo
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="$1"
VERSION_CODE="$2"
WORK="$3"
ALIAS="drinkwithme-fdroid"
# Nicht geheim (siehe fdroid/config.yml): Der Schutz liegt in der geheimen Schlüsseldatei.
export FDROID_KEYSTORE_PASS="drinkwithme-fdroid"

fingerprint() { grep 'SHA256:' | head -1 | awk '{print $2}' | tr -d ':' | tr 'A-F' 'a-f'; }

if [ -z "${FDROID_REPO_KEYSTORE:-}" ] || [ -z "${FDROID_REPO_FINGERPRINT:-}" ]; then
  echo "FDROID_REPO_KEYSTORE und FDROID_REPO_FINGERPRINT müssen gesetzt sein." >&2
  exit 1
fi

rm -rf "$WORK"
mkdir -p "$WORK/repo" "$WORK/metadata/com.drinkwithme.app"
cp "$ROOT/fdroid/config.yml" "$WORK/config.yml"
chmod 600 "$WORK/config.yml"
cp "$ROOT/fdroid/metadata/com.drinkwithme.app.yml" "$WORK/metadata/"
# Texte und Bilder im Fastlane-Format (title.txt, short_description.txt, full_description.txt, images/…).
cp -R "$ROOT/fastlane/metadata/android/." "$WORK/metadata/com.drinkwithme.app/"
# Symbol der Paketquelle: fdroidserver sucht „icon.png“ (repo_icon) im Arbeitsordner und kopiert es nach repo/icons/.
cp "$ROOT/fastlane/metadata/android/en-US/images/icon.png" "$WORK/icon.png"

umask 077
# Leerzeichen und Zeilenumbrüche entfernen (beim Einfügen am Handy entstehen leicht welche).
printf '%s' "$FDROID_REPO_KEYSTORE" | tr -d ' \t\r\n' | base64 -d > "$WORK/keystore.p12"
umask 022

ACTUAL="$(keytool -list -v -keystore "$WORK/keystore.p12" -storepass "$FDROID_KEYSTORE_PASS" -alias "$ALIAS" | fingerprint)"
if [ "$ACTUAL" != "$FDROID_REPO_FINGERPRINT" ]; then
  echo "::error::Der Schlüssel im Secret FDROID_REPO_KEYSTORE passt nicht zum erwarteten Fingerabdruck."
  echo "Erwartet: $FDROID_REPO_FINGERPRINT" >&2
  echo "Im Secret: ${ACTUAL:-(nicht lesbar)}" >&2
  rm -f "$WORK/keystore.p12"
  exit 1
fi

cp "$APK" "$WORK/repo/com.drinkwithme.app_${VERSION_CODE}.apk"
(cd "$WORK" && fdroid update --pretty)
rm -f "$WORK/keystore.p12"
rm -rf "$WORK/repo/status"

# Prüfen: Der Index ist mit genau diesem Schlüssel signiert und enthält die neue Version.
SIGNED="$(keytool -printcert -jarfile "$WORK/repo/entry.jar" | fingerprint)"
if [ "$SIGNED" != "$FDROID_REPO_FINGERPRINT" ]; then
  echo "::error::entry.jar ist nicht mit dem erwarteten Schlüssel signiert ($SIGNED)."
  exit 1
fi
python3 - "$WORK/repo/index-v2.json" "$VERSION_CODE" <<'PY'
import json, sys
index = json.load(open(sys.argv[1], encoding="utf-8"))
codes = [v["manifest"]["versionCode"] for v in index["packages"]["com.drinkwithme.app"]["versions"].values()]
if int(sys.argv[2]) not in codes:
    sys.exit(f"versionCode {sys.argv[2]} fehlt im Index (gefunden: {codes})")
print("Index enthält versionCode", sys.argv[2])
PY
echo "F-Droid-Paketquelle gebaut: $WORK/repo (Fingerabdruck $SIGNED)"
