# Oryn — audit transversal UI et nouvel onglet Découvrir

**Date :** 5 novembre 2026  
**Base de rollback :** `521ea6a9953e68c02bb59eae919fcf95c0c15a2a` (PR #10, Oryn 1.1.1).  
**Nouvelle version QA :** `1.2.0` / `versionCode 4`.  
**Décision propriétaire :** quatre onglets Accueil, Bibliothèque, Découvrir, Favoris, import `+` séparé. Cette décision remplace **uniquement** la règle antérieure à trois onglets. Aucune couleur, identité persistée ou token du design system n'a été changé.

## Références et consignes appliquées

- `CLAUDE.md` renvoie à `AGENTS.md`; parcourir également `DESIGN.md`, `docs/design-system.md`, `docs/UI_REFERENCE_RESEARCH.md`.
- Skills Codex présents dans `.agents/skills/` : `oryn-ui-craft`, `oryn-native-accessibility`, et `oryn-text-selection` pour tout futur correctif de sélection. Les principes de design natif Compose, retour Android, contraste, taille des cibles et non-régression s'appliquent à **chaque** écran concerné, pas au seul élément signalé.
- Faute d'exécuteur Codex connecté à cette session, les changements ont été appliqués via l'interface GitHub ; ne pas prétendre qu'un agent Codex ou Claude a exécuté la branche. Le présent document constitue aussi un brief vérifiable pour une revue Codex ultérieure.

## Causes racines confirmées

| Problème | Cause dans le code | Correctif et vérification |
| --- | --- | --- |
| Pill clair / Sheet toujours sombre | `ReaderSheet.kt` utilisait `orynColorScheme(true, currentAccent)`; la Pill calculait déjà le caractère sombre du parent | Une fonction partagée `readerChromeScheme` lit `MaterialTheme.colorScheme.background` *avant* de créer le thème du panneau ; utilisée par Pill et Sheet ; tests JVM de correspondance light/dark et contraste 4,5:1 sur les trois accents |
| Catalogues inaccessibles depuis la barre | `ReaderTab.Explore` correspondait à Bibliothèque via `OrynNav.openLibrary()` ; `ExploreScreen` n'avait pas de destination dédiée visible | Nouvel onglet `Discover("discover")`, quatrième destination visible avec import indépendant ; routes héritées `explore` et `online_search` redirigent vers le hub existant, qui affiche Catalogues / Recherche classique / Communauté |
| CTA de l'accueil ouvrait un ancien écran Anna | `HomeScreen` pointait sur `online_search`, interface de scraping non qualifiée | CTA ouvre `discover` ; l'extracteur HTML n'est pas présenté comme un catalogue pleinement fonctionnel |
| Anna's Archive mal classée | La carte `annas-archive.gl` avait été placée dans « accès ouvert et licences libres » sans validation de la recherche intégrée | Classer comme **index externe, droits à vérifier** ; ouvrir via `ACTION_VIEW` dans le navigateur Android, pas via WebView ni téléchargement interne |
| Accueil montre le logo launcher | `OrynBrandMark` réutilisait `ic_launcher_foreground` | Réutilise `OrynMascot` existante, adaptée aux couleurs Material ; le logo et l'icône adaptative restent réservés au launcher |
| Accent sélectionné dans les filtres encre profonde | `GlassChip` marquait les onglets avec `primary` plutôt que l'accent approuvé | `primaryContainer` / `onPrimaryContainer` réutilisés : même logique que la navbar et les autres filtres |
| L'onboarding affichait des couleurs de couvertures étrangères au design system | Six constantes `Color(0xFF...)` locales, et glissements malgré animation réduite | Dessins mappés aux couleurs `EditorialColors` existantes ; transitions neutralisées si les animations système sont coupées |
| Ombres en mode sombre | Navbar et bouton d'import appliquaient leurs ombres en permanence | Ombres supprimées en thème sombre, sans déplacer la barre ni supprimer le CTA |
| Instructions contradictoires | `AGENTS.md`, `DESIGN.md` et skill UI exigeaient encore trois onglets | Règle de navigation actualisée à quatre destinations, sans changer les autres règles de couleur, accessibilité ou sécurité |

## Anna's Archive — vérification précise

- `https://annas-archive.gl/` et sa FAQ ont été consultées le **05/11/2026** via une récupération de page publique ; elles répondaient et listaient notamment `.gl`, `.pk` et `.gd`. Discussion indépendante Reddit du 23/09/2026 : même liste de miroirs officiels, avec fonctionnalités en cours de rétablissement.
- **Confiance technique : mitigée.** Un domaine reconnu n'atteste ni la sûreté de tous ses fichiers, ni la fiabilité des redirections et liens, ni la légalité de contenus.
- **Non prouvé** : fonctionnement de `AnnasArchiveSource.search`, parseur HTML `div[class*='justify-center']`, `getDownloadLinks` et `downloadFile` sur Android. La FAQ indique une API JSON stable pour le téléchargement rapide réservé aux membres, pas une API publique de recherche fiable. Le scraping dépend donc de la structure HTML et de protections antibot instables.
- Par choix de sûreté, Oryn ouvre `https://annas-archive.gl` dans le navigateur **uniquement** sur action de l'utilisateur ; aucun téléchargement automatique, aucune nouvelle autorisation, aucune authentification, aucun contournement ni import automatique. Si aucun navigateur n'est présent, afficher l'erreur traduite.
- **Tests restants** : ouverture externe sur Pixel 9 ; redirection réelle et navigation retour ; aucun accès à un faux miroir ; vérification éventuelle d'un téléchargement licite dans un projet de recherche séparé, avant d'exposer l'extracteur historique.

## Inventaire de conception transversale (code, pas capture appareil)

Inspection des composables de `BookActions`, `BookCard`, `CloverAccent`, `FloatingActionNavBar`, `Glass`, `ReaderCompactControls`, `ReaderSheet`, `ScreenScaffold`, `BookmarksScreen`, `CommunityAuthSection`, `CommunityCatalogDialogs`, `CommunityCatalogSection`, `ExploreScreen`, `HomeScreen`, `LibraryScreen`, `LookupSheet`, `OnboardingScreen`, `OnlineSearchScreen`, `ReaderAnnotations`, `ReaderTools`, `SettingsScreen`, `WebLibraryDialog`, `ReaderHtml`, `PdfReader`.

- Couleurs structurelles : surfaces Material 3 et `OrynColors` ; **aucun changement** au fond `#FAFAF8`, à la carte blanche ou à la palette.
- Couleurs de documents : les pigments d'annotations et de notes du PDF sont des données éditoriales ou persistées ; ne pas les remplacer aveuglément.
- Les chemins communautaires, protections réseau, DAO, moteurs EPUB/PDF, identités DataStore et icône corbeille protégée restent inchangés.
- `OnlineSearchScreen` reste du code historique non exposé par les routes courantes ; il contient des chaînes non traduites et un parcours de téléchargement non qualifié. À auditer séparément avant éventuelle réactivation, pas à annoncer comme disponible.
- L'audit source ne garantit **pas** l'absence de clipping, de défaut de contraste après composition alpha ou de problème de TalkBack sur les appareils.

## Validation avant fusion

1. CI finale au **SHA exact** : `:app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest`.
2. Vérifier les gardes communautaires, `com.example.oryn.reader`, `versionCode=4`, `versionName=1.2.0` et le certificat debug signé ; conserver les artefacts et SHA.
3. Pixel 9 : ouvrir l'onglet Découvrir, parcourir Catalogues / Recherche classique / Communauté ; retour Android, rotation et police 200 %, thème clair/sombre, contour d'accessibilité et cibles de chaque onglet.
4. Reader : choisir clair/sombre/système avec chacun des trois accents ; vérifier que la Pill et le Sheet suivent les mêmes couleurs, que thèmes de lecture changent le papier mais **pas** le chrome ; vérifier la luminosité gérée par Android.
5. Tester CTA Collections → Découvrir, icône `+` séparée, favoris, Bibliothèque, accueil, page Réglages et mascotte.
6. Tester lien Anna's Archive en navigateur et vérifier que les fonctions communautaires désactivées le restent.
7. Capturer les écrans, comparer les contrastes à 100 % et 200 % de taille de texte ; compiler les tests Android **ne vaut pas exécution sur appareil**.

## Suite des audits conservés

- Gestes et poignées de sélection ; édition de note par son ancre ; choix de couleurs d'annotation personnalisées.
- Dictionnaire multilingue, exemples et synonymes réels ; gestion de modèles de traduction hors ligne.
- Fiche livre entièrement repensée, détails, métadonnées et états vides.
- Launcher adaptive icon : échelle, masques, zones de sécurité et version monochrome — pas modifié dans cette passe.
- Accessibilité de tous les écrans à grande taille de police, examen manuel des contrastes sur Pixel 9.

**Rollback sans perte de données :** ne pas fusionner la PR et conserver la branche de base `521ea6a`; aucun script de migration, écriture de données utilisateur ni publication de version n'a été effectué.
