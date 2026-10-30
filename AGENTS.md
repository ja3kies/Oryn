# Oryn — règles pour les assistants IA (Codex, Replit, Claude, DeepSeek…)

Lis ce fichier et `DESIGN.md` avant toute modification. En cas de doute, ne change pas le fichier : pose la question.

## Produit
- Oryn Reader : appli Android de lecture, **locale d'abord** (EPUB, PDF, TXT, HTML), sans compte, sans publicité, sans abonnement.
- Écrans : Accueil, Bibliothèque, Favoris & notes, Réglages, lecteur, catalogues en ligne, fiche livre. Navigation : exactement 3 onglets en barre flottante + bouton « + » d'import.
- Langues : français (source) et anglais. L'appli est à usage personnel.

## Stack
- Kotlin 2.0, Jetpack Compose + Material 3, Room, DataStore. `minSdk 24`, `targetSdk 35`. Package `com.example.oryn.reader`.
- Build : `./gradlew assembleDebug`. Un code qui ne compile pas n'est pas livré : relis les imports, les paramètres et les accolades.

## Règles de code
- **Textes** : jamais de chaîne affichée en dur. Français dans `tr("…")` / `trf("…", args)` et, pour chaque nouveau texte, l'entrée anglaise dans `i18n/I18n.kt`. Utiliser `Text` de `com.example.oryn.reader.i18n`.
- **Modifications ciblées** : ne réécris pas un fichier entier pour changer trois lignes. Pas de reformatage, pas de renommage inutile.
- **Pas de fichiers parasites** : jamais de `.bak`, `.orig`, copie `*_complet.txt` dans le dépôt. Pas de `app/build`, `.gradle`, `.backup` (voir `.gitignore`).
- **Réseau** : HTTPS uniquement. Aucune clé d'API, aucun secret dans le code. Aucun traceur, analytics, replay de session ni police chargée depuis un serveur tiers (les polices sont dans `res/font`).
- **Fichiers de l'utilisateur** : lectures bornées (taille, nombre d'entrées), jamais d'écrasement sans vérification. WebView : JavaScript désactivé pour les livres, contenu mixte interdit.
- **Sources de livres** : uniquement des sources légales (domaine public, libres de droits). Ne pas ajouter, étendre ni réparer de sources de téléchargement piratées.

## À ne pas toucher sans demande explicite
- La barre de navigation flottante (structure et icônes) : `ui/components/FloatingActionNavBar.kt`.
- L'icône corbeille `HugeIcons.Trash` de la suppression de sélection dans la bibliothèque.
- Les clés DataStore et les noms d'enum déjà enregistrés (`AppAccentTheme.ORANGE/FUCHSIA/GREEN/BLUE`…) : les renommer casse les préférences existantes.

## Qualité attendue (UX)
- Cibles tactiles ≥ 48 dp, contrastes AA (texte ≥ 4,5:1), descriptions pour TalkBack sur les icônes seules, respect de « réduire les animations ».
- Lois d'UX suivies : Hick (peu de choix à la fois), Fitts (grandes cibles à portée du pouce), proximité, Von Restorff (un seul élément en accent par écran), Jakob (conventions iOS/Android connues).
- Évite les tics « vibecodés » : dégradés violet-bleu, texte en dégradé, emojis dans les titres, cartes en verre, bordures colorées, fondus au scroll, tirets cadratins en rafale, textes marketing creux.
