#!/data/data/com.termux/files/usr/bin/bash
set -eu
ROOT="$(pwd)"
NAME="$(basename "$ROOT")"
OUT="$HOME/${NAME}-inspection"
mkdir -p "$OUT"
REPORT="$OUT/project-report.txt"
{
  echo "=== PROJECT OVERVIEW ==="
  echo "Folder: $NAME"
  echo "Date: $(date)"
  echo
  echo "=== FILE STRUCTURE ==="
  find . -maxdepth 4 -type f -not -path './.git/*' -not -path '*/build/*' -not -path '*/.gradle/*' -not -path '*/node_modules/*' -not -path '*/.idea/*' -not -name '*.keystore' -not -name '*.jks' -not -name '*.apk' -not -name '*.aab' | sort
  echo
  echo "=== GIT STATUS ==="
  git status --short --branch 2>&1 || true
  echo
  echo "=== KEY CONFIGURATION FILES ==="
  for f in settings.gradle settings.gradle.kts build.gradle build.gradle.kts gradle/libs.versions.toml gradle/wrapper/gradle-wrapper.properties app/build.gradle app/build.gradle.kts app/src/main/AndroidManifest.xml; do
    if [ -f "$f" ]; then echo; echo "--- $f ---"; sed -n '1,220p' "$f"; fi
  done
} > "$REPORT"
ARCHIVE="$HOME/${NAME}-source-review.zip"
rm -f "$ARCHIVE"
zip -qr "$ARCHIVE" . -x '*/.git/*' '.git/*' '*/build/*' '*/.gradle/*' '*/node_modules/*' '*/.idea/*' '*/.cxx/*' '*/captures/*' '*/local.properties' 'local.properties' '*/gradle.properties' 'gradle.properties' '*/.env' '*/.env.*' '.env' '.env.*' '*/google-services.json' '*/secrets.properties' 'secrets.properties' '*/key.properties' 'key.properties' '*.jks' '*.keystore' '*.apk' '*.aab' '*credentials*' '*service-account*.json' '*/.DS_Store'
echo "Inspection report: $REPORT"
echo "Source archive:   $ARCHIVE"
echo "Review both files for secrets before sharing."
