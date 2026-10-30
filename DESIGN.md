# Oryn — guide de style (source de vérité visuelle)

Direction : **monochrome calme + un seul accent vif (chartreuse)**. Éditorial, sobre, expressif par la composition (grandes couvertures, cartes pleines, pilules) plutôt que par la décoration. Clair et sombre soignés.

Valeurs réelles dans `ui/theme/Theme.kt`, `OrynDesignTokens.kt`, `ReaderThemeColors.kt`. Ne pas inventer de couleur : utiliser `MaterialTheme.colorScheme` et `colors.accent` / `colors.onAccent`.

## Couleurs
| Rôle | Clair | Sombre |
|---|---|---|
| Fond | `#EFEFEC` | `#121212` |
| Surface carte | `#FFFFFF` | `#262625` |
| Texte principal | `#111111` | `#F2F2EF` |
| Texte secondaire | `#55554F` | `#B6B6B0` |
| Accent (chartreuse) | `#C8F135` | `#CBF53C` |
| Texte sur accent | `#141414` | `#141414` |
- Accent alternatifs : Citron `#FFE04A`, Corail `#FF7A59`. Le nom d'enum stocké reste `ORANGE` (chartreuse), `FUCHSIA` (citron), `GREEN` (corail).
- L'accent n'est **jamais** du texte ni une icône sur fond clair (contraste 1,3:1) : en clair on utilise l'encre ; en sombre, l'accent est autorisé.
- **Un seul grand aplat d'accent par écran** (par exemple la carte « Continuer »).
- Surlignages : `#C8F135`, `#FFE04A`, `#FF8F72`, `#FFC9A8`.

## Typographie
- Police : **Hanken Grotesk** (OFL, équivalent libre de Neue Montreal), fichiers `res/font/hanken_{regular,medium,semibold,bold}.ttf`. Pas d'Inter, pas de police chargée depuis Internet.
- Titres : Medium, interlettrage légèrement négatif. Corps : Regular. Légendes : Medium 11 sp, petites capitales espacées, **jamais en surtitre décoratif**.
- Échelle : page 30 sp, section 20 sp, titre de carte 18 sp, corps 14–16 sp, légende 11 sp. Les tailles sont en `sp`.
- La police de lecture (`ReaderFontFamily`) est indépendante de celle de l'interface.

## Espacement, formes, élévation
- Grille de **4 dp** : 4, 8, 12, 16, 20, 24, 32, 40, 48. Jamais 6, 10, 14, 18, 22… (`OrynSpace`).
- Marges d'écran 16 dp. Sections séparées de 16 à 24 dp.
- Rayons : contrôle 12, moyen 20, carte 24, grand 28, feuille 32, pilule 100 (`OrynShape`).
- Pas de verre dépoli, pas de bordures colorées. Ombre douce seulement en clair (cartes posées, couvertures). Aucune ombre en sombre.

## Composants
- Boutons : pilule noire (clair) ou claire (sombre) pour l'action principale ; pilule teintée `onSurface` 8 % pour le reste. Hauteur ≥ 48 dp.
- Sélection : fond accent + texte `onAccent`.
- Couvertures : `coverFrame()` (10 dp, liseré blanc 2 dp en clair, ombre douce).
- Icônes : jeu Lucide existant (`OrynIcons`, `HugeIcons`), trait 2, extrémités rondes. Pas d'emoji.
- Barre de navigation flottante : ne pas modifier.

## Mouvement
- Court (150–300 ms), ressorts doux, retour de pression léger. Respecter `MotionPreferences` (réduction des animations). Aucun fondu déclenché par le défilement.

## Ton des textes
- Concret et court, en tutoiement. Dire ce que fait la fonction, pas des promesses (« Sans compte, sans pub », pas « Une lecture qui te ressemble »).
