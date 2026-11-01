# Revue d'implémentation — catalogue communautaire

Date : 2026-11-01  
Branche : `feature/community-epub-catalog`  
Périmètre : migration initiale et quatre Edge Functions.  
Statut : **non déployé, non exécuté sur un projet Supabase**.

## Décision

Trois opérations serveur sont écrites : création d'une fiche en attente après consentement, puis finalisation technique après validation EPUB et calcul SHA-256. La validation ne publie jamais le livre : il reste `pending` jusqu'à une modération séparée.

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

1. Modération séparée, explicitement autorisée.
2. Quotas, rate limits, nettoyage des uploads abandonnés, signalement et retrait copyright.
3. Tests runtime avec deux utilisateurs et rôle anonyme ; advisors Supabase.
4. Vérifier les schémas exposés Data API dans un vrai projet.
5. Audit Cloudflare non exécuté ; aucun environnement isolé disponible ici.

## Vérifications

- Fichiers relus sur GitHub après commit.
- Tests Deno écrits pour les métadonnées, la validation EPUB, le retrait et le téléchargement ; **non exécutés**.
- Aucun SQL exécuté, aucune migration appliquée, aucune Edge Function déployée.
- Aucun test RLS/Storage runtime, aucun advisor, aucun build Android.

## Références

- [Edge Function limits](https://supabase.com/docs/guides/functions/limits)
- [Edge Functions — Auth legacy JWT](https://supabase.com/docs/guides/functions/auth-legacy-jwt)
- [Authorization headers](https://supabase.com/docs/guides/functions/auth-headers)
- [CORS](https://supabase.com/docs/guides/functions/cors)
- [Supabase Changelog](https://supabase.com/changelog?tags=edge+functions)
