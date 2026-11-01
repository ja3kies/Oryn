# Revue d'implémentation — catalogue communautaire

Date : 2026-11-01  
Branche : `feature/community-epub-catalog`  
Périmètre : deux migrations et six Edge Functions.  
Statut : **non déployé, non exécuté sur un projet Supabase**.

## Décision

Six Edge Functions serveur couvrent la création après consentement, la validation EPUB, le retrait, le téléchargement authentifié, la modération et le nettoyage des uploads expirés. La validation ne publie jamais le livre : il reste `pending` jusqu'à une modération séparée.

## `catalog-create-upload`

- JWT activé dans la configuration et bearer token revérifié via Auth `getUser(token)`.
- Refus des utilisateurs anonymes.
- Liste blanche stricte des champs ; propriétaire dérivé de l'utilisateur Auth vérifié.
- Consentement explicite obligatoire ; déclaration versionnée côté serveur.
- Corps JSON borné à 8 KiB ; insertion uniquement en `pending`.
- Chemin Storage généré par la base, upload séparé via session utilisateur.
- Bucket privé, limite ramenée à 8 MiB pour cadrer avec le budget de validation.

## `catalog-finalize-upload`

- Accepte uniquement un UUID de fiche ; aucune clé Storage fournie par le client.
- Vérifie le propriétaire et le statut `pending` avant de télécharger l'objet.
- Validation ZIP/EPUB avec zip.js 2.24.0 ; parsing XML avec fast-xml-parser 5.11.2.
- Contrôle du premier `mimetype` non compressé, du MIME exact, de `META-INF/container.xml`, du chemin OPF, des métadonnées, manifest et spine.
- Rejette chemins absolus/traversée, collisions, chiffrement, symlinks, ZIP64, multi-disques, méthodes de compression non prises en charge et CRC/en-têtes incohérents.
- Limites : 8 MiB compressés, 250 entrées, 8 MiB par entrée, 32 MiB décompressés au total, XML de 1 MiB, ratio maximal 100:1.
- Chaque entrée est lue via un flux borné ; SHA-256 calculé côté serveur.
- Seul le hash est enregistré ; statut inchangé à `pending`. La modération reste distincte.
- Finalisation répétée idempotente après enregistrement du hash.

Cette validation structurelle ne constitue ni un antivirus, ni une vérification des droits, ni une garantie de compatibilité avec tous les lecteurs EPUB.

## Point de capacité à mesurer

La documentation Supabase annonce 256 MiB de mémoire et 2 secondes de CPU par requête Edge Function. Les limites d'archive sont volontairement conservatrices, mais cela ne garantit pas que tous les EPUB valides seront traités dans le budget CPU. Un benchmark sur projet de développement et fichiers représentatifs est obligatoire avant déploiement. Si le plafond CPU est atteint, déplacer la décompression vers un worker dédié ; ne pas supprimer les contrôles ni relever les limites à l'aveugle.

## Corrections de sécurité conservées

- Pas de `SECURITY DEFINER` dans le schéma exposé `public`.
- Helper interne `private`, `search_path = ''`, privilèges limités.
- RLS activé et forcé ; aucun INSERT/UPDATE/DELETE direct côté client.
- Bucket privé et upload limité au chemin généré de la fiche pending du propriétaire.
- Clé service-role exclusivement côté serveur.

## `catalog-retract`

- Authentifie l'utilisateur et refuse les comptes anonymes.
- Accepte uniquement un UUID ; refuse les champs `owner_id` et `storage_key`.
- Recherche la fiche par ID et propriétaire vérifié.
- Contrôle le format de la clé Storage générée par la base.
- Passe la fiche à `removed` et renseigne `removed_at` avant toute suppression de fichier.
- Conserve la ligne comme tombstone ; aucun effacement de compte ou d'autres données.
- En cas d'échec Storage, retourne HTTP 202 avec nettoyage à reprendre ; une nouvelle requête retente la suppression.
- Une fiche déjà retirée peut être rappelée pour terminer le nettoyage.

Le retrait du catalogue est donc prioritaire sur le nettoyage physique : un échec Storage ne réactive jamais la visibilité du livre.

## `catalog-download`

- JWT vérifié via Auth `getUser(token)` ; comptes anonymes refusés.
- Corps POST/JSON limité à 2 KiB et strictement réduit à un UUID `bookId`.
- Chaque demande re-vérifie `status = published`, le hash SHA-256, `published_at` et l'absence de `removed_at`.
- Les fiches absentes, pending, rejected ou removed produisent le même 404.
- Le chemin Storage est relu depuis la base et comparé au format généré `owner_id/book_id.epub` ; aucun chemin client n'est accepté.
- Le fichier privé est récupéré côté serveur depuis le chemin généré en base puis transmis en flux par l'Edge Function authentifiée.
- Taille recontrôlée à 8 MiB maximum ; réponse `application/epub+zip`, disposition attachment et en-têtes `private, no-store`.
- Aucun lien Storage signé ni jeton de téléchargement n'est exposé au client.

Un téléchargement déjà commencé et une copie locale déjà enregistrée ne peuvent pas être rappelés. Le retrait empêche les nouvelles autorisations avant le début du nettoyage Storage.

## Bloquants restants

1. Configurer `CATALOG_MODERATOR_USER_IDS` comme secret dans un projet Supabase réel, puis tester les sessions modérateur et non-modérateur.
2. Quotas, rate limits, nettoyage des uploads abandonnés, signalement et retrait copyright.
3. Tests runtime avec deux utilisateurs et rôle anonyme ; advisors Supabase.
4. Vérifier les schémas exposés Data API dans un vrai projet.
5. Audit Cloudflare non exécuté ; aucun environnement isolé disponible ici.


## Quotas et nettoyage des uploads abandonnés

- Migration dédiée : `20261101000200_catalog_quotas_and_expiry.sql`.
- Limites atomiques par propriétaire : 5 créations par heure glissante, 10 livres actifs (pending + published), 3 uploads pending.
- Verrou transactionnel par propriétaire pour empêcher le contournement par requêtes concurrentes.
- Réponse HTTP 429 pour les limites atteintes.
- Expiration des uploads pending après 24 h ; la validation EPUB supprime l'échéance. Le nettoyage attend ensuite 15 minutes de grâce avant le changement d'état et la suppression Storage.
- `catalog-cleanup` passe d'abord les fiches expirées à `expired`, puis supprime l'objet privé. La ligne est conservée comme tombstone ; les échecs Storage sont retentés lors des exécutions suivantes.
- Fonction planifiée protégée par `CATALOG_CLEANUP_SECRET` (au moins 32 caractères), envoyé dans `x-catalog-cleanup-secret`. `verify_jwt = false` est compensé par ce secret serveur ; la planification Supabase Cron reste à configurer.

Les limites globales/IP et la protection contre les abus de téléchargement ne sont pas encore implémentées.

## Vérifications

- Fichiers relus sur GitHub après commit.
- Tests Deno écrits pour les métadonnées, la validation EPUB, le retrait, le téléchargement et la modération ; **non exécutés**.
- Contrainte SQL de publication ajustée pour permettre le retrait d'un livre publié tout en conservant son horodatage de publication.
- Aucun SQL exécuté, aucune migration appliquée, aucune Edge Function déployée.
- Aucun test RLS/Storage runtime, aucun advisor, aucun build Android.

## Références

- [Edge Function limits](https://supabase.com/docs/guides/functions/limits)
- [Edge Functions — Auth legacy JWT](https://supabase.com/docs/guides/functions/auth-legacy-jwt)
- [Authorization headers](https://supabase.com/docs/guides/functions/auth-headers)
- [CORS](https://supabase.com/docs/guides/functions/cors)
- [Supabase Changelog](https://supabase.com/changelog?tags=edge+functions)


## `catalog-moderate`

- Nouvelle Edge Function POST/JSON, corps limité à 2 KiB, champs stricts `bookId` et `decision`.
- Authentifie le bearer token via Auth `getUser(token)` et refuse les comptes anonymes.
- Autorisation basée uniquement sur la variable serveur `CATALOG_MODERATOR_USER_IDS` (UUID Auth séparés par des virgules) ; aucun rôle issu des métadonnées modifiables par le client.
- Publication/rejet uniquement si la fiche est encore `pending`, possède un hash SHA-256 et n'est pas retirée.
- Mise à jour conditionnelle atomique : une rétractation ou une modération concurrente ne peut pas être écrasée.
- `published_at` est renseigné uniquement lors de la publication ; les états non modérables partagent une réponse 409.
- `verify_jwt = true` ajouté à `supabase/config.toml`.

Secret Edge Function requis : `CATALOG_MODERATOR_USER_IDS`. Aucun UUID de modérateur ni secret n'est enregistré dans Git. Configuration absente ou mal formée : refus fermé (503).

Tests unitaires écrits mais non exécutés. Aucun déploiement ni test runtime Supabase effectué.
