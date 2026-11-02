# Android — phase 1

Le catalogue est indépendant de Room et de Gutendex. Explorer propose un espace Communauté distinct, traduit FR/EN, avec états indisponible, connexion requise, chargement, vide, erreur, hors ligne et contenu. Aucun import, upload, téléchargement ni partage n'est implémenté ici.

## Configuration locale

Dans `local.properties` (ignoré par Git) : `community.supabase.url` et `community.supabase.publishableKey`. Fournir uniquement l'origine HTTPS et une clé `sb_publishable_…`. Les clés legacy JWT, `service_role` et `sb_secret_…` sont refusées. La clé publique sera dans l'APK ; ce n'est pas un secret ni une autorisation utilisateur.

`PRODUCTION_VERIFIED = false` bloque le repository avant même la lecture de session et le client avant chaque requête. Aucune préférence utilisateur ni configuration locale ne peut lever ce verrou. Aucun endpoint Supabase n'a été appelé pendant cette phase.

## Authentification préparée, parcours différé

`CommunitySessionProvider` définit le point d'intégration d'une future session Supabase Auth. L'implémentation livrée reste déconnectée ; aucun mot de passe, jeton d'accès ou de renouvellement n'est stocké. Le client prépare la vérification serveur `/auth/v1/user` (UUID valide et utilisateur non anonyme) avant lecture de métadonnées publiées. Un 401 efface la session ; aucune décision d'autorisation ne dépend de claims JWT décodés localement. Pas de deep link ni de login factice.

Avant activation : choisir et vérifier SDK/PKCE compatible API 24, stockage Android Keystore exclu des sauvegardes, rotation/expiration/révocation des sessions, liens de retour stricts et parcours de connexion accessible. Valider le backend en environnement isolé, RLS avec deux utilisateurs et anonyme, quotas, schémas exposés, disponibilité et audit prescrit par SECURITY-PLAN.md. Le verrou reste fermé jusqu'au déploiement et à la vérification de production autorisés séparément.

## Limites et protections

Lecture de 40 résultats maximum, réponse bornée à 128 KiB, délais réseau, HTTPS sans redirections, sans cache/cookies/logging ni exposition du corps d'erreur. Les métadonnées distantes sont validées ; aucune URL de fichier, identité propriétaire ou chemin local n'est exposé. Les états 404/503 indiquent une indisponibilité, les autres erreurs HTTP une erreur générique ; une erreur de transport indique le réseau indisponible. Pas de tâche en arrière-plan ni de retry automatique.

Le futur partage devra demander une confirmation pour chaque EPUB et enregistrer sa déclaration de droits versionnée côté serveur. Aucune observation de la bibliothèque locale n'est introduite. La recherche/pagination et les mutations restent à développer après validation du contrat backend. Vérification visuelle sur appareil (TalkBack, grande police, contraste, clair/sombre) encore nécessaire avant fusion.

## Contrôles locaux

Tests unitaires du verrou repository/client et du refus des configurations dangereuses. Vérification statique des chaînes FR/EN, des cibles de 48 dp via GlassChip/Button, de l'état sélectionné TalkBack et de la région d'annonce des états. Aucun mouvement ajouté. Contrastes des tokens utilisés : texte/surface clair 12,33:1, sombre 15,39:1 et bouton primaire 12,20:1 (AA). Les erreurs sont distinguées par le texte, sans dépendre de la couleur.

Documentation Supabase consultée : [API keys](https://supabase.com/docs/guides/api/api-keys), [sessions](https://supabase.com/docs/guides/auth/sessions), [password auth](https://supabase.com/docs/guides/auth/passwords) et changelog au 2 novembre 2026. Aucun changement SDK, SQL, RLS ou Storage effectué. La dépendance ViewModel Compose 2.8.7 assure la conservation et l'annulation à la destruction du ViewModel ; elle suit la version Lifecycle du projet.

## Résultat de vérification — 2 novembre 2026

`./gradlew :app:assembleDebug :app:testDebugUnitTest` : **BUILD SUCCESSFUL**, 3 tests, 0 échec, 0 erreur. Environnement préparé avec JDK 17 complet, SDK 35, caches et configuration proxy hors du dépôt. `git diff --check` passe. Les avertissements de dépréciation restants concernent le code existant. Aucun test sur appareil ni sur backend Supabase n'a été exécuté.

Fichiers concernés : `app/build.gradle.kts`, les quatre fichiers Kotlin de `community/`, `ui/screen/CommunityCatalogSection.kt`, `ui/screen/ExploreScreen.kt`, `i18n/I18n.kt`, `CommunityCatalogTest.kt` et ce document. Aucun fichier Room, migration ou fonction serveur modifié.
