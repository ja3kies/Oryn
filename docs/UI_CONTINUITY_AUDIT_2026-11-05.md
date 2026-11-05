# Oryn — continuité de l'audit UI/UX, 5 novembre 2026

**Source immuable avant modification :** `feat/oryn-ui-reader-accessibility-v2` / `7ae9170779739b5403a6545b432f2d89e17c97ab`, PR #7, dernier run de référence réussi `41819214974`. **Cible :** une branche corrective séparée, PR en brouillon, aucune fusion automatique.

## Contraintes non négociables

- Conserver exactement le design system documenté dans `DESIGN.md` : fond clair `#FAFAF8`, surfaces blanches, palette teal/rose/lime/cyan, thèmes éditoriaux indépendants, mêmes tokens `OrynShape` et `MaterialTheme`.
- Ne pas modifier `ui/theme/Theme.kt`, `ui/theme/OrynDesignTokens.kt`, `FloatingActionNavBar.kt`, l'icône protégée `HugeIcons.Trash`, les clés de préférences, les moteurs EPUB/PDF, les réseaux communautaires verrouillés, ni les données utilisateur.
- Rechercher d'abord la cause dans Compose ; préférer les composants Material 3 existants et les chaînes FR/EN de `I18n.kt`. Ne pas importer les couleurs/espacements/termes du projet Klyro.

## Audit de source préalable et correctifs limités

| Gravité | Composant | Cause vérifiée dans la base | Correctif proposé |
|---|---|---|---|
| P1 | `ExploreScreen.RemoteCover` | Le rendu distant était `ContentScale.Crop` sur `primaryContainer`, contrairement à `BookCover` local (`Fit` + neutralité) ; coupe les couvertures et laisse un fond accentué | Utiliser `ContentScale.Fit`, fond `surfaceContainerLowest` et teinte neutre du fallback. Aucun changement de ratio forcé du contenu |
| P1 | `GridBook` / `BookCard` en sélection | Les boutons favoris restaient activables au milieu de la sélection multiple | Masquer uniquement les actions favorites durant la sélection, maintenir les gestes et l'accès hors sélection |
| P1 | Bibliothèque : CTA de suppression | L'icône était cliquable même à 0 livre sélectionné, sans action ni état disabled annoncé | Utiliser `IconButton(enabled=...)` en conservant `HugeIcons.Trash` et la confirmation déjà présente |
| P2 | Semantics de sélection | Le clic d'un livre en mode sélection pouvait être annoncé comme « Ouvrir » / aucun libellé plutôt que sélectionner ou désélectionner | Libellé contextuel avec titre du livre, traductions FR/EN |
| P2 | Reader Sheet | Police (4 choix) et thèmes éditoriaux (5 choix) placés en une ligne, potentiellement comprimés sur écrans étroits ou en grand texte | Répartir ces mêmes choix via `FlowRow` responsif ; ne modifier aucune couleur, identité ou option enregistrée |
| P2 | Réglages reader | Boutons de sélection d'alignement/marges/mode annonçaient une action sans signaler le choix actif | Exposer l'état sélectionné en sémantique lorsque pertinent |
| P2 | Fiche livre et confirmation multi-suppression | Surface et arrondis de certains `AlertDialog` dépendaient des valeurs implicites, tandis que la fiche utilisait des tokens explicites | Utiliser les mêmes `surface` et `OrynShape.sheet` déjà approuvés ; différencier textuellement/couleur les confirmations destructives |
| P2 | Téléchargements catalogue | Indicateur circulaire sans libellé accessible | Annoncer l'état de chargement avec la traduction existante |

## Références libres et natives

- [Android Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) : cibles tactiles de 48 dp, noms accessibles et composants natifs.
- [Android accessibility guidance](https://developer.android.com/guide/topics/ui/accessibility/apps) : actions non ambiguës, états et qualification manuelle.
- [Now in Android](https://github.com/android/nowinandroid) : patterns Compose/Material 3 accessibles et maintenance native ; inspiration architecturale, pas de reprise directe de code.
- [KOReader — code FOSS du navigateur de couvertures](https://github.com/koreader/koreader/blob/master/plugins/coverbrowser.koplugin/mosaicmenu.lua) : consultation des modes de grille et gestion de couvertures ; inspiration de comportement uniquement, aucun code AGPL importé.
- [Recherche Oryn déjà documentée](UI_REFERENCE_RESEARCH.md) : conserver l'architecture native Compose, les annotations et les positions de lecture existantes.
- Les guides locaux `.agents/skills/oryn-ui-craft/SKILL.md` et `.agents/skills/oryn-native-accessibility/SKILL.md` précisent les garde-fous.

## Critères de sortie vérifiables

- PR/CI sur **le dernier SHA exact** : `:app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest`.
- Vérifier artefact APK, identité de signature et verrouillage des fonctions communautaires. Comparer la liste de fichiers modifiés aux interdictions ci-dessus.
- La compilation des tests Android ne vaut pas leur **exécution** ; confirmer sur Pixel 9, lumière et nuit, 100%/200% font scale, écran étroit, TalkBack, couvertures aux ratios extrêmes, sélection/désélection, reader sheet ouvert/fermé et progressions.
- Alternative UX à soumettre au propriétaire avant implémentation : édition de la note personnelle via un composant de notation plus compact et explicitement effaçable, si les cinq étoiles 48 dp saturent le dialogue à 200% de police. Pas de remplacement dans cette passe.

**Rollback :** fermer la PR corrective ou rétablir le parent `7ae9170` ; aucune migration, modification destructive ni fusion vers `main`.
