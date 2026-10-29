#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

ROOT="$HOME/Oryn"
cd "$ROOT"

SRC="app/src/main/kotlin/com/example/oryn/reader/ui/components/FloatingActionNavBar.kt"
RES="app/src/main/res/drawable-nodpi"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="$HOME/Oryn-navbar-backup-$STAMP"

for cmd in curl magick python; do
  command -v "$cmd" >/dev/null 2>&1 || {
    echo "ERREUR : commande manquante : $cmd"
    exit 1
  }
done

[[ -f "$SRC" ]] || { echo "ERREUR : fichier Kotlin introuvable."; exit 1; }


mkdir -p "$RES"
mkdir -p "$BACKUP"
cp -p "$SRC" "$BACKUP/FloatingActionNavBar.kt"

TMP="$(mktemp -d)"
cleanup() { rm -rf "$TMP"; }
trap cleanup EXIT

download_icon() {
  local id="$1" out="$2"
  local prefix="${id%%:*}" name="${id#*:}"
  local url="https://api.iconify.design/${prefix}/${name}.svg"
  local svg="$TMP/$out.svg"

  echo "Téléchargement : $id"
  curl --fail --location --silent --show-error \
    --retry 2 "$url" -o "$svg"

  grep -q '<svg' "$svg" || {
    echo "ERREUR : réponse SVG invalide pour $id"
    exit 1
  }

  magick -background none -density 384 "$svg" \
    -resize 96x96 "PNG32:$RES/$out.png"

  [[ -s "$RES/$out.png" ]] || {
    echo "ERREUR : conversion impossible pour $id"
    exit 1
  }
}

download_icon "hugeicons:home-04" nav_home
download_icon "hugeicons:library" nav_library
download_icon "hugeicons:bookmark-02" nav_bookmark

# Modifie uniquement les trois icônes visibles et le rendu d'icône de la navbar.
python - "$SRC" <<'PY'
from pathlib import Path
import sys

p = Path(sys.argv[1])
s = p.read_text()

replacements = [
    (
        'import androidx.compose.ui.graphics.vector.ImageVector',
        'import androidx.compose.ui.graphics.vector.ImageVector\n'
        'import androidx.compose.ui.res.painterResource'
    ),
    (
        'import com.example.oryn.reader.ui.components.OrynIcons',
        'import com.example.oryn.reader.ui.components.OrynIcons'
    ),
    (
        'val selectedIcon: ImageVector\n)',
        'val selectedIcon: ImageVector,\n'
        '    val navIconRes: Int? = null\n)'
    ),
    (
        'Home("home", "Accueil", OrynIcons.Home, OrynIcons.HomeFilled),',
        'Home("home", "Accueil", OrynIcons.Home, OrynIcons.HomeFilled, R.drawable.nav_home),'
    ),
    (
        'Explore("explore", "Bibliothèque", OrynIcons.Library, OrynIcons.Library),',
        'Explore("explore", "Bibliothèque", OrynIcons.Library, OrynIcons.Library, R.drawable.nav_library),'
    ),
    (
        'Favorites("bookmarks", "Favoris", OrynIcons.Bookmark, OrynIcons.BookmarkFilled),',
        'Favorites("bookmarks", "Favoris", OrynIcons.Bookmark, OrynIcons.BookmarkFilled, R.drawable.nav_bookmark),'
    ),
    (
        'Icon(imageVector = tab.icon, contentDescription = tab.label, tint = ink, modifier = Modifier.size(24.dp))',
        '''if (tab.navIconRes != null) {
            Icon(
                painter = painterResource(id = tab.navIconRes),
                contentDescription = tab.label,
                tint = ink,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = ink,
                modifier = Modifier.size(24.dp)
            )
        }'''
    ),
]

for old, new in replacements:
    count = s.count(old)
    if count != 1:
        raise SystemExit(
            f"ERREUR : motif attendu trouvé {count} fois : {old!r}\n"
            "Le fichier n'a pas été modifié par ce patch."
        )
    s = s.replace(old, new, 1)

# Ajoute l'import R si absent.
if 'import com.example.oryn.R' not in s:
    lines = s.splitlines()
    package_index = next(
        (i for i, line in enumerate(lines) if line.startswith("package ")),
        None
    )
    if package_index is None:
        raise SystemExit("ERREUR : déclaration package introuvable.")
    lines.insert(package_index + 1, "")
    lines.insert(package_index + 2, "import com.example.oryn.R")
    s = "\n".join(lines) + ("\n" if s.endswith("\n") else "")

p.write_text(s)
PY

echo
echo "Vérification de compilation..."
if ./gradlew :app:compileDebugKotlin; then
  echo
  echo "SUCCÈS : compilation Kotlin réussie."
  echo "Icônes installées : Accueil, Bibliothèque, Favoris."
  echo "Bouton + et logo non ciblés."
  echo "Sauvegarde du Kotlin : $BACKUP/FloatingActionNavBar.kt"
else
  echo
  echo "ÉCHEC : compilation. Sauvegarde disponible dans : $BACKUP"
  echo "Les ressources ajoutées n'ont pas été supprimées automatiquement."
  exit 1
fi
