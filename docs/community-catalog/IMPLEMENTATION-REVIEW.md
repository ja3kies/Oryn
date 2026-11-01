# Revue d'implémentation — catalogue communautaire

Date : 2026-11-01  
Branche : `feature/community-epub-catalog`  
Périmètre : revue statique de la migration et de sa documentation.  
Statut : **non déployé, non exécuté sur un projet Supabase**.

## Décision

La migration initiale a été corrigée dans GitHub avant tout déploiement connu. Ne pas intégrer Android ni activer le catalogue tant que les contrôles ci-dessous ne sont pas terminés.

## Corrections apportées

### 1. Fonctions privilégiées hors du schéma exposé

- Suppression des fonctions `SECURITY DEFINER` dans `public` pour la création et le retrait.
- Déplacement du seul helper nécessaire à la policy Storage vers `private.can_upload_community_epub(text)`.
- `search_path = ''`, noms de schémas qualifiés, contrôle via `auth.uid()`.
- Exécution révoquée à `PUBLIC` et `anon`, accordée uniquement à `authenticated`.
- Le schéma `private` ne doit jamais être ajouté aux schémas exposés par la Data API.

Création, retrait, publication, validation et téléchargement sont désormais explicitement réservés à des Edge Functions de confiance qui valident le JWT avec Supabase Auth avant d'utiliser un client serveur. Ces Edge Functions ne sont pas encore implémentées.

### 2. Droits de la table

- RLS activé et forcé.
- Lecture limitée aux lignes publiées ou appartenant à l'utilisateur authentifié.
- Privilèges directs de la table révoqués, puis SELECT limité à certaines colonnes accordé à `authenticated`.
- Aucun INSERT/UPDATE/DELETE direct côté client.

### 3. Storage

- Bucket privé, limite de 50 MiB et MIME déclarés EPUB/ZIP.
- Policy INSERT limitée au chemin généré du livre pending appartenant à l'utilisateur.
- Aucune policy client SELECT/UPDATE/DELETE ajoutée pour ce bucket.

Le MIME et la limite de taille ne remplacent pas une validation du contenu EPUB.

## Points bloquants restants

1. **Edge Functions absentes** : création, finalisation/validation, retrait, modération et téléchargement ne sont pas encore implémentés.
2. **Validation EPUB absente** : contrôler ZIP, entrée `mimetype`, `META-INF/container.xml`, OPF, chemins, collisions, archives chiffrées, nombre d'entrées et tailles compressées/décompressées ; calculer SHA-256 côté serveur.
3. **Suppression Storage absente** : le retrait doit masquer la fiche immédiatement puis supprimer l'objet de manière idempotente.
4. **Liens signés et caches** : vérifier le statut publié à chaque demande ; utiliser une durée courte et documenter qu'un lien déjà émis reste utilisable jusqu'à expiration, sauf suppression/invalidation adaptée.
5. **Data API** : vérifier la liste réelle des schémas exposés et les grants dans un projet de développement.
6. **Tests d'exécution absents** : deux utilisateurs indépendants + rôle anonyme ; lecture, création, retrait, upload, remplacement, téléchargement, publication et retrait.
7. **Audit Cloudflare absent** : à exécuter après intégration complète, dans l'environnement isolé requis par le skill.

## Vérifications statiques ajoutées

Le fichier `supabase/tests/community_catalog_security.sql` contient des assertions sur les fonctions privilégiées, les grants, RLS et les policies. Il n'a pas été exécuté : aucun projet Supabase n'est connecté.

## État réel

- Migration modifiée sur GitHub, branche de travail uniquement.
- Aucun projet Supabase accessible via la connexion actuelle.
- Aucun SQL exécuté, aucune migration appliquée, aucun advisor exécuté.
- Aucun EPUB téléversé ; aucune Edge Function déployée.
- Aucun test Android ni audit Cloudflare exécuté.

## Références officielles

- [Database Functions](https://supabase.com/docs/guides/database/functions)
- [Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security)
- [Private Storage downloads](https://supabase.com/docs/guides/storage/serving/downloads)
- [Supabase Changelog — breaking changes](https://supabase.com/changelog?types=breaking-change)
