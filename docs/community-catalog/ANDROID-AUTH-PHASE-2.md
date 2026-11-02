# Android Auth PKCE — phase 2

## Audit préalable des 10 fichiers de phase 1

Audit réalisé avant modification, le 2 novembre 2026, sur la branche `feature/community-epub-catalog`. Les modifications non commitées de phase 1 sont conservées.

| Fichier | Résultat de lecture |
|---|---|
| `app/build.gradle.kts` | Configuration locale ignorée par Git, clé publishable seulement ; aucune configuration Auth ni redirection. |
| `community/CommunityCatalog.kt` | Modèles distincts de Room, verrou privé constant false ; interface de session sans implémentation persistante. |
| `community/CommunityCatalogViewModel.kt` | StateFlow, scope annulé avec le ViewModel, pas de réseau direct. |
| `community/RemoteCommunityCatalogRepository.kt` | Verrou avant lecture de session, erreurs génériques, annulation propagée, aucune mutation. |
| `community/SupabaseCommunityCatalogApi.kt` | HTTPS, aucune redirection ni retry automatique, réponse bornée, appel annulable ; aucun endpoint upload. |
| `ui/screen/CommunityCatalogSection.kt` | États FR/EN, tokens visuels, connexion absente ; région TalkBack trop large à limiter au message de statut lors de l'ajout Auth. |
| `ui/screen/ExploreScreen.kt` | Communauté séparée, aucun changement au pipeline local ; erreurs techniques Gutendex préexistantes hors périmètre Auth. |
| `i18n/I18n.kt` | Nouvelles chaînes de phase 1 couvertes FR/EN, aucun token ni donnée de compte. |
| `CommunityCatalogTest.kt` | Trois tests de non-activation et validation de configuration, aucun test de session encore. |
| `ANDROID-PHASE-1.md` | Limites et vérifications annoncées explicitement, pas de prétention de validation runtime Supabase. |

Aucun contournement du verrou ou partage automatique identifié sur ce périmètre. Ce contrôle de code ne constitue pas l'audit complet avant publication prévu par SECURITY-PLAN.md. Le manifeste autorise les backups de préférences locales ; le stockage Auth utilisera exclusivement `noBackupFilesDir`, sans modifier cette politique ni Room.

## Implémentation et frontières

- Flux OAuth PKCE via navigateur Android externe, jamais via WebView : `/auth/v1/authorize`, `code_challenge_method=s256`, échange POST `/token?grant_type=pkce`. Le fournisseur OAuth est configuré, sans valeur par défaut.
- Le code verifier et le nonce sont indépendants (64 caractères aléatoires chacun). Le nonce `oryn_state` est conservé dans `redirect_to`, distinct du state OAuth géré par Supabase. Une seule tentative, cinq minutes, consommée durablement avant échange. Annulation/erreur ambiguë : nouvelle tentative requise. Les codes implicites/access tokens en fragment sont refusés.
- Callback HTTPS uniquement, hôte exact configuré, chemin exact `/auth/callback`, sans port, userinfo, fragment, doublons ni champs inconnus. Activity dédiée, aucune URI Auth acheminée vers l'import de fichiers de MainActivity. Intent nettoyé et aucun callback transmis à MainActivity.
- App Links `autoVerify` ; Android 12+ exige le statut système `VERIFIED`, pas une simple sélection utilisateur. API 24–30 : résolution par défaut vers Oryn obligatoire ; contrôle `assetlinks`/adb externe requis car l'API publique Android ne donne pas le statut de vérification par hôte. PKCE et nonce restent exigés sur toutes les versions.
- Restauration et retour au premier plan : revalidation serveur `/user`, refus des comptes anonymes et des changements d'identité. Aucun droit issu de `user_metadata` ou d'un JWT décodé localement. Refresh à moins de 60 secondes de l'expiration, à la demande ou pendant l'affichage actif de Communauté ; aucun travail en arrière-plan. Rotations sérialisées par Mutex, nouveau refresh token sauvegardé atomiquement avant une vérification utilisateur pouvant échouer hors ligne. Pas de retry automatique après erreur ; nouvelle action/retour au premier plan nécessaire.
- Déconnexion : suppression locale durable avant révocation de la session courante (`scope=local`). En cas d'échec/annulation, message explicite : révocation distante non confirmée. Les JWT déjà émis peuvent rester valables jusqu'à leur expiration ; aucun mécanisme client ne garantit leur révocation immédiate.
- Tokens, verifier et nonce : fichier AES-256-GCM authentifié, IV neuf par écriture, AAD liée au package/version, clé non exportable Android Keystore. Fichier atomique dans `noBackupFilesDir` : ni backup cloud ni transfert d'appareil. Liaison cryptographique de la configuration au contenu sauvegardé ; changement de configuration = session/tentative effacée. Données corrompues, clé perdue ou stockage défaillant : refus fermé, aucun stockage clair de secours. Les chaînes sensibles ne sont jamais des data classes, ni placées dans SavedState, Intent sortant ou logs. Les tableaux temporaires clairs sont effacés ; les chaînes Kotlin restent sujettes au GC.
- API bornée à 64 KiB, HTTPS avec validation TLS normale, délais, pas de redirection, pas de retry HTTP automatique, pas de cache/cookie/journalisation. Pas de clé privilégiée, de secret OAuth fournisseur ou de mot de passe dans l'APK.

Le client REST réutilise OkHttp 4.12.0 déjà présent : pas de SDK Supabase/Ktor/Serialization supplémentaire ni de relèvement de minSdk. Références consultées : [PKCE](https://supabase.com/docs/guides/auth/sessions/pkce-flow), [sessions](https://supabase.com/docs/guides/auth/sessions), [redirect URLs](https://supabase.com/docs/guides/auth/redirect-urls), OpenAPI du serveur Auth et changelog (TLS/CBC et API_EXTERNAL_URL self-hosted). Le chemin externe reste l'origine gateway + `/auth/v1` ; aucune modification de la configuration serveur.

## Activation réservée à un environnement isolé

Aucune configuration externe fournie pendant ce travail ; Auth reste désactivé. Aucun endpoint Supabase réel n'est appelé par défaut. Le catalogue conserve `PRODUCTION_VERIFIED = false` et aucune opération de partage n'existe.

Uniquement dans `local.properties` ignoré par Git, fournir de vraies valeurs contrôlées :

| Propriété | Contrat |
|---|---|
| `community.supabase.url` | Origine gateway HTTPS du projet de test isolé, aucun chemin/query/identifiant |
| `community.supabase.publishableKey` | Clé publique `sb_publishable_…` du même projet ; jamais secret/service_role |
| `community.auth.provider` | Fournisseur OAuth configuré dans ce projet (`github`, `google`, `azure`, `apple`, `gitlab`, `keycloak`) |
| `community.auth.redirectHost` | Hôte HTTPS réellement contrôlé, en minuscules, sans chemin ni port |
| `community.auth.isolatedVerified` | `true` seulement après vérification manuelle de l'isolation et d'assetlinks |
| `community.auth.enabled` | `true` pour le build debug isolé uniquement |

Sans toutes ces propriétés valides : bouton de connexion absent, callback désactivé dans le manifeste, aucune restauration ou requête Auth. Release impose toujours Auth désactivé, même si local.properties demande l'activation. Ne jamais pointer ces propriétés vers la production pour tester.

L'hôte de retour doit servir `/.well-known/assetlinks.json`, avec le package `com.example.oryn.reader` et l'empreinte SHA-256 du certificat de signature du build debug de test. Configurer dans Supabase isolé l'URL de retour HTTPS exacte (hôte + `/auth/callback`) et vérifier que le nonce de query est conservé. Éviter les wildcards d'hôte/chemin ; les règles de query acceptées doivent être contrôlées sur la version Auth de test. Ne pas activer si Supabase retombe sur Site URL ou supprime le nonce. Le callback doit être traité dans l'application ; ne pas héberger de scripts/analytics susceptibles de copier les codes. Un Supabase local doit être exposé par une passerelle HTTPS de test correctement certifiée, sans autoriser HTTP/cleartext ni désactiver la validation TLS dans l'application.

## Tests isolés préparés et contrôles sur appareil

1. Installer le build debug avec configuration isolée, lancer `adb shell pm verify-app-links --re-verify com.example.oryn.reader`, puis contrôler `adb shell pm get-app-links com.example.oryn.reader` (commandes Android 12+). Vérifier que seul le domaine contrôlé est vérifié ; contrôle équivalent via paramètres/adb sur API 24–30.
2. Parcours navigateur réel : connexion, retour App Link, retour arrière/annulation, code expiré, rejeu, état erroné, hôte/chemin/port/fragment falsifiés. Ne jamais coller de vrais tokens/codes dans une commande conservée dans l'historique. Vérifier qu'aucun lien ne déclenche l'import EPUB.
3. Fermer/relancer le processus pendant le navigateur puis après connexion. Vérifier la restauration et la revalidation ; avancer l'expiration sur le projet isolé puis vérifier une seule rotation et la reprise hors ligne. Déconnexion pendant une requête et après coupure réseau : aucun token local restant.
4. Sur appareil, inspecter seulement les métadonnées du coffre : fichier sous `no_backup/community_auth`, contenu non lisible en clair, absence dans backup/transfert, effacement/refus après corruption ou perte de clé. Ne jamais imprimer le contenu ou les valeurs Keystore. Valider TalkBack, grande police et clair/sombre.
5. Test de contrat optionnel `IsolatedAuthContractTest` : fournir par environnement `ORYN_AUTH_TEST_ISOLATED_VERIFIED=true`, `ORYN_AUTH_TEST_URL`, `ORYN_AUTH_TEST_PUBLISHABLE_KEY`, `ORYN_AUTH_TEST_REDIRECT_HOST`, `ORYN_AUTH_TEST_PROVIDER`, et `ORYN_AUTH_TEST_ACCESS_TOKEN` d'un utilisateur de test dédié. Puis `./gradlew --no-daemon :app:testDebugUnitTest --tests '*IsolatedAuthContractTest'`. Ce test n'effectue qu'un GET `/user`, sans compte créé, rotation ni révocation. Utiliser un lanceur qui ne journalise pas les variables ; ne jamais passer de token en argument Gradle ni activer `--debug`. Configuration absente : test explicitement ignoré, sans valeur fictive de secours.

Les tests JVM hermétiques utilisent uniquement des fixtures `*.example.test` et un serveur HTTPS loopback avec CA générée pour les tests. Cette réécriture d'URL et cette CA sont absentes du code applicatif. Ils ne remplacent pas les tests d'un vrai serveur Supabase ni ceux d'Android Keystore/App Links sur appareil.

## Vérification finale

`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:processReleaseMainManifest :app:generateReleaseBuildConfig` : **BUILD SUCCESSFUL**. 35 tests découverts : **34 réussis, 0 échec, 1 ignoré** (contrat Supabase isolé non configuré). Tests Auth/session/PKCE, transport HTTPS local, annulations, renouvellement concurrent, conservation de rotation après erreur de vérification, expiration pendant vérification, corruption/AAD/IV et trois tests de verrou catalogue de phase 1.

Manifestes fusionnés debug/release contrôlés : callback désactivé, HTTPS uniquement, chemin exact, autoVerify, aucun domaine fictif ; constantes Auth false. Configurations applicatives non renseignées, aucun endpoint Supabase réel appelé. `git diff --check` passe. Aucun appel Log/println/printStackTrace, aucun token en SavedState/Intent sortant ; clés configurées absentes de l'APK généré. Les seules valeurs de test sont des fixtures dans `src/test`, jamais utilisées comme valeurs applicatives de secours.

Contrôle de régression : Explorer/Gutendex, MainActivity/MainViewModel, Room, data/import, backup_rules/data_extraction_rules et `supabase/` inchangés. Le gate catalogue reste strictement false ; aucun upload, partage ou download communautaire ajouté. Aucun commit/push effectué et les modifications de phase 1 restent préservées.

### Fichiers de phase 2

- Modifiés : `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `community/CommunityCatalog.kt` (documentation du contrat), `ui/screen/CommunityCatalogSection.kt`, `i18n/I18n.kt`.
- Ajoutés sous `app/src/main/kotlin/com/example/oryn/reader/` : `community/auth/AuthModels.kt`, `Pkce.kt`, `SupabaseAuthApi.kt`, `SupabaseAuthController.kt`, `SecureAuthStore.kt`, `CommunityAuthRuntime.kt`, `CommunityAuthCallbackActivity.kt`, ainsi que `ui/screen/CommunityAuthSection.kt`.
- Ajoutés sous `app/src/test/kotlin/com/example/oryn/reader/community/auth/` : `AuthTestFixtures.kt`, `PkceTest.kt`, `AuthControllerTest.kt`, `AuthVaultTest.kt`, `SupabaseAuthApiTest.kt`, `IsolatedAuthContractTest.kt`.
- Ajouté : ce rapport d'audit et protocole de validation.

### Blocages et suite

Pas de projet Auth isolé configuré, de fournisseur OAuth, de domaine App Link/certificat déclaré ni d'appareil de test attaché. Les tests backend réels, la vérification système du domaine et Android Keystore/backup sur appareil ne sont donc pas exécutés. Auth demeure désactivé, et release/catalogue/partage restent verrouillés. Prochaine étape : fournir et vérifier ces configurations dans un environnement isolé, exécuter le protocole sur API 24 et 31+, sans activation de production.
