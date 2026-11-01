# Revue d'implémentation — catalogue communautaire

Date : 2026-11-01  
Branche : `feature/community-epub-catalog`  
Périmètre : migration initiale et deux Edge Functions.  
Statut : **non déployé, non exécuté sur un projet Supabase**.

## Décision

Deux opérations serveur sont écrites : création d'une fiche en attente après consentement, puis finalisation technique après validation EPUB et calcul SHA-256. La validation ne publie jamais le livre : il reste `pending` jusqu'à une modération séparée.

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

## Bloquants restants

1. Cycle de vie Storage : retrait puis suppression idempotente de l'objet.
2. Téléchargement : vérifier le statut publié et la politique à chaque demande.
3. Modération séparée, explicitement autorisée.
4. Quotas, rate limits, nettoyage des uploads abandonnés, signalement et retrait copyright.
5. Tests runtime avec deux utilisateurs et rôle anonyme ; advisors Supabase.
6. Vérifier les schémas exposés Data API dans un vrai projet.
7. Audit Cloudflare non exécuté ; aucun environnement isolé disponible ici.

## Vérifications

- Fichiers relus sur GitHub après commit.
- Tests Deno écrits pour les métadonnées et les contrôles EPUB ; **non exécutés**.
- Aucun SQL exécuté, aucune migration appliquée, aucune Edge Function déployée.
- Aucun test RLS/Storage runtime, aucun advisor, aucun build Android.

## Références

- [Edge Function limits](https://supabase.com/docs/guides/functions/limits)
- [Edge Functions — Auth legacy JWT](https://supabase.com/docs/guides/functions/auth-legacy-jwt)
- [Authorization headers](https://supabase.com/docs/guides/functions/auth-headers)
- [CORS](https://supabase.com/docs/guides/functions/cors)
- [Supabase Changelog](https://supabase.com/changelog?tags=edge+functions)
