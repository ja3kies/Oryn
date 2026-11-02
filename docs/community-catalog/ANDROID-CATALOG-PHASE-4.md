# Phase 4 — catalogue, simulation et parcours locaux

## Audit avant modification

Branche `feature/community-epub-catalog`, 2 novembre 2026. Lecture des règles AGENTS/DESIGN, modèles, repository distant, client HTTPS, ViewModel, UI Communauté/Explorer et du contrat de consentement serveur, puis revue des frontières Auth (PKCE, callback, Keystore, restauration/refresh) et des rapports de phases 1–3. Les changements locaux existants sont préservés.

- Le gate privé `PRODUCTION_VERIFIED = false` bloque le repository avant session/Auth et le client avant réseau. Aucun endpoint de mutation ni pipeline d'import communautaire n'est branché.
- Le modèle existant ne couvre que les quatre métadonnées d'une liste ; pagination, recherche, détails et simulation manquent. Le ViewModel ne gère pas encore les réponses obsolètes ni les erreurs de pagination.
- Les états principaux sont présents ; les fixtures de développement doivent rester séparées de toute publication réelle et du système de session.
- `BookCover` dépend de `BookEntity` et du cache local : aucun faux BookEntity ne sera créé pour rendre une fiche distante. Une icône de livre neutre reprend les mêmes tokens, sans URL de couverture ni lecture du disque.
- Auth reste désactivé faute de configuration isolée et de validation Android réelle. Cette phase ne change pas ses contrôles et ne traite jamais une simulation comme une session.
- Le serveur exige une confirmation explicite et la version `oryn-epub-rights-v1`. La préparation locale ne constituera ni un consentement persisté ni une autorisation d'envoyer un fichier.

## Architecture et comportement

Le modèle distingue explicitement `CommunityOrigin.Remote` et `Simulation`. Une fiche contient uniquement des métadonnées bornées (titre/auteur/langue, description optionnelle, taille/hash optionnels) ; aucune identité propriétaire, URI locale, URL Storage ou copie de BookEntity. Les nouvelles métadonnées sont validées avant usage. Le contrat `CommunityCatalogDataSource` est injectable sans Android ni session.

`SimulatedCommunityCatalogRepository` contrôle la provenance de chaque élément, l'identifiant des détails, les doublons, la taille de page et le curseur suivant. Il exige `BuildConfig.DEBUG`. Les véritables fixtures sont dans `src/debug` : 18 fiches explicitement nommées « EPUB de démonstration », auteur de démonstration, aucun fichier, hash, taille inventée ou URL. En release, une autre factory retourne toujours null et le bouton de démonstration n'est pas disponible. Aucun faux élément n'est présenté comme publié ni converti en livre local.

Le repository distant conserve son verrou privé et l'authentification serveur existante ; ses nouvelles entrées recherche/détails sont indisponibles sans contrat backend vérifié. Il n'est jamais remplacé automatiquement par une simulation lorsqu'une requête échoue. La simulation est une option de développement choisie explicitement, pas une réussite Auth, ni une permission ou une session artificielle. L'interface du vrai compte reste indépendante.

Le ViewModel expose un instantané cohérent : query, origine, liste/état, changement de page, erreur de page et détail. Recherche titre/auteur insensible à la casse et aux accents, filtres de langue, tri croissant/décroissant, remise à la première page après modification et pagination stable. Une page de six éléments est affichée par défaut (contrat borné à 40) ; pas d'accumulation infinie dans un composable. Recherche avec délai de 350 ms, annulation et générations pour empêcher les anciennes réponses d'écraser une nouvelle recherche ou de rouvrir une fiche fermée. Une erreur réseau de pagination préserve la page courante ; une expiration d'authentification efface les résultats précédents. Une page vide permet de revenir en arrière.

En debug, ouvrir Explorer → Communauté → « Explorer la démonstration · développement ». Le bandeau « données simulées » est présent dans la liste et les détails, et chaque ligne porte « Exemple simulé · aucun fichier ». Les scénarios Exemples, Chargement, Vide, Erreur, Hors ligne, Connexion requise et Indisponible utilisent la même interface et aucun endpoint. Quitter la démonstration revient au repository distant verrouillé et efface les détails simulés. Aucun mode de démonstration ni consentement n'est enregistré dans DataStore ou Room.

## Téléchargement préparé, aucune exécution

La fiche présente les métadonnées disponibles, puis « Examiner le téléchargement ». Les exemples n'ont pas de fichier : la préparation est refusée avec `DemonstrationOnly`. Une fiche distante sans taille/hash est également refusée. Un plan local complet ne contient qu'ID, taille attendue et SHA-256 ; il n'autorise rien. `executeDownload` renvoie toujours `Disabled`, sans client HTTP, session, destination, tâche en attente ou callback d'import. Le bouton de téléchargement reste désactivé et une explication lisible l'accompagne.

Avant un futur branchement réseau : vérifier une session utilisateur, réautoriser côté serveur le statut publié/non retiré et les quotas, utiliser le flux HTTPS authentifié de l'Edge Function (aucun lien Storage public), contrôler les redirections, MIME, taille bornée et SHA-256, valider le contenu EPUB, gérer l'annulation et les fichiers temporaires sans écraser une copie existante. L'import local ne pourra commencer qu'après ces contrôles et une action explicite. Aucun de ces accès fichiers/réseau/import n'est exécuté dans cette phase.

## Partage préparé, aucun upload

« Préparer le partage d’un EPUB » ouvre une fenêtre locale. L'utilisateur choisit lui-même un document via le sélecteur Android limité aux EPUB. Le composable lit seulement DISPLAY_NAME, SIZE et le MIME via ContentResolver : un seul enregistrement et deux colonnes, pas de lecture/copie des octets, hash, analyse ZIP, permission persistante, scan de bibliothèque, URI conservée ou écriture Room. Les erreurs/absence du sélecteur et des informations du fournisseur sont traitées sans détails sensibles. Le fournisseur Android choisi peut lui-même avoir un comportement réseau ; Oryn ne lance aucun téléchargement/upload et ne lit pas le contenu.

Un candidat exige extension `.epub`, MIME EPUB, nom borné sans contrôle/chemin et taille connue de 1 octet à 8 MiB. Ce contrôle de métadonnées n'est **pas** une validation structurelle ni une preuve des droits. Le texte UI l'indique explicitement.

Deux confirmations indépendantes, décochées par défaut : droits nécessaires et volonté de rendre cet EPUB accessible à la communauté après validation. Elles sont liées à un identifiant de sélection neuf et à `oryn-epub-rights-v1`. Changer de fichier (même nom), fermer la fenêtre ou recréer l'écran les efface ; aucune restauration automatique ni réutilisation. « Vérifier mon choix » ne fait qu'une préparation en mémoire et indique qu'aucun fichier n'est envoyé. `executeShare` retourne toujours `Disabled`, même pour un plan valide.

Avant un upload réel, une nouvelle session/autorisation et une nouvelle confirmation par EPUB seront obligatoires. Le serveur devra rester l'autorité du propriétaire, du chemin, du consentement versionné, de la validation, de la modération et des quotas. Le plan local ne prouve ni session, ni publication, ni droits, et ne constitue jamais une autorisation serveur. Aucun partage automatique ou job différé n'existe.

## Design, traductions et accessibilité

Réutilisation Material 3, `GlassChip`, tokens OrynSpace/OrynShape, surfaces et couleurs sémantiques existantes. Aucun gradient, cover factice ou nouvelle bibliothèque visuelle. Couverture distante non chargée ; `BookCover` local et ses caches ne sont pas modifiés.

Textes FR/EN via I18n, y compris les titres et descriptions de démonstration. Les données du jeu de simulation (identifiants et objets) restent dans debug ; les traductions UI ne constituent pas un catalogue de publication. Cibles tactiles ≥ 48 dp, filtres sémantiques, choix de scénario avec état sélectionné/TalkBack, cases de consentement actionnables sur toute leur ligne, annonces polies des états, dialogues à contenu défilant et boutons hors de la zone défilante. Aucun changement d'animation : seuls les indicateurs fonctionnels existants sont utilisés. Contrastes fond/texte repris des couples Oryn vérifiés en phase 2 ; vérification sur appareil/grande police encore nécessaire.

## Vérifications et parcours manuel

Les tests unitaires couvrent recherche/accents/filtres/tri, pagination sans doublons/fin, toutes les erreurs, provenance mélangée refusée, détails absents/incorrects, limites de métadonnées et annulation. Tests ViewModel avec horloge virtuelle : debounce, retours tardifs, détails fermés, retour au repository verrouillé, erreurs de pagination et session expirée. Tests de consentement par sélection/version et impossibilité d'exécution des plans pourtant valides. Les tests Auth des phases précédentes sont conservés.

Pour vérifier l'UI sur appareil debug :

1. Ouvrir Communauté sans mode démo : état indisponible, aucun faux livre. Activer explicitement la démonstration ; vérifier tous les bandeaux/lignes et les sept scénarios.
2. Sur Exemples, rechercher « DEMONSTRATION », filtrer FR/EN, parcourir les trois pages, trier dans les deux sens et réinitialiser. Taper vite pour confirmer l'absence de résultat obsolète ; quitter la démo pendant un chargement.
3. Ouvrir une fiche, vérifier description/langue/provenance, examiner le téléchargement : aucun fichier disponible dans la simulation et exécution désactivée. Fermer pendant un chargement ; aucun retour intempestif de la fiche.
4. Dans le partage, choisir un EPUB local au profil de test : pas d'octets lus ni d'upload. Confirmer une seule case puis les deux ; contrôler l'activation du seul bouton « Vérifier mon choix », sans bouton d'envoi réel. Rechoisir le même document, fermer/rouvrir et faire tourner l'appareil : aucune confirmation ne survit.
5. Essayer taille inconnue, document trop gros, MIME non EPUB ou fournisseur inaccessible ; toutes les confirmations doivent rester indisponibles. Annuler le sélecteur et fermer la fenêtre pendant la lecture des métadonnées : aucun effet sur la bibliothèque.
6. Avec TalkBack, police 200 %, thèmes clair/sombre, petit écran et paysage : navigation/filtres/consentement lisibles et accessibles, dialogues défilants sans masquer Fermer. Vérifier également Gutendex, import local et lecture avec un livre du profil de test, sans effacer les données.
7. Installer un build release sur le profil de test seulement : aucun bouton ni source de démonstration ; catalogue et partage toujours bloqués. Ne pas fournir de backend réel pour faire réussir ce test.

Aucun appareil/émulateur n'est disponible dans l'environnement actuel. Ces étapes, ainsi que les validations Auth réelles décrites dans le rapport de phase 3, restent à effectuer. Aucun endpoint Supabase réel, SQL ou migration n'a été utilisé. Le test de contrat Auth isolé reste optionnel et ignoré faute de configuration.

## Résultat final

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:compileReleaseKotlin` : **BUILD SUCCESSFUL**. **70 tests, 69 réussis, 0 échec, 1 ignoré** (contrat Auth Supabase isolé non configuré). Vingt et un tests ajoutés ; les 49 précédents sont préservés. Le test de recherche couvre aussi les libellés anglais visibles des exemples.
- Factory release inspectée par `javap` : `available = false`, retour constant null. Classe de fixtures `DevelopmentCommunitySource` absente des classes release ; le repository simulé vérifie également DEBUG. Aucun exemple ne peut remplacer silencieusement le repository distant.
- `git diff --check` réussi ; contrôle des traductions UI et comparaison de hashes avec l'entrée de phase 4. Auth, manifeste, MainActivity/MainViewModel, Explorer/Gutendex, lecture, Room/import/bibliothèque, politiques de backup et `supabase/` inchangés. Aucun secret, URI persistée, logger ni job d'upload ajouté. Aucun déploiement, commit ou push.
- ADB vérifié : aucun appareil connecté. Les contrôles visuels/accessibilité et le sélecteur réel restent à valider sur appareil selon les étapes ci-dessus. Le backend de production reste injoignable par le catalogue ; exécution des téléchargements et du partage toujours désactivée. Auth réel et contrat backend restent bloqués par les prérequis de la phase 3.

### Fichiers concernés

Sept fichiers existants modifiés : `app/build.gradle.kts` (test coroutine 1.7.3 aligné sur le runtime existant), `community/CommunityCatalog.kt`, `community/CommunityCatalogViewModel.kt`, `community/RemoteCommunityCatalogRepository.kt`, `ui/screen/CommunityCatalogSection.kt`, `i18n/I18n.kt`, `CommunityCatalogTest.kt`.

Dix ajouts utiles : `community/CommunityCatalogModels.kt`, `community/SimulatedCommunityCatalogRepository.kt`, `community/CommunityTransferPreparation.kt`, `ui/screen/CommunityCatalogDialogs.kt`, factories `CommunityDevelopmentCatalog.kt` distinctes dans `src/debug` et `src/release`, tests `CommunityCatalogRepositoryTest.kt`, `CommunityCatalogViewModelTest.kt`, `CommunityTransferPreparationTest.kt`, et ce rapport.

Prochaine étape : revue UI sur appareil en debug et release, puis vérification Auth isolée et contrat de catalogue réel avant toute proposition distincte d'activation. Aucune confirmation locale de cette phase ne sera réutilisable pour un futur upload.
