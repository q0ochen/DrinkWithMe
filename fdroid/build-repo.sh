#!/usr/bin/env bash
# Baut die eigene F-Droid-Paketquelle (signierter Index + APKs) für eine Testversion.
#
# Aufruf:   fdroid/build-repo.sh <apk> <versionCode> <arbeitsordner> [<ordner mit bisherigen APKs>]
# Umgebung: FDROID_REPO_KEYSTORE     Base64 der PKCS12-Schlüsseldatei (GitHub-Secret)
#           FDROID_REPO_FINGERPRINT  erwarteter SHA-256 des Schlüssel-Zertifikats (klein, ohne Doppelpunkte)
# Braucht:  fdroid (fdroidserver), apksigner, keytool, python3. Ergebnis: <arbeitsordner>/repo
#
# Aus dem optionalen vierten Ordner wird die neueste ältere APK übernommen (archive_older: 2), damit F-Droid-Apps,
# deren Index noch eine Version alt ist, ihren Download weiterhin finden.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="$1"
VERSION_CODE="$2"
WORK="$3"
PREVIOUS="${4:-}"
ALIAS="drinkwithme-fdroid"
PACKAGE="com.drinkwithme.app"
# Nicht geheim (siehe fdroid/config.yml): Der Schutz liegt in der geheimen Schlüsseldatei.
export FDROID_KEYSTORE_PASS="drinkwithme-fdroid"

fingerprint() { grep 'SHA256:' | head -1 | awk '{print $2}' | tr -d ':' | tr 'A-F' 'a-f'; }
fail() { echo "::error::$1"; exit 1; }

[ -n "${FDROID_REPO_KEYSTORE:-}" ] || fail "FDROID_REPO_KEYSTORE ist nicht gesetzt."
[ -n "${FDROID_REPO_FINGERPRINT:-}" ] || fail "FDROID_REPO_FINGERPRINT ist nicht gesetzt."

# Der Fingerabdruck muss überall gleich sein, wo Tester ihn bekommen (Link, QR-Code, Doku).
for doc in README.md docs/F-DROID.md docs/fdroid-paketquelle-qr.svg; do
  grep -qi "$FDROID_REPO_FINGERPRINT" "$ROOT/$doc" \
    || fail "$doc nennt nicht den Fingerabdruck $FDROID_REPO_FINGERPRINT (Link/QR-Code anpassen, siehe EINRICHTUNG 5.5)."
done

rm -rf "$WORK"
mkdir -p "$WORK/repo" "$WORK/metadata/$PACKAGE"
trap 'rm -f "$WORK/keystore.p12"' EXIT
cp "$ROOT/fdroid/config.yml" "$WORK/config.yml"
chmod 600 "$WORK/config.yml"
# Anti-Feature-Bezeichnungen (F-Droid 2.0 zeigt nur, was die Paketquelle definiert).
cp -R "$ROOT/fdroid/config" "$WORK/config"
cp "$ROOT/fdroid/metadata/$PACKAGE.yml" "$WORK/metadata/"
# Texte und Bilder im Fastlane-Format (title.txt, short_description.txt, full_description.txt, images/…).
cp -R "$ROOT/fastlane/metadata/android/." "$WORK/metadata/$PACKAGE/"
# Symbol der Paketquelle: fdroidserver sucht „icon.png“ (repo_icon) im Arbeitsordner und kopiert es nach repo/icons/.
cp "$ROOT/fastlane/metadata/android/en-US/images/icon.png" "$WORK/icon.png"

# Schlüssel aus dem Secret. Leerzeichen und Zeilenumbrüche entfernen (beim Einfügen am Handy entstehen leicht welche).
umask 077
if ! printf '%s' "$FDROID_REPO_KEYSTORE" | tr -d ' \t\r\n' | base64 -d > "$WORK/keystore.p12" 2>/dev/null; then
  fail "Das Secret FDROID_REPO_KEYSTORE ist kein gültiger Base64-Text (unvollständig kopiert?)."
fi
umask 022
KEYTOOL_OUT="$(keytool -list -v -keystore "$WORK/keystore.p12" -storepass "$FDROID_KEYSTORE_PASS" -alias "$ALIAS" 2>&1 || true)"
ACTUAL="$(printf '%s\n' "$KEYTOOL_OUT" | fingerprint || true)"
if [ "$ACTUAL" != "$FDROID_REPO_FINGERPRINT" ]; then
  echo "Erwartet:  $FDROID_REPO_FINGERPRINT"
  echo "Im Secret: ${ACTUAL:-(nicht lesbar)}"
  printf '%s\n' "$KEYTOOL_OUT" | grep -i "error\|fehler" | head -3 || true
  fail "Der Schlüssel im Secret FDROID_REPO_KEYSTORE passt nicht zum erwarteten Fingerabdruck oder ist nicht lesbar (falsche Datei, anderes Passwort oder unvollständig kopiert)."
fi

cp "$APK" "$WORK/repo/${PACKAGE}_${VERSION_CODE}.apk"
# Die neueste ältere APK aus der bisherigen Paketquelle behalten (nur echte ältere Versionen).
if [ -n "$PREVIOUS" ] && [ -d "$PREVIOUS" ]; then
  OLDER="$(find "$PREVIOUS" -maxdepth 1 -name "${PACKAGE}_*.apk" -printf '%f\n' \
    | sed -n "s/^${PACKAGE}_\([0-9]\+\)\.apk$/\1/p" | awk -v vc="$VERSION_CODE" '$1 < vc' | sort -n | tail -1)"
  if [ -n "$OLDER" ]; then
    cp "$PREVIOUS/${PACKAGE}_${OLDER}.apk" "$WORK/repo/"
    echo "Übernehme die bisherige Version ${OLDER}."
  fi
fi

(cd "$WORK" && fdroid update --pretty)
rm -f "$WORK/keystore.p12"
rm -rf "$WORK/repo/status"

# Prüfen: Der Index ist mit genau diesem Schlüssel signiert, enthält die neue Version und definiert NonFreeNet.
SIGNED="$(keytool -printcert -jarfile "$WORK/repo/entry.jar" 2>&1 | fingerprint || true)"
[ "$SIGNED" = "$FDROID_REPO_FINGERPRINT" ] || fail "entry.jar ist nicht mit dem erwarteten Schlüssel signiert (${SIGNED:-unbekannt})."
python3 - "$WORK/repo/index-v2.json" "$VERSION_CODE" "$PACKAGE" <<'PY'
import json, sys
index = json.load(open(sys.argv[1], encoding="utf-8"))
codes = sorted(v["manifest"]["versionCode"] for v in index["packages"][sys.argv[3]]["versions"].values())
if int(sys.argv[2]) not in codes:
    sys.exit(f"::error::versionCode {sys.argv[2]} fehlt im Index (gefunden: {codes})")
if "NonFreeNet" not in index["repo"].get("antiFeatures", {}):
    sys.exit("::error::Der Index definiert das Anti-Feature NonFreeNet nicht (fdroid/config/antiFeatures.yml).")
print("Index enthält die Versionen", codes)
PY
echo "F-Droid-Paketquelle gebaut: $WORK/repo (Fingerabdruck $SIGNED)"
