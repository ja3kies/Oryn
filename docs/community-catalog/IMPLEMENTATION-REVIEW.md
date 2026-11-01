# Revue d'implémentation — catalogue communautaire

Date : 2026-11-01  
Branche : `feature/community-epub-catalog`  
Périmètre : revue statique de la migration, de la première Edge Function et de leur documentation.  
Statut : **non déployé, non exécuté sur un projet Supabase**.

## Décision

La première opération serveur est maintenant écrite : création d'une fiche EPUB en attente après authentification et consentement. Elle ne téléverse aucun fichier et ne rend aucun livre public. Ne pas intégrer Android avant la validation des flux serveur et des tests d'exécution.

## Première Edge Function : `catalog-create-upload`

Fichiers : `supabase/functions/catalog-create-upload/`

Contrôles présents :

- `verify_jwt = true` dans la configuration CLI.
- Vérification indépendante du bearer token via Auth `getUser(token)`.
- Refus des utilisateurs anonymes.
- Liste blanche stricte des champs ; les champs d'identité et de stockage fournis par le client sont rejetés.
- `owner_id` dérivé uniquement de l'utilisateur Auth vérifié.
- Consentement explicite obligatoire ; version de déclaration fixée côté serveur.
- Métadonnées bornées et normalisées.
- Corps HTTP limité à 8 KiB pendant la lecture en flux, même si `Content-Length` est absent.
- Insertion uniquement en statut `pending`.
- Clé Storage générée par la base ; aucun nom de fichier utilisateur.
- Clé service-role utilisée uniquement côté Edge Function.
- Réponse sans détails SQL, sans clé secrète et sans lien de téléchargement.

La réponse fournit le chemin exact du bucket privé pour un upload séparé depuis Android, avec la session utilisateur. Cette fonction ne vérifie pas encore le contenu EPUB ; la publication est donc impossible à ce stade.

## Corrections précédentes conservées

### Fonctions privilégiées hors du schéma exposé

- Pas de fonction `SECURITY DEFINER` dans `public`.
- Helper Storage placé dans `private`, `search_path = ''`, noms qualifiés.
- Exécution révoquée à `PUBLIC` et `anon`, accordée à `authenticated`.
- `private` doit rester absent des schémas exposés par la Data API.

### Droits et Storage

- RLS activé et forcé sur la table.
- Lecture limitée aux livres publiés ou appartenant à l'utilisateur, avec colonnes explicites.
- Aucun INSERT/UPDATE/DELETE direct côté client.
- Bucket privé, limite 50 MiB, upload seulement vers le chemin exact d'une fiche pending appartenant à l'utilisateur.
- Aucune policy client de lecture, remplacement ou suppression de l'objet.

## Bloquants restants

1. **Finalisation/validation EPUB** : contrôler ZIP, entrée `mimetype`, `META-INF/container.xml`, OPF, chemins, collisions, archives chiffrées, nombre d'entrées, tailles compressées/décompressées et calculer SHA-256 côté serveur.
2. **Cycle de vie Storage** : retrait immédiat de la fiche puis suppression idempotente de l'objet.
3. **Téléchargement** : vérifier le statut publié et les quotas à chaque demande ; URL signée à durée courte ou streaming.
4. **Modération** : voie administrative séparée et explicitement autorisée.
5. **Anti-abus** : rate limits, quotas, prévention des créations pending abandonnées et journalisation non sensible.
6. **Tests runtime** : deux utilisateurs + rôle anonyme ; lecture, création, upload, remplacement, publication, téléchargement, retrait et isolation croisée.
7. **Configuration Data API** : vérifier les schémas exposés et grants réels dans un projet de développement.
8. **Audit Cloudflare** : non exécuté ; à lancer après intégration complète, dans l'environnement isolé exigé par le skill.

## Vérifications

- Relecture statique des fichiers sur GitHub effectuée après écriture.
- Tests Deno ajoutés pour les validations de métadonnées ; **pas exécutés**, car aucun runtime Deno ni projet Supabase n'est relié ici.
- Aucun SQL exécuté, aucune migration appliquée, aucune Edge Function déployée.
- Aucun test RLS/Storage runtime, aucun advisor Supabase, aucun build Android.

## Références officielles

- [Edge Functions — Auth legacy JWT](https://supabase.com/docs/guides/functions/auth-legacy-jwt)
- [Authorization headers](https://supabase.com/docs/guides/functions/auth-headers)
- [CORS](https://supabase.com/docs/guides/functions/cors)
- [Supabase Changelog](https://supabase.com/changelog?tags=edge+functions)
