#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

PROJECT="$HOME/Oryn"
DOWNLOADS="$HOME/storage/downloads"
RES="$PROJECT/app/src/main/res/drawable-nodpi"
NAME="ic_home"
TARGET="$RES/$NAME.png"
BACKUP="$PROJECT/icon-backups/$(date +%Y%m%d-%H%M%S)"

echo "=== Oryn : conversion automatique d'icône SVG ==="

command -v magick >/dev/null || {
  echo "Erreur : ImageMagick absent. Lance : pkg install imagemagick"
  exit 1
}

if [[ ! -d "$DOWNLOADS" ]]; then
  echo "Stockage non accessible. Lance : termux-setup-storage"
  exit 1
fi

mapfile -t FILES < <(
  find "$DOWNLOADS" -maxdepth 2 -type f -iname '*.svg' \
    -printf '%T@ %p\n' 2>/dev/null |
    sort -nr |
    cut -d' ' -f2-
)

if [[ ${#FILES[@]} -eq 0 ]]; then
  echo "Aucun SVG trouvé dans $DOWNLOADS"
  exit 1
fi

echo "SVG le plus récent : ${FILES[0]}"
mkdir -p "$RES"

# Sauvegarder les anciennes ressources portant le même nom.
if [[ -e "$TARGET" || -e "$PROJECT/app/src/main/res/drawable/$NAME.xml" ]]; then
  mkdir -p "$BACKUP"
  [[ ! -e "$TARGET" ]] || cp -a "$TARGET" "$BACKUP/"
  [[ ! -e "$PROJECT/app/src/main/res/drawable/$NAME.xml" ]] ||
    cp -a "$PROJECT/app/src/main/res/drawable/$NAME.xml" "$BACKUP/"
  echo "Sauvegarde : $BACKUP"
fi

TMP="$RES/.${NAME}-temp.png"
if ! magick -background none "${FILES[0]}" \
    -resize 192x192 -gravity center -extent 192x192 \
    "PNG32:$TMP"; then
  rm -f "$TMP"
  echo "Échec de conversion SVG. L'ancienne icône est préservée."
  exit 1
fi

# Vérifier que le PNG est lisible avant remplacement.
magick identify "$TMP" >/dev/null
mv -f "$TMP" "$TARGET"

# Éviter un conflit de ressources Android XML/PNG portant le même nom.
XML="$PROJECT/app/src/main/res/drawable/$NAME.xml"
if [[ -f "$XML" ]]; then
  mkdir -p "$BACKUP"
  mv "$XML" "$BACKUP/"
fi

echo "Icône générée : $TARGET"
echo
echo "Vérification des références Kotlin/Java :"
grep -R -n --include='*.kt' --include='*.java' \
  -E 'R\.drawable\.ic_home|Icons\.(Default|Outlined|Filled)\.Home' \
  "$PROJECT/app/src" 2>/dev/null || true

echo
echo "Lancement de la compilation Android..."
cd "$PROJECT"
if [[ -x ./gradlew ]]; then
  ./gradlew assembleDebug
else
  echo "Gradle wrapper introuvable : compilation ignorée."
  exit 1
fi

echo
echo "Terminé. Vérifie que l'écran utilise bien R.drawable.ic_home."
