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
