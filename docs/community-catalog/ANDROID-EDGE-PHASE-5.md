# Phase 5 — contrats Edge, repository fermé et validation Android

## Audit avant modification

Branche `feature/community-epub-catalog`, base distante `2a206b8`. Les modifications locales non commitées des phases 1–4 sont présentes et préservées ; aucun reset, merge, déploiement ou push. Revue des règles AGENTS/DESIGN, gates catalogue/transferts, Auth PKCE/Keystore/refresh, sources debug/release, tests et rapports précédents, et lecture des six Edge Functions et de leurs validations.

| Fonction existante | Contrat observé | Surface Android préparée |
|---|---|---|
| `catalog-create-upload` | POST title/author/language/rightsConfirmed ; 201 pending, bucket privé, chemin généré, expiration, 8 MiB max | Création de ticket après consentement ; aucun upload Storage implémenté |
| `catalog-finalize-upload` | POST bookId ; 200 pending/validated/sha256, jamais publication ; 409/422 possibles | Finalisation explicite d'un identifiant, sans chemin client |
| `catalog-retract` | POST bookId ; 200 removed ou 202 removed/cleanupPending | Retrait explicite ; distinguer suppression d'objet et nettoyage restant |
| `catalog-download` | POST bookId ; 200 flux EPUB privé/no-store de taille bornée | Téléchargement avec taille/hash attendus ; aucun import Room ni fichier publié |
| `catalog-moderate` | POST bookId/decision publish ou reject ; autorisation par liste serveur ; 403 sinon | Contrat séparé du repository utilisateur, accès client refusé par défaut faute de capacité serveur vérifiée |
| `catalog-cleanup` | POST protégé par secret serveur dédié ; compteurs de batch ; jamais session Android | Documenté uniquement : aucune méthode, endpoint permis ou secret de nettoyage dans le client Android |

Tous les endpoints utilisateur revalident le JWT via Auth et refusent les comptes anonymes. Aucun propriétaire, statut publié ou chemin fourni par le client ne peut remplacer les contrôles serveur. Les credentials privilégiés restent exclusivement dans les fonctions serveur existantes.

État ADB détecté automatiquement au début de phase : aucun appareil connecté. Les tests instrumentés seront préparés/compilés, sans inventer d'exécution. La configuration isolée et les validations réelles des phases 2–3 restent absentes ; aucune opération réseau communautaire ne sera activée.

## Architecture et verrouillage

Le code `community/edge` fournit les DTO validés, le protocole POST, le transport HTTPS borné et un cœur de repository injectable (API/session/capacité de modération). Les façades applicatives sont `CommunityEdgeRepository.fromBuild(session)` et, séparément, `CommunityModerationRepository.fromBuild(session)`. L'interface utilisateur ordinaire ne possède que quatre opérations : création de ticket, finalisation, retrait et téléchargement.

Le gate privé `SECURITY_VALIDATED = false` s'ajoute au gate catalogue existant et à la configuration Auth isolée. La façade le vérifie avant lecture/renouvellement de session ; l'API de production le revérifie avant même de construire HTTP. Aucun booléen public, préférence ou mode de simulation ne peut ouvrir ce gate. Les contrats HTTP internes peuvent être testés directement avec des fixtures de session et un serveur TLS loopback ; cela ne désactive aucune protection dans la fabrique applicative et ne crée pas de mode réseau de secours.

Les fonctions de transfert de la phase 4 restent elles aussi strictement `Disabled`. Le nouveau repository n'est branché à aucun bouton de production, observateur de bibliothèque, import ou job différé. Créer un ticket n'envoie que les métadonnées explicitement confirmées, jamais les octets d'un EPUB. Aucun client Storage/upload n'a été ajouté et la création ne déclenche pas automatiquement une finalisation.

### Modération et nettoyage

- Le contrat de modération est distinct de l'interface utilisateur. Le repository de production utilise une politique refusant tout accès faute de preuve serveur liée à la session courante. Aucun fournisseur de capacité serveur n'existe encore : cela reste un blocage avant activation. Ni `user_metadata`, ni rôle décodé du JWT, ni liste de comptes Android ne peuvent accorder ce droit.
- Même si une future capacité serveur est vérifiée, `catalog-moderate` doit toujours refaire son propre contrôle d'autorisation. Le test d'une capacité locale synthétique suivie d'un 403 prouve seulement que le refus serveur est conservé ; il ne valide pas une autorisation réelle.
- `catalog-cleanup` demeure exclusivement serveur : aucun enum d'endpoint, méthode, header de secret ou modèle de credential de nettoyage côté Android. Son secret doit rester dans le serveur/ordonnanceur isolé. Les compteurs `expired/cleaned/retryPending/batchLimit` appartiennent au contrat serveur documenté, pas à une API mobile. Aucune planification ou invocation de nettoyage n'a été effectuée.

### Transport et erreurs

- Origine HTTPS validée, sans userinfo, path, query ou fragment ; allowlist fermée de cinq noms de fonctions. L'autorité et le chemin exact sont contrôlés avant d'attacher le JWT utilisateur. La configuration applicative conserve les règles plus strictes de la phase 1. Les ports HTTPS loopback explicites sont réservés au composant interne utilisé par les tests.
- `Authorization: Bearer <JWT utilisateur>` et `apikey: <clé publique publishable>`, jamais une clé de projet dans Authorization. Aucun credential privilégié n'est lu/embarqué. Le format du bearer est contrôlé sans en décoder les claims pour accorder des permissions ; la session doit provenir du fournisseur Auth vérifié et le serveur revalide l'utilisateur.
- Client neuf, sans interceptor/journalisation, cookie, cache, authenticator ou retry automatique. Redirections HTTP/HTTPS refusées, y compris vers un autre hôte ; certificat non fiable refusé. Les clients injectés dans les tests doivent également respecter l'absence de redirects/retry/interceptors/authenticators. Aucun trust-all ni désactivation de vérification du hostname.
- Corps JSON UTF-8 borné à 8 KiB, réponses JSON bornées à 32 KiB, JSON UTF-8 strict et DTO validés. Le chemin Storage retourné doit être opaque au format UUID/UUID.epub associé au bookId ; il n'est jamais transformé en URL ni suivi. Dates UTC parsées sans java.time pour préserver API 24. La finalisation reste pending, une rétractation 202 garde cleanupPending, et une publication n'existe que dans la réponse administrative conforme.
- Téléchargement : POST avec seulement bookId, HTTP 200/MIME EPUB requis, taille attendue ≤ 8 MiB, lecture bornée même en chunked, signature ZIP minimale et SHA-256 constant-time. Aucun nom Content-Disposition n'est utilisé. Les octets restent en mémoire, redaction dans toString, fermeture explicite pour les effacer, aucun fichier ou import Room. Cette vérification d'intégrité ne remplace pas une validation structurelle/antivirus/droits ; l'import réel reste interdit et devra être revalidé dans une future phase.
- 401 → session concernée invalidée, sans rejeu automatique ; 403 → accès refusé sans déconnecter l'utilisateur ; 404 → introuvable ; 429 → quota avec Retry-After numérique borné s'il existe ; 409/422/413 distingués ; 500/503 → erreur/indisponibilité ; coupure/timeout → réseau indisponible. Les corps d'erreur, URL, tokens et causes détaillées ne sont ni retournés à l'UI ni journalisés.
- Une mutation interrompue ou ayant reçu un 5xx, une redirection ou un résultat illisible peut avoir eu lieu côté serveur : le résultat expose `outcomeMayHaveChanged`, sans relancer l'opération. Les fonctions existantes ne fournissent pas d'idempotency key de création ; il faut réconcilier l'état ou refaire un consentement explicite, jamais rejouer silencieusement une création.

### Correction de course de session

Un 401 d'une ancienne requête pouvait sinon effacer une session fraîchement renouvelée par une autre opération. Le contrat de session ajoute `invalidate(token)` ; SupabaseAuthController compare atomiquement le token courant sous son Mutex avant effacement. Les fournisseurs non rotatifs conservent le comportement clear par défaut ; tout fournisseur rotatif injecté doit implémenter la comparaison. Le repository catalogue existant et les nouvelles opérations utilisent cette invalidation ciblée. Un test HTTP retarde un 401, renouvelle la session entre-temps et vérifie que le nouveau token reste intact, sans rejouer la requête.

## Compilation release et règles de sauvegarde

Le premier `assembleRelease` a révélé huit erreurs lint **préexistantes** `FullBackupContent` : des exclusions de database, covers, books et external n'appartenaient à aucun chemin inclus. Android précise qu'en présence d'éléments include, seuls ces chemins sont sauvegardés (référence : [Auto Backup, include/exclude](https://developer.android.com/identity/data/autobackup)).

Correction ciblée : retirer seulement ces exclusions redondantes dans `backup_rules.xml` et la section cloud de `data_extraction_rules.xml`. Les inclusions cloud restent exactement `sharedpref/.` et `file/datastore` ; Room, les livres et covers demeurent donc exclus implicitement. La section device-transfer reste identique. Les fichiers Auth dans noBackupFilesDir restent exclus. Deux tests vérifient ces listes et les protections des données ; aucun suppression/baseline lint, désactivation de protection ou changement de la politique effective n'a été utilisé pour faire passer release.

Aucun schéma Room, fichier utilisateur, préférence sauvegardée, Gutendex, lecteur, navigation ou serveur Supabase n'a été modifié.

## Tests HTTP hermétiques

Le serveur MockWebServer utilise un certificat TLS généré pour localhost ; son port fait partie de l'origine autorisée du test. Aucune réécriture d'URL par interceptor n'est utilisée. Les seules valeurs de bearer/clé sont des fixtures synthétiques dans src/test, jamais un compte réel ou une clé privilégiée. La CA de test n'existe pas dans le transport de production.

Couverture : endpoints/bodies/headers exacts, droits explicitement confirmés, ticket privé, finalisation pending, retrait 200/202, modération refusée par défaut/403 serveur, téléchargement en mémoire et hash, réponses malformées, MIME/hash/taille invalides, session absente/expirée/renouvelée/refusée, invalidation ciblée, erreurs 401/403/404/429/500, timeout, socket coupée, annulation, certificat non fiable, redirections sans transfert de bearer, absence du nettoyage et gates applicatif/API avant Auth. Aucun de ces résultats ne prouve un déploiement ni une configuration RLS/Storage réelle.

Références Supabase relues : changelog, [Authorization headers](https://supabase.com/docs/guides/functions/auth-headers) et [verify_jwt](https://supabase.com/docs/guides/functions/auth-legacy-jwt). La clé publique est envoyée uniquement dans apikey et le JWT utilisateur dans Authorization. La revalidation utilisateur et l'autorisation serveur des handlers sont conservées ; aucune configuration verify_jwt ni clé serveur modifiée.

## Scénarios Android instrumentés préparés

Source : `app/src/androidTest/.../CommunityAndroidScenariosTest.kt`, runner AndroidJUnitRunner. Quatre scénarios hermétiques dans l'Activity de test Compose, **sans lancer MainActivity ni accéder à Room** :

1. Contrôleur PKCE sur Android avec Auth/API/coffre en mémoire : nonce forgé refusé, callback nominal, rejeu refusé puis déconnexion. Ce test ne lance ni navigateur réel, ni App Link, ni Android Keystore et ne doit pas être présenté comme une preuve OAuth de bout en bout.
2. Interface réelle Communauté en mode développement explicitement choisi : recherche, filtre, remise à zéro, pagination et présence du marquage de simulation ; aucune session synthétique injectée dans l'application.
3. Fiche de démonstration et préparation du téléchargement : aucun fichier réel, bouton désactivé, aucun import possible.
4. Fenêtre réelle de consentement : sélecteur de document injecté avec métadonnées de fixture uniquement, deux confirmations obligatoires et remise à zéro pour une nouvelle sélection. En utilisation normale, l'argument est absent et le sélecteur Android existant reste inchangé ; aucune fixture n'est fournie par défaut.

Les scénarios exigent Auth applicatif désactivé et sont ignorés si une configuration Auth réelle est activée : ils ne doivent pas lire une session d'un vrai compte pour tester des fixtures. Ils sont compilés via `:app:assembleDebugAndroidTest`. Ne pas exécuter contre un profil personnel ou une installation production ; aucun test ne doit effacer ou réinitialiser les données pour réussir.

### Détection automatique et exécution restante

La commande `adb devices -l` a été exécutée automatiquement : **liste vide**. Aucun `connectedDebugAndroidTest`, scénario instrumenté ou OAuth réel n'a donc été exécuté, et aucun résultat appareil n'est annoncé. L'APK de test peut être installé/exécuté ultérieurement sur un appareil/profil dédié, sans données personnelles, avec les gates communautaires fermés.

Pour l'opérateur, après connexion d'un profil de test :

1. Vérifier ADB (appareil online et autorisé), identité du profil de test et absence de configuration Auth réelle pour les quatre scénarios hermétiques. Ne pas forcer les statuts de vérification de domaine.
2. Exécuter `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.oryn.reader.community.CommunityAndroidScenariosTest`, sans `--debug` ni dumps de tokens. Archiver uniquement le vrai rapport instrumenté produit par Gradle et les états/tests, jamais le contenu de session.
3. Réaliser séparément la connexion OAuth PKCE réelle sur projet isolé et hôte HTTPS possédé : assetlinks/certificat/OS verified, retour navigateur, fermeture de processus, restauration, expiration, refus/rejeu/annulation et déconnexion. Suivre A1–A12 du rapport de phase 3. Le test de contrôleur en mémoire n'est pas ce test réel.
4. Refaire la recherche/filtres/pagination/TalkBack/police 200 % et le parcours de consentement avec un EPUB local du seul profil de test : MIME/taille inconnue/limite, retour arrière, changement de document, rotation d'écran. Aucun contenu ne doit être envoyé ; aucune confirmation ne doit survivre à une nouvelle sélection.
5. Pour le téléchargement de cette phase : vérifier uniquement la préparation et le blocage réseau. Les essais réels de flux EPUB, contrôles serveur et isolation de comptes ne peuvent avoir lieu qu'après configuration isolée, audit/validation et autorisation d'activation distincts. Ne jamais changer une protection pour faire passer les tests présents.

La configuration isolée, l'hôte/certificat vérifié, la capacité de modération serveur et les tests RLS/Storage restent manquants. Le nettoyage doit être testé via son ordonnanceur serveur isolé avec son secret serveur, jamais à partir d'Android. Aucun point de cette préparation n'autorise un déploiement ou une connexion à la production.

## Résultat final observé

- Commande : `./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:assembleDebugAndroidTest` → **BUILD SUCCESSFUL** ; release passe également lintVitalRelease sans désactivation de contrôles.
- **93 tests JVM découverts : 92 réussis, 0 échec, 1 ignoré**, soit 23 nouveaux tests en complément des 70 précédents. Le seul ignoré reste le GET Auth réel isolé non configuré. **Quatre scénarios instrumentés compilés, aucun exécuté** ; aucune réussite Android réelle n'est revendiquée.
- Artifacts produits : `app/build/outputs/apk/debug/app-debug.apk`, `app/build/outputs/apk/release/app-release-unsigned.apk` (non signé pour distribution), `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
- Contrôle des manifestes et constantes debug/release : Auth et callback désactivés, URL/clé non renseignées, HTTPS conservé. Gates catalogue/transferts/API fermés. Aucun logger, credential privilégié ou appel de nettoyage dans le code Android ajouté.
- `git diff --check` réussi, comparaison avec l'état d'entrée de phase 5 : aucun changement de `supabase/`, Room/data/import, lecteur, MainActivity/MainViewModel, Gutendex/Explorer, manifeste source ou traductions. Les deux fichiers de backup ont seulement été normalisés selon leur liste d'inclusion effective, comme expliqué et testé ci-dessus. Aucun changement de données utilisateur, déploiement, commit ou push.

### Fichiers de cette phase

Sept fichiers existants modifiés : `app/build.gradle.kts` (runner/dépendances instrumentées), `community/CommunityCatalog.kt` (contrat d'invalidation ciblée), `community/RemoteCommunityCatalogRepository.kt`, `community/auth/SupabaseAuthController.kt`, `ui/screen/CommunityCatalogDialogs.kt` (sélecteur injectable, comportement normal inchangé), `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`.

Dix ajouts : les quatre fichiers `community/edge/CommunityEdgeModels.kt`, `CommunityEdgeHttp.kt`, `CommunityEdgeProtocol.kt`, `CommunityEdgeRepository.kt` ; les quatre fichiers de tests JVM `edge/EdgeTestFixture.kt`, `CommunityEdgeHttpTest.kt`, `CommunityEdgeRepositoryTest.kt`, `CommunityBackupPolicyTest.kt` ; `src/androidTest/.../CommunityAndroidScenariosTest.kt` et ce rapport.

Blocages réels : aucun appareil connecté, aucun projet/fournisseur Auth isolé et domaine App Link vérifié disponibles, aucun fournisseur de capacité de modération serveur validé, ni test runtime RLS/Storage ou Edge Functions déployées. Le réseau communautaire reste désactivé. Prochaine étape : exécuter les scénarios sur un profil Android dédié et achever la validation Auth/autorisations/contrats sur un environnement isolé, sans autorisation implicite de production.
