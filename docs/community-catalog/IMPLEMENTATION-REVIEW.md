# Revue d'implémentation — catalogue communautaire

Date : 2026-11-01  
Branche : `feature/community-epub-catalog`  
Périmètre : revue statique de la migration initiale et de sa documentation.  
Statut : **non déployé, non testé sur un projet Supabase**.

## Décision

Ne pas déployer la migration actuelle et ne pas connecter l'interface Android au catalogue avant correction des points bloquants ci-dessous.

## Constats bloquants

### 1. Fonctions privilégiées dans le schéma exposé `public`

La migration crée `public.create_community_shared_book`, `public.retract_community_shared_book` et `public.can_upload_community_epub` avec `SECURITY DEFINER`.

Les fonctions `create` et `retract` révoquent bien l'exécution à `PUBLIC` et `anon`, accordent l'exécution à `authenticated`, fixent `search_path = ''` et contrôlent `auth.uid()`. Ce sont de bonnes protections, mais elles ne suppriment pas le risque architectural : une fonction `SECURITY DEFINER` dans un schéma exposé peut être appelée via la Data API avec les privilèges de son propriétaire.

**Correction exigée :** déplacer toute logique privilégiée dans un schéma non exposé (par exemple `private`) et n'exposer que des points d'entrée strictement nécessaires, ou déplacer les opérations de création/retrait dans des Edge Functions qui valident le JWT et l'identité côté serveur. Ne jamais résoudre ce point en ajoutant simplement des privilèges client.

La fonction de contrôle Storage doit également être déplacée hors de `public` ou remplacée par un mécanisme qui ne publie pas de fonction privilégiée dans un schéma exposé.

### 2. Exposition Data API à configurer explicitement

Depuis le 30 octobre 2026, les nouvelles tables ne sont plus automatiquement exposées à la Data API par défaut sur les projets concernés. La présence de grants SQL et de RLS ne garantit donc pas que les appels REST/RPC seront disponibles.

**Correction exigée :** documenter et vérifier les paramètres Data API du projet de développement. N'exposer que les schémas nécessaires ; ne pas exposer le schéma interne `private`. Accorder les privilèges minimaux explicitement et conserver RLS sur chaque table exposée.

### 3. Validation EPUB absente

Le MIME déclaré et l'extension `.epub` ne prouvent pas qu'un fichier est un EPUB sûr. La limite Storage de 50 MiB ne borne pas à elle seule la taille décompressée ni les structures ZIP/XML malformées.

**Correction exigée :** validation serveur du conteneur ZIP, de `mimetype`, de `META-INF/container.xml`, du document OPF, des chemins, du nombre d'entrées et des tailles compressées/décompressées ; rejeter les archives chiffrées, les chemins traversants, les collisions et les limites dépassées. Calculer SHA-256 côté serveur. Ne jamais publier sur la base du seul MIME fourni par le client.

### 4. Cycle de vie Storage incomplet

Le retrait modifie immédiatement la ligne en `removed`, mais la suppression physique de l'objet n'est pas implémentée. Les URL signées éventuelles et les caches ne sont pas encore gérés.

**Correction exigée :** implémenter un nettoyage serveur idempotent et vérifier le statut publié à chaque demande de téléchargement. Les URL signées doivent avoir une durée courte ; définir le comportement des retraits vis-à-vis des URL déjà émises et des copies locales téléchargées.

## Points déjà présents dans la migration

- RLS activé et forcé sur `public.community_shared_books`.
- Bucket `community-epubs` privé, limite initiale de 50 MiB.
- Aucun accès anonyme accordé dans les grants décrits.
- Les écritures directes client sur la table sont révoquées.
- Les enregistrements sont créés en état `pending` avec consentement.
- Le chemin Storage est généré depuis l'identité et l'UUID, pas depuis le nom de fichier.
- La policy Storage limite l'upload au chemin d'un enregistrement pending du propriétaire.

Ces éléments sont des propriétés du code SQL relu, pas des résultats de tests d'exécution.

## Ordre de correction recommandé

1. Décider de la frontière privilégiée : Edge Functions pour création/retrait/validation/téléchargement, ou fonctions internes dans un schéma non exposé avec wrappers minimaux.
2. Revoir la migration initiale avant son premier déploiement ; ne pas modifier une migration déjà appliquée sur un environnement partagé.
3. Ajouter des tests négatifs avec deux utilisateurs fictifs et le rôle anonyme : lecture, création, retrait, upload, remplacement, téléchargement et retrait d'un livre.
4. Ajouter la validation EPUB et le nettoyage Storage côté serveur.
5. Vérifier les réglages Data API et les advisors Supabase sur un projet de développement.
6. Intégrer Android seulement après réussite des contrôles précédents.
7. Exécuter ensuite l'audit Cloudflare Security Audit Skill complet, sur un commit identifié et dans l'environnement isolé exigé par le skill.

## Références officielles consultées

- [Supabase — Database Functions](https://supabase.com/docs/guides/database/functions) : préférer `SECURITY INVOKER`, contrôler explicitement les privilèges d'exécution et protéger les fonctions `SECURITY DEFINER`.
- [Supabase — Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security) : ne pas placer de fonctions `SECURITY DEFINER` dans un schéma exposé.
- [Supabase — Changelog Data API](https://supabase.com/changelog?types=breaking-change) : évolution de l'exposition automatique des nouvelles tables, applicable à partir du 30 octobre 2026.
- [Supabase — Private Storage downloads](https://supabase.com/docs/guides/storage/serving/downloads) : utiliser des accès authentifiés ou des URL signées à durée limitée pour les buckets privés.

## Limites de cette revue

Aucun projet Supabase n'est connecté. Aucun SQL n'a été exécuté. Aucun fichier EPUB n'a été téléversé. Aucun test RLS/Storage, advisor, build Android ou audit Cloudflare n'a été lancé. Les points dépendant de la configuration réelle restent à valider.
