# Audit UX basé sur les captures de qualification — 4 novembre 2026

**Portée :** captures fournies de l'accueil, de la fiche d'un livre, des écrans de lecture clair/sombre et de la bibliothèque. Corrections sur `feat/oryn-ui-soft-white-brand` (PR #6), à partir du dernier code accessible. **Aucune validation tactile ou capture automatisée sur Pixel 9 n'est revendiquée.**

## Résultats / preuves

| Gravité | Observation dans la capture | Cause identifiée dans le code | Correction |
|---|---|---|---|
| Critique UX | La fiche livre semble presque illisible : texte sombre sur fond vert foncé laissant voir l'écran derrière | `BookActions.InfoDialog` utilise `Modifier.glass(..., tint, frosted=true)` pour un contenu long alors que la teinte dérive de la couverture | Remplacement par une `Surface` Material 3 **opaque**, avec `contentColor=onSurface`, élévation limitée et colonne défilante |
| Élevée | Action `Modifier` dans la fiche | La modale d'édition était composée **avant** la fiche principale, donc un second `Dialog` risquait d'être derrière | Commuter une seule modale à la fois : fermer la composition de la fiche pendant l'édition |
| Élevée | Progression de lecture `Livre : 3 % %`, `Chapitre : 10 % %` | `trf` remplace `%s` mais ne traite pas `%%` comme `printf`; les clés et traductions EN comportaient un `%` surnuméraire | Un unique `%` en FR/EN + test JVM de régression |
| Élevée | Icône `+` dans l'en-tête **et** action `+` flottante dans la bibliothèque | Deux CTA identiques sans différence de sens | Conserver exclusivement le bouton d'import global attendu par `AGENTS.md`; garder tri et changement grille/liste |
| Moyenne | Derniers livres / collection très proches de la navigation flottante | `navBarClearance=88.dp` inférieure à la hauteur visible bar+voile+zone geste | Augmenter la réserve de scroll sans déplacer/renommer la barre de navigation existante |
| Moyenne | Année affichée (ex. 1961) peut sembler être l'année d'édition du fichier local | La fiche récupère `first_publish_year` d'un résultat Open Library identifié par un match de **titre partiel**, sans auteur vérifié | Exiger titre exact normalisé, valider un nom de famille, bornes de notes/années et cache V2 ; libellé explicite `Année (catalogue)` |
| Moyenne | `Supprimer` pour la couverture placé près de `Changer` | Action destructive sans confirmation | Dialogue de confirmation natif, sans empiler deux modales |
| Faible | Menu de tri peut rester en français en mode anglais | `Text(option.label)` sans `tr` | Utilisation de `tr(option.label)` |

## Patterns existants conservés

- Navigation : **trois onglets + un seul bouton d'import**. Ne pas créer une nouvelle navigation iOS.
- Retour système, scrim modal, dialogue de confirmation d'une action destructive : conventions Android/Material 3.
- Recherche et filtres par pastilles : conserver les interactions déjà connues.
- Contrôles du lecteur : apparitions ponctuelles ; la sélection/annotation s'appuie sur les offsets source, sans remplacer arbitrairement le moteur par `SelectionContainer`.
- Papier beige dans le lecteur : **préférence de lecture indépendante** ; ne pas le transformer en fond blanc car le chrome de l'app est `#FAFAF8`.
- Aucune modification des données utilisateur, migrations, branche stable ou verrous Supabase.

## Références d'implémentation

- [Dialogues Compose](https://developer.android.com/develop/ui/compose/components/dialog)
- [Material 3 et sheets](https://developer.android.com/develop/ui/compose/components/bottom-sheets)
- [Gestion des insets Compose](https://developer.android.com/develop/ui/compose/system/insets)
- [Gestion des insets dans Material 3](https://developer.android.com/develop/ui/compose/system/material-insets)

## Vérifications et réserves

**À effectuer automatiquement** sur le SHA final : tests JVM, lint debug/release, builds APK debug/release, compilation des tests instrumentés, vérification de signature et des verrous communautaires. Une CI verte **ne valide pas** le rendu ou les gestes physiques.

**Qualification manuelle au jalon de test :**
1. Ouvrir la fiche du livre sur Pixel 9 en clair/sombre ; vérifier fond opaque, contraste lisible, défilement, fermeture et action `Modifier`.
2. Confirmer que le dialogue `Supprimer la couverture ?` permet Annuler sans suppression.
3. Lire un EPUB en papier et en sombre ; ouvrir les contrôles ; vérifier les `%` FR/EN.
4. Faire défiler la bibliothèque jusqu'au dernier élément avec le clavier masqué puis affiché ; vérifier qu'il ne passe pas sous la barre flottante.
5. Dans la bibliothèque : un seul `+` d'import, tri/grille/liste conservés ; recherche et filtres opérationnels.
6. Contrôler la fiche de `Les Nuits blanches` : l'année provenant d'Open Library n'est pas présentée comme celle de l'édition locale.
7. Rotation, mise à l'échelle du texte et TalkBack, si disponibles.

**Rollback :** fermer la PR #6 ou revenir au commit de base de `feat/oryn-reader-150`, sans réinitialisation forcée de la branche stable ni désinstallation de l'application.

## Second pass — reader pill, reader sheet, cover and book metadata

**Scope:** Static inspection of all **46 Kotlin files** under `app/src/main/kotlin/com/example/oryn/reader/ui/`: 18 components, 24 screen/reader files and 4 theme files. This is a code audit, **not** a complete live TalkBack or contrast sweep of every runtime state. The screenshots and code provided the following reproducible findings:

| Area | Root cause | Fix / check |
|---|---|---|
| Book-cover accent stripes | `BookCover` uses `ContentScale.Fit` (correctly avoiding crop) on `primaryContainer` (bright accent) | Set neutral `surfaceContainerLowest` for letterboxing; preserve full illustration and the existing 3D cover |
| Cover fallback in dark mode | Fallback text used `onPrimaryContainer` despite neutral surface | Use `onSurface`, icon `onSurfaceVariant`, increase tiny text from 9sp to 11sp |
| Destructive cover action | Two wide CTAs `Changer`/`Supprimer` made deletion overly prominent | Keep labelled `Changer`, replace delete with an accessible 48dp trash icon and confirmation |
| Noninteractive stars | `RatingRow` painted Open Library stars but exposed no onClick | Store a separate local personal rating 0–5, five accessible 48dp star buttons; tapping the same rating clears it. Show external rating separately and only when sourced |
| Missing bibliographic data | EPUB may lack metadata; remote search can also return no match | Retain verified Open Library enrichment (date, description, publisher, pages) and permit user edits stored locally; never fabricate unknown values |
| Reader pill | Tiny progress line and an unnecessary horizontal scroll hiding tool affordances | Fixed five 48dp actions with no hidden scroll; thicker progress bar and scalable progress header |
| Reader sheet | Tiny text labels; icon-only tab navigation; some accent combinations unreadable; 3-margin + 2-scroll buttons did not fit a half-width card | Label two sections; keep accessible light-on-dark primary; enlarge labels/swatch targets; stack margin and scroll groups; opaque dark sheet |
| Common controls | Custom filter chips lacked selected semantics and icon CTAs lacked button roles | `selectable` and button action names applied in `Glass.kt`; book taps/long-press affordances described |
| Screenshots/high-value metadata | Edition year wrongly implied as exact year of imported file | Clarify that catalogue info can refer to a different edition and expose manual correction |

### Contrast source calculations

Computed **WCAG relative luminance contrast**, not inferred from palette names:

- Light app text `#0F3A3A` on soft-white `#FAFAF8`: **11.91:1**.
- Light book-card text `#0F3A3A` on white: **12.45:1**.
- Light variant text `#405E59` on `#F1F2F0`: **6.31:1**.
- Selected lime `#D0E86E` with deep teal `#0F3A3A`: **9.16:1**.
- Selected rose `#B5185F` with ivory `#FEFFDE`: **6.31:1**.
- Selected cyan `#35C1C2` with deep teal: **5.67:1**.
- Night surface `#0D2727` with ivory: **15.39:1**.

A new JVM test verifies the live `orynColorScheme` semantic foreground/background pairings over all accent choices and both themes. Local artwork overlaid on screenshots, disabled/transparent combinations and user-supplied cover art still require **runtime** checks.

### Native-skill selection

Read [AgenticSkills Design category](https://agenticskills.io/category/design), favoring:
- [Platform Design Skills / Android guidelines](https://agenticskills.io/skills/platform-design-skills), native Android Material 3 + WCAG 2.2.
- [UI/UX Pro Max](https://agenticskills.io/skills/ui-ux-pro-max), mobile pre-delivery QA and semantic control principles.

Integrated an **original Android-specific adaptation** at `.agents/skills/oryn-native-accessibility/SKILL.md` and routed `AGENTS.md` to it. Web-only shadcn/Tailwind styling has not been imported into Jetpack Compose.

### Quality gate and remaining limits

PR #7 builds from the UI branch; the parent UI PR #6 and main remain unaffected. Automated checks need to complete at the final SHA: JVM, debug/release lint, APK signing, debug/release builds, instrumented-test **compilation**. Test scripts are not a substitute for on-device interaction. On Pixel 9 verify: edge-gutter behavior across odd cover ratios; modal text and stars with TalkBack; saved manual year/description/pages/publisher across restart; reader pill on text scales 100%/200%, sheet scroll and tabs, selection gestures and return. Community Auth/catalog/transfers remain locked.
