# Oryn — phases 7 à 10 : audit, robustesse et préparation

2026-11-02 · branche `feature/community-epub-catalog` · décision **NO-GO pour activation/livraison publique**.
Audit local du code et tests hermétiques réalisés ; validation réelle Android/Supabase incomplète. Lint stabilisé ensuite : voir la dernière section. Aucun déploiement, changement de production, activation réseau, upload ou opération destructive distante. Room, Gutendex, bibliothèque et lecteur préservés.

## Périmètre, méthode et état Git

Reconnaissance unique : AGENTS/DESIGN, historique Git, rapports phases 1–6, sources Kotlin et Compose communautaires, manifest/sauvegardes, six handlers Edge, migrations, politiques RLS/Storage, tests et dépendances. Les rapports historiques restent des instantanés ; les résultats actuels sont ci-dessous.

Le working tree initial contenait toutes les phases 1–6 non commitées. Elles ont été conservées, contrôlées puis enregistrées dans `debb5bf` ; aucun reset/stash/écrasement. Base serveur initiale `2a206b8`. Commits locaux, aucun push :

| Commit | Domaine |
| --- | --- |
| `debb5bf` | Fondations Android existantes phases 1–6, catalogue/transferts verrouillés |
| `d7f591b` | Échéance d’upload, recontrôle du retrait, garde du test local, lockfile |
| `5b0c50b` | Annulation HTTP et traductions communautaires |
| `e04ddd6` | Répertoire ZIP borné, régressions d’archives et microbenchmark |
| `3db42e0` | Rapport initial et mise à jour ciblée du README Supabase |

Le workflow Cloudflare complet exigé par [SECURITY-PLAN](../community-catalog/SECURITY-PLAN.md) n’a **pas** été exécuté : vérificateurs indépendants et sandbox d’audit conforme non établis. Ce document est une revue ciblée avec preuves de régression, pas une certification de sécurité ni une autorisation d’activation. Ce prérequis demeure `needs_validation`.

## Phase 7 — constats et corrections

Les niveaux décrivent l’impact du code dans les conditions indiquées ; aucune exploitation en production n’a été testée. Aucun accès interutilisateurs démontré. Les comportements Android restent derrière les gates fermées.

| ID | Gravité / exploitabilité | Preuve et correction |
| --- | --- | --- |
| S1 | Moyenne, conditionnelle : utilisateur propriétaire d’un ticket pending périmé sur un backend utilisant ces sources | Le handler finalisait un EPUB après l’échéance annoncée tant que cleanup n’avait pas changé le statut ; test réel du handler + SDK, fetch intégralement simulé, échouant avant correction. Refus 409 avant Storage ; migration préparée restreignant INSERT à pending, hash absent et échéance valide. Trigger privé vérifiant l’heure DB effective lors de l’écriture du hash, sans nouveau privilège client, empêche aussi une validation devenue tardive pendant le traitement. Mapping P0001 → 409 couvert. **Application SQL et exploitation Storage réelle non validées.** |
| S2 | Moyenne opérationnelle : lancement manuel du test avec credentials privilégiés pointant vers production | Le test d’intégration n’imposait qu’un environnement non vide avant création de comptes/fixtures et nettoyage. Il exige maintenant un opt-in explicite et deux URL loopback de même origine, sans userinfo/query/fragment, avant toute lecture de credentials. Quatre tests couvrent absence d’opt-in, destinations hostiles, configuration incomplète et cas local valide. Aucun backend réel appelé. |
| H1 | Faible, renforcement du retrait : requête déjà autorisée avant retrait | Un retrait pendant le fetch Storage pouvait laisser revenir HTTP 200. Test en échec avant correction, recheck publié/hash/removed après fetch et avant stream ; retrait → 404, erreur de vérification → 503 sans détails. Succès publié toujours couvert. Ce n’était pas une nouvelle autorisation interutilisateurs ; des octets déjà livrés ou une course après le dernier contrôle restent impossibles à rappeler. |
| R1 | Robustesse / défense mémoire, pas une fuite externe démontrée | Une annulation entre réponse HTTP et reprise de coroutine abandonnait le buffer sans effacement explicite. Deux tests JVM initialement en échec ; handoff avec callback d’annulation effaçant les octets non livrés. Un test positif préserve les octets effectivement remis pour leur vérification d’intégrité. |
| R2 | Robustesse / coût d’entrée invalide ; DoS Edge non démontré | La limite 250 entrées s’appliquait après matérialisation de tout le répertoire ZIP. Lecture par générateur arrêtée à l’entrée 251, mêmes contrôles CRC/ZIP/XML/taille/ratio et fermeture du reader dans finally. Mesure locale ci-dessous ; capacité Edge réelle à valider. |
| U1 | Défaut de traduction, sans impact de sécurité établi | Auteur/description simulés restaient en français ; un titre distant égal à une clé UI pouvait être traduit à tort. Traduction appliquée uniquement à la provenance Simulation, données distantes préservées. Trois régressions FR/EN. |

Migration `supabase/migrations/20261102173226_enforce_pending_upload_deadline.sql` créée avec **Supabase CLI 2.81.3 `migration new`**, jamais appliquée. CLI 3.0.0 n’a pas pu créer son répertoire de configuration sur ce filesystem ; utilisation de la version 2.81.3 après contrôle SHA-256, sans modifier HOME. Les exécutables/outils/cache restent hors Git. Le test SQL transactionnel `supabase/tests/community_deadline_security.sql` prépare les cas propriétaire A/B, ticket expiré avant cleanup, hash tardif refusé et ticket validé non réutilisable ; **non exécuté**.

## Couverture des frontières et limites

| Surface inspectée | Éléments établis par code/tests locaux | Ce qui reste `needs_validation` |
| --- | --- | --- |
| PKCE / App Links | S256, nonce indépendant, URI HTTPS/authority/path exacts, pollution/fragment/implicit-token/rejeu/expiration/annulation rejetés ; callback dédié et intents expurgés | Navigateur OAuth, assetlinks, empreinte réelle, état OS verified API 31+, résolution API 24–30, reprise après mort du processus |
| Sessions / coffre | Mutex, rotation durable, /user, expiration et réponses tardives, invalidation ciblée 401, logout local avant révocation, AES-GCM/AAD/IV et format borné testés JVM ; I/O du coffre sur Dispatchers.IO | Keystore matériel, restauration/backup/device transfer, perte/invalidation de clé, logcat et profilage sur appareil. Les chaînes JVM de tokens ne sont pas effaçables en mémoire |
| Edge / autorisation | Allowlist HTTPS avant bearer, aucune redirection/retry/cookie/cache/interceptor ; 401/403/404/429/5xx/coupures/annulation testés, corps d’erreur non exposés. Propriétaire dérivé de getUser ; modération allowlist serveur, refus client par défaut ; cleanup absent de l’API Android | Déploiement effectif, configuration JWT gateway, quotas globaux/IP, limites de téléchargement et CPU. getUser vérifie l’identité ; il ne prouve pas ici une révocation immédiate de tous JWT après logout. Contrôler session_id/auth.sessions si une telle garantie est requise |
| RLS / Storage | Migrations : RLS activée/forcée, writes clients révoqués, lecture publiée/propre, bucket privé, helper privé SECURITY DEFINER à search_path vide et owner/path exacts ; migration préparée plus restrictive | Politiques effectives sur instance, schémas exposés, anciennes politiques Storage permissives, Security Advisors, deux utilisateurs + anonyme. TO authenticated inclut les comptes anonymes si cette option est activée : les handlers les refusent, mais la politique de lecture directe ne les distingue pas ; tester cette configuration avant ouverture |
| Consentement / retrait | Deux confirmations liées à une sélection, réinitialisation UI ; préparation révocable/consommation atomique unique ; aucun upload automatique, URI persistée ou contenu envoyé par l’UI ; 202 retrait ne republie pas | Droits réels/modération et retrait Storage en présence de concurrence, gestion des objets orphelins après suppression de compte, reprise des suppressions removed en échec (cleanup actuel cible expired ; retrait à retenter explicitement) |
| Release / permissions | Garde sur DEX et manifest binaire : fixtures/traductions spécifiques absentes, contrôle positif debug et négatif debug-as-release ; INTERNET + permission signature AndroidX seulement ; cleartext=false ; callback Auth désactivé sans filtre en release ; overlay debug HTTPS autoVerify/path exact réservé à une configuration Auth complète | Signature de diffusion, AAB/splits et appareil. Le release produit reste non signé |

Les limites dépendant du déploiement sont des hypothèses/contrôles restants, pas des vulnérabilités de production démontrées. Aucune politique RLS/Storage/Auth désactivée, aucun bucket rendu public, aucune clé privilégiée Android, aucun code de nettoyage serveur exposé au mobile.

## Phase 8 — robustesse, performance et UI

Recherche : debounce 350 ms et génération anti-résultat périmé ; pagination bornée à une page et erreurs réessayables sans effacer cette page ; détails annulables. Régressions existantes conservées. Aucun cache authentifié persistant ajouté : pas de nouvelle invalidation intercomptes. Le repository distant reste préparatoire : requêtes non défaut et fiches distantes indisponibles ; le contrat serveur de recherche/pagination et métadonnées taille/hash doit être validé avant activation. Aucun succès distant n’est simulé. Pas d’optimisation speculative des 18 fixtures ni de refactorisation esthétique. JSON/réseau bornés, HTTP sur workers OkHttp, coffre sur IO ; fluidité et latence réelles non mesurées.

Le microbenchmark reproductible `scripts/benchmark-community-epub.ts` rejette un ZIP synthétique de **507 802 octets, 5 000 entrées**. Deno 2.9.8, même environnement local, sans backend/appareil, hors build Gradle ; deux séries alternées avant/après, 3 échauffements puis 20 échantillons chacune. Baseline extraite de `debb5bf` ; les résultats de la première mesure sous charge Gradle ont été écartés de cette comparaison.

| Série | Médiane avant → après | P95 avant → après |
| --- | --- | --- |
| 1 | 57,17 → 3,98 ms | 84,31 → 6,90 ms |
| 2 | 59,57 → 3,35 ms | 87,72 → 6,20 ms |

Ces gains concernent uniquement le rejet d’un répertoire hostile local ; aucune mesure RAM, débit réseau, Android ou capacité de production n’est revendiquée. Le chemin valide et sept catégories d’archives invalides sont testés : nombre d’entrées, traversal/collisions, DTD/entités, ressource manquante, ratio, XML trop gros, CRC altéré. Une archive traversante peut être refusée par zip.js avant le contrôle applicatif ; le test exige un EpubValidationError de refus, jamais un succès.

Compose : composants/thème Oryn, 48 dp, rôles des cases et filtres, annonces d’états, états hors ligne et distinction simulation conservés ; traductions de fixtures toujours exclusivement debug. Aucun résultat de TalkBack/contraste effectif/font scaling sur appareil n’est inventé.

## Phase 9 — résultats initiaux, avant stabilisation lint

| Vérification | Résultat final |
| --- | --- |
| JVM `:app:testDebugUnitTest` | **108 réussis, 0 échec/erreur, 1 ignoré** (sonde Auth isolée sans configuration) |
| Deno `supabase/functions/*/*_test.ts supabase/tests/*_test.ts` | **38 réussis, 0 échec, 1 ignoré** (intégration locale sans opt-in/instance) |
| Scénarios instrumentés | **5 compilés**, `:app:assembleDebugAndroidTest` terminé ; **0 exécuté**, `adb devices -l` vide |
| Builds `:app:assembleDebug :app:assembleRelease` | **Réussis**, invocation finale dédiée `BUILD SUCCESSFUL` ; release non signé |
| Android lint debug/release complet | **Échec : 15 erreurs par variante**, 71 avertissements debug / 63 release ; détails ci-dessous. L’invocation globale avec `--continue` a fini en échec de lint, même si les assemblages ont terminé |
| Typecheck Deno | Six index Edge contrôlés, lockfile `--frozen`, réussi |
| Lint Deno | Neuf fichiers changés contrôlés, réussi avec seule exclusion `no-import-prefix` (convention d’imports npm/jsr explicites déjà épinglés). Premier lint refusait cette règle stylistique récente ; aucune règle d’authentification/sécurité affaiblie |
| Artefacts / diff / secrets | Garde release positive et négative réussie ; diff sans erreurs d’espaces ; scan de 534 fichiers, ~24 Mo de patches historiques accessibles, 28 logs et DEX debug/release : aucun motif de secret contrôlé |
| SQL / Supabase réel / Advisors | **Non exécutés** : aucun conteneur Supabase actif ni environnement isolé fourni ; aucun endpoint Supabase contacté |

Total tests exécutés avec succès : **146 ; échecs finaux : 0 ; ignorés : 2**. Les 5 scénarios Android compilés et SQL non exécuté ne sont pas comptés comme réussis. Avant correction : 2 régressions de handlers et 2 tests de handoff mémoire en échec, puis passes ciblées et globale ; les erreurs intermédiaires de type/attentes des nouveaux tests ont été corrigées sans désactiver les contrôles.

Lint Android : une erreur AppLinkUrlError concerne l’ancien intent VIEW de fichiers dans MainActivity, 13 ProduceStateDoesNotAssignValue concernent Book3D, BookActions, BookCard, ExploreScreen, Glass, LookupSheet (2), PdfReader (2), ReaderPager, ReaderScreen (2), ReaderTools ; une ByteOrderMark concerne BookTextLoader. Les **15 lignes ont été comparées et sont identiques à `2a206b8`** ; aucune suppression/baseline lint ajoutée. Aucun lancement de lint sur ce vieux commit n’est revendiqué. Ces diagnostics doivent être analysés dans leur domaine sans changer le comportement du lecteur/import ; ils bloquent le lint global et la livraison complète.

Dépendances : pas de nouvelle dépendance Android dans les phases 7–10, résolution `releaseRuntimeClasspath` contrôlée (OkHttp 4.12.0, Okio JVM 3.6.0, coroutines core JVM 1.7.3). Lockfile Deno avec intégrités/transitifs ajouté et vérifié frozen. Requête OSV datée du 2026-11-02 pour Supabase JS 2.95.0, zip.js 2.24.0, fast-xml-parser 5.11.2, OkHttp 4.12.0, Okio JVM 3.6.0 et coroutines core 1.7.3 : aucun identifiant retourné. Couverture de ces six coordonnées uniquement, pas un audit exhaustif de toutes les dépendances ni une garantie d’absence de vulnérabilité. Docs Supabase sessions/storage/changelog actuels consultés, aucune modification d’API non vérifiée introduite.

Recherche de secrets : clés privées, clés Supabase privilégiées, clés AWS et JWT dont role=service_role ; aucun credential dans les rapports. Les scans par motifs et des patches textuels historiques ne prouvent pas l’absence de tout secret dans tous les anciens blobs binaires. Les tokens de fixtures ne figurent pas dans les logs des passes finales JVM.

SHA-256 des APK vérifiés, actualisés après stabilisation :

```text
debug   d25a1e4e8f62c7ccff778c8cf4e232a999016087734de2a6d8d42de4dcbde133
release 0818cea7295765c7f7e6bc908d649d987ce7f731307bead5ca031229042823fc
```

Commandes finales reproductibles (JDK 17, SDK 35, Gradle wrapper du projet) :

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --continue
./gradlew :app:assembleDebug :app:assembleRelease :app:dependencies --configuration releaseRuntimeClasspath
python3 scripts/check-community-release.py
deno test --no-prompt --lock=supabase/deno.lock --frozen --allow-read=supabase --allow-env=ORYN_CATALOG_TEST_LOCAL_VERIFIED supabase/functions/*/*_test.ts supabase/tests/*_test.ts
deno check --lock=supabase/deno.lock --frozen supabase/functions/*/index.ts
```

Microbenchmark : exporter `git show debb5bf:supabase/functions/catalog-finalize-upload/validation.ts` dans `/tmp/oryn-validation-before.ts` ; exécuter `deno run --lock=supabase/deno.lock --frozen --allow-read=supabase,/tmp/oryn-validation-before.ts scripts/benchmark-community-epub.ts` avec puis sans argument `file:///tmp/oryn-validation-before.ts`, hors autres charges. Aucun `--allow-net`/`--allow-env` nécessaire aux handlers simulés ou au benchmark ; le chargement initial de dépendances publiques est distinct des permissions réseau d’exécution. La passe finale a utilisé `--cached-only` après préparation du cache.

## Phase 10 — checklist réelle et activation progressive

Checklist détaillée appareil/isolé réutilisée : [phase 6](../community-catalog/ANDROID-SECURITY-PHASE-6.md#checklist-appareil--à-exécuter-non-validée-ici). Compléments obligatoires, statut actualisé après stabilisation :

- [x] Résoudre les 15 diagnostics lint préexistants, relancer lint/JVM/builds et contrôler le diff. Les vérifications réelles de lecture/import restent dans la checklist appareil ci-dessous.
- [ ] Appareil jetable API 24–30 et 31+, version cible, certificat installé et hash APK consignés ; exécuter `connectedDebugAndroidTest`, puis vrais scénarios navigateur → retour Oryn, mort de processus/redémarrage, refresh, mode avion, logout. Keystore/backup/logcat privés, TalkBack/FR/EN/font scaling, Gutendex et lecture locale à contrôler.
- [ ] Domaine HTTPS réellement possédé, TLS et assetlinks sans redirect/wildcard, package + empreinte exacts ; PM verified et link handling autorisé requis, y compris après révocation/changement de navigateur. Aucun fallback custom scheme. Configuration privée sans valeurs fictives ; Auth reste false actuellement en debug **et** release.
- [ ] Instance Supabase locale jetable réellement disponible : vérifier sa provenance puis opt-in loopback du runner, credentials injectés privément. Appliquer la nouvelle migration **sur cette instance uniquement**, exécuter les deux SQL de sécurité et l’intégration A/B/anonyme/modérateur. Refuser upload/finalisation au-delà de l’échéance même sans Cron, revalidation tardive, retrait pendant fetch et accès après retrait. Tester politiques effectives, objets expirés/removed/orphelins, quota/CPU et erreurs. Aucun de ces tests ne devient un test de production.
- [ ] Finaliser l’audit indépendant Cloudflare et les contrôles de supply chain/signature/AAB, RLS/Storage effectifs, journalisation et abus ; lever seulement les constats bloquants avec preuves revues.

Plan d’ouverture, sans changement de flags effectué ici :

1. **État actuel** : Auth disabled (config vide), gates catalogue/Edge false, transferts Disabled. Simulation debug seule, aucune donnée de démonstration dans release. Conserver cet état tant qu’un prérequis manque.
2. **Auth isolée debug** : après domaine/certificat/instance validés, autoriser seulement le flux Auth du profil jetable ; catalogue/Edge/transferts restent fermés. Révocation des liens ou configuration incomplète doit refermer le flux.
3. **Lecture catalogue isolée** : dans une future modification relue, séparer une gate read-only testable et les gates de mutation. Tests d’isolation, recherche/pagination et révocation avant tout pilote. Ne pas activer l’actuelle gate globale de façon improvisée.
4. **Téléchargement isolé** : intégrité, limites, droits et validation d’import séparées ; aucun branchement Room/lecteur tant que cette frontière n’a pas sa validation. Commencer par comptes internes et observation expurgée, sans télémétrie sensible ni auto import.
5. **Partage isolé explicite** : nouvelle confirmation par EPUB, droits/version/identité liés, aucun upload en arrière-plan ou rejeu ; finalisation et publication restent séparées, modération serveur, cleanup réservé serveur. Vérifier retrait/échecs avant élargissement.
6. **Éventuel pilote public** : décision de livraison séparée après tous contrôles et autorisation explicite de déploiement ; aucune opération de production couverte par cette session. Ouverture graduelle à un groupe limité, métriques agrégées sans données privées, arrêt immédiat des nouvelles opérations en cas de défaut d’isolement. Revenir aux gates fermées, arrêter les nouvelles tâches et effacer localement les sessions via le parcours prévu ; les mutations déjà engagées doivent être réconciliées, jamais rejouées silencieusement.

Prochaine priorité : obtenir un appareil + domaine vérifié + Supabase local pour exécuter la checklist, particulièrement la migration/RLS/Storage. La préparation locale des phases 7–10 est terminée ; aucune validation externe ou livraison publique n’est déclarée accomplie.


## Stabilisation finale — validation locale actuelle

2026-11-02 · départ `3db42e0`, working tree propre. **15/15 diagnostics initiaux résolus ; zéro erreur lint debug/release.** Les résultats de phase 9 ci-dessus restent historiques. Aucun nouvel audit Supabase, aucune activation Auth/réseau/transfert, aucun déploiement/push.

### Classement et corrections minimales

| Diagnostic initial | Sévérité / origine | Risque de correction et traitement |
| --- | --- | --- |
| 13 × ProduceStateDoesNotAssignValue | Error ; analyse UAST de lint 8.7.3, alors que les 13 producteurs affectent effectivement value | Risque modéré côté outillage, nul changement métier : moteur lint seul épinglé en 8.9.3 via l’option AGP prévue `android.experimental.lint.version`. AGP 8.7.3, Kotlin 2.0.21, Compose et dépendances runtime inchangés. Les écrans, clés, coroutines et affectations d’origine sont conservés. |
| 1 × ByteOrderMark | Error ; caractère U+FEFF littéral dans la chaîne removePrefix du lecteur TXT | Risque très faible : notation `"\uFEFF"` équivalente. **Fichier de classe BookTextLoader byte pour byte identique** à celui sauvegardé avant correction ; aucun changement du décodage, repli d’encodage ou normalisation des lignes. |
| 1 × AppLinkUrlError | Error ; filtre MainActivity VIEW de fichiers avec MIME mais sans schémas explicites | Risque faible sur la résolution des intents : content/file explicités, quatre MIME, action et catégories conservés. Les URI de fichiers exploitables gardent le même périmètre ; aucune autorisation http/https ajoutée au filtre d’import. Résolution réelle sur appareil encore à vérifier. |

Le moteur plus récent a aussi détecté **une erreur supplémentaire confirmée** : hôte Auth vide dans le filtre HTTPS, même lorsque l’activité était désactivée. Correction sans valeur fictive : filtre retiré du manifeste commun ; `app/src/isolatedAuth/AndroidManifest.xml` sélectionné **uniquement pour debug si authConfigured est complet et explicitement isolé**. Release ne sélectionne jamais cet overlay. Sans configuration, callback désactivé et aucun filtre Auth dans les APK. Le filtre optionnel conserve HTTPS, autoVerify, chemin exact et hôte configurable ; aucune protection PKCE ou vérification OS modifiée.

Trois tests JVM ajoutés dans CommunityManifestPolicyTest : périmètre content/file et MIME d’import ; absence de filtre Auth commun et attributs du callback ; contrat HTTPS strict de l’overlay optionnel. La garde d’APK exige désormais un callback release désactivé **sans aucun filtre**, plutôt qu’un filtre à hôte vide. Le fichier de session généré par Kotlin est ignoré via `/.kotlin/`, jamais ajouté à Git.

### Preuves et contrôles exécutés

- Essais locaux d’explicitation des récepteurs et d’activation K2 sous l’ancien moteur non concluants, **retirés**. Aucun écran remanié pour satisfaire lint, aucune suppression, baseline, règle désactivée ou erreur transformée en warning.
- Contrôle négatif sous lint 8.9.3 : un producteur temporaire réellement vide fait échouer lint avec **exactement une erreur ProduceStateDoesNotAssignValue**, dans LintProduceStateSentinel.kt. La règle est donc toujours active. Source temporaire supprimée dans finally, absente de Git et des DEX debug/release finaux.
- Passe ciblée : fusion manifestes debug/release + **3 tests de manifeste réussis**. Vérification du bytecode TXT identique, puis validation globale ci-dessous.

```sh
./gradlew :app:lintDebug :app:lintRelease :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --continue --console=plain
python3 scripts/check-community-release.py
```

| Contrôle actuel | Résultat réellement obtenu |
| --- | --- |
| Lint 8.9.3 debug/release | **Réussi, 0 erreur** ; respectivement **93 et 85 avertissements** conservés. Ces avertissements ne sont pas masqués et ne sont pas assimilés à des vulnérabilités démontrées. Ils comprennent recommandations de versions/UseKtx, ressources, ModifierParameter, API 24, accessibilité HTML, singleton de contexte et trois diagnostics TLS dans le JAR BouncyCastle déjà présent ; aucune réécriture métier ou mise à jour runtime faite pour les supprimer. |
| JVM global | **112 tests : 111 réussis, 0 échec/erreur, 1 ignoré** (sonde Auth isolée, configuration absente). Les 3 nouveaux tests sont compris dans ce total, pas additionnés une deuxième fois. |
| Builds | **Debug et release réussis**, invocation globale BUILD SUCCESSFUL. Le lint release complet a passé ; les tâches lintVital sont ensuite marquées SKIPPED par AGP après ce contrôle complet, pas désactivées. |
| APK debug manuel | `app/build/outputs/apk/debug/app-debug.apk`, signature vérifiée par apksigner. Release : `app-release-unsigned.apk`, aucune signature de publication ajoutée. Hashes actualisés plus haut. |
| Garde d’artefacts | Réussie : fixtures hors release et contrôle positif debug ; permissions inchangées, cleartext false, callback release sans filtre. Contrôle des deux APK et BuildConfig : **Auth false, aucun filtre de callback**, aucune source temporaire. Les gates catalogue/transferts demeurent fermées et couvertes par les tests JVM existants. |
| Instrumentés / SQL-RLS | **Non exécutés** : ADB ne détecte aucun appareil, Docker ne contient aucun Supabase local, aucun environnement isolé prêt. Les cinq scénarios Android restent disponibles ; aucun succès matériel, App Links, Keystore ou SQL/RLS n’est revendiqué. |
| Diff / secrets / Git | Diff contrôlé, pas de modification Room/Gutendex/algorithmes du lecteur, sources Compose identiques au départ. Recherche de clés privées/secret Supabase/clé AWS/JWT service_role dans modifications, logs de stabilisation et DEX : aucune correspondance des motifs contrôlés. Aucun secret ni APK/cache ajouté à Git. |

Logs détaillés conservés hors Git dans `/tmp/oryn-stabilization-*.log` : ciblage initial, résolution moteur, contrôle négatif, tests manifestes et passe finale. Aucun rerun des anciens audits/backend Deno inchangés n’est présenté comme une nouvelle preuve.

Commits de code : `658e2a3` (lint et cache), `3207608` (BOM équivalent), `bd93630` (manifestes, tests, garde d’APK), suivis du commit documentaire du présent état. Aucun push. **Préparation locale terminée ; livraison publique toujours NO-GO** jusqu’à validation appareil, domaine HTTPS réellement vérifié, instance Supabase isolée et audit indépendant prévus dans la checklist. Prochaine action : tester l’APK debug sur appareil, puis valider l’overlay Auth et SQL/RLS uniquement quand ces prérequis existent.
