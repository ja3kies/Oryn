# Phase 6 — audit ciblé Android et préparation préproduction

Date : 2026-11-02. Branche : `feature/community-epub-catalog`, HEAD `2a206b8`.
État initial : phases 1–5 présentes dans le working tree, non commitées ; conservées. HEAD et la référence locale origin sont identiques (0/0), sans prétendre à une synchronisation distante récente. Aucun commit, push, déploiement ou appel Supabase effectué.

**Décision : NO-GO pour l’activation du catalogue, les transferts et la publication.** Les constantes `PRODUCTION_VERIFIED=false` et `SECURITY_VALIDATED=false`, les façades applicatives et l’exécution locale des transferts restent verrouillées avant toute lecture Auth/initialisation HTTP. Auth reste désactivée sans configuration isolée complète, et toujours en release. Les seules requêtes de cette phase sont celles des tests HTTP hermétiques vers MockWebServer loopback TLS (et l’outillage de documentation/build), jamais un backend Supabase.

Cet audit ciblé et les régressions ne constituent pas le workflow Cloudflare complet de [SECURITY-PLAN.md](SECURITY-PLAN.md). Ses validations indépendantes, sorties structurées et contraintes de sandbox restent obligatoires avant activation publique : **needs_validation**. Aucun résultat d’audit formel, d’appareil ou de backend isolé n’est inventé.

## Périmètre et constats corrigés

Lecture des rapports et surfaces phases 1–5 : configuration Gradle, modèles, sources simulées, repository catalogue, UI Explorer/consentement, PKCE, contrôleur de sessions, API Auth, coffre Keystore, callback/runtime App Links, client/protocole/repositories Edge, tests, manifest et règles de sauvegarde. Vérification des contrats contre les six fonctions existantes, sans changer le serveur : quatre opérations utilisateur, modération séparée refusée par défaut, nettoyage exclusivement serveur.

| Frontière | Risque latent derrière les verrous fermés | Correction et preuve |
| --- | --- | --- |
| Auth → repository → HTTP | Une réponse 200 reçue après déconnexion, rotation de session ou expiration pouvait encore être retournée. | `isCurrent(token)` atomique sous le Mutex Auth, sans réseau, exige session validée, même token et durée valide. Contrat par défaut refusant. Contrôle après réponse dans Edge et catalogue ; aucune suppression d’une session plus récente. Tests contrôleur réel + HTTP TLS retardé et changement de session. |
| Téléchargement → demandeur | Des octets déjà vérifiés pouvaient survivre au rejet d’une réponse tardive. | Fermeture/effacement du résultat AutoCloseable dans finally, y compris si la vérification de session échoue. Tests du rejet après changement de session et d’une panne du fournisseur de session. Aucun fichier ni import. |
| Consentement → création | Un objet de préparation pouvait être réutilisé pour plusieurs créations et ne possédait pas de révocation explicite. | Préparation révocable et consommation atomique unique après Auth, avant création. Révocation pendant l’attente Auth bloque HTTP ; concurrence et échec incertain exigent une nouvelle confirmation. Façade désactivée ne consomme rien. |
| Fixtures → release | La source simulée était déjà debug-only, mais ses 20 traductions spécifiques restaient dans le dictionnaire commun. | Dictionnaire de fixtures dans src/debug, variante release vide ; fusion au dictionnaire existant. Contrôle du contenu DEX des APK avec contrôle positif debug. Les notices génériques restent des textes UI, sans fiche simulée disponible en release. |

Une préparation consommée marque le début d’une tentative : révoquer ne peut pas rappeler une requête déjà engagée. Une rétractation serveur explicite est alors nécessaire. Un échec incertain ou une réponse rejetée après changement de session garde `outcomeMayHaveChanged` pour une mutation ; aucun rejeu automatique. Il n’existe toujours aucun envoi de contenu EPUB, planificateur d’upload, écriture Storage ou finalisation implicite. Le dialogue lit seulement les métadonnées du document et efface les deux confirmations lors d’un changement de sélection/fermeture ; son avis local ne devient pas un consentement persistant.

## Contrôles de sécurité effectués

- PKCE S256 : vecteur RFC, secrets indépendants, nonce constant-time, origine/authority HTTPS et chemin exacts, paramètres dupliqués/encodés/inconnus, fragments/tokens implicites, userinfo, port et redirection imbriquée rejetés. Tentative consommée durablement avant échange, durée 300 s, annulation et rejeu après redémarrage couverts par les tests existants.
- Client Edge : destination HTTPS autorisée vérifiée avant bearer, aucune redirection/retry automatique, aucun interceptor, cache, cookie ou authenticator. TLS non fiable refusé. Réponses bornées et validées ; 401 ciblé sans effacer un nouveau token, 403 sans déconnexion, 404/429/500/503, timeout, coupure et annulation couverts. Le serveur demeure l’autorité pour les rôles, droits, RLS et Storage.
- Sessions : expiration/renouvellement sérialisés, validation d’identité, restauration, rotation persistée avant vérification utilisateur, échecs réseau et révocation distante incertaine distingués. Déconnexion locale durable avant appel serveur. Les chaînes JVM de tokens ne sont pas effaçables en mémoire ; elles ne sont ni journalisées ni conservées dans les intents/états UI.
- Coffre : AES-256-GCM, IV aléatoire, AAD liée au package, clé Android Keystore non exportée, AtomicFile dans noBackupFilesDir, format borné, absence de fallback clair. Les tests JVM vérifient chiffrement/altération/format ; ils ne prouvent pas le fonctionnement du matériel Keystore ni des sauvegardes Android.
- APK release : aucune classe source de fixtures ni titre/auteur/description synthétiques. Factory release retourne null ; bytecode des deux gates retourne false. Manifest effectivement empaqueté : INTERNET et permission signature interne AndroidX seulement, aucun accès global aux fichiers, cleartext=false, callback dédié désactivé, HTTPS autoVerify et chemin exact `/auth/callback`. Host vide tant que non configuré. Pas d’élargissement des permissions.
- Revue des sinks : aucun Log/println/printStackTrace ni journaliseur réseau dans community ; DTO sensibles avec toString expurgé. Recherche de clés privées, clés privilégiées, identifiants AWS et JWT service_role dans sources/docs et logs de build sans correspondance. Contrôle complémentaire des DEX pour credentials privilégiés ; ce scan par motifs ne constitue pas une garantie exhaustive.
- Diff phase 6 limité aux frontières communautaires, traductions de fixtures, tests, script et ce rapport. Room, bibliothèque, Gutendex, lecteur, sources serveur, manifest et sauvegardes inchangés pendant cette phase. Les corrections lint des règles de sauvegarde appartiennent à la phase 5 et leurs régressions restent couvertes.

## Validation reproductible

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:assembleDebugAndroidTest
python3 scripts/check-community-release.py
```

Le script utilise ANDROID_HOME (ou --aapt2), inspecte les DEX et le manifest binaire via aapt2 ; il n’exécute/installe aucun APK. Test négatif : lui fournir l’APK debug comme release doit échouer sur les fixtures. Il vise les builds actuels sans minification et ne remplace pas une validation des futurs AAB/splits/signatures de diffusion.

Les résultats finaux sont consignés en dernière section. Les APK de validation ne contiennent aucune configuration Supabase de secours.

## Checklist appareil — à exécuter, non validée ici

- [ ] Appareil dédié API 24–30 et API 31+, puis version Android cible ; aucun compte/fichier personnel. Consigner OS, version/commit, hash APK, certificat SHA-256, date et vérificateur. L’APK release actuel est non signé ; aucune clé de publication n’a été ajoutée.
- [ ] Exécuter `adb devices -l`, choisir explicitement le numéro de série, puis `./gradlew :app:connectedDebugAndroidTest`. Cinq scénarios hermétiques disponibles : PKCE/callback forgé/rejeu + restauration et déconnexion ; recherche/filtre/pagination simulés ; téléchargement bloqué ; deux consentements réinitialisés ; préparation révoquée toujours inexécutable. Ces tests utilisent une Activity de test Compose, un store/API Auth en mémoire ; aucun navigateur, compte ou Keystore réel n’y est simulé comme une preuve d’intégration.
- [ ] Hors connexion : ouvrir Explorer et bibliothèque, parcourir/lire un livre local, changer de langue/thème, revenir de rotation/arrière-plan, fermer le sélecteur. Aucun trafic communautaire, import, mutation Room ou upload ; vérifier TalkBack, focus, libellés, tailles de texte et cibles 48 dp en FR/EN. Les états erreur, vide et hors ligne de démonstration restent clairement identifiés.
- [ ] Pour Auth réel uniquement, disposer d’un projet isolé, fournisseur OAuth de test et domaine HTTPS réellement possédé. Vérifier TLS et `/.well-known/assetlinks.json` (package exact et empreinte du certificat debug réellement installé), sans wildcard ni redirection. Utiliser [auth-isolated.properties.example](auth-isolated.properties.example), valeurs privées dans local.properties ignoré, aucune valeur fictive. Ne mettre les deux flags Auth à true que dans la configuration debug isolée après ces vérifications ; conserver les gates catalogue/Edge fermées.
- [ ] Installer ce debug isolé. API 31+ : `adb shell pm verify-app-links --re-verify com.example.oryn.reader`, puis `adb shell pm get-app-links com.example.oryn.reader` ; hôte en verified et link handling autorisé requis. API 24–30 : vérifier assetlinks/TLS/certificat manuellement et la résolution par défaut vers le callback Oryn. Une simple sélection « ouvrir avec » ne prouve pas la possession du domaine. Aucun schéma personnalisé de secours.
- [ ] Login depuis le bouton explicite → navigateur fournisseur → HTTPS callback → retour Oryn. Tester déni/annulation, timeout, app tuée pendant navigation, mauvais host/path/state, nonce réutilisé, paramètres dupliqués/fragment/token implicite et vérification de domaine révoquée pendant le flux. Ne jamais utiliser de vrai code ou token dans une commande ADB ou un enregistrement d’écran.
- [ ] Après login : tuer/recréer le processus puis redémarrer l’appareil ; restauration via /user, renouvellement et rotation puis redémarrage, expiration/révocation, mode avion aux frontières échange/refresh/validation/logout. Déconnexion locale ne doit jamais ressusciter après redémarrage ; révocation serveur incertaine doit être visible sans retry silencieux.
- [ ] Sur ce profil jetable, vérifier ciphertext seul sous no_backup/community_auth, alias Keystore et exclusion backup/device-transfer, altération/perte de clé traitées sans fallback, effacement à logout. Contrôler logcat privé sans le commiter ni afficher credentials/codes/verifier. Ne pas modifier l’alias d’une installation personnelle pour tester.
- [ ] Révoquer/changer la sélection et décocher chaque droit avant confirmation ; aucune reprise automatique après erreur, redémarrage ou reconnexion. Aucun upload ni téléchargement réel depuis Android tant que les gates restent fermées. Le retrait serveur et l’accès post-retrait se testent séparément sur le backend isolé, ci-dessous.

## Checklist backend isolé — needs_validation

- [ ] Projet et comptes jetables distincts de production ; confirmer project ref, propriétaire, URL, stockage privé et secrets serveur via canal autorisé, sans clé privilégiée dans Android. Aucune modification de production. Déploiement/test isolé à réaliser séparément, jamais présenté comme effectué ici.
- [ ] Deux utilisateurs A/B, anonyme, compte modérateur vérifié et compte sans ce rôle. Vérifier RLS sur toutes les tables et politiques Storage par lectures/écritures/retraits croisés ; owner/statut/approbation/quota impossibles à s’auto-attribuer. Examiner migrations et privilèges effectifs, pas uniquement les réponses HTTP.
- [ ] Exécuter les contrats existants des six Edge Functions sur cette instance : création autorisée avec droits, quotas/expiration pending, validation EPUB réelle et finalisation pending, modération serveur, téléchargement published, rétractation immédiate et nettoyage/retry. Nettoyage accessible uniquement à l’autorité serveur ; modération refusée aux comptes ordinaires. Confirmer 401/403/404/429/500 et erreurs sans fuite.
- [ ] Retrait par A : fiche introuvable et téléchargement refusé immédiatement, objet supprimé ou cleanupPending sans réapparition, anciennes URL inutilisables après leur expiration. Vérifier aussi l’accès inter-propriétaires et aux objets pending/rejected/removed. Aucun bucket public ni URL longue durée.
- [ ] Vérifier taille/décompression/path traversal/XML/EPUB côté serveur, quotas DB, limites globales/IP/téléchargement et Cron effectif ; confirmer service_role exclusivement côté serveur, logs expurgés et clé de nettoyage limitée. Les limites d’abus et Cron restaient à valider dans SECURITY-PLAN.
- [ ] Sonde JVM Auth réelle optionnelle `IsolatedAuthContractTest` : fournir les six variables ORYN_AUTH_TEST_* via un gestionnaire de secrets du runner, dont ISOLATED_VERIFIED=true après contrôle d’isolation. Aucun token dans Git, arguments de commande ou logs ; test en lecture seule /user, aucun endpoint de production. Sans configuration complète, conserver le skip et la fonctionnalité désactivée.
- [ ] Joindre les preuves expurgées, résultats négatifs, commit audité et revue indépendante du workflow Cloudflare. Aucune ouverture des gates sur la seule base des tests simulés et builds Android.

## Résultats et blocages

- `:app:testDebugUnitTest` : **103 tests, 102 réussis, 1 ignoré, 0 échec/erreur**. Le seul skip est `IsolatedAuthContractTest.verifiesDedicatedTestUserAgainstIsolatedAuth` : aucune configuration isolée fournie. Dix nouvelles régressions Edge/Auth/consentement, tests HTTP TLS locaux sans bypass de certificat.
- `:app:assembleDebug` et `:app:assembleRelease` : **réussis**, lintVitalRelease inclus. Release produit `app-release-unsigned.apk` : compilation, pas signature/distribution.
- `:app:assembleDebugAndroidTest` : **réussi**, cinq scénarios compilés. `connectedDebugAndroidTest` **non exécuté** : `adb devices -l` ne liste aucun appareil/émulateur.
- `scripts/check-community-release.py` : **réussi** sur les APK construits ; contrôle négatif avec l’APK debug comme release **refusé comme attendu**. Aucun marqueur synthétique dans release ; contrôle positif debug complet.
- `git diff --check`, examen des sources ajoutées et comparaison des hashes avec l’état initial : **réussis**. Recherche de secrets sur sources/docs/logs de build/tests et DEX debug/release : aucune correspondance des motifs contrôlés. Aucun fichier temporaire ajouté au dépôt.

Aucune configuration isolée/domaine vérifié fourni. Tests instrumentés compilés uniquement ; OAuth navigateur, Keystore matériel, sauvegarde, App Links OS, RLS/Storage, retrait et nettoyage réels non exécutés. Aucun accès production, réseau communautaire ou upload activé.


## Fichiers modifiés pendant la phase 6

- `community/CommunityCatalog.kt`, `RemoteCommunityCatalogRepository.kt`, `auth/SupabaseAuthController.kt` : identité de session courante avant livraison.
- `community/CommunityTransferPreparation.kt`, `edge/CommunityEdgeModels.kt`, `edge/CommunityEdgeRepository.kt` : consentement révocable/unique et rejet/effacement des résultats tardifs.
- `i18n/I18n.kt`, variantes debug/release `i18n/CommunityDevelopmentTranslations.kt` : exclusion des traductions de fixtures de release.
- `src/test/.../edge/CommunityEdgeRepositoryTest.kt`, `EdgeTestFixture.kt`, `src/androidTest/.../CommunityAndroidScenariosTest.kt` : régressions d’intégration et scénarios Android préparés.
- `scripts/check-community-release.py` et le présent rapport : garde d’artefacts et checklist de validation. Les chemins Kotlin ci-dessus sont relatifs à `app/src/main/kotlin/com/example/oryn/reader`, sauf source set précisé.
